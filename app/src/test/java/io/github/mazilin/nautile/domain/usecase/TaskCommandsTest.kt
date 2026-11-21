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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** S1-T03 任务用例；通过内存替身验证规则与仓库调用顺序。 */
class TaskCommandsTest {

    private val now: Instant = Instant.parse("2026-11-19T02:00:00Z")

    private lateinit var taskRepository: InMemoryTaskRepository
    private lateinit var projectRepository: InMemoryProjectRepository
    private lateinit var commands: TaskCommands

    @Before
    fun setUp() {
        taskRepository = InMemoryTaskRepository()
        projectRepository = InMemoryProjectRepository()
        commands = TaskCommands(taskRepository, projectRepository, FakeClock(now))
    }

    private suspend fun insertProject(status: ProjectStatus = ProjectStatus.ACTIVE): ProjectId =
        projectRepository.insert(
            Project(
                id = ProjectId(0L),
                name = "课程项目",
                status = status,
                createdAt = now,
                updatedAt = now,
            ),
        )

    @Test
    fun acT01_create_rejects_blank_title() = runTest {
        val result = commands.create("   ", GtdPosition.INBOX)

        assertEquals(TaskCommandResult.Invalid(TaskCommandError.BLANK_TITLE), result)
        assertTrue(taskRepository.observeAll().first().isEmpty())
    }

    @Test
    fun acT01_create_inserts_task_in_given_position() = runTest {
        val result = commands.create("写周报", GtdPosition.NEXT_ACTION)

        val created = (result as TaskCommandResult.Success).task
        assertTrue(created.id.value > 0L)
        assertEquals(GtdPosition.NEXT_ACTION, created.position)
        assertEquals(listOf("写周报"), taskRepository.observeByPosition(GtdPosition.NEXT_ACTION).first().map { it.title })
    }

    @Test
    fun acT17_create_in_closed_project_needs_project_reopen() = runTest {
        val projectId = insertProject(ProjectStatus.COMPLETED)

        val result = commands.create("写周报", GtdPosition.INBOX, projectId)

        assertEquals(TaskCommandResult.NeedsProjectReopen(projectId), result)
        assertTrue(taskRepository.observeAll().first().isEmpty())
    }

    @Test
    fun acT01_complete_sets_ended_and_records_previous_position() = runTest {
        val id = (commands.create("写周报", GtdPosition.WAITING) as TaskCommandResult.Success).task.id

        val result = commands.complete(id)

        val closed = (result as TaskCommandResult.Success).task
        assertEquals(GtdPosition.ENDED, closed.position)
        assertEquals(GtdPosition.WAITING, closed.positionBeforeClose)
        assertEquals(ClosedOutcome.COMPLETED, closed.closedOutcome)
        assertEquals(GtdPosition.ENDED, taskRepository.get(id)?.position)
    }

    @Test
    fun acT01_abandon_sets_ended_with_abandoned_outcome() = runTest {
        val id = (commands.create("写周报", GtdPosition.INBOX) as TaskCommandResult.Success).task.id

        val result = commands.abandon(id)

        assertEquals(ClosedOutcome.ABANDONED, (result as TaskCommandResult.Success).task.closedOutcome)
    }

    @Test
    fun acT01_complete_rejects_already_closed_task() = runTest {
        val id = (commands.create("写周报", GtdPosition.INBOX) as TaskCommandResult.Success).task.id
        commands.complete(id)

        val result = commands.complete(id)

        assertEquals(TaskCommandResult.Invalid(TaskCommandError.ALREADY_CLOSED), result)
    }

    @Test
    fun acT29_reopen_restores_previous_position() = runTest {
        val id = (commands.create("写周报", GtdPosition.SOMEDAY) as TaskCommandResult.Success).task.id
        commands.complete(id)

        val result = commands.reopen(id)

        val reopened = (result as TaskCommandResult.Success).task
        assertEquals(GtdPosition.SOMEDAY, reopened.position)
        assertNull(reopened.closedOutcome)
        assertNull(reopened.positionBeforeClose)
    }

    @Test
    fun acT29_reopen_active_task_is_rejected() = runTest {
        val id = (commands.create("写周报", GtdPosition.INBOX) as TaskCommandResult.Success).task.id

        val result = commands.reopen(id)

        assertEquals(TaskCommandResult.Invalid(TaskCommandError.NOT_CLOSED), result)
    }

    @Test
    fun acT29_reopen_task_in_closed_project_needs_project_reopen() = runTest {
        val projectId = insertProject()
        val id = (commands.create("写周报", GtdPosition.INBOX, projectId) as TaskCommandResult.Success).task.id
        commands.complete(id)
        projectRepository.update(
            requireNotNull(projectRepository.get(projectId)).copy(status = ProjectStatus.COMPLETED),
        )

        val result = commands.reopen(id)

        assertEquals(TaskCommandResult.NeedsProjectReopen(projectId), result)
    }

    @Test
    fun acT01_move_to_ended_is_rejected() = runTest {
        val id = (commands.create("写周报", GtdPosition.INBOX) as TaskCommandResult.Success).task.id

        val result = commands.moveToGtd(id, GtdPosition.ENDED)

        assertEquals(TaskCommandResult.Invalid(TaskCommandError.TARGET_ENDED), result)
        assertEquals(GtdPosition.INBOX, taskRepository.get(id)?.position)
    }

    @Test
    fun acT01_move_between_positions_updates_task() = runTest {
        val id = (commands.create("写周报", GtdPosition.INBOX) as TaskCommandResult.Success).task.id

        commands.moveToGtd(id, GtdPosition.WAITING)

        assertEquals(GtdPosition.WAITING, taskRepository.get(id)?.position)
        assertTrue(taskRepository.observeByPosition(GtdPosition.INBOX).first().isEmpty())
    }

    @Test
    fun acT01_delete_removes_task() = runTest {
        val id = (commands.create("写周报", GtdPosition.INBOX) as TaskCommandResult.Success).task.id

        val result = commands.delete(id)

        assertEquals(TaskCommandResult.Deleted, result)
        assertNull(taskRepository.get(id))
    }

    @Test
    fun acD06_update_field_rejects_blank_title() = runTest {
        val id = (commands.create("写周报", GtdPosition.INBOX) as TaskCommandResult.Success).task.id

        val result = commands.updateField(id, TaskFieldUpdate.Title(" "))

        assertEquals(TaskCommandResult.Invalid(TaskCommandError.BLANK_TITLE), result)
        assertEquals("写周报", taskRepository.get(id)?.title)
    }

    @Test
    fun acD06_update_field_saves_title_and_priority() = runTest {
        val id = (commands.create("写周报", GtdPosition.INBOX) as TaskCommandResult.Success).task.id

        commands.updateField(id, TaskFieldUpdate.Title("写季报"))
        commands.updateField(id, TaskFieldUpdate.Priority(io.github.mazilin.nautile.domain.model.Priority.HIGH))

        val stored = requireNotNull(taskRepository.get(id))
        assertEquals("写季报", stored.title)
        assertEquals(io.github.mazilin.nautile.domain.model.Priority.HIGH, stored.priority)
    }

    @Test
    fun acT01_update_field_project_in_closed_project_needs_project_reopen() = runTest {
        val projectId = insertProject(ProjectStatus.COMPLETED)
        val id = (commands.create("写周报", GtdPosition.INBOX) as TaskCommandResult.Success).task.id

        val result = commands.updateField(id, TaskFieldUpdate.Project(projectId))

        assertEquals(TaskCommandResult.NeedsProjectReopen(projectId), result)
        assertNull(taskRepository.get(id)?.projectId)
    }
}
