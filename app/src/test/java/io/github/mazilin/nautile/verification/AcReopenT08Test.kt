package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.usecase.ProjectCommandResult
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommandResult
import io.github.mazilin.nautile.domain.usecase.TaskCommands
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

/**
 * S1-T08 / T50-01 的项目重开与任务命令衔接 AC 验证（T-064）。
 *
 * 预期取自 AC-T-28、AC-T-29、BR-45/80；只通过 ProjectCommands/TaskCommands
 * 的公开结果与内存仓库状态断言，不复用开发自测的 ProjectCommandsTest/TaskCommandsTest。
 */
class AcReopenT08Test {

    private val now: Instant = Instant.parse("2026-11-21T03:00:00Z")

    private lateinit var taskRepository: InMemoryTaskRepository
    private lateinit var projectRepository: InMemoryProjectRepository
    private lateinit var clock: FakeClock
    private lateinit var projectCommands: ProjectCommands
    private lateinit var taskCommands: TaskCommands

    @Before
    fun setUp() {
        taskRepository = InMemoryTaskRepository()
        projectRepository = InMemoryProjectRepository(taskRepository)
        clock = FakeClock(now)
        projectCommands = ProjectCommands(projectRepository, taskRepository, clock)
        taskCommands = TaskCommands(taskRepository, projectRepository, clock)
    }

    private suspend fun insertProject(status: ProjectStatus): ProjectId =
        projectRepository.insert(
            Project(
                id = ProjectId(0L),
                name = "概率论复习",
                status = status,
                createdAt = now.minusSeconds(3600),
                updatedAt = now.minusSeconds(3600),
            ),
        )

    private suspend fun insertClosedTask(projectId: ProjectId): TaskId =
        taskRepository.insert(
            Task(
                id = TaskId(0L),
                title = "刷题",
                projectId = projectId,
                position = GtdPosition.ENDED,
                positionBeforeClose = GtdPosition.WAITING,
                closedOutcome = ClosedOutcome.COMPLETED,
                closedAt = now.minusSeconds(60),
                closedOn = LocalDate.of(2026, 11, 21),
                createdAt = now.minusSeconds(600),
                updatedAt = now.minusSeconds(60),
            ),
        )

    @Test
    fun AC_T_28_reopen_closed_project_needs_confirmation_and_keeps_status() = runTest {
        val projectId = insertProject(ProjectStatus.COMPLETED)
        val taskId = insertClosedTask(projectId)
        val before = taskRepository.get(taskId)

        val result = projectCommands.reopen(projectId)

        assertEquals(
            ProjectCommandResult.NeedsReopenConfirmation(projectId, ProjectStatus.COMPLETED),
            result,
        )
        assertEquals(ProjectStatus.COMPLETED, projectRepository.get(projectId)?.status)
        assertEquals(before, taskRepository.get(taskId))
    }

    @Test
    fun AC_T_28_reopen_confirmed_activates_project_and_keeps_children() = runTest {
        val projectId = insertProject(ProjectStatus.COMPLETED)
        val taskId = insertClosedTask(projectId)
        val before = taskRepository.get(taskId)
        clock.advanceSeconds(120)

        val result = projectCommands.reopen(projectId, confirmed = true)

        assertTrue(result is ProjectCommandResult.Success)
        val project = projectRepository.get(projectId)
        assertEquals(ProjectStatus.ACTIVE, project?.status)
        assertEquals(now.plusSeconds(120), project?.updatedAt)
        assertEquals(before, taskRepository.get(taskId))
        assertEquals(1, taskRepository.observeAll().first().size)
    }

    @Test
    fun AC_T_28_reopen_cancel_writes_nothing_and_active_project_is_idempotent() = runTest {
        val closedId = insertProject(ProjectStatus.COMPLETED)
        val closedBefore = projectRepository.get(closedId)

        projectCommands.reopen(closedId)

        assertEquals(closedBefore, projectRepository.get(closedId))

        val activeId = insertProject(ProjectStatus.ACTIVE)
        val activeBefore = projectRepository.get(activeId)
        val result = projectCommands.reopen(activeId, confirmed = true)

        assertTrue(result is ProjectCommandResult.Success)
        assertEquals(activeBefore, projectRepository.get(activeId))
    }

    @Test
    fun AC_T_28_closed_project_task_reopen_needs_project_reopen_then_succeeds_once() = runTest {
        val projectId = insertProject(ProjectStatus.COMPLETED)
        val taskId = insertClosedTask(projectId)
        val before = taskRepository.get(taskId)

        val blocked = taskCommands.reopen(taskId)
        assertEquals(TaskCommandResult.NeedsProjectReopen(projectId), blocked)
        assertEquals(before, taskRepository.get(taskId))

        projectCommands.reopen(projectId, confirmed = true)
        val retried = taskCommands.reopen(taskId)

        assertTrue(retried is TaskCommandResult.Success)
        val reopened = taskRepository.get(taskId)
        assertEquals(GtdPosition.WAITING, reopened?.position)
        assertNull(reopened?.closedOutcome)
        assertNull(reopened?.closedAt)
        assertEquals(1, taskRepository.observeAll().first().count { it.id == taskId })
    }

    @Test
    fun AC_T_28_closed_project_create_blocked_then_succeeds_once() = runTest {
        val projectId = insertProject(ProjectStatus.COMPLETED)
        val beforeCount = taskRepository.observeAll().first().size

        val blocked = taskCommands.create("新任务", GtdPosition.INBOX, projectId)
        assertEquals(TaskCommandResult.NeedsProjectReopen(projectId), blocked)
        assertEquals(beforeCount, taskRepository.observeAll().first().size)

        projectCommands.reopen(projectId, confirmed = true)
        val retried = taskCommands.create("新任务", GtdPosition.INBOX, projectId)
        assertTrue(retried is TaskCommandResult.Success)

        val tasks = taskRepository.observeAll().first()
        assertEquals(beforeCount + 1, tasks.size)
        assertEquals(1, tasks.count { it.title == "新任务" })
    }

    @Test
    fun AC_T_28_closed_project_move_blocked_then_succeeds_once() = runTest {
        val projectId = insertProject(ProjectStatus.COMPLETED)
        val taskId = taskRepository.insert(
            Task(
                id = TaskId(0L),
                title = "待移动",
                projectId = projectId,
                position = GtdPosition.INBOX,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val blocked = taskCommands.moveToGtd(taskId, GtdPosition.NEXT_ACTION)
        assertEquals(TaskCommandResult.NeedsProjectReopen(projectId), blocked)
        assertEquals(GtdPosition.INBOX, taskRepository.get(taskId)?.position)

        projectCommands.reopen(projectId, confirmed = true)
        val retried = taskCommands.moveToGtd(taskId, GtdPosition.NEXT_ACTION)

        assertTrue(retried is TaskCommandResult.Success)
        assertEquals(GtdPosition.NEXT_ACTION, taskRepository.get(taskId)?.position)
        assertEquals(1, taskRepository.observeAll().first().count { it.id == taskId })
    }

    @Test
    fun AC_T_28_repeated_unconfirmed_create_never_writes() = runTest {
        val projectId = insertProject(ProjectStatus.ABANDONED)
        val before = taskRepository.observeAll().first()

        repeat(3) {
            assertEquals(
                TaskCommandResult.NeedsProjectReopen(projectId),
                taskCommands.create("重复尝试", GtdPosition.INBOX, projectId),
            )
        }

        assertEquals(before, taskRepository.observeAll().first())
        assertEquals(ProjectStatus.ABANDONED, projectRepository.get(projectId)?.status)
        assertNotNull(projectRepository.get(projectId))
    }
}
