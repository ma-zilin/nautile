package io.github.mazilin.nautile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mazilin.nautile.di.AppContainer
import io.github.mazilin.nautile.interaction.AppTab
import io.github.mazilin.nautile.interaction.FullScreenPage
import io.github.mazilin.nautile.interaction.Overlay
import io.github.mazilin.nautile.ui.AppRoot
import io.github.mazilin.nautile.ui.screen.common.PlaceholderScreen
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
                val todoState by todoViewModel.uiState.collectAsStateWithLifecycle()
                val taskDetailState by todoViewModel.taskDetail.collectAsStateWithLifecycle()
                val projectDetailState by todoViewModel.projectDetail.collectAsStateWithLifecycle()
                val newTaskState by todoViewModel.newTask.collectAsStateWithLifecycle()
                val activeTab by container.navigationCoordinator.activeTab.collectAsStateWithLifecycle()
                var scrollNavigationVisible by remember { mutableStateOf(true) }

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
                        FullScreenPlaceholder(page, onBack = { container.navigationCoordinator.back() })
                    },
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

/** T-06 的全屏子页占位；T-10 替换为设置根与默认页的真实内容。 */
@Composable
private fun FullScreenPlaceholder(page: FullScreenPage, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("返回")
            }
            Text(text = page.title, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = "${page.title}（T-10 实现）",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}
