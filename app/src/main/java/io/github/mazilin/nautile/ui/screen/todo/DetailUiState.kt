package io.github.mazilin.nautile.ui.screen.todo

import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.usecase.TaskFieldUpdate
import java.time.LocalDate
import java.time.LocalTime

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

    /**
     * 编辑面板一次保存的全部字段（R4 G5、CMP-37.1）。
     *
     * 只描述用户确认后的目标值；执行时按当前任务逐字段对比，未变化字段不写库，
     * 全部沿用既有 TaskCommands.updateField/moveToGtd，不新增命令语义。
     * 项目变更触发重开确认时保存整份目标值，确认后只重试一次。
     */
    data class SaveTaskEdit(
        val taskId: TaskId,
        val title: String,
        val detail: String,
        val priority: Priority,
        val dueDate: LocalDate?,
        val dueTime: LocalTime?,
        val projectId: ProjectId?,
        val position: GtdPosition,
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

/**
 * 任务编辑面板状态（R4 G5、CMP-37.1、AC-D-07）。
 *
 * 打开时从当前任务预填；标题/详情/四项属性只保存在内存，点“保存”后一次对比并逐字段写库。
 * [closed] 为 true 时 GTD 清单入口禁用（CMP-35）；[titleInvalid] 为 true 时阻止关闭并提示。
 */
data class TaskEditUiState(
    val taskId: TaskId,
    val title: String,
    val detail: String,
    val priority: Priority,
    val dueDate: LocalDate?,
    val dueTime: LocalTime?,
    val projectId: ProjectId?,
    val projectName: String?,
    val position: GtdPosition,
    val closed: Boolean = false,
    val titleInvalid: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    /** 项目菜单内“新建项目”的名称草稿与错误（与详情面板同一套守卫，CMP-34）。 */
    val projectDraftName: String = "",
    val projectDraftError: String? = null,
    val creatingProject: Boolean = false,
)

/** 项目编辑面板状态（CMP-37.1）：只改名称，空名阻止关闭并提示。 */
data class ProjectEditUiState(
    val projectId: ProjectId,
    val name: String,
    val nameInvalid: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

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
