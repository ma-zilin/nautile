package io.github.mazilin.nautile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mazilin.nautile.di.AppContainer
import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.interaction.AppTab
import io.github.mazilin.nautile.interaction.FullScreenPage
import io.github.mazilin.nautile.interaction.Overlay
import io.github.mazilin.nautile.interaction.PreviewBackdrop
import io.github.mazilin.nautile.ui.AppRoot
import io.github.mazilin.nautile.ui.screen.common.PlaceholderScreen
import io.github.mazilin.nautile.ui.screen.settings.DefaultPageScreen
import io.github.mazilin.nautile.ui.screen.settings.SettingsScreen
import io.github.mazilin.nautile.ui.screen.settings.SettingsViewModel
import io.github.mazilin.nautile.ui.screen.settings.StartupState
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
                val startup by settingsViewModel.startup.collectAsStateWithLifecycle()
                val taskDetailState by todoViewModel.taskDetail.collectAsStateWithLifecycle()
                val projectDetailState by todoViewModel.projectDetail.collectAsStateWithLifecycle()
                val newTaskState by todoViewModel.newTask.collectAsStateWithLifecycle()
                val activeTab by container.navigationCoordinator.activeTab.collectAsStateWithLifecycle()
                val imeVisible by container.keyboardCoordinator.imeVisible.collectAsStateWithLifecycle()
                val overlayEntry by container.overlayBackStack.top.collectAsStateWithLifecycle()
                var startupApplied by remember { mutableStateOf(false) }
                var scrollNavigationVisible by remember { mutableStateOf(true) }

                // AC-N-14/P-03：等 DataStore 首次发射；失败回落待办并提示，不伪造设置。
                LaunchedEffect(startup) {
                    when (val state = startup) {
                        is StartupState.Ready -> if (!startupApplied) {
                            val startupPage = state.settings.lastPage ?: state.settings.defaultPage
                            container.navigationCoordinator.applyInitialTab(startupPage.toTab())
                            startupApplied = true
                        }

                        StartupState.Failed -> if (!startupApplied) {
                            container.navigationCoordinator.applyInitialTab(AppTab.TODO)
                            startupApplied = true
                            Toast.makeText(
                                this@MainActivity,
                                "设置读取失败，已回到待办",
                                Toast.LENGTH_LONG,
                            ).show()
                        }

                        StartupState.Loading -> Unit
                    }
                }
                // 切换一级页时记录 lastPage。
                LaunchedEffect(activeTab, startupApplied) {
                    if (startupApplied) settingsViewModel.recordLastPage(activeTab.toPage())
                }
                if (startup == StartupState.Loading || !startupApplied) {
                    StartupPlaceholder()
                    return@NautileTheme
                }

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

                Box(modifier = Modifier.fillMaxSize()) {
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
                                    navigationUpdatesEnabled = !imeVisible && overlayEntry == null,
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
                            // 三类面板/详情改由下方宿主负责“先播退出、后关闭”（T5/T6），
                            // 这里只保留 GTD 占位；其余覆盖层不直接组合。
                            when (overlay) {
                                // GTD 由 GtdSelectorOverlay 宿主渲染：覆盖层关闭后仍保留到退出动效结束（CMP-19）。
                                Overlay.GtdSelector -> Unit
                                else -> Unit
                            }
                        },
                        // T-075：预测预览的下层页面一律用无回调的只读实例，避免写数据/改状态。
                        previewContent = { backdrop ->
                            when (backdrop) {
                                is PreviewBackdrop.Tab -> when (backdrop.tab) {
                                    AppTab.TODO -> TodoScreen(
                                        // 预览实例不携带确认/重开对话框，避免在预览期产生 Dialog 副作用。
                                        state = todoState.copy(
                                            projectConfirmation = null,
                                            projectReopenPrompt = null,
                                        ),
                                    )
                                    else -> PlaceholderScreen(
                                        title = backdrop.tab.title,
                                        onOpenSettings = {},
                                    )
                                }

                                is PreviewBackdrop.FullScreen -> when (backdrop.page) {
                                    FullScreenPage.Settings -> SettingsScreen(
                                        settings = settings,
                                        onBack = {},
                                        onOpenDefaultPage = {},
                                        onPredictiveBackChanged = {},
                                    )

                                    FullScreenPage.DefaultPage -> DefaultPageScreen(
                                        currentPage = settings.defaultPage,
                                        onSelect = {},
                                        onBack = {},
                                    )
                                }
                            }
                        },
                    )
                    // T5/T6：三类面板/详情“先播退出、后关闭”的最小接线。
                    // 覆盖层关闭后保留最后一次状态快照用于退出绘制，不改返回结果与数据语义。
                    var lastProjectDetail by remember { mutableStateOf(projectDetailState) }
                    if (projectDetailState != null) lastProjectDetail = projectDetailState
                    lastProjectDetail?.let { detail ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.systemBars),
                        ) {
                            ProjectDetailSheet(
                                state = detail,
                                onNameChanged = todoViewModel::onProjectNameChanged,
                                onCommitName = todoViewModel::commitProjectName,
                                onToggleCollapsed = {
                                    todoViewModel.toggleProjectCollapsed(detail.project.id)
                                },
                                onOpenChild = { taskId ->
                                    // 单主覆盖层：先关项目详情再开任务详情；返回回列表。
                                    container.overlayBackStack.close()
                                    todoViewModel.closeDetail()
                                    todoViewModel.openTaskDetail(taskId)
                                    val opened = container.overlayBackStack.open(
                                        Overlay.TaskDetail(taskId.value),
                                        canDismiss = detailCanDismiss,
                                    )
                                    if (!opened) todoViewModel.closeDetail()
                                },
                                onCloseRequested = requestCloseDetail,
                                visible = projectDetailState != null,
                                onExitFinished = { lastProjectDetail = null },
                            )
                        }
                    }
                    var lastTaskDetail by remember { mutableStateOf(taskDetailState) }
                    if (taskDetailState != null) lastTaskDetail = taskDetailState
                    lastTaskDetail?.let { detail ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.systemBars),
                        ) {
                            TaskDetailSheet(
                                state = detail,
                                onTitleChanged = todoViewModel::onTaskTitleChanged,
                                onCommitTitle = todoViewModel::commitTaskTitle,
                                onReopen = { todoViewModel.reopenTask(detail.task.id) },
                                onCloseRequested = requestCloseDetail,
                                onDetailChanged = todoViewModel::onTaskDetailChanged,
                                onCommitDetail = todoViewModel::commitTaskDetail,
                                visible = taskDetailState != null,
                                onExitFinished = { lastTaskDetail = null },
                            )
                        }
                    }
                    var lastNewTask by remember { mutableStateOf(newTaskState) }
                    if (newTaskState != null) lastNewTask = newTaskState
                    lastNewTask?.let { state ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.systemBars),
                        ) {
                            NewTaskSheet(
                                state = state,
                                onKindChanged = todoViewModel::onNewTaskKindChanged,
                                onTargetChanged = todoViewModel::onNewTaskTargetChanged,
                                onTitleChanged = todoViewModel::onNewTaskTitleChanged,
                                onDetailChanged = todoViewModel::onNewTaskDetailChanged,
                                onProjectNameChanged = todoViewModel::onNewProjectNameChanged,
                                onCreate = {
                                    todoViewModel.createNewTask { closeNewTaskPanel() }
                                },
                                onCreateProject = {
                                    todoViewModel.createProject { closeNewTaskPanel() }
                                },
                                onCancel = closeNewTaskPanel,
                                onDismissRequest = {
                                    if (newTaskCanDismiss()) closeNewTaskPanel()
                                },
                                visible = newTaskState != null,
                                onExitFinished = { lastNewTask = null },
                            )
                        }
                    }
                    GtdSelectorOverlay(
                        open = overlayEntry?.overlay == Overlay.GtdSelector,
                        currentPosition = todoState.position,
                        tasks = todoState.visibleTasks,
                        onSelect = todoViewModel::selectPosition,
                        onClose = { container.overlayBackStack.close() },
                        onExitFinished = {
                            // 退出动效后兜底：正常路径已由 onClose/系统返回关闭覆盖层。
                            if (container.overlayBackStack.current?.overlay == Overlay.GtdSelector) {
                                container.overlayBackStack.close()
                            }
                        },
                    )
                }
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

/**
 * GTD 覆盖层宿主（G1-A2、CMP-19）。
 *
 * 选择器常驻组合，由 [open] 驱动开合：打开播放入场，覆盖层关闭后仍保留到退出动效结束再停止绘制，
 * 不改动 OverlayBackStack 的关闭时机与返回结果。
 * G3 起为全屏左右分屏：inset 由选择器自身各消费一次，宿主不再加 padding（CMP-10、SPC-05）。
 */
@Composable
private fun GtdSelectorOverlay(
    open: Boolean,
    currentPosition: GtdPosition,
    tasks: List<Task>,
    onSelect: (GtdPosition) -> Unit,
    onClose: () -> Unit,
    onExitFinished: () -> Unit,
) {
    GtdSelector(
        currentPosition = currentPosition,
        tasks = tasks,
        onSelect = onSelect,
        onClose = onClose,
        animateReveal = true,
        visible = open,
        onExitFinished = onExitFinished,
    )
}

/** 启动期轻量占位（P-03）：不写业务数据，读取完成后进入默认/上次页。 */
@Composable
private fun StartupPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("正在载入…", style = MaterialTheme.typography.bodyMedium)
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
