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

/** S1 项目纯规则（AC-T-17、BR-45；D-22）。 */
object ProjectRules {

    fun validateName(name: String): ProjectRuleViolation? =
        if (name.isBlank()) ProjectRuleViolation.BLANK_NAME else null

    fun isActive(project: Project): Boolean = project.status == ProjectStatus.ACTIVE

    /** 项目下仍在进行（未结项）的子任务；批量结项只作用于这些任务。 */
    fun activeTasks(projectId: ProjectId, tasks: List<Task>): List<Task> =
        tasks.filter { it.projectId == projectId && !TaskRules.isClosed(it) }

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
