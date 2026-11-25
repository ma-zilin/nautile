package io.github.mazilin.nautile.ui.screen.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.repository.ProjectRepository
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import io.github.mazilin.nautile.domain.repository.TaskRepository
import io.github.mazilin.nautile.domain.rule.ProjectRules
import io.github.mazilin.nautile.domain.rule.TaskGrouping
import io.github.mazilin.nautile.domain.rule.TaskRules
import io.github.mazilin.nautile.domain.usecase.ProjectCommandResult
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommandError
import io.github.mazilin.nautile.domain.usecase.TaskCommandResult
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.domain.usecase.TaskFieldUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 详情面板的临时状态（保存中/错误/阻止离开提示），不持久化。 */
private data class DetailExtras(
    val saving: Boolean = false,
    val error: String? = null,
    val leaveBlocked: Boolean = false,
)

private data class ProjectDetailBase(
    val id: ProjectId?,
    val projects: List<Project>,
    val tasks: List<Task>,
    val nameDraft: String?,
)

private data class TaskDetailBase(
    val id: TaskId?,
    val tasks: List<Task>,
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
) : ViewModel() {

    private val allTasks = taskRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val allProjects = projectRepository.observeAll()
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

    val uiState: StateFlow<TodoUiState> = combine(
        settingsRepository.settings,
        allTasks,
        allProjects,
        confirmation,
        projectReopenPrompt,
    ) { settings, tasks, projects, pending, reopenPrompt ->
        val position = settings.lastGtdPosition ?: GtdPosition.INBOX
        val visibleTasks = tasks.filter { it.position == position }
        TodoUiState(
            position = position,
            groups = TaskGrouping.byProjectIncludingEmpty(visibleTasks, projects),
            visibleTasks = visibleTasks,
            projectConfirmation = pending,
            projectReopenPrompt = reopenPrompt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoUiState())

    /** 任务详情：字段草稿与保存状态由本类持有。 */
    private val taskDetailBase = combine(
        taskDetailId,
        allTasks,
        taskTitleDraft,
        taskDetailDraft,
    ) { id, tasks, titleDraft, detailDraft ->
        TaskDetailBase(id, tasks, titleDraft, detailDraft)
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
                current.title,
                current.targetPosition,
                null,
                current.detail,
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
