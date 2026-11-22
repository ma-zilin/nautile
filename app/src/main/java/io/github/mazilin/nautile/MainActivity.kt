package io.github.mazilin.nautile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mazilin.nautile.di.AppContainer
import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.interaction.AppTab
import io.github.mazilin.nautile.interaction.FullScreenPage
import io.github.mazilin.nautile.interaction.Overlay
import io.github.mazilin.nautile.ui.AppRoot
import io.github.mazilin.nautile.ui.screen.common.PlaceholderScreen
import io.github.mazilin.nautile.ui.screen.settings.DefaultPageScreen
import io.github.mazilin.nautile.ui.screen.settings.SettingsScreen
import io.github.mazilin.nautile.ui.screen.settings.SettingsViewModel
import io.github.mazilin.nautile.ui.screen.todo.GtdSelector
import io.github.mazilin.nautile.ui.screen.todo.NewTaskSheet
import io.github.mazilin.nautile.ui.screen.todo.ProjectDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.TaskDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoViewModel
import io.github.mazilin.nautile.ui.theme.NautileTheme

/**
 * 单 Activity 宿主：装配 AppRoot 并注册系统返回分发（S1-T06/T07）。
 *
 * 返回顺序由 [io.github.mazilin.nautile.interaction.BackDispatcher] 决定；
 * 本类只提供收键盘动作与“交还系统”的兜底。
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as NautileApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NautileTheme {
                val todoViewModel: TodoViewModel = viewModel(
                    factory = remember {
                        viewModelFactory {
                            initializer {
                                TodoViewModel(
                                    taskRepository = container.taskRepository,
                                    projectRepository = container.projectRepository,
                                    settingsRepository = container.settingsRepository,
                                    taskCommands = container.taskCommands,
                                    projectCommands = container.projectCommands,
                                )
                            }
                        }
                    },
                )
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = remember {
                        viewModelFactory {
                            initializer {
                                SettingsViewModel(container.settingsRepository)
                            }
                        }
                    },
                )
                val todoState by todoViewModel.uiState.collectAsStateWithLifecycle()
                val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
                val taskDetailState by todoViewModel.taskDetail.collectAsStateWithLifecycle()
                val projectDetailState by todoViewModel.projectDetail.collectAsStateWithLifecycle()
                val newTaskState by todoViewModel.newTask.collectAsStateWithLifecycle()
                val activeTab by container.navigationCoordinator.activeTab.collectAsStateWithLifecycle()
                val settingsLoaded by settingsViewModel.loaded.collectAsStateWithLifecycle()
                var startupApplied by remember { mutableStateOf(false) }
                var scrollNavigationVisible by remember { mutableStateOf(true) }

                // AC-N-14：等 DataStore 首次发射后再渲染，避免先闪默认待办再跳页。
                LaunchedEffect(settingsLoaded, settings) {
                    if (settingsLoaded) {
                        val startupPage = settings.lastPage ?: settings.defaultPage
                        container.navigationCoordinator.applyInitialTab(startupPage.toTab())
                        startupApplied = true
                    }
                }
                // 切换一级页时记录 lastPage；首次恢复不重复写入用户可见行为。
                LaunchedEffect(activeTab, startupApplied) {
                    if (startupApplied) settingsViewModel.recordLastPage(activeTab.toPage())
                }
                if (!settingsLoaded || !startupApplied) return@NautileTheme

                val requestCloseDetail: () -> Unit = {
                    if (todoViewModel.requestDetailLeave()) {
                        todoViewModel.closeDetail()
                        container.overlayBackStack.close()
                    }
                }
                val detailCanDismiss: () -> Boolean = {
                    val canLeave = todoViewModel.requestDetailLeave()
                    if (canLeave) todoViewModel.closeDetail()
                    canLeave
                }
                val newTaskCanDismiss: () -> Boolean = {
                    val canLeave = todoViewModel.requestNewTaskDismiss()
                    if (canLeave) {
                        todoViewModel.closeNewTask()
                        scrollNavigationVisible = true
                    }
                    canLeave
                }
                val closeNewTaskPanel: () -> Unit = {
                    todoViewModel.closeNewTask()
                    container.overlayBackStack.close()
                    scrollNavigationVisible = true
                }

                AppRoot(
                    navigation = container.navigationCoordinator,
                    overlays = container.overlayBackStack,
                    scrollNavigationVisible = if (activeTab == AppTab.TODO) scrollNavigationVisible else true,
                    tabContent = { tab ->
                        when (tab) {
                            AppTab.TODO -> TodoScreen(
                                state = todoState,
                                onOpenSettings = container.navigationCoordinator::navigateToSettings,
                                onOpenSelector = { container.overlayBackStack.open(Overlay.GtdSelector) },
                                onCompleteTask = todoViewModel::completeTask,
                                onAbandonTask = todoViewModel::abandonTask,
                                onRequestProjectClose = todoViewModel::requestProjectClose,
                                onConfirmProjectClose = todoViewModel::confirmProjectClose,
                                onCancelProjectClose = todoViewModel::cancelProjectClose,
                                onNavigationVisibilityChanged = { scrollNavigationVisible = it },
                                onSwitchPosition = todoViewModel::selectPosition,
                                onOpenTaskDetail = { taskId ->
                                    todoViewModel.openTaskDetail(taskId)
                                    val opened = container.overlayBackStack.open(
                                        Overlay.TaskDetail(taskId.value),
                                        canDismiss = detailCanDismiss,
                                    )
                                    if (!opened) todoViewModel.closeDetail()
                                },
                                onOpenProjectDetail = { projectId ->
                                    todoViewModel.openProjectDetail(projectId)
                                    val opened = container.overlayBackStack.open(
                                        Overlay.ProjectDetail,
                                        canDismiss = detailCanDismiss,
                                    )
                                    if (!opened) todoViewModel.closeDetail()
                                },
                                onConfirmProjectReopen = todoViewModel::confirmProjectReopen,
                                onCancelProjectReopen = todoViewModel::cancelProjectReopen,
                                onOpenNewTask = {
                                    todoViewModel.openNewTask()
                                    val opened = container.overlayBackStack.open(
                                        Overlay.NewTaskSheet,
                                        canDismiss = newTaskCanDismiss,
                                    )
                                    if (!opened) todoViewModel.closeNewTask()
                                },
                                newTaskOpen = newTaskState != null,
                            )

                            else -> PlaceholderScreen(
                                title = tab.title,
                                onOpenSettings = container.navigationCoordinator::navigateToSettings,
                            )
                        }
                    },
                    fullScreenContent = { page ->
                        when (page) {
                            FullScreenPage.Settings -> SettingsScreen(
                                settings = settings,
                                onBack = { container.navigationCoordinator.back() },
                                onOpenDefaultPage = {
                                    container.navigationCoordinator.navigateToDefaultPage()
                                },
                                onPredictiveBackChanged = settingsViewModel::setPredictiveBackEnabled,
                            )

                            FullScreenPage.DefaultPage -> DefaultPageScreen(
                                currentPage = settings.defaultPage,
                                onSelect = settingsViewModel::setDefaultPage,
                                onBack = { container.navigationCoordinator.back() },
                            )
                        }
                    },
                    predictiveBackEnabled = settings.predictiveBackEnabled,
                    overlayContent = { overlay ->
                        when (overlay) {
                            Overlay.GtdSelector -> GtdSelector(
                                currentPosition = todoState.position,
                                tasks = todoState.visibleTasks,
                                onSelect = todoViewModel::selectPosition,
                                onClose = { container.overlayBackStack.close() },
                            )

                            is Overlay.TaskDetail -> taskDetailState?.let { detail ->
                                TaskDetailSheet(
                                    state = detail,
                                    onTitleChanged = todoViewModel::onTaskTitleChanged,
                                    onCommitTitle = todoViewModel::commitTaskTitle,
                                    onReopen = { todoViewModel.reopenTask(detail.task.id) },
                                    onCloseRequested = requestCloseDetail,
                                )
                            }

                            Overlay.ProjectDetail -> projectDetailState?.let { detail ->
                                ProjectDetailSheet(
                                    state = detail,
                                    onNameChanged = todoViewModel::onProjectNameChanged,
                                    onCommitName = todoViewModel::commitProjectName,
                                    onToggleCollapsed = {
                                        todoViewModel.toggleProjectCollapsed(detail.project.id)
                                    },
                                    onCloseRequested = requestCloseDetail,
                                )
                            }

                            Overlay.NewTaskSheet -> newTaskState?.let { state ->
                                NewTaskSheet(
                                    state = state,
                                    onTitleChanged = todoViewModel::onNewTaskTitleChanged,
                                    onCreate = {
                                        todoViewModel.createNewTask { closeNewTaskPanel() }
                                    },
                                    onCancel = closeNewTaskPanel,
                                    onDismissRequest = {
                                        if (newTaskCanDismiss()) closeNewTaskPanel()
                                    },
                                )
                            }

                            else -> Unit
                        }
                    },
                )
            }
        }

        // 观察 IME 可见性，供返回分发“先收键盘”使用；不改动 Insets 分发结果。
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            container.keyboardCoordinator.updateImeVisible(
                insets.isVisible(WindowInsetsCompat.Type.ime()),
            )
            insets
        }
        window.decorView.requestApplyInsets()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (container.backDispatcher.dispatch(::hideIme)) return
                    // 未消费：临时停用自身，交还系统默认返回（回桌面/退出）。
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            },
        )
    }

    private fun hideIme() {
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.ime())
    }
}

/** 启动映射：设置中的一级页面与底部导航 tab 一一对应（AC-N-14）。 */
private fun AppPage.toTab(): AppTab = when (this) {
    AppPage.TODO -> AppTab.TODO
    AppPage.SCHEDULE -> AppTab.SCHEDULE
    AppPage.LOG -> AppTab.LOG
    AppPage.AI -> AppTab.AI
}

/** 记录 lastPage 时把当前 tab 转回设置中的页面标识。 */
private fun AppTab.toPage(): AppPage = when (this) {
    AppTab.TODO -> AppPage.TODO
    AppTab.SCHEDULE -> AppPage.SCHEDULE
    AppTab.LOG -> AppPage.LOG
    AppTab.AI -> AppPage.AI
}
