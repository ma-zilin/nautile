package io.github.mazilin.nautile.ui.screen.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.model.ViewClassification
import io.github.mazilin.nautile.domain.model.ViewOptions
import io.github.mazilin.nautile.domain.model.ViewSort
import io.github.mazilin.nautile.domain.repository.ProjectRepository
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import io.github.mazilin.nautile.domain.repository.TaskRepository
import io.github.mazilin.nautile.domain.rule.ProjectRules
import io.github.mazilin.nautile.domain.rule.TaskGrouping
import io.github.mazilin.nautile.domain.rule.TaskRules
import io.github.mazilin.nautile.domain.rule.TaskSearch
import io.github.mazilin.nautile.domain.usecase.ProjectCommandResult
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommandError
import io.github.mazilin.nautile.domain.usecase.TaskCommandResult
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.domain.usecase.TaskFieldUpdate
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 详情面板的临时状态（保存中/错误/阻止离开提示/属性草稿），不持久化。 */
private data class DetailExtras(
    val saving: Boolean = false,
    val error: String? = null,
    val leaveBlocked: Boolean = false,
    /** 详情项目菜单“新建项目”的名称草稿与错误（CMP-34）。 */
    val projectDraftName: String = "",
    val projectDraftError: String? = null,
    val creatingProject: Boolean = false,
)

private data class ProjectDetailBase(
    val id: ProjectId?,
    val projects: List<Project>,
    val tasks: List<Task>,
    val nameDraft: String?,
)

/** 搜索态本地输入：只在内存中，不持久化、不写任务字段（CMP-38）。 */
private data class SearchInput(
    val active: Boolean = false,
    val query: String = "",
)

/** 当前清单的未过滤事实，供搜索再派生分组，避免重复订阅数据源。 */
private data class TodoListBase(
    val position: GtdPosition,
    val tasks: List<Task>,
    val projects: List<Project>,
    val confirmation: ProjectConfirmation?,
    val reopenPrompt: ProjectReopenPrompt?,
    val savedViewOptions: ViewOptions,
)

/** 视图选项本地覆盖：写入成功前/后都以内存值为准，失败时回退到旧值（CMP-41）。 */
private data class ViewOptionsOverride(val position: GtdPosition, val options: ViewOptions)

/** 手动排序本地覆盖：拖拽落位后先按新顺序展示，写入失败再整体回滚（CMP-40.2）。 */
private data class ManualOrderOverride(val position: GtdPosition, val orders: Map<TaskId, Long>)

/** G9 视图选项的瞬时状态（不持久化）：本地覆盖与错误提示。 */
private data class ViewPreferences(
    val optionsOverride: ViewOptionsOverride? = null,
    val viewError: String? = null,
    val manualOverride: ManualOrderOverride? = null,
    val manualError: String? = null,
)

private data class TaskDetailBase(
    val id: TaskId?,
    val tasks: List<Task>,
    val projects: List<Project>,
    val titleDraft: String?,
    val detailDraft: String?,
)

/**
 * 待办页状态与命令（S1-T07/T08）。
 *
 * 列表只显示当前 GTD 清单与默认分组；详情字段编辑即保存，无效字段阻止离开。
 * 项目重开确认由 [ProjectReopenPrompt] 承载，确认后只重试原命令一次（T50-01）。
 */
class TodoViewModel(
    taskRepository: TaskRepository,
    projectRepository: ProjectRepository,
    private val settingsRepository: SettingsRepository,
    private val taskCommands: TaskCommands,
    private val projectCommands: ProjectCommands,
    /**
     * 手动排序批量写入（CMP-40.2）：生产环境接 `RoomTaskRepository.updateManualOrders` 的事务，
     * 测试或预览可传空实现；抛异常表示写入失败，由本类回滚内存顺序。
     */
    private val writeManualOrders: suspend (Map<TaskId, Long>) -> Unit = {},
) : ViewModel() {

    private val allTasks = taskRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val allProjects = projectRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** G6 项目菜单只列出活跃项目（CMP-34）：已结项/已放弃项目不可选。 */
    val activeProjects: StateFlow<List<Project>> = allProjects
        .map { projects -> projects.filter { it.status == ProjectStatus.ACTIVE } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val confirmation = MutableStateFlow<ProjectConfirmation?>(null)
    private val projectReopenPrompt = MutableStateFlow<ProjectReopenPrompt?>(null)

    private val taskDetailId = MutableStateFlow<TaskId?>(null)
    private val taskTitleDraft = MutableStateFlow<String?>(null)
    private val taskDetailDraft = MutableStateFlow<String?>(null)
    private val taskTitleInvalid = MutableStateFlow(false)
    private val taskDetailExtras = MutableStateFlow(DetailExtras())

    private val projectDetailId = MutableStateFlow<ProjectId?>(null)
    private val projectNameDraft = MutableStateFlow<String?>(null)
    private val projectNameInvalid = MutableStateFlow(false)
    private val projectDetailExtras = MutableStateFlow(DetailExtras())
    private val collapsedProjects = MutableStateFlow<Set<ProjectId>>(emptySet())

    private val newTaskState = MutableStateFlow<NewTaskUiState?>(null)
    val newTask: StateFlow<NewTaskUiState?> = newTaskState.asStateFlow()

    private val search = MutableStateFlow(SearchInput())

    /** G9 视图选项本地状态：选择即生效，写入失败再回退（CMP-41）。 */
    private val viewPreferences = MutableStateFlow(ViewPreferences())

    private val todoListBase = combine(
        settingsRepository.settings,
        allTasks,
        allProjects,
        confirmation,
        projectReopenPrompt,
    ) { settings, tasks, projects, pending, reopenPrompt ->
        val position = settings.lastGtdPosition ?: GtdPosition.INBOX
        TodoListBase(
            position = position,
            tasks = tasks.filter { it.position == position },
            projects = projects,
            confirmation = pending,
            reopenPrompt = reopenPrompt,
            savedViewOptions = ViewOptions.fromSettings(settings.perModuleViewOptions, position),
        )
    }

    val uiState: StateFlow<TodoUiState> = combine(
        todoListBase,
        search,
        viewPreferences,
    ) { base, input, preferences ->
        val options = preferences.optionsOverride
            ?.takeIf { it.position == base.position }
            ?.options
            ?: base.savedViewOptions
        val manualOrders = preferences.manualOverride
            ?.takeIf { it.position == base.position }
            ?.orders
        val filtered = TaskSearch.filter(base.tasks, base.projects, input.query)
        // 搜索只过滤可见性；分组/排序仍按当前视图选项（CMP-38）。
        val visibleSource = if (input.active) filtered else base.tasks
        val viewTasks = manualOrders?.let { orders ->
            visibleSource.map { task ->
                orders[task.id]?.let { order -> task.copy(manualOrder = order) } ?: task
            }
        } ?: visibleSource
        TodoUiState(
            position = base.position,
            // 搜索态只保留有命中的组（不显示 0 结果组）；退出搜索恢复含空项目的完整分组。
            groups = TaskGrouping.byView(
                tasks = viewTasks,
                projects = base.projects,
                options = options,
                includeEmptyProjects = !input.active,
            ),
            // 选择器计数仍用当前清单全部任务，不随搜索过滤变化。
            visibleTasks = base.tasks,
            projectConfirmation = base.confirmation,
            projectReopenPrompt = base.reopenPrompt,
            searchAvailable = true,
            viewOptionsAvailable = true,
            searchActive = input.active,
            searchQuery = input.query,
            viewOptions = options,
            viewOptionsError = preferences.viewError,
            manualOrderError = preferences.manualError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoUiState())

    /** 任务详情：字段草稿与保存状态由本类持有。 */
    private val taskDetailBase = combine(
        taskDetailId,
        allTasks,
        allProjects,
        taskTitleDraft,
        taskDetailDraft,
    ) { id, tasks, projects, titleDraft, detailDraft ->
        TaskDetailBase(id, tasks, projects, titleDraft, detailDraft)
    }

    val taskDetail: StateFlow<TaskDetailUiState?> = combine(
        taskDetailBase,
        taskTitleInvalid,
        taskDetailExtras,
    ) { base, invalid, extras ->
        val task = base.id?.let { taskId -> base.tasks.firstOrNull { it.id == taskId } }
        task?.let {
            TaskDetailUiState(
                task = it,
                titleDraft = base.titleDraft,
                titleInvalid = invalid,
                saving = extras.saving,
                error = extras.error,
                leaveBlocked = extras.leaveBlocked,
                canReopen = it.closedOutcome != null,
                detailDraft = base.detailDraft,
                projectName = it.projectId?.let { id ->
                    base.projects.firstOrNull { project -> project.id == id }?.name
                },
                projectDraftName = extras.projectDraftName,
                projectDraftError = extras.projectDraftError,
                creatingProject = extras.creatingProject,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val projectDetailBase = combine(
        projectDetailId,
        allProjects,
        allTasks,
        projectNameDraft,
    ) { id, projects, tasks, draft ->
        ProjectDetailBase(id, projects, tasks, draft)
    }

    val projectDetail: StateFlow<ProjectDetailUiState?> = combine(
        projectDetailBase,
        projectNameInvalid,
        projectDetailExtras,
        collapsedProjects,
    ) { base, invalid, extras, collapsed ->
        val project = base.id?.let { id -> base.projects.firstOrNull { it.id == id } }
        project?.let {
            ProjectDetailUiState(
                project = it,
                children = base.tasks.filter { task -> task.projectId == it.id },
                nameDraft = base.nameDraft,
                nameInvalid = invalid,
                saving = extras.saving,
                error = extras.error,
                leaveBlocked = extras.leaveBlocked,
                collapsed = it.id in collapsed,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---- 列表命令 ----

    /** 点按左侧清单或边界拉动后切换；持久化上次 GTD（AC-N-14）。 */
    fun selectPosition(position: GtdPosition) {
        viewModelScope.launch { settingsRepository.setLastGtdPosition(position) }
    }

    // ---- 搜索（G8、CMP-38）：只改可见性，不写任务字段、不改排序 ----

    /** 打开搜索：进入搜索态并清空上次查询（查询不跨页面/重启保留）。 */
    fun openSearch() {
        search.value = SearchInput(active = true, query = "")
    }

    /** 退出搜索：清空查询并恢复完整清单分组。 */
    fun closeSearch() {
        search.value = SearchInput()
    }

    fun onSearchQueryChanged(text: String) {
        search.update { it.copy(query = text) }
    }

    /** 清除图标：只清空查询，保持搜索栏与焦点（CMP-38）。 */
    fun clearSearch() {
        search.update { it.copy(query = "") }
    }

    // ---- 视图选项与手动排序（G9、CMP-39—CMP-41） ----

    /** 切换分类：立即生效并写入当前模块；同值不重复写。 */
    fun selectViewClassification(classification: ViewClassification) {
        val current = uiState.value
        if (current.viewOptions.classification == classification) return
        persistViewOptions(current.position, current.viewOptions.copy(classification = classification))
    }

    /** 切换排序：立即生效并写入当前模块；同值不重复写。 */
    fun selectViewSort(sort: ViewSort) {
        val current = uiState.value
        if (current.viewOptions.sort == sort) return
        persistViewOptions(current.position, current.viewOptions.copy(sort = sort))
    }

    /**
     * 选择即写入；写入失败回退到上一份内存值并提示，不显示“已保存”（CMP-41）。
     * 旧值优先取本类覆盖值，其次由设置流重新派生。
     */
    private fun persistViewOptions(position: GtdPosition, options: ViewOptions) {
        val previous = viewPreferences.value.optionsOverride
        viewPreferences.update {
            it.copy(optionsOverride = ViewOptionsOverride(position, options), viewError = null)
        }
        viewModelScope.launch {
            val result = runCatching {
                settingsRepository.setViewOptions(position, options.encode())
            }
            if (result.isFailure) {
                viewPreferences.update { current ->
                    current.copy(
                        optionsOverride = previous,
                        viewError = "视图选项保存失败，请重试",
                    )
                }
            }
        }
    }

    /**
     * 拖拽落位后一次性写入当前清单所有任务的新顺序（CMP-40.2）。
     *
     * 仅手动排序、非搜索态接受；先按新顺序展示，写入失败整体回滚并提示。
     * [orderedIds] 只在目标组内变化，其他组顺序与相对位置保持不变。
     */
    fun reorderGroup(groupKey: String, orderedIds: List<TaskId>) {
        val state = uiState.value
        if (state.searchActive || state.viewOptions.sort != ViewSort.MANUAL) return
        val group = state.groups.firstOrNull { it.key == groupKey } ?: return
        if (group.tasks.map { it.id } == orderedIds) return

        val tasksById = state.groups.flatMap { it.tasks }.associateBy { it.id }
        val reordered = state.groups.flatMap { current ->
            if (current.key == groupKey) {
                orderedIds.mapNotNull { tasksById[it] }
            } else {
                current.tasks
            }
        }
        // 数量不一致说明顺序与当前展示脱节（例如数据刚变化），放弃本次写入。
        if (reordered.size != state.groups.sumOf { it.tasks.size }) return
        val orders = reordered.mapIndexed { index, task -> task.id to index.toLong() }.toMap()
        if (orders.isEmpty()) return

        val position = state.position
        val override = ManualOrderOverride(position, orders)
        val previous = viewPreferences.value.manualOverride
        viewPreferences.update { it.copy(manualOverride = override, manualError = null) }
        viewModelScope.launch {
            val result = runCatching { writeManualOrders(orders) }
            if (result.isFailure) {
                viewPreferences.update { current ->
                    if (current.manualOverride != override) {
                        current
                    } else {
                        current.copy(
                            manualOverride = previous,
                            manualError = "手动排序保存失败，已恢复原顺序",
                        )
                    }
                }
            }
        }
    }

    /** 关闭错误提示（不在下一次操作时误留失败状态）。 */
    fun dismissViewError() {
        viewPreferences.update { it.copy(viewError = null, manualError = null) }
    }

    fun completeTask(taskId: TaskId) {
        viewModelScope.launch { taskCommands.complete(taskId) }
    }

    fun abandonTask(taskId: TaskId) {
        viewModelScope.launch { taskCommands.abandon(taskId) }
    }

    /** 项目滑动：有活动子任务时先请求确认数量（AC-T-17）。 */
    fun requestProjectClose(projectId: ProjectId, outcome: ClosedOutcome) {
        val project = allProjects.value.firstOrNull { it.id == projectId } ?: return
        val activeCount = allTasks.value.count {
            it.projectId == projectId && it.closedOutcome == null
        }
        if (activeCount == 0) {
            closeProject(projectId, outcome)
            return
        }
        confirmation.value = ProjectConfirmation(
            projectId = projectId,
            projectName = project.name,
            outcome = outcome,
            taskCount = activeCount,
        )
    }

    fun confirmProjectClose() {
        val pending = confirmation.value ?: return
        confirmation.value = null
        closeProject(pending.projectId, pending.outcome)
    }

    fun cancelProjectClose() {
        confirmation.value = null
    }

    private fun closeProject(projectId: ProjectId, outcome: ClosedOutcome) {
        viewModelScope.launch {
            when (outcome) {
                ClosedOutcome.COMPLETED -> projectCommands.complete(projectId, confirmed = true)
                ClosedOutcome.ABANDONED -> projectCommands.abandon(projectId, confirmed = true)
            }
        }
    }

    // ---- 详情：打开/关闭与字段提交 ----

    fun openTaskDetail(taskId: TaskId) {
        taskTitleDraft.value = null
        taskDetailDraft.value = null
        taskTitleInvalid.value = false
        taskDetailExtras.value = DetailExtras()
        taskDetailId.value = taskId
    }

    fun openProjectDetail(projectId: ProjectId) {
        projectNameDraft.value = null
        projectNameInvalid.value = false
        projectDetailExtras.value = DetailExtras()
        projectDetailId.value = projectId
    }

    fun closeDetail() {
        taskDetailId.value = null
        taskTitleDraft.value = null
        taskDetailDraft.value = null
        taskTitleInvalid.value = false
        taskDetailExtras.value = DetailExtras()
        projectDetailId.value = null
        projectNameDraft.value = null
        projectNameInvalid.value = false
        projectDetailExtras.value = DetailExtras()
    }

    fun onTaskTitleChanged(text: String) {
        taskTitleDraft.value = text
        taskTitleInvalid.value = TaskRules.validateTitle(text) != null
        taskDetailExtras.update { it.copy(error = null, leaveBlocked = false) }
    }

    /** 详情编辑即保存；空串由命令层归一为 null（FR-D-03）。 */
    fun onTaskDetailChanged(text: String) {
        taskDetailDraft.value = text
        taskDetailExtras.update { it.copy(error = null, leaveBlocked = false) }
    }

    fun commitTaskDetail() {
        val taskId = taskDetailId.value ?: return
        val draft = taskDetailDraft.value ?: return
        viewModelScope.launch {
            taskDetailExtras.update { it.copy(saving = true) }
            when (val result = taskCommands.updateField(taskId, TaskFieldUpdate.Detail(draft))) {
                is TaskCommandResult.Success -> {
                    taskDetailDraft.value = null
                    taskDetailExtras.value = DetailExtras()
                }

                is TaskCommandResult.Invalid -> {
                    taskDetailExtras.update { it.copy(saving = false, error = taskErrorText(result.error)) }
                }

                is TaskCommandResult.NotFound -> {
                    taskDetailExtras.update { it.copy(saving = false, error = "任务不存在") }
                }

                else -> {
                    taskDetailExtras.update { it.copy(saving = false, error = "保存失败，请重试") }
                }
            }
        }
    }

    /** 字段结束即保存；失败保留草稿并显示错误，不进入“已保存”状态。 */
    fun commitTaskTitle() {
        val taskId = taskDetailId.value ?: return
        val draft = taskTitleDraft.value ?: return
        viewModelScope.launch {
            taskDetailExtras.update { it.copy(saving = true) }
            when (val result = taskCommands.updateField(taskId, TaskFieldUpdate.Title(draft))) {
                is TaskCommandResult.Success -> {
                    taskTitleDraft.value = null
                    taskTitleInvalid.value = false
                    taskDetailExtras.value = DetailExtras()
                }

                is TaskCommandResult.Invalid -> {
                    taskTitleInvalid.value = result.error == TaskCommandError.BLANK_TITLE
                    taskDetailExtras.update { it.copy(saving = false, error = taskErrorText(result.error)) }
                }

                is TaskCommandResult.NotFound -> {
                    taskDetailExtras.update { it.copy(saving = false, error = "任务不存在") }
                }

                else -> {
                    taskDetailExtras.update { it.copy(saving = false, error = "保存失败，请重试") }
                }
            }
        }
    }

    // ---- 详情属性编辑（G7、CMP-30—CMP-37）：选择/清除即写库 ----

    /** 优先级四档；选择“无”即清除（CMP-33），不改其他字段。 */
    fun onTaskPriorityChanged(priority: Priority) {
        val taskId = taskDetailId.value ?: return
        if (allTasks.value.firstOrNull { it.id == taskId }?.priority == priority) return
        runTaskCommand(PendingTaskCommand.UpdateField(taskId, TaskFieldUpdate.Priority(priority)))
    }

    /** 日期清除时一并清除时刻，避免出现孤立时刻（CMP-32）。 */
    fun onTaskDueDateChanged(date: LocalDate?) {
        val taskId = taskDetailId.value ?: return
        val task = allTasks.value.firstOrNull { it.id == taskId } ?: return
        if (date == task.dueDate && (date != null || task.dueTime == null)) return
        viewModelScope.launch {
            taskDetailExtras.update { it.copy(saving = true, error = null) }
            // 先清时刻再清日期：即使日期写入失败，也不会留下“无日期有时刻”的非法状态。
            if (date == null && task.dueTime != null) {
                val timeResult = taskCommands.updateField(taskId, TaskFieldUpdate.DueTime(null))
                if (timeResult !is TaskCommandResult.Success) {
                    reportTaskFieldResult(timeResult)
                    return@launch
                }
            }
            reportTaskFieldResult(taskCommands.updateField(taskId, TaskFieldUpdate.DueDate(date)))
        }
    }

    /** 时刻必须依附日期；无日期时忽略写入，避免非法中间值（CMP-32）。 */
    fun onTaskDueTimeChanged(time: LocalTime?) {
        val taskId = taskDetailId.value ?: return
        val task = allTasks.value.firstOrNull { it.id == taskId } ?: return
        if (task.dueDate == null || task.dueTime == time) return
        viewModelScope.launch {
            taskDetailExtras.update { it.copy(saving = true, error = null) }
            reportTaskFieldResult(taskCommands.updateField(taskId, TaskFieldUpdate.DueTime(time)))
        }
    }

    /** 项目选择只写 projectId；已结项项目沿用重开确认守卫（CMP-34）。 */
    fun onTaskProjectChanged(projectId: ProjectId?) {
        val taskId = taskDetailId.value ?: return
        if (allTasks.value.firstOrNull { it.id == taskId }?.projectId == projectId) return
        runTaskCommand(PendingTaskCommand.UpdateField(taskId, TaskFieldUpdate.Project(projectId)))
    }

    /** 详情内切换 GTD 清单；ENDED 与已结项任务校验沿用 moveToGtd（CMP-35）。 */
    fun moveTaskToGtdFromDetail(target: GtdPosition) {
        val taskId = taskDetailId.value ?: return
        if (allTasks.value.firstOrNull { it.id == taskId }?.position == target) return
        moveTaskToGtd(taskId, target)
    }

    /** 清空详情写 null（CMP-36）；先丢弃未提交草稿，避免离开时把旧文本写回。 */
    fun clearTaskDetail() {
        val taskId = taskDetailId.value ?: return
        taskDetailDraft.value = null
        viewModelScope.launch {
            taskDetailExtras.update { it.copy(saving = true, error = null) }
            reportTaskFieldResult(taskCommands.updateField(taskId, TaskFieldUpdate.Detail(null)))
        }
    }

    fun onTaskProjectDraftChanged(text: String) {
        taskDetailExtras.update { it.copy(projectDraftName = text, projectDraftError = null) }
    }

    /** 详情项目菜单内“新建项目”：成功后回填为当前项目并写入任务（CMP-34）。 */
    fun createProjectForTaskDetail() {
        val taskId = taskDetailId.value ?: return
        val current = taskDetailExtras.value
        if (current.creatingProject) return
        if (ProjectRules.validateName(current.projectDraftName) != null) {
            taskDetailExtras.update { it.copy(projectDraftError = "项目名称不能为空") }
            return
        }
        viewModelScope.launch {
            taskDetailExtras.update {
                it.copy(creatingProject = true, projectDraftError = null, error = null)
            }
            when (val created = projectCommands.create(current.projectDraftName)) {
                is ProjectCommandResult.Success -> {
                    // 新项目必然是活跃状态，这里只做回填写入，不触发重开守卫。
                    when (val result = taskCommands.updateField(
                        taskId,
                        TaskFieldUpdate.Project(created.project.id),
                    )) {
                        is TaskCommandResult.Success -> taskDetailExtras.value = DetailExtras()
                        is TaskCommandResult.NotFound -> taskDetailExtras.update {
                            it.copy(creatingProject = false, error = "任务不存在")
                        }
                        else -> taskDetailExtras.update {
                            it.copy(creatingProject = false, projectDraftError = "回填项目失败，请重试")
                        }
                    }
                }

                is ProjectCommandResult.Invalid -> taskDetailExtras.update {
                    it.copy(creatingProject = false, projectDraftError = "项目名称不能为空")
                }

                else -> taskDetailExtras.update {
                    it.copy(creatingProject = false, projectDraftError = "创建失败，请重试")
                }
            }
        }
    }

    /** 详情属性写入结果：失败保留原值并显示错误，不进入“已保存”。 */
    private fun reportTaskFieldResult(result: TaskCommandResult) {
        when (result) {
            is TaskCommandResult.Success -> taskDetailExtras.update {
                it.copy(saving = false, error = null)
            }

            is TaskCommandResult.Invalid -> taskDetailExtras.update {
                it.copy(saving = false, error = taskErrorText(result.error))
            }

            is TaskCommandResult.NotFound -> taskDetailExtras.update {
                it.copy(saving = false, error = "任务不存在")
            }

            is TaskCommandResult.NeedsProjectReopen -> taskDetailExtras.update {
                it.copy(saving = false, error = "项目已结项，请重新打开项目")
            }

            TaskCommandResult.Deleted -> taskDetailExtras.update {
                it.copy(saving = false, error = "任务不存在")
            }
        }
    }

    fun onProjectNameChanged(text: String) {
        projectNameDraft.value = text
        projectNameInvalid.value = ProjectRules.validateName(text) != null
        projectDetailExtras.update { it.copy(error = null, leaveBlocked = false) }
    }

    /** 项目信息编辑即保存；失败保留输入并显示错误。 */
    fun commitProjectName() {
        val projectId = projectDetailId.value ?: return
        val draft = projectNameDraft.value ?: return
        viewModelScope.launch {
            projectDetailExtras.update { it.copy(saving = true) }
            when (val result = projectCommands.rename(projectId, draft)) {
                is ProjectCommandResult.Success -> {
                    projectNameDraft.value = null
                    projectNameInvalid.value = false
                    projectDetailExtras.value = DetailExtras()
                }

                is ProjectCommandResult.Invalid -> {
                    projectNameInvalid.value = true
                    projectDetailExtras.update { it.copy(saving = false, error = "项目名称不能为空") }
                }

                is ProjectCommandResult.NotFound -> {
                    projectDetailExtras.update { it.copy(saving = false, error = "项目不存在") }
                }

                else -> {
                    projectDetailExtras.update { it.copy(saving = false, error = "保存失败，请重试") }
                }
            }
        }
    }

    /**
     * 返回/下滑关闭前的离开检查：无效字段阻止并提示；有效草稿先提交，等保存结果再关闭，
     * 避免“保存失败却已关闭”。返回 true 才允许关闭覆盖层。
     */
    fun requestDetailLeave(): Boolean {
        val taskId = taskDetailId.value
        val taskDraft = taskTitleDraft.value
        if (taskId != null && taskDraft != null) {
            if (TaskRules.validateTitle(taskDraft) != null) {
                taskTitleInvalid.value = true
                taskDetailExtras.update { it.copy(leaveBlocked = true, error = "请先修正无效标题") }
                return false
            }
            commitTaskTitle()
            taskDetailExtras.update { it.copy(leaveBlocked = true, error = "正在保存，请稍候再关闭") }
            return false
        }
        val taskDetailDraftValue = taskDetailDraft.value
        if (taskId != null && taskDetailDraftValue != null) {
            commitTaskDetail()
            taskDetailExtras.update { it.copy(leaveBlocked = true, error = "正在保存，请稍候再关闭") }
            return false
        }
        val projectId = projectDetailId.value
        val projectDraft = projectNameDraft.value
        if (projectId != null && projectDraft != null) {
            if (ProjectRules.validateName(projectDraft) != null) {
                projectNameInvalid.value = true
                projectDetailExtras.update { it.copy(leaveBlocked = true, error = "请先修正无效项目名称") }
                return false
            }
            commitProjectName()
            projectDetailExtras.update { it.copy(leaveBlocked = true, error = "正在保存，请稍候再关闭") }
            return false
        }
        taskDetailExtras.update { it.copy(leaveBlocked = false) }
        projectDetailExtras.update { it.copy(leaveBlocked = false) }
        return true
    }

    fun toggleProjectCollapsed(projectId: ProjectId) {
        collapsedProjects.update { current ->
            if (projectId in current) current - projectId else current + projectId
        }
    }

    // ---- 新建待办底部面板（S1-T09、FR-D-02、BR-69） ----

    /** 打开面板时固定当前 GTD 清单；已结束时默认目标收件箱并要求选择（T-09）。 */
    fun openNewTask() {
        val position = uiState.value.position
        newTaskState.value = if (position == GtdPosition.ENDED) {
            NewTaskUiState(
                position = position,
                targetPosition = GtdPosition.INBOX,
                showTargetPicker = true,
            )
        } else {
            NewTaskUiState(position = position, targetPosition = position)
        }
    }

    /** 取消/显式关闭：不产生任何记录。 */
    fun closeNewTask() {
        newTaskState.value = null
    }

    fun onNewTaskKindChanged(kind: NewTaskKind) {
        newTaskState.update {
            it?.copy(kind = kind, error = null, attempted = false)
        }
    }

    /** 已结束清单内添加时可选目标清单；已结束不可选（T-09、AC-T-01）。 */
    fun onNewTaskTargetChanged(target: GtdPosition) {
        if (target == GtdPosition.ENDED) return
        newTaskState.update { it?.copy(targetPosition = target) }
    }

    fun onNewTaskTitleChanged(text: String) {
        newTaskState.update { current ->
            current?.copy(
                title = text,
                titleInvalid = TaskRules.validateTitle(text) != null,
                error = null,
            )
        }
    }

    fun onNewTaskDetailChanged(text: String) {
        newTaskState.update { current -> current?.copy(detail = text) }
    }

    // ---- G6 属性选择（CMP-30—CMP-37）：选择即改内存状态，创建时一次写入 ----

    /** 优先级清除即选择“无”（CMP-33）；不改其他属性。 */
    fun onNewTaskPriorityChanged(priority: Priority) {
        newTaskState.update { current -> current?.copy(priority = priority) }
    }

    /** 日期清除时一并清空时刻（时刻必须依附日期，CMP-32）。 */
    fun onNewTaskDueDateChanged(date: LocalDate?) {
        newTaskState.update { current ->
            current?.copy(dueDate = date, dueTime = if (date == null) null else current.dueTime)
        }
    }

    /** 无日期时忽略时刻写入，避免产生“只有时刻”的非法中间值。 */
    fun onNewTaskDueTimeChanged(time: LocalTime?) {
        newTaskState.update { current ->
            if (current == null || current.dueDate == null) current else current.copy(dueTime = time)
        }
    }

    /** 项目选择只改项目归属；null 表示无项目（CMP-34）。 */
    fun onNewTaskProjectChanged(projectId: ProjectId?) {
        newTaskState.update { current -> current?.copy(projectId = projectId) }
    }

    fun onNewTaskProjectDraftChanged(text: String) {
        newTaskState.update { current ->
            current?.copy(projectDraftName = text, projectDraftError = null)
        }
    }

    /** 项目菜单内“新建项目”：成功后回填为当前项目并保留面板；失败保留名称与错误。 */
    fun createProjectForNewTask() {
        val current = newTaskState.value ?: return
        if (current.creatingProject) return
        if (ProjectRules.validateName(current.projectDraftName) != null) {
            newTaskState.update { it?.copy(projectDraftError = "项目名称不能为空") }
            return
        }
        viewModelScope.launch {
            newTaskState.update { it?.copy(creatingProject = true, projectDraftError = null) }
            when (val result = projectCommands.create(current.projectDraftName)) {
                is ProjectCommandResult.Success -> newTaskState.update {
                    it?.copy(
                        projectId = result.project.id,
                        projectDraftName = "",
                        projectDraftError = null,
                        creatingProject = false,
                    )
                }

                is ProjectCommandResult.Invalid -> newTaskState.update {
                    it?.copy(creatingProject = false, projectDraftError = "项目名称不能为空")
                }

                else -> newTaskState.update {
                    it?.copy(creatingProject = false, projectDraftError = "创建失败，请重试")
                }
            }
        }
    }

    fun onNewProjectNameChanged(text: String) {
        newTaskState.update { current ->
            current?.copy(
                projectName = text,
                projectNameInvalid = ProjectRules.validateName(text) != null,
                error = null,
            )
        }
    }

    /** 系统返回/背景关闭：创建中或已尝试创建且当前表单无效时阻止并提示。 */
    fun requestNewTaskDismiss(): Boolean {
        val current = newTaskState.value ?: return true
        if (current.saving) return false
        if (!current.attempted) return true
        return when (current.kind) {
            NewTaskKind.TODO -> {
                if (TaskRules.validateTitle(current.title) != null) {
                    newTaskState.update {
                        it?.copy(titleInvalid = true, error = "请先填写有效标题")
                    }
                    false
                } else {
                    true
                }
            }

            NewTaskKind.PROJECT -> {
                if (ProjectRules.validateName(current.projectName) != null) {
                    newTaskState.update {
                        it?.copy(projectNameInvalid = true, error = "请先填写有效项目名称")
                    }
                    false
                } else {
                    true
                }
            }
        }
    }

    /** 只有点“创建”才写库；校验失败留在面板，成功后才关闭并回调。 */
    fun createNewTask(onCreated: () -> Unit) {
        val current = newTaskState.value ?: return
        if (current.saving) return
        if (TaskRules.validateTitle(current.title) != null) {
            newTaskState.update {
                it?.copy(titleInvalid = true, attempted = true, error = "标题不能为空")
            }
            return
        }
        viewModelScope.launch {
            newTaskState.update { it?.copy(saving = true, error = null) }
            when (val result = taskCommands.create(
                title = current.title,
                position = current.targetPosition,
                projectId = current.projectId,
                detail = current.detail,
                priority = current.priority,
                dueDate = current.dueDate,
                // 防御：无日期时不写时刻，避免非法中间值。
                dueTime = if (current.dueDate == null) null else current.dueTime,
                manualOrder = null,
            )) {
                is TaskCommandResult.Success -> {
                    newTaskState.value = null
                    onCreated()
                }

                is TaskCommandResult.Invalid -> newTaskState.update {
                    it?.copy(
                        saving = false,
                        attempted = true,
                        titleInvalid = result.error == TaskCommandError.BLANK_TITLE,
                        error = taskErrorText(result.error),
                    )
                }

                is TaskCommandResult.NotFound -> newTaskState.update {
                    it?.copy(saving = false, error = "创建失败，请重试")
                }

                is TaskCommandResult.NeedsProjectReopen -> newTaskState.update {
                    it?.copy(saving = false, error = "项目已结项，无法创建")
                }

                TaskCommandResult.Deleted -> newTaskState.update { it?.copy(saving = false) }
            }
        }
    }

    /** T-07：在新建面板内创建项目，复用既有 ProjectCommands.create。 */
    fun createProject(onCreated: () -> Unit) {
        val current = newTaskState.value ?: return
        if (current.saving) return
        if (ProjectRules.validateName(current.projectName) != null) {
            newTaskState.update {
                it?.copy(projectNameInvalid = true, attempted = true, error = "项目名称不能为空")
            }
            return
        }
        viewModelScope.launch {
            newTaskState.update { it?.copy(saving = true, error = null) }
            when (val result = projectCommands.create(current.projectName)) {
                is ProjectCommandResult.Success -> {
                    newTaskState.value = null
                    onCreated()
                }

                is ProjectCommandResult.Invalid -> newTaskState.update {
                    it?.copy(
                        saving = false,
                        attempted = true,
                        projectNameInvalid = true,
                        error = "项目名称不能为空",
                    )
                }

                else -> newTaskState.update {
                    it?.copy(saving = false, error = "创建失败，请重试")
                }
            }
        }
    }

    // ---- 项目重开确认与一次性重试（T50-01） ----

    fun reopenTask(taskId: TaskId) {
        runTaskCommand(PendingTaskCommand.Reopen(taskId))
    }

    fun createTask(
        title: String,
        position: GtdPosition,
        projectId: ProjectId?,
        detail: String? = null,
    ) {
        runTaskCommand(PendingTaskCommand.Create(title, position, projectId, detail))
    }

    fun moveTaskToGtd(taskId: TaskId, target: GtdPosition) {
        runTaskCommand(PendingTaskCommand.MoveToGtd(taskId, target))
    }

    fun confirmProjectReopen() {
        val prompt = projectReopenPrompt.value ?: return
        projectReopenPrompt.value = null
        viewModelScope.launch {
            when (val result = projectCommands.reopen(prompt.projectId, confirmed = true)) {
                is ProjectCommandResult.Success -> runTaskCommand(prompt.pending, isRetry = true)
                is ProjectCommandResult.NotFound -> setDetailError("项目不存在，未执行任务操作")
                else -> setDetailError("项目重开失败，未执行任务操作")
            }
        }
    }

    /** 取消重开：不写项目、不写任务（AC-T-28 取消分支）。 */
    fun cancelProjectReopen() {
        projectReopenPrompt.value = null
    }

    private fun runTaskCommand(command: PendingTaskCommand, isRetry: Boolean = false) {
        viewModelScope.launch {
            setDetailSaving(true)
            val result = execute(command)
            setDetailSaving(false)
            when (result) {
                is TaskCommandResult.NeedsProjectReopen -> {
                    if (isRetry) {
                        setDetailError("项目状态已变化，请重试")
                        return@launch
                    }
                    val project = allProjects.value.firstOrNull { it.id == result.projectId }
                    if (project == null) {
                        setDetailError("项目不存在，未执行任务操作")
                        return@launch
                    }
                    projectReopenPrompt.value = ProjectReopenPrompt(
                        projectId = project.id,
                        projectName = project.name,
                        status = project.status,
                        pending = command,
                    )
                }

                is TaskCommandResult.Invalid -> setDetailError(taskErrorText(result.error))
                is TaskCommandResult.NotFound -> setDetailError("任务不存在")
                is TaskCommandResult.Success -> setDetailError(null)
                TaskCommandResult.Deleted -> setDetailError(null)
            }
        }
    }

    private suspend fun execute(command: PendingTaskCommand): TaskCommandResult = when (command) {
        is PendingTaskCommand.Create ->
            taskCommands.create(command.title, command.position, command.projectId, command.detail)

        is PendingTaskCommand.MoveToGtd -> taskCommands.moveToGtd(command.taskId, command.target)
        is PendingTaskCommand.Reopen -> taskCommands.reopen(command.taskId)
        is PendingTaskCommand.UpdateField ->
            taskCommands.updateField(command.taskId, command.update)
    }

    private fun setDetailSaving(saving: Boolean) {
        if (taskDetailId.value != null) taskDetailExtras.update { it.copy(saving = saving) }
        if (projectDetailId.value != null) projectDetailExtras.update { it.copy(saving = saving) }
    }

    private fun setDetailError(message: String?) {
        if (taskDetailId.value != null) taskDetailExtras.update { it.copy(error = message) }
        if (projectDetailId.value != null) projectDetailExtras.update { it.copy(error = message) }
    }

    private fun taskErrorText(error: TaskCommandError): String = when (error) {
        TaskCommandError.BLANK_TITLE -> "标题不能为空"
        TaskCommandError.TARGET_ENDED -> "不能直接移到已结束"
        TaskCommandError.ALREADY_CLOSED -> "任务已结项"
        TaskCommandError.NOT_CLOSED -> "任务尚未结项"
        TaskCommandError.PROJECT_NOT_FOUND -> "项目不存在"
    }
}
