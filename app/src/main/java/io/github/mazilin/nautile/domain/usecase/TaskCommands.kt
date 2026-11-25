package io.github.mazilin.nautile.domain.usecase

import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.ProjectRepository
import io.github.mazilin.nautile.domain.repository.TaskRepository
import io.github.mazilin.nautile.domain.rule.TaskRuleViolation
import io.github.mazilin.nautile.domain.rule.TaskRules
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 任务用例的明确结果；不向 UI 抛裸异常（08 S1-T03 步骤 7）。 */
sealed interface TaskCommandResult {
    data class Success(val task: Task) : TaskCommandResult
    data class Invalid(val error: TaskCommandError) : TaskCommandResult
    data class NotFound(val id: TaskId) : TaskCommandResult
    data class NeedsProjectReopen(val projectId: ProjectId) : TaskCommandResult
    data object Deleted : TaskCommandResult
}

enum class TaskCommandError {
    BLANK_TITLE,
    TARGET_ENDED,
    ALREADY_CLOSED,
    NOT_CLOSED,
    PROJECT_NOT_FOUND,
}

/** 详情字段编辑即保存的字段更新（FR-D-01、AC-D-06）。 */
sealed interface TaskFieldUpdate {
    data class Title(val title: String) : TaskFieldUpdate
    data class Detail(val detail: String?) : TaskFieldUpdate
    // 嵌套类同名会遮蔽模型枚举，此处保留全限定名。
    data class Priority(val priority: io.github.mazilin.nautile.domain.model.Priority) : TaskFieldUpdate
    data class DueDate(val dueDate: LocalDate?) : TaskFieldUpdate
    data class DueTime(val dueTime: LocalTime?) : TaskFieldUpdate
    data class Project(val projectId: ProjectId?) : TaskFieldUpdate
}

/**
 * S1 任务用例（08 S1-T03 步骤 7）。
 *
 * 规则校验先后顺序与调用顺序在测试中固定；事务边界由仓库批量方法表达。
 */
class TaskCommands(
    private val tasks: TaskRepository,
    private val projects: ProjectRepository,
    private val clock: Clock,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun create(
        title: String,
        position: GtdPosition,
        projectId: ProjectId? = null,
        detail: String? = null,
        priority: Priority = Priority.NONE,
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
        manualOrder: Long? = null,
    ): TaskCommandResult {
        TaskRules.validateTitle(title)?.let { return TaskCommandResult.Invalid(map(it)) }
        if (!TaskRules.canMoveTo(position)) {
            return TaskCommandResult.Invalid(TaskCommandError.TARGET_ENDED)
        }
        if (projectId != null) {
            when (val project = projects.get(projectId)) {
                null -> return TaskCommandResult.Invalid(TaskCommandError.PROJECT_NOT_FOUND)
                else -> if (project.status != ProjectStatus.ACTIVE) {
                    return TaskCommandResult.NeedsProjectReopen(projectId)
                }
            }
        }
        val now = clock.now()
        val draft = Task(
            id = TaskId(0L),
            title = title,
            projectId = projectId,
            position = position,
            priority = priority,
            dueDate = dueDate,
            dueTime = dueTime,
            createdAt = now,
            updatedAt = now,
            detail = normalizeDetail(detail),
            manualOrder = manualOrder,
        )
        val id = tasks.insert(draft)
        return TaskCommandResult.Success(draft.copy(id = id))
    }

    suspend fun updateField(taskId: TaskId, update: TaskFieldUpdate): TaskCommandResult {
        val task = tasks.get(taskId) ?: return TaskCommandResult.NotFound(taskId)
        val changed: Task = when (update) {
            is TaskFieldUpdate.Title -> {
                TaskRules.validateTitle(update.title)?.let {
                    return TaskCommandResult.Invalid(map(it))
                }
                task.copy(title = update.title)
            }

            is TaskFieldUpdate.Detail -> task.copy(detail = normalizeDetail(update.detail))

            is TaskFieldUpdate.Priority -> task.copy(priority = update.priority)
            is TaskFieldUpdate.DueDate -> task.copy(dueDate = update.dueDate)
            is TaskFieldUpdate.DueTime -> task.copy(dueTime = update.dueTime)
            is TaskFieldUpdate.Project -> {
                if (update.projectId != null) {
                    when (val project = projects.get(update.projectId)) {
                        null -> return TaskCommandResult.Invalid(TaskCommandError.PROJECT_NOT_FOUND)
                        else -> if (project.status != ProjectStatus.ACTIVE) {
                            return TaskCommandResult.NeedsProjectReopen(update.projectId)
                        }
                    }
                }
                task.copy(projectId = update.projectId)
            }
        }
        val saved = changed.copy(updatedAt = clock.now())
        tasks.update(saved)
        return TaskCommandResult.Success(saved)
    }

    suspend fun complete(taskId: TaskId): TaskCommandResult = close(taskId, ClosedOutcome.COMPLETED)

    suspend fun abandon(taskId: TaskId): TaskCommandResult = close(taskId, ClosedOutcome.ABANDONED)

    /** 重新打开；已结项项目中的任务先提示重开项目（AC-T-29、BR-80）。 */
    suspend fun reopen(taskId: TaskId): TaskCommandResult {
        val task = tasks.get(taskId) ?: return TaskCommandResult.NotFound(taskId)
        if (!TaskRules.isClosed(task)) {
            return TaskCommandResult.Invalid(TaskCommandError.NOT_CLOSED)
        }
        val projectId = task.projectId
        if (projectId != null) {
            when (val project = projects.get(projectId)) {
                null -> return TaskCommandResult.Invalid(TaskCommandError.PROJECT_NOT_FOUND)
                else -> if (project.status != ProjectStatus.ACTIVE) {
                    return TaskCommandResult.NeedsProjectReopen(projectId)
                }
            }
        }
        val reopened = TaskRules.reopen(task, clock.now())
        tasks.update(reopened)
        return TaskCommandResult.Success(reopened)
    }

    suspend fun delete(taskId: TaskId): TaskCommandResult {
        tasks.get(taskId) ?: return TaskCommandResult.NotFound(taskId)
        tasks.delete(taskId)
        return TaskCommandResult.Deleted
    }

    /** 移动到 GTD 位置；目标为“已结束”或任务已结项时拒绝（AC-T-01）。 */
    suspend fun moveToGtd(taskId: TaskId, target: GtdPosition): TaskCommandResult {
        val task = tasks.get(taskId) ?: return TaskCommandResult.NotFound(taskId)
        if (TaskRules.isClosed(task)) {
            return TaskCommandResult.Invalid(TaskCommandError.ALREADY_CLOSED)
        }
        if (!TaskRules.canMoveTo(target)) {
            return TaskCommandResult.Invalid(TaskCommandError.TARGET_ENDED)
        }
        val projectId = task.projectId
        if (projectId != null) {
            when (val project = projects.get(projectId)) {
                null -> return TaskCommandResult.Invalid(TaskCommandError.PROJECT_NOT_FOUND)
                else -> if (project.status != ProjectStatus.ACTIVE) {
                    return TaskCommandResult.NeedsProjectReopen(projectId)
                }
            }
        }
        val moved = task.copy(position = target, updatedAt = clock.now())
        tasks.update(moved)
        return TaskCommandResult.Success(moved)
    }

    private suspend fun close(taskId: TaskId, outcome: ClosedOutcome): TaskCommandResult {
        val task = tasks.get(taskId) ?: return TaskCommandResult.NotFound(taskId)
        if (TaskRules.isClosed(task)) {
            return TaskCommandResult.Invalid(TaskCommandError.ALREADY_CLOSED)
        }
        val closed = TaskRules.close(task, outcome, clock.now(), zoneId)
        tasks.update(closed)
        return TaskCommandResult.Success(closed)
    }

    private fun map(violation: TaskRuleViolation): TaskCommandError =
        when (violation) {
            TaskRuleViolation.BLANK_TITLE -> TaskCommandError.BLANK_TITLE
            TaskRuleViolation.TARGET_ENDED -> TaskCommandError.TARGET_ENDED
            TaskRuleViolation.ALREADY_CLOSED -> TaskCommandError.ALREADY_CLOSED
            TaskRuleViolation.NOT_CLOSED -> TaskCommandError.NOT_CLOSED
        }

    /** 详情空白归一为 null；非空白保留原文（FR-D-03、T-079 §1.1）。 */
    private fun normalizeDetail(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() }
}
