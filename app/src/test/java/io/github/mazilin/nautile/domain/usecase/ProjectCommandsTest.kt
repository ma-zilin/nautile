package io.github.mazilin.nautile.domain.usecase

import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.testing.FakeClock
import io.github.mazilin.nautile.testing.InMemoryProjectRepository
import io.github.mazilin.nautile.testing.InMemoryTaskRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** S1-T03 项目用例；结项确认分支与删除级联通过内存替身验证。 */
class ProjectCommandsTest {

    private val now: Instant = Instant.parse("2026-11-19T02:00:00Z")

    private lateinit var taskRepository: InMemoryTaskRepository
    private lateinit var projectRepository: InMemoryProjectRepository
    private lateinit var commands: ProjectCommands

    @Before
    fun setUp() {
        taskRepository = InMemoryTaskRepository()
        projectRepository = InMemoryProjectRepository(taskRepository)
        commands = ProjectCommands(projectRepository, taskRepository, FakeClock(now))
    }

    private suspend fun insertTask(
        title: String,
        projectId: ProjectId? = null,
        position: GtdPosition = GtdPosition.INBOX,
    ): TaskId = taskRepository.insert(
        Task(
            id = TaskId(0L),
            title = title,
            projectId = projectId,
            position = position,
            closedOutcome = if (position == GtdPosition.ENDED) ClosedOutcome.COMPLETED else null,
            closedAt = if (position == GtdPosition.ENDED) now else null,
            closedOn = if (position == GtdPosition.ENDED) LocalDate.of(2026, 11, 19) else null,
            createdAt = now,
            updatedAt = now,
        ),
    )

    @Test
    fun acT17_blank_project_name_is_rejected() = runTest {
        val result = commands.create("   ")

        assertEquals(ProjectCommandResult.Invalid(ProjectCommandError.BLANK_NAME), result)
        assertTrue(projectRepository.observeAll().first().isEmpty())
    }

    @Test
    fun acT17_create_and_rename_project() = runTest {
        val created = (commands.create("课程项目") as ProjectCommandResult.Success).project

        commands.rename(created.id, "课程项目二")

        assertEquals("课程项目二", projectRepository.get(created.id)?.name)
    }

    @Test
    fun acT17_project_with_active_tasks_needs_confirmation() = runTest {
        val projectId = (commands.create("课程项目") as ProjectCommandResult.Success).project.id
        insertTask("子任务一", projectId)
        insertTask("子任务二", projectId, GtdPosition.WAITING)
        insertTask("已完成子任务", projectId, GtdPosition.ENDED)

        val result = commands.complete(projectId)

        assertEquals(ProjectCommandResult.NeedsConfirmation(2), result)
        assertEquals(ProjectStatus.ACTIVE, projectRepository.get(projectId)?.status)
        assertEquals(
            3,
            taskRepository.observeAll().first().count { it.projectId == projectId },
        )
    }

    @Test
    fun acT17_cancel_leaves_project_and_tasks_unchanged() = runTest {
        val projectId = (commands.create("课程项目") as ProjectCommandResult.Success).project.id
        val taskId = insertTask("子任务一", projectId)

        commands.complete(projectId, confirmed = false)

        assertEquals(ProjectStatus.ACTIVE, projectRepository.get(projectId)?.status)
        assertEquals(GtdPosition.INBOX, taskRepository.get(taskId)?.position)
        assertNull(taskRepository.get(taskId)?.closedOutcome)
    }

    @Test
    fun acT17_confirm_closes_project_and_active_children() = runTest {
        val projectId = (commands.create("课程项目") as ProjectCommandResult.Success).project.id
        val activeId = insertTask("子任务一", projectId)
        val endedId = insertTask("已完成子任务", projectId, GtdPosition.ENDED)

        val result = commands.complete(projectId, confirmed = true)

        assertEquals(ProjectStatus.COMPLETED, (result as ProjectCommandResult.Success).project.status)
        assertEquals(ProjectStatus.COMPLETED, projectRepository.get(projectId)?.status)
        assertEquals(GtdPosition.ENDED, taskRepository.get(activeId)?.position)
        assertEquals(ClosedOutcome.COMPLETED, taskRepository.get(activeId)?.closedOutcome)
        assertEquals(GtdPosition.INBOX, taskRepository.get(activeId)?.positionBeforeClose)
        assertEquals(ClosedOutcome.COMPLETED, taskRepository.get(endedId)?.closedOutcome)
    }

    @Test
    fun acT17_abandon_project_closes_children_as_abandoned() = runTest {
        val projectId = (commands.create("课程项目") as ProjectCommandResult.Success).project.id
        val taskId = insertTask("子任务一", projectId)

        commands.abandon(projectId, confirmed = true)

        assertEquals(ProjectStatus.ABANDONED, projectRepository.get(projectId)?.status)
        assertEquals(ClosedOutcome.ABANDONED, taskRepository.get(taskId)?.closedOutcome)
    }

    @Test
    fun acT17_closed_project_cannot_close_again() = runTest {
        val projectId = (commands.create("课程项目") as ProjectCommandResult.Success).project.id
        commands.complete(projectId, confirmed = true)

        val result = commands.complete(projectId, confirmed = true)

        assertEquals(ProjectCommandResult.Invalid(ProjectCommandError.ALREADY_CLOSED), result)
    }

    @Test
    fun acT17_delete_project_needs_confirmation_then_removes_children() = runTest {
        val projectId = (commands.create("课程项目") as ProjectCommandResult.Success).project.id
        val firstId = insertTask("子任务一", projectId)
        val secondId = insertTask("子任务二", projectId)

        val pending = commands.delete(projectId, confirmed = false)
        assertEquals(ProjectCommandResult.NeedsConfirmation(2), pending)
        assertNotNull(projectRepository.get(projectId))
        assertNotNull(taskRepository.get(firstId))

        val deleted = commands.delete(projectId, confirmed = true)

        assertEquals(ProjectCommandResult.Deleted, deleted)
        assertNull(projectRepository.get(projectId))
        assertNull(taskRepository.get(firstId))
        assertNull(taskRepository.get(secondId))
    }

    @Test
    fun acT17_delete_empty_project_needs_no_confirmation() = runTest {
        val projectId = (commands.create("课程项目") as ProjectCommandResult.Success).project.id

        val result = commands.delete(projectId)

        assertEquals(ProjectCommandResult.Deleted, result)
        assertNull(projectRepository.get(projectId))
    }
}
