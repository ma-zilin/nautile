package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S1-T03 默认分组：项目任务不重复，单独任务归入“单独任务”组（FR-V-01、D-10）。 */
class TaskGroupingTest {

    private val now: Instant = Instant.parse("2026-11-19T02:00:00Z")

    private fun project(id: Long, name: String) = Project(
        id = ProjectId(id),
        name = name,
        createdAt = now,
        updatedAt = now,
    )

    private fun task(id: Long, projectId: ProjectId? = null) = Task(
        id = TaskId(id),
        title = "任务 $id",
        projectId = projectId,
        position = GtdPosition.INBOX,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun frV01_groups_tasks_by_project_without_duplicates() {
        val first = project(1L, "课程")
        val second = project(2L, "生活")
        val empty = project(3L, "空项目")
        val tasks = listOf(
            task(11L, first.id),
            task(12L, second.id),
            task(13L, first.id),
        )

        val groups = TaskGrouping.byProject(tasks, listOf(first, second, empty))

        assertEquals(2, groups.size)
        assertEquals(listOf(TaskId(11L), TaskId(13L)), groups[0].tasks.map { it.id })
        assertEquals(listOf(TaskId(12L)), groups[1].tasks.map { it.id })
        assertEquals(tasks.map { it.id }.toSet(), groups.flatMap { group -> group.tasks.map { it.id } }.toSet())
    }

    @Test
    fun frV01_standalone_and_unknown_project_tasks_go_to_single_group() {
        val known = project(1L, "课程")
        val tasks = listOf(
            task(21L, known.id),
            task(22L, null),
            task(23L, ProjectId(99L)),
        )

        val groups = TaskGrouping.byProject(tasks, listOf(known))

        assertEquals(2, groups.size)
        assertEquals(known, groups[0].project)
        assertTrue(groups[1].isUngrouped)
        assertEquals(listOf(TaskId(22L), TaskId(23L)), groups[1].tasks.map { it.id })
    }

    @Test
    fun frV01_returns_empty_when_no_tasks() {
        assertTrue(TaskGrouping.byProject(emptyList(), listOf(project(1L, "课程"))).isEmpty())
    }
}
