package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.usecase.ProjectCommandError
import io.github.mazilin.nautile.domain.usecase.ProjectCommandResult
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommandError
import io.github.mazilin.nautile.domain.usecase.TaskCommandResult
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.domain.usecase.TaskFieldUpdate
import io.github.mazilin.nautile.testing.FakeClock
import io.github.mazilin.nautile.testing.InMemoryProjectRepository
import io.github.mazilin.nautile.testing.InMemoryTaskRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 任务/项目用例 AC 验证（T-052；AC-T-01、AC-T-17、AC-T-29、AC-D-06、AC-D-08）。
 *
 * 预期结果取自基线 AC 文本；通过公开用例与仓库接口断言，不复制开发自测断言。
 * 每个 AC 的正常路径与拒绝分支分别至少一例。
 */
class AcTaskBehaviorTest {

    private val now: Instant = Instant.parse("2026-11-21T02:00:00Z")
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private lateinit var taskStore: InMemoryTaskRepository
    private lateinit var projectStore: InMemoryProjectRepository
    private lateinit var clock: FakeClock
    private lateinit var tasks: TaskCommands
    private lateinit var projects: ProjectCommands

    @Before
    fun setUp() {
        taskStore = InMemoryTaskRepository()
        projectStore = InMemoryProjectRepository(taskStore)
        clock = FakeClock(now)
        tasks = TaskCommands(taskStore, projectStore, clock, zone)
        projects = ProjectCommands(projectStore, taskStore, clock, zone)
    }

    private suspend fun createTask(
        title: String = "任务",
        position: GtdPosition = GtdPosition.INBOX,
        projectId: ProjectId? = null,
    ): TaskId = (tasks.create(title, position, projectId) as TaskCommandResult.Success).task.id

    private suspend fun createProject(name: String = "课程项目"): ProjectId =
        (projects.create(name) as ProjectCommandResult.Success).project.id

    private suspend fun storedTask(id: TaskId): Task? = taskStore.get(id)

    // ---------- AC-T-01：任务位置、结项与项目归属 ----------

    @Test
    fun AC_T_01_create_in_four_active_positions() = runTest {
        listOf(
            GtdPosition.INBOX,
            GtdPosition.NEXT_ACTION,
            GtdPosition.WAITING,
            GtdPosition.SOMEDAY,
        ).forEach { position ->
            val result = tasks.create("任务-$position", position)

            assertTrue("在 $position 创建应成功", result is TaskCommandResult.Success)
            assertEquals(position, (result as TaskCommandResult.Success).task.position)
        }
        assertEquals(4, taskStore.observeAll().first().size)
    }

    @Test
    fun AC_T_01_create_in_ended_is_rejected() = runTest {
        val result = tasks.create("直接结项", GtdPosition.ENDED)

        assertEquals(TaskCommandResult.Invalid(TaskCommandError.TARGET_ENDED), result)
        assertTrue(taskStore.observeAll().first().isEmpty())
    }

    @Test
    fun AC_T_01_blank_title_is_rejected() = runTest {
        val result = tasks.create("   ", GtdPosition.INBOX)

        assertEquals(TaskCommandResult.Invalid(TaskCommandError.BLANK_TITLE), result)
        assertTrue(taskStore.observeAll().first().isEmpty())
    }

    @Test
    fun AC_T_01_complete_and_abandon_enter_ended_atomically() = runTest {
        val completedId = createTask("完成项", GtdPosition.WAITING)
        val abandonedId = createTask("放弃项", GtdPosition.SOMEDAY)

        val completed = tasks.complete(completedId) as TaskCommandResult.Success
        val abandoned = tasks.abandon(abandonedId) as TaskCommandResult.Success

        assertEquals(GtdPosition.ENDED, completed.task.position)
        assertEquals(ClosedOutcome.COMPLETED, completed.task.closedOutcome)
        assertEquals(GtdPosition.WAITING, completed.task.positionBeforeClose)
        assertEquals(now, completed.task.closedAt)
        assertEquals(LocalDate.of(2026, 11, 21), completed.task.closedOn)

        assertEquals(GtdPosition.ENDED, abandoned.task.position)
        assertEquals(ClosedOutcome.ABANDONED, abandoned.task.closedOutcome)
        assertEquals(GtdPosition.SOMEDAY, abandoned.task.positionBeforeClose)

        assertEquals(
            setOf("完成项", "放弃项"),
            taskStore.observeByPosition(GtdPosition.ENDED).first().map { it.title }.toSet(),
        )
    }

    @Test
    fun AC_T_01_move_to_ended_is_rejected() = runTest {
        val id = createTask("未结项", GtdPosition.INBOX)

        val result = tasks.moveToGtd(id, GtdPosition.ENDED)

        assertEquals(TaskCommandResult.Invalid(TaskCommandError.TARGET_ENDED), result)
        assertEquals(GtdPosition.INBOX, storedTask(id)?.position)
    }

    @Test
    fun AC_T_01_move_between_active_positions_updates_position() = runTest {
        val id = createTask("移动任务", GtdPosition.INBOX)

        val result = tasks.moveToGtd(id, GtdPosition.WAITING)

        assertTrue(result is TaskCommandResult.Success)
        assertEquals(GtdPosition.WAITING, (result as TaskCommandResult.Success).task.position)
        assertEquals(GtdPosition.WAITING, storedTask(id)?.position)
        assertTrue(taskStore.observeByPosition(GtdPosition.INBOX).first().isEmpty())
    }

    @Test
    fun AC_T_01_closed_task_cannot_move_between_positions() = runTest {
        val id = createTask("已结项", GtdPosition.INBOX)
        tasks.complete(id)

        val result = tasks.moveToGtd(id, GtdPosition.WAITING)

        assertEquals(TaskCommandResult.Invalid(TaskCommandError.ALREADY_CLOSED), result)
        assertEquals(GtdPosition.ENDED, storedTask(id)?.position)
    }

    @Test
    fun AC_T_01_task_can_belong_to_project_and_position() = runTest {
        val projectId = createProject()

        val result = tasks.create("项目任务", GtdPosition.NEXT_ACTION, projectId)

        val created = (result as TaskCommandResult.Success).task
        assertEquals(projectId, created.projectId)
        assertEquals(GtdPosition.NEXT_ACTION, created.position)
        assertEquals(projectId, storedTask(created.id)?.projectId)
    }

    @Test
    fun AC_T_01_project_is_not_a_sixth_gtd_position() {
        assertEquals(5, GtdPosition.entries.size)
        assertTrue(GtdPosition.entries.none { it.name == "PROJECT" })
    }

    @Test
    fun AC_T_01_delete_task_removes_it() = runTest {
        val id = createTask("待删除")

        val result = tasks.delete(id)

        assertEquals(TaskCommandResult.Deleted, result)
        assertNull(storedTask(id))
    }

    // ---------- AC-T-17：项目完成/放弃/删除 ----------

    @Test
    fun AC_T_17_project_with_three_active_children_needs_confirmation() = runTest {
        val projectId = createProject()
        repeat(3) { index -> createTask("子任务-$index", GtdPosition.INBOX, projectId) }

        val result = projects.complete(projectId)

        assertEquals(ProjectCommandResult.NeedsConfirmation(3), result)
        assertEquals(ProjectStatus.ACTIVE, projectStore.get(projectId)?.status)
        assertEquals(
            3,
            taskStore.observeAll().first().count { it.projectId == projectId && it.closedOutcome == null },
        )
    }

    @Test
    fun AC_T_17_cancel_leaves_project_and_children_unchanged() = runTest {
        val projectId = createProject()
        val taskId = createTask("子任务", GtdPosition.WAITING, projectId)

        val result = projects.complete(projectId, confirmed = false)

        assertEquals(ProjectCommandResult.NeedsConfirmation(1), result)
        assertEquals(ProjectStatus.ACTIVE, projectStore.get(projectId)?.status)
        assertEquals(GtdPosition.WAITING, storedTask(taskId)?.position)
        assertNull(storedTask(taskId)?.closedOutcome)
    }

    @Test
    fun AC_T_17_confirm_completes_project_and_children() = runTest {
        val projectId = createProject()
        val first = createTask("子任务一", GtdPosition.INBOX, projectId)
        val second = createTask("子任务二", GtdPosition.WAITING, projectId)

        val result = projects.complete(projectId, confirmed = true)

        assertEquals(
            ProjectStatus.COMPLETED,
            (result as ProjectCommandResult.Success).project.status,
        )
        assertEquals(ProjectStatus.COMPLETED, projectStore.get(projectId)?.status)
        assertEquals(GtdPosition.ENDED, storedTask(first)?.position)
        assertEquals(GtdPosition.ENDED, storedTask(second)?.position)
        assertEquals(ClosedOutcome.COMPLETED, storedTask(first)?.closedOutcome)
        assertEquals(ClosedOutcome.COMPLETED, storedTask(second)?.closedOutcome)
        assertEquals(GtdPosition.INBOX, storedTask(first)?.positionBeforeClose)
        assertEquals(GtdPosition.WAITING, storedTask(second)?.positionBeforeClose)
    }

    @Test
    fun AC_T_17_abandon_sets_project_and_children_abandoned() = runTest {
        val projectId = createProject()
        val taskId = createTask("子任务", GtdPosition.INBOX, projectId)

        val result = projects.abandon(projectId, confirmed = true)

        assertEquals(
            ProjectStatus.ABANDONED,
            (result as ProjectCommandResult.Success).project.status,
        )
        assertEquals(ProjectStatus.ABANDONED, projectStore.get(projectId)?.status)
        assertEquals(ClosedOutcome.ABANDONED, storedTask(taskId)?.closedOutcome)
    }

    @Test
    fun AC_T_17_closed_project_cannot_close_again() = runTest {
        val projectId = createProject()
        projects.complete(projectId, confirmed = true)

        val result = projects.complete(projectId, confirmed = true)

        assertEquals(ProjectCommandResult.Invalid(ProjectCommandError.ALREADY_CLOSED), result)
    }

    @Test
    fun AC_T_17_delete_project_needs_confirmation_then_cascades() = runTest {
        val projectId = createProject()
        val first = createTask("子任务一", GtdPosition.INBOX, projectId)
        val second = createTask("子任务二", GtdPosition.WAITING, projectId)

        val pending = projects.delete(projectId, confirmed = false)
        assertEquals(ProjectCommandResult.NeedsConfirmation(2), pending)
        assertEquals(projectId, projectStore.get(projectId)?.id)
        assertEquals(first, storedTask(first)?.id)
        assertEquals(second, storedTask(second)?.id)

        val deleted = projects.delete(projectId, confirmed = true)
        assertEquals(ProjectCommandResult.Deleted, deleted)
        assertNull(projectStore.get(projectId))
        assertNull(storedTask(first))
        assertNull(storedTask(second))
    }

    // ---------- AC-T-29：重新打开 ----------

    @Test
    fun AC_T_29_reopen_restores_previous_position_and_clears_closed_fields() = runTest {
        val id = createTask("重开任务", GtdPosition.WAITING)
        tasks.complete(id)
        clock.advanceSeconds(60)

        val result = tasks.reopen(id) as TaskCommandResult.Success

        assertEquals(GtdPosition.WAITING, result.task.position)
        assertNull(result.task.positionBeforeClose)
        assertNull(result.task.closedOutcome)
        assertNull(result.task.closedAt)
        assertNull(result.task.closedOn)
        assertEquals(now.plusSeconds(60), result.task.updatedAt)
        assertEquals(GtdPosition.WAITING, storedTask(id)?.position)
    }

    @Test
    fun AC_T_29_reopen_without_previous_position_goes_inbox() = runTest {
        val id = taskStore.insert(
            Task(
                id = TaskId(0L),
                title = "无结项前位置",
                position = GtdPosition.ENDED,
                closedOutcome = ClosedOutcome.COMPLETED,
                closedAt = now,
                closedOn = LocalDate.of(2026, 11, 21),
                createdAt = now,
                updatedAt = now,
            ),
        )

        val result = tasks.reopen(id) as TaskCommandResult.Success

        assertEquals(GtdPosition.INBOX, result.task.position)
    }

    @Test
    fun AC_T_29_reopen_active_task_is_rejected() = runTest {
        val id = createTask("未结项")

        val result = tasks.reopen(id)

        assertEquals(TaskCommandResult.Invalid(TaskCommandError.NOT_CLOSED), result)
    }

    @Test
    fun AC_T_29_reopen_in_closed_project_prompts_project_reopen() = runTest {
        val projectId = createProject()
        val id = createTask("项目任务", GtdPosition.INBOX, projectId)
        tasks.complete(id)
        val project = requireNotNull(projectStore.get(projectId))
        projectStore.update(project.copy(status = ProjectStatus.COMPLETED))

        val result = tasks.reopen(id)

        assertEquals(TaskCommandResult.NeedsProjectReopen(projectId), result)
    }

    // ---------- AC-D-06 / AC-D-08：字段编辑即保存与无效字段 ----------

    @Test
    fun AC_D_06_update_title_saves_immediately() = runTest {
        val id = createTask("原标题")

        val result = tasks.updateField(id, TaskFieldUpdate.Title("新标题"))

        assertTrue(result is TaskCommandResult.Success)
        assertEquals("新标题", storedTask(id)?.title)
    }

    @Test
    fun AC_D_06_update_blank_title_is_rejected_and_not_saved() = runTest {
        val id = createTask("原标题")

        val result = tasks.updateField(id, TaskFieldUpdate.Title("   "))

        assertEquals(TaskCommandResult.Invalid(TaskCommandError.BLANK_TITLE), result)
        assertEquals("原标题", storedTask(id)?.title)
    }

    @Test
    fun AC_D_06_project_rename_saves_and_blank_name_is_rejected() = runTest {
        val projectId = createProject("旧项目名")

        val saved = projects.rename(projectId, "新项目名")
        assertTrue(saved is ProjectCommandResult.Success)
        assertEquals("新项目名", projectStore.get(projectId)?.name)

        val rejected = projects.rename(projectId, "   ")
        assertEquals(ProjectCommandResult.Invalid(ProjectCommandError.BLANK_NAME), rejected)
        assertEquals("新项目名", projectStore.get(projectId)?.name)
    }

    @Test
    fun AC_D_06_update_task_project_to_closed_project_prompts_reopen() = runTest {
        val activeProject = createProject("活跃项目")
        val closedProject = createProject("已结项项目")
        projects.complete(closedProject, confirmed = true)
        val id = createTask("项目任务", GtdPosition.INBOX, activeProject)

        val result = tasks.updateField(id, TaskFieldUpdate.Project(closedProject))

        assertEquals(TaskCommandResult.NeedsProjectReopen(closedProject), result)
        assertEquals(activeProject, storedTask(id)?.projectId)
    }

    @Test
    fun AC_D_08_invalid_title_then_corrected_saves() = runTest {
        val id = createTask("原标题")

        val invalid = tasks.updateField(id, TaskFieldUpdate.Title(" "))
        assertEquals(TaskCommandResult.Invalid(TaskCommandError.BLANK_TITLE), invalid)
        assertEquals("原标题", storedTask(id)?.title)

        val corrected = tasks.updateField(id, TaskFieldUpdate.Title("修正标题"))
        assertTrue(corrected is TaskCommandResult.Success)
        assertEquals("修正标题", storedTask(id)?.title)
    }
}
