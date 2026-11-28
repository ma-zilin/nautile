package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import java.time.Instant

/** 项目规则违规原因。 */
enum class ProjectRuleViolation {
    BLANK_NAME,
    ALREADY_CLOSED,
}

/**
 * 项目全量完成进度（R7 #24、架构 §7.2）。
 *
 * 口径：跨全部 GTD 清单统计归属该项目的子任务，仅 [ClosedOutcome.COMPLETED] 计入分子；
 * 无子任务时 [percent] 为 null（不显示虚假 0%）。
 */
data class ProjectProgress(
    val completedCount: Int,
    val totalCount: Int,
) {
    /** 向下取整 0..100；0 子任务返回 null。 */
    val percent: Int?
        get() = if (totalCount == 0) null else (completedCount * 100) / totalCount
}

/** S1 项目纯规则（AC-T-17、BR-45；D-22；R7 #24）。 */
object ProjectRules {

    fun validateName(name: String): ProjectRuleViolation? =
        if (name.isBlank()) ProjectRuleViolation.BLANK_NAME else null

    fun isActive(project: Project): Boolean = project.status == ProjectStatus.ACTIVE

    /** 项目下仍在进行（未结项）的子任务；批量结项只作用于这些任务。 */
    fun activeTasks(projectId: ProjectId, tasks: List<Task>): List<Task> =
        tasks.filter { it.projectId == projectId && !TaskRules.isClosed(it) }

    /** 计算项目在全量任务下的完成进度（与详情页口径 100% 一致；R7 #24）。 */
    fun progress(allTasks: List<Task>, projectId: ProjectId): ProjectProgress {
        var total = 0
        var completed = 0
        allTasks.forEach { task ->
            if (task.projectId == projectId) {
                total++
                if (task.closedOutcome == ClosedOutcome.COMPLETED) completed++
            }
        }
        return ProjectProgress(completedCount = completed, totalCount = total)
    }

    /** 参数顺序兼容重载：`progress(projectId, tasks)`，与 [activeTasks] 保持一致。 */
    fun progress(projectId: ProjectId, tasks: List<Task>): ProjectProgress =
        progress(tasks, projectId)

    /** 项目结项状态由完成/放弃结果映射，不进入任务完成率（BR-45、D-11）。 */
    fun close(project: Project, outcome: ClosedOutcome, now: Instant): Project = project.copy(
        status = when (outcome) {
            ClosedOutcome.COMPLETED -> ProjectStatus.COMPLETED
            ClosedOutcome.ABANDONED -> ProjectStatus.ABANDONED
        },
        updatedAt = now,
    )

    fun reopen(project: Project, now: Instant): Project = project.copy(
        status = ProjectStatus.ACTIVE,
        updatedAt = now,
    )
}
