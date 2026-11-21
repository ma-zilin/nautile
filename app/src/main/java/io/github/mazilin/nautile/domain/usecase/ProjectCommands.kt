package io.github.mazilin.nautile.domain.usecase

import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.ProjectRepository
import io.github.mazilin.nautile.domain.repository.TaskRepository
import io.github.mazilin.nautile.domain.rule.ProjectRuleViolation
import io.github.mazilin.nautile.domain.rule.ProjectRules
import io.github.mazilin.nautile.domain.rule.TaskRules
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/** 项目用例的明确结果；批量操作返回确认分支，不向 UI 抛异常。 */
sealed interface ProjectCommandResult {
    data class Success(val project: Project) : ProjectCommandResult
    data class NeedsConfirmation(val taskCount: Int) : ProjectCommandResult
    data class Invalid(val error: ProjectCommandError) : ProjectCommandResult
    data class NotFound(val id: ProjectId) : ProjectCommandResult
    data object Deleted : ProjectCommandResult
}

enum class ProjectCommandError {
    BLANK_NAME,
    ALREADY_CLOSED,
}

/**
 * S1 项目用例（AC-T-17、BR-45；08 S1-T03 步骤 7）。
 *
 * 批量结项与删除通过 [ProjectRepository] 的批量方法落在同一事务（T05 实现）。
 */
class ProjectCommands(
    private val projects: ProjectRepository,
    private val tasks: TaskRepository,
    private val clock: Clock,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun create(name: String): ProjectCommandResult {
        ProjectRules.validateName(name)?.let { return ProjectCommandResult.Invalid(map(it)) }
        val now = clock.now()
        val draft = Project(
            id = ProjectId(0L),
            name = name,
            status = ProjectStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )
        val id = projects.insert(draft)
        return ProjectCommandResult.Success(draft.copy(id = id))
    }

    suspend fun rename(projectId: ProjectId, name: String): ProjectCommandResult {
        val project = projects.get(projectId) ?: return ProjectCommandResult.NotFound(projectId)
        ProjectRules.validateName(name)?.let { return ProjectCommandResult.Invalid(map(it)) }
        val renamed = project.copy(name = name, updatedAt = clock.now())
        projects.update(renamed)
        return ProjectCommandResult.Success(renamed)
    }

    /** 完成项目；有活动子任务时先返回需要确认的数量（AC-T-17）。 */
    suspend fun complete(projectId: ProjectId, confirmed: Boolean = false): ProjectCommandResult =
        closeProject(projectId, ClosedOutcome.COMPLETED, confirmed)

    /** 放弃项目；与完成同一口径（AC-T-17、BR-47）。 */
    suspend fun abandon(projectId: ProjectId, confirmed: Boolean = false): ProjectCommandResult =
        closeProject(projectId, ClosedOutcome.ABANDONED, confirmed)

    /** 删除项目；有子任务时先返回需要确认的数量，确认后同事务删除子任务与项目（X-03）。 */
    suspend fun delete(projectId: ProjectId, confirmed: Boolean = false): ProjectCommandResult {
        projects.get(projectId) ?: return ProjectCommandResult.NotFound(projectId)
        val children = tasks.observeAll().first().filter { it.projectId == projectId }
        if (children.isNotEmpty() && !confirmed) {
            return ProjectCommandResult.NeedsConfirmation(children.size)
        }
        projects.deleteWithTasks(projectId, children.map { it.id })
        return ProjectCommandResult.Deleted
    }

    private suspend fun closeProject(
        projectId: ProjectId,
        outcome: ClosedOutcome,
        confirmed: Boolean,
    ): ProjectCommandResult {
        val project = projects.get(projectId) ?: return ProjectCommandResult.NotFound(projectId)
        if (!ProjectRules.isActive(project)) {
            return ProjectCommandResult.Invalid(ProjectCommandError.ALREADY_CLOSED)
        }
        val children = tasks.observeAll().first()
        val activeChildren = ProjectRules.activeTasks(projectId, children)
        if (activeChildren.isNotEmpty() && !confirmed) {
            return ProjectCommandResult.NeedsConfirmation(activeChildren.size)
        }
        val now = clock.now()
        val closedProject = ProjectRules.close(project, outcome, now)
        val closedChildren: List<Task> = activeChildren.map { task ->
            TaskRules.close(task, outcome, now, zoneId)
        }
        projects.updateWithTasks(closedProject, closedChildren)
        return ProjectCommandResult.Success(closedProject)
    }

    private fun map(violation: ProjectRuleViolation): ProjectCommandError =
        when (violation) {
            ProjectRuleViolation.BLANK_NAME -> ProjectCommandError.BLANK_NAME
            ProjectRuleViolation.ALREADY_CLOSED -> ProjectCommandError.ALREADY_CLOSED
        }
}
