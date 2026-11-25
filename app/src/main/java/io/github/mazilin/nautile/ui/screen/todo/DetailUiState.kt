package io.github.mazilin.nautile.ui.screen.todo

import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.usecase.TaskFieldUpdate

/**
 * 待重试的任务命令描述；不保存 Composable、仓库或 suspend lambda（T50-01）。
 * [detail] 用于项目重开确认后的重试，避免丢失新建时的详情（T-080）。
 */
sealed interface PendingTaskCommand {
    data class Create(
        val title: String,
        val position: GtdPosition,
        val projectId: ProjectId?,
        val detail: String? = null,
    ) : PendingTaskCommand

    data class MoveToGtd(
        val taskId: TaskId,
        val target: GtdPosition,
    ) : PendingTaskCommand

    data class Reopen(val taskId: TaskId) : PendingTaskCommand

    /** 详情属性编辑走既有 updateField；项目已结项时由重开确认守卫重试一次（CMP-34）。 */
    data class UpdateField(
        val taskId: TaskId,
        val update: TaskFieldUpdate,
    ) : PendingTaskCommand
}

/** 项目已结项时的重开确认；确认后由 [TodoViewModel] 只重试原命令一次。 */
data class ProjectReopenPrompt(
    val projectId: ProjectId,
    val projectName: String,
    val status: ProjectStatus,
    val pending: PendingTaskCommand,
)

/** 任务半屏详情状态（S1-T08、AC-D-06/08、AC-T-29；G7 属性编辑）。 */
data class TaskDetailUiState(
    val task: Task,
    val titleDraft: String?,
    val titleInvalid: Boolean,
    val saving: Boolean,
    val error: String?,
    val leaveBlocked: Boolean,
    val canReopen: Boolean,
    val detailDraft: String? = null,
    /** 当前关联项目名（项目可能已结项，仍显示名称）；无项目为 null。 */
    val projectName: String? = null,
    /** 项目菜单“新建项目”的名称草稿（CMP-34）。 */
    val projectDraftName: String = "",
    val projectDraftError: String? = null,
    val creatingProject: Boolean = false,
) {
    val titleValue: String get() = titleDraft ?: task.title
    val detailValue: String get() = detailDraft ?: task.detail.orEmpty()
    val canLeave: Boolean
        get() = !titleInvalid && titleDraft == null && detailDraft == null
}

/** 项目半屏详情状态（AC-D-06、FR-V-08）。 */
data class ProjectDetailUiState(
    val project: Project,
    val children: List<Task>,
    val nameDraft: String?,
    val nameInvalid: Boolean,
    val saving: Boolean,
    val error: String?,
    val leaveBlocked: Boolean,
    val collapsed: Boolean,
) {
    val nameValue: String get() = nameDraft ?: project.name
    val canLeave: Boolean get() = !nameInvalid && nameDraft == null
    val closedLabel: String
        get() = when (project.status) {
            ProjectStatus.ACTIVE -> "活跃"
            ProjectStatus.COMPLETED -> "已完成"
            ProjectStatus.ABANDONED -> "已放弃"
        }
}

/** 子任务结项展示文案；无结项时显示 GTD 清单名。 */
fun Task.detailStatusLabel(): String = when (closedOutcome) {
    ClosedOutcome.COMPLETED -> "已完成"
    ClosedOutcome.ABANDONED -> "已放弃"
    null -> position.title
}
