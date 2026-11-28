package io.github.mazilin.nautile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import io.github.mazilin.nautile.data.repository.RoomTaskRepository
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
import io.github.mazilin.nautile.ui.screen.log.LogOverlayHost
import io.github.mazilin.nautile.ui.screen.log.LogScreen
import io.github.mazilin.nautile.ui.screen.log.LogViewModel
import io.github.mazilin.nautile.ui.screen.log.RunningTimerBar
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleOverlayHost
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleScreen
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleViewModel
import io.github.mazilin.nautile.ui.screen.settings.CategoryManagementScreen
import io.github.mazilin.nautile.ui.screen.settings.CategoryManagementViewModel
import io.github.mazilin.nautile.ui.screen.settings.DefaultPageScreen
import io.github.mazilin.nautile.ui.screen.settings.SettingsScreen
import io.github.mazilin.nautile.ui.screen.settings.SettingsViewModel
import io.github.mazilin.nautile.ui.screen.settings.StartupState
import io.github.mazilin.nautile.ui.screen.todo.GtdSelector
import io.github.mazilin.nautile.ui.screen.todo.NewTaskSheet
import io.github.mazilin.nautile.ui.screen.todo.ProjectDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.ProjectEditSheet
import io.github.mazilin.nautile.ui.screen.todo.TaskDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.TaskEditSheet
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

    /**
     * 搜索态优先关闭钩子（G8、CMP-38）：由组合期更新，系统返回先退出搜索，
     * 再按既有 IME → 覆盖层 → 导航顺序分发；搜索未打开时不参与，返回语义不变。
     */
    private var closeSearchIfActive: (() -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 深浅色只有一处来源：既传给根主题（COL-02），也用于状态栏图标明暗（CMP-51）。
            val darkTheme = isSystemInDarkTheme()
            // 状态栏图标随主题切换：浅色用深色图标、深色用浅色图标；系统切换时随重组更新。
            SideEffect {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                }
            }
            NautileTheme(darkTheme = darkTheme) {
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
                                    // 手动排序复用 G5 的批量事务；装配层是唯一知道具体实现的地方。
                                    writeManualOrders = { orders ->
                                        val repository = container.taskRepository
                                        if (repository is RoomTaskRepository) {
                                            repository.updateManualOrders(orders)
                                        } else {
                                            error("当前任务仓库不支持手动排序")
                                        }
                                    },
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
                // R6-G2-T4：日程页接入真实 ViewModel；时区与时钟由容器/默认注入，UI 不直接读系统时钟。
                val scheduleViewModel: ScheduleViewModel = viewModel(
                    factory = remember {
                        viewModelFactory {
                            initializer {
                                ScheduleViewModel(
                                    repository = container.scheduleRepository,
                                    clock = container.clock,
                                    commands = container.scheduleCommands,
                                )
                            }
                        }
                    },
                )
                // R6-G4：日志记录/计时页；时间与模式均来自容器注入，UI 不读系统时钟。
                val logViewModel: LogViewModel = viewModel(
                    factory = remember {
                        viewModelFactory {
                            initializer {
                                LogViewModel(
                                    logRepository = container.timeLogRepository,
                                    categoryRepository = container.timeCategoryRepository,
                                    settingsRepository = container.settingsRepository,
                                    commands = container.timeLogCommands,
                                    clock = container.clock,
                                )
                            }
                        }
                    },
                )
                // R6-G4-T5：分类管理全屏子页。
                val categoryManagementViewModel: CategoryManagementViewModel = viewModel(
                    factory = remember {
                        viewModelFactory {
                            initializer {
                                CategoryManagementViewModel(
                                    timeCategoryRepository = container.timeCategoryRepository,
                                    timeLogRepository = container.timeLogRepository,
                                    commands = container.timeCategoryCommands,
                                )
                            }
                        }
                    },
                )
                val todoState by todoViewModel.uiState.collectAsStateWithLifecycle()
                val scheduleState by scheduleViewModel.uiState.collectAsStateWithLifecycle()
                val scheduleEditor by scheduleViewModel.editor.collectAsStateWithLifecycle()
                val logState by logViewModel.uiState.collectAsStateWithLifecycle()
                val logEditor by logViewModel.editor.collectAsStateWithLifecycle()
                val logDetail by logViewModel.detail.collectAsStateWithLifecycle()
                val categoryState by categoryManagementViewModel.uiState.collectAsStateWithLifecycle()
                val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
                val startup by settingsViewModel.startup.collectAsStateWithLifecycle()
                val taskDetailState by todoViewModel.taskDetail.collectAsStateWithLifecycle()
                val projectDetailState by todoViewModel.projectDetail.collectAsStateWithLifecycle()
                val taskEditState by todoViewModel.taskEdit.collectAsStateWithLifecycle()
                val projectEditState by todoViewModel.projectEdit.collectAsStateWithLifecycle()
                val newTaskState by todoViewModel.newTask.collectAsStateWithLifecycle()
                val newTaskProjects by todoViewModel.activeProjects.collectAsStateWithLifecycle()
                val activeTab by container.navigationCoordinator.activeTab.collectAsStateWithLifecycle()
                val imeVisible by container.keyboardCoordinator.imeVisible.collectAsStateWithLifecycle()
                val overlayEntry by container.overlayBackStack.top.collectAsStateWithLifecycle()
                var startupApplied by remember { mutableStateOf(false) }
                var scrollNavigationVisible by remember { mutableStateOf(true) }
                var logScrollNavigationVisible by remember { mutableStateOf(true) }

                // 每次重组把最新的搜索开关暴露给 Activity 返回回调（不读 Compose 状态以外的数据）。
                // 有覆盖层时仍按既有顺序先关覆盖层，搜索栏只在一级待办页上优先关闭。
                SideEffect {
                    closeSearchIfActive = {
                        if (todoState.searchActive && overlayEntry == null) {
                            todoViewModel.closeSearch()
                            true
                        } else {
                            false
                        }
                    }
                }

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
                // 查询不跨页面保留（CMP-38）：离开待办模块时退出搜索。
                LaunchedEffect(activeTab) {
                    if (activeTab != AppTab.TODO) todoViewModel.closeSearch()
                    // 日志页导航显隐只在页内滚动时生效；离开/回到页面恢复可见。
                    if (activeTab != AppTab.LOG || overlayEntry != null) logScrollNavigationVisible = true
                }
                // 日程覆盖层关闭后清理编辑器，避免异步加载完成后留下无宿主的草稿。
                val scheduleOverlay = overlayEntry?.overlay
                val scheduleOverlayOpen =
                    scheduleOverlay == Overlay.NewSchedule || scheduleOverlay is Overlay.ScheduleDetail
                LaunchedEffect(scheduleOverlayOpen) {
                    if (!scheduleOverlayOpen) scheduleViewModel.closeEditor()
                }
                // 日志覆盖层关闭后清理编辑器/详情，避免异步加载完成后留下无宿主的草稿。
                val logOverlayOpen = scheduleOverlay == Overlay.NewLog ||
                    scheduleOverlay is Overlay.LogDetail
                LaunchedEffect(logOverlayOpen) {
                    if (!logOverlayOpen) {
                        logViewModel.closeEditor()
                        logViewModel.closeDetail()
                    }
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
                // 日程面板的关闭条件：无效字段由 ViewModel 阻止并提示（AC-D-06/08）。
                val scheduleCanDismiss: () -> Boolean = { scheduleViewModel.dismissEditor() }
                // 日志面板的关闭条件：无效字段/运行中编辑由 ViewModel 阻止并提示。
                val logEditorCanDismiss: () -> Boolean = { logViewModel.dismissEditor() }
                val logDetailCanDismiss: () -> Boolean = { logViewModel.dismissDetail() }

                Box(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        navigation = container.navigationCoordinator,
                        overlays = container.overlayBackStack,
                        scrollNavigationVisible = when (activeTab) {
                            AppTab.TODO -> scrollNavigationVisible
                            AppTab.LOG -> logScrollNavigationVisible
                            else -> true
                        },
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
                                    onOpenSearch = todoViewModel::openSearch,
                                    onSearchQueryChanged = todoViewModel::onSearchQueryChanged,
                                    onClearSearch = todoViewModel::clearSearch,
                                    onCloseSearch = todoViewModel::closeSearch,
                                    onViewClassificationSelected =
                                        todoViewModel::selectViewClassification,
                                    onViewSortSelected = todoViewModel::selectViewSort,
                                    onReorderGroup = todoViewModel::reorderGroup,
                                    onDismissViewError = todoViewModel::dismissViewError,
                                    newTaskOpen = newTaskState != null,
                                    navigationUpdatesEnabled = !imeVisible && overlayEntry == null,
                                )

                                AppTab.SCHEDULE -> ScheduleScreen(
                                    state = scheduleState,
                                    onOpenSettings = container.navigationCoordinator::navigateToSettings,
                                    onDateSelected = scheduleViewModel::onDateSelected,
                                    onRangeStep = scheduleViewModel::onRangeStep,
                                    onToday = scheduleViewModel::onToday,
                                    onViewModeSelected = scheduleViewModel::onViewModeSelected,
                                    onSourceFilterSelected =
                                        scheduleViewModel::onSourceFilterSelected,
                                    onOpenNew = {
                                        scheduleViewModel.onOpenNew()
                                        val opened = container.overlayBackStack.open(
                                            Overlay.NewSchedule,
                                            canDismiss = scheduleCanDismiss,
                                        )
                                        if (!opened) scheduleViewModel.closeEditor()
                                    },
                                    onOpenDetail = { id ->
                                        scheduleViewModel.onOpenDetail(id)
                                        val opened = container.overlayBackStack.open(
                                            Overlay.ScheduleDetail(id.value),
                                            canDismiss = scheduleCanDismiss,
                                        )
                                        if (!opened) scheduleViewModel.closeEditor()
                                    },
                                    onDismissMessage = scheduleViewModel::onDismissMessage,
                                )

                                AppTab.LOG -> LogScreen(
                                    state = logState,
                                    onOpenSettings = container.navigationCoordinator::navigateToSettings,
                                    onCategoryTap = logViewModel::onCategoryQuickTap,
                                    onShowAllCategories = {
                                        container.overlayBackStack.open(Overlay.LogCategoryPicker)
                                    },
                                    onOpenLog = { id ->
                                        logViewModel.onOpenLog(id)
                                        val opened = container.overlayBackStack.open(
                                            Overlay.LogDetail(id.value),
                                            canDismiss = logDetailCanDismiss,
                                        )
                                        if (!opened) logViewModel.closeDetail()
                                    },
                                    onOpenManual = {
                                        logViewModel.onOpenManual()
                                        val opened = container.overlayBackStack.open(
                                            Overlay.NewLog,
                                            canDismiss = logEditorCanDismiss,
                                        )
                                        if (!opened) logViewModel.closeEditor()
                                    },
                                    onDismissMessage = logViewModel::onDismissMessage,
                                    onNavigationVisibilityChanged = {
                                        logScrollNavigationVisible = it
                                    },
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
                                    onOpenCategoryManagement = {
                                        container.navigationCoordinator.navigateToCategoryManagement()
                                    },
                                )

                                FullScreenPage.DefaultPage -> DefaultPageScreen(
                                    currentPage = settings.defaultPage,
                                    onSelect = settingsViewModel::setDefaultPage,
                                    onBack = { container.navigationCoordinator.back() },
                                )

                                FullScreenPage.CategoryManagement -> CategoryManagementScreen(
                                    state = categoryState,
                                    onBack = { container.navigationCoordinator.back() },
                                    onCreateTopLevel = categoryManagementViewModel::openCreateTopLevel,
                                    onCreateChild = categoryManagementViewModel::openCreateChild,
                                    onEdit = categoryManagementViewModel::openEdit,
                                    onRequestDelete = categoryManagementViewModel::requestDelete,
                                    onNameChanged = categoryManagementViewModel::onNameChanged,
                                    onSave = categoryManagementViewModel::save,
                                    onCloseEdit = categoryManagementViewModel::closeEdit,
                                    onConfirmDelete = categoryManagementViewModel::confirmDelete,
                                    onDismissDeleteConfirm =
                                        categoryManagementViewModel::dismissDeleteConfirm,
                                    onDismissGuard = categoryManagementViewModel::dismissGuard,
                                    onDismissMessage = categoryManagementViewModel::onDismissMessage,
                                )
                            }
                        },
                        predictiveBackEnabled = settings.predictiveBackEnabled,
                        // 计时条是导航的扩展：仅在日志页、有运行中且键盘未打开时渲染；
                        // 导航隐藏、覆盖层、全屏子页由 AppRoot 条件统一隐藏，但都不结束计时。
                        bottomBarContent = {
                            if (activeTab == AppTab.LOG && !imeVisible && logState.running.isNotEmpty()) {
                                RunningTimerBar(
                                    running = logState.running,
                                    onTimerTap = logViewModel::onRunningTimerTap,
                                )
                            }
                        },
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

                                    // 日程预览使用只读状态，不接任何回调（T-075、AC-N-21）。
                                    AppTab.SCHEDULE -> ScheduleScreen(state = scheduleState)

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

                                    // 分类管理的预测预览映射为设置页（架构 §5.4），只读无回调。
                                    FullScreenPage.CategoryManagement -> SettingsScreen(
                                        settings = settings,
                                        onBack = {},
                                        onOpenDefaultPage = {},
                                        onPredictiveBackChanged = {},
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
                        Box(modifier = Modifier.fillMaxSize()) {
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
                                onOpenEdit = todoViewModel::openProjectEdit,
                                visible = projectDetailState != null && projectEditState == null,
                                onExitFinished = { lastProjectDetail = null },
                            )
                        }
                    }
                    var lastTaskDetail by remember { mutableStateOf(taskDetailState) }
                    if (taskDetailState != null) lastTaskDetail = taskDetailState
                    lastTaskDetail?.let { detail ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            TaskDetailSheet(
                                state = detail,
                                onTitleChanged = todoViewModel::onTaskTitleChanged,
                                onCommitTitle = todoViewModel::commitTaskTitle,
                                onReopen = { todoViewModel.reopenTask(detail.task.id) },
                                onCloseRequested = requestCloseDetail,
                                onDetailChanged = todoViewModel::onTaskDetailChanged,
                                onCommitDetail = todoViewModel::commitTaskDetail,
                                visible = taskDetailState != null && taskEditState == null,
                                onExitFinished = { lastTaskDetail = null },
                                projects = newTaskProjects,
                                onPriorityChanged = todoViewModel::onTaskPriorityChanged,
                                onDueDateChanged = todoViewModel::onTaskDueDateChanged,
                                onDueTimeChanged = todoViewModel::onTaskDueTimeChanged,
                                onProjectChanged = todoViewModel::onTaskProjectChanged,
                                onGtdChanged = todoViewModel::moveTaskToGtdFromDetail,
                                onProjectDraftChanged = todoViewModel::onTaskProjectDraftChanged,
                                onCreateProjectForTask =
                                    todoViewModel::createProjectForTaskDetail,
                                onClearDetail = todoViewModel::clearTaskDetail,
                                onOpenEdit = todoViewModel::openTaskEdit,
                            )
                        }
                    }
                    // 任务编辑面板：与新建同构；返回顺序为菜单→编辑面板→详情（R4 G5）。
                    var lastTaskEdit by remember { mutableStateOf(taskEditState) }
                    if (taskEditState != null) lastTaskEdit = taskEditState
                    lastTaskEdit?.let { edit ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            TaskEditSheet(
                                state = edit,
                                projects = newTaskProjects,
                                onTitleChanged = todoViewModel::onTaskEditTitleChanged,
                                onDetailChanged = todoViewModel::onTaskEditDetailChanged,
                                onPriorityChanged = todoViewModel::onTaskEditPriorityChanged,
                                onDueDateChanged = todoViewModel::onTaskEditDueDateChanged,
                                onDueTimeChanged = todoViewModel::onTaskEditDueTimeChanged,
                                onProjectChanged = todoViewModel::onTaskEditProjectChanged,
                                onProjectDraftChanged =
                                    todoViewModel::onTaskEditProjectDraftChanged,
                                onCreateProjectForTask =
                                    todoViewModel::createProjectForTaskEdit,
                                onPositionChanged = todoViewModel::onTaskEditPositionChanged,
                                onSave = todoViewModel::saveTaskEdit,
                                onDismissRequest = {
                                    todoViewModel.requestTaskEditDismiss()
                                },
                                visible = taskEditState != null,
                                onExitFinished = { lastTaskEdit = null },
                            )
                        }
                    }
                    // 项目编辑面板：仅名称；同样由覆盖层内部承载，不新增覆盖层类型。
                    var lastProjectEdit by remember { mutableStateOf(projectEditState) }
                    if (projectEditState != null) lastProjectEdit = projectEditState
                    lastProjectEdit?.let { edit ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            ProjectEditSheet(
                                state = edit,
                                onNameChanged = todoViewModel::onProjectEditNameChanged,
                                onSave = todoViewModel::saveProjectEdit,
                                onDismissRequest = {
                                    todoViewModel.requestProjectEditDismiss()
                                },
                                visible = projectEditState != null,
                                onExitFinished = { lastProjectEdit = null },
                            )
                        }
                    }
                    var lastNewTask by remember { mutableStateOf(newTaskState) }
                    if (newTaskState != null) lastNewTask = newTaskState
                    lastNewTask?.let { state ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            NewTaskSheet(
                                state = state,
                                projects = newTaskProjects,
                                onKindChanged = todoViewModel::onNewTaskKindChanged,
                                onTargetChanged = todoViewModel::onNewTaskTargetChanged,
                                onTitleChanged = todoViewModel::onNewTaskTitleChanged,
                                onDetailChanged = todoViewModel::onNewTaskDetailChanged,
                                onProjectNameChanged = todoViewModel::onNewProjectNameChanged,
                                onPriorityChanged = todoViewModel::onNewTaskPriorityChanged,
                                onDueDateChanged = todoViewModel::onNewTaskDueDateChanged,
                                onDueTimeChanged = todoViewModel::onNewTaskDueTimeChanged,
                                onProjectChanged = todoViewModel::onNewTaskProjectChanged,
                                onProjectDraftChanged = todoViewModel::onNewTaskProjectDraftChanged,
                                onCreateProjectForTask = todoViewModel::createProjectForNewTask,
                                onClearDetail = { todoViewModel.onNewTaskDetailChanged("") },
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
                    // R6-G3：日程新建/详情半屏；“先播退出、后关闭”，不改待办覆盖层行为。
                    ScheduleOverlayHost(
                        viewModel = scheduleViewModel,
                        editor = if (scheduleOverlayOpen) scheduleEditor else null,
                        onOverlayClosed = { container.overlayBackStack.close() },
                    )
                    // R6-G4：日志补录/详情/“全部分类”两级选择器；同宿主先播退出、后关闭。
                    LogOverlayHost(
                        viewModel = logViewModel,
                        editor = if (scheduleOverlay == Overlay.NewLog) logEditor else null,
                        detail = if (scheduleOverlay is Overlay.LogDetail) logDetail else null,
                        uiState = logState,
                        pickerOpen = scheduleOverlay == Overlay.LogCategoryPicker,
                        onCategoryPicked = logViewModel::onCategoryQuickTap,
                        onPickerClosed = { container.overlayBackStack.close() },
                        onOverlayClosed = { container.overlayBackStack.close() },
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
                    // 搜索优先关闭：只退出搜索栏，不退出页面/应用，也不改变既有返回顺序。
                    if (closeSearchIfActive?.invoke() == true) return
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
 * GTD 覆盖层宿主（CMP-19）。
 *
 * 选择器常驻组合，由 [open] 驱动开合：打开播放入场，覆盖层关闭后仍保留到退出动效结束再停止绘制，
 * 不改动 OverlayBackStack 的关闭时机与返回结果。
 * R4 起为左侧全高抽屉：状态栏上、导航栏下 inset 由抽屉内容自身消费一次，宿主不再加 padding
 * （CMP-10/13、SPC-05）。
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
