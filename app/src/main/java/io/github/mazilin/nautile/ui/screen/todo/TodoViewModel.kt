package io.github.mazilin.nautile.ui.screen.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.repository.ProjectRepository
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import io.github.mazilin.nautile.domain.repository.TaskRepository
import io.github.mazilin.nautile.domain.rule.TaskGrouping
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 待办页状态与命令（S1-T07）。
 *
 * 只显示“当前 GTD 清单”，默认分组由 [TaskGrouping] 计算；视图分组/排序菜单不在 D-10 范围。
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

    val uiState: StateFlow<TodoUiState> = combine(
        settingsRepository.settings,
        allTasks,
        allProjects,
        confirmation,
    ) { settings, tasks, projects, pending ->
        val position = settings.lastGtdPosition ?: GtdPosition.INBOX
        val visibleTasks = tasks.filter { it.position == position }
        TodoUiState(
            position = position,
            groups = TaskGrouping.byProject(visibleTasks, projects),
            visibleTasks = visibleTasks,
            projectConfirmation = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoUiState())

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
}
