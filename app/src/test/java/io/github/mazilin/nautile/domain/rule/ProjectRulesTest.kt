package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S1-T03 项目规则；纯 JVM，无 Android 依赖。 */
class ProjectRulesTest {

    private val now: Instant = Instant.parse("2026-11-19T02:00:00Z")
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val projectId = ProjectId(7L)

    private fun project(status: ProjectStatus = ProjectStatus.ACTIVE) = Project(
        id = projectId,
        name = "课程项目",
        status = status,
        createdAt = now,
        updatedAt = now,
    )

    private fun task(
        id: Long,
        position: GtdPosition,
        projectId: ProjectId? = ProjectId(7L),
    ) = Task(
        id = TaskId(id),
        title = "子任务 $id",
        projectId = projectId,
        position = position,
        closedOutcome = if (position == GtdPosition.ENDED) ClosedOutcome.COMPLETED else null,
        closedAt = if (position == GtdPosition.ENDED) now else null,
        closedOn = if (position == GtdPosition.ENDED) LocalDate.of(2026, 11, 19) else null,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun acT17_blank_project_name_is_rejected() {
        assertEquals(ProjectRuleViolation.BLANK_NAME, ProjectRules.validateName(" "))
        assertNull(ProjectRules.validateName("课程项目"))
    }

    @Test
    fun acT17_active_tasks_exclude_ended_children_and_other_projects() {
        val tasks = listOf(
            task(1L, GtdPosition.INBOX),
            task(2L, GtdPosition.WAITING),
            task(3L, GtdPosition.ENDED),
            task(4L, GtdPosition.INBOX, projectId = ProjectId(99L)),
        )

        val active = ProjectRules.activeTasks(projectId, tasks)

        assertEquals(listOf(TaskId(1L), TaskId(2L)), active.map { it.id })
    }

    @Test
    fun acT17_close_maps_outcome_to_project_status() {
        val completed = ProjectRules.close(project(), ClosedOutcome.COMPLETED, now)
        val abandoned = ProjectRules.close(project(), ClosedOutcome.ABANDONED, now)

        assertEquals(ProjectStatus.COMPLETED, completed.status)
        assertEquals(ProjectStatus.ABANDONED, abandoned.status)
        assertEquals(now, completed.updatedAt)
    }

    @Test
    fun acT17_closed_project_reopens_to_active() {
        val reopened = ProjectRules.reopen(project(ProjectStatus.COMPLETED), now)

        assertEquals(ProjectStatus.ACTIVE, reopened.status)
        assertEquals(now, reopened.updatedAt)
    }
}
