package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.repository.TaskRepository
import io.github.mazilin.nautile.domain.rule.TaskTimeRules
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.testing.FakeClock
import io.github.mazilin.nautile.testing.FakeSettingsRepository
import io.github.mazilin.nautile.testing.InMemoryProjectRepository
import io.github.mazilin.nautile.testing.InMemoryTaskRepository
import io.github.mazilin.nautile.ui.screen.todo.TodoViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R5-G3 独立验收（JVM 单测）：任务逾期规则、截止时间文案与完成防重入/失败回滚。
 *
 * 依据 `docs/需求基线.md` v2.16（FR-G-09、FR-G-14、FR-D-03、BR-38、AC-T-17、AC-D-09）与
 * `.plans/设计/03_交互与动效规格.md`（§2.2、§3.3.2）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AcTaskTimeAndOverdueR5G3Test {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 11, 27)
    private val nowTime: LocalTime = LocalTime.of(14, 30)
    private val now: Instant = LocalDateTime.of(today, nowTime).atZone(zone).toInstant()
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun task(
        id: Long = 0L,
        title: String = "示例任务",
        position: GtdPosition = GtdPosition.INBOX,
        closedOutcome: ClosedOutcome? = null,
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
    ): Task = Task(
        id = TaskId(id),
        title = title,
        position = position,
        positionBeforeClose = if (closedOutcome != null) GtdPosition.INBOX else null,
        closedOutcome = closedOutcome,
        closedAt = if (closedOutcome != null) now else null,
        closedOn = if (closedOutcome != null) today else null,
        dueDate = dueDate,
        dueTime = dueTime,
        createdAt = now,
        updatedAt = now,
    )

    // ---------- TaskTimeRules.isOverdue ----------

    @Test
    fun FR_G_09_BR_38_no_due_date_is_never_overdue() {
        val t = task(dueDate = null, dueTime = null)
        assertFalse(TaskTimeRules.isOverdue(t, now, zone))
    }

    @Test
    fun FR_G_09_BR_38_closed_task_with_past_due_is_never_overdue() {
        val completedPast = task(
            id = 1L,
            position = GtdPosition.ENDED,
            closedOutcome = ClosedOutcome.COMPLETED,
            dueDate = today.minusDays(5),
            dueTime = LocalTime.of(8, 0),
        )
        val abandonedPast = task(
            id = 2L,
            position = GtdPosition.ENDED,
            closedOutcome = ClosedOutcome.ABANDONED,
            dueDate = today.minusDays(2),
            dueTime = null,
        )
        // 即使异常数据仅有 position=ENDED 或仅有 closedOutcome 非空，也恒不为逾期。
        val endedOnly = task(
            id = 3L,
            position = GtdPosition.ENDED,
            closedOutcome = null,
            dueDate = today.minusDays(1),
        )
        val outcomeOnly = task(
            id = 4L,
            position = GtdPosition.INBOX,
            closedOutcome = ClosedOutcome.COMPLETED,
            dueDate = today.minusDays(1),
        )

        assertFalse(TaskTimeRules.isOverdue(completedPast, now, zone))
        assertFalse(TaskTimeRules.isOverdue(abandonedPast, now, zone))
        assertFalse(TaskTimeRules.isOverdue(endedOnly, now, zone))
        assertFalse(TaskTimeRules.isOverdue(outcomeOnly, now, zone))
    }

    @Test
    fun FR_G_09_AC_D_09_date_only_yesterday_is_overdue_today_and_tomorrow_are_not() {
        val yesterdayTask = task(id = 1L, dueDate = today.minusDays(1), dueTime = null)
        val todayTask = task(id = 2L, dueDate = today, dueTime = null)
        val tomorrowTask = task(id = 3L, dueDate = today.plusDays(1), dueTime = null)

        assertTrue("仅日期且为昨天应判逾期", TaskTimeRules.isOverdue(yesterdayTask, now, zone))
        assertFalse("仅日期且为今天不应判逾期", TaskTimeRules.isOverdue(todayTask, now, zone))
        assertFalse("仅日期且为明天不应判逾期", TaskTimeRules.isOverdue(tomorrowTask, now, zone))
    }

    @Test
    fun FR_G_09_AC_D_09_date_and_time_strictly_before_now_is_overdue_equal_or_after_is_not() {
        val beforeTask = task(id = 1L, dueDate = today, dueTime = LocalTime.of(14, 29))
        val equalTask = task(id = 2L, dueDate = today, dueTime = LocalTime.of(14, 30))
        val afterTask = task(id = 3L, dueDate = today, dueTime = LocalTime.of(14, 31))
        val pastDateLaterTime = task(id = 4L, dueDate = today.minusDays(1), dueTime = LocalTime.of(23, 59))

        assertTrue("早于当前时刻应判逾期", TaskTimeRules.isOverdue(beforeTask, now, zone))
        assertFalse("等于当前时刻不应判逾期（严格早于）", TaskTimeRules.isOverdue(equalTask, now, zone))
        assertFalse("晚于当前时刻不应判逾期", TaskTimeRules.isOverdue(afterTask, now, zone))
        assertTrue("昨日任意时刻均早于今日，应判逾期", TaskTimeRules.isOverdue(pastDateLaterTime, now, zone))
    }

    @Test
    fun FR_G_09_BR_38_overdue_check_does_not_mutate_task_or_auto_close() {
        val overdue = task(
            id = 9L,
            position = GtdPosition.NEXT_ACTION,
            closedOutcome = null,
            dueDate = today.minusDays(3),
            dueTime = LocalTime.of(9, 0),
        )
        val snapshot = overdue.copy()

        assertTrue(TaskTimeRules.isOverdue(overdue, now, zone))
        assertEquals("逾期判定不得修改任务对象任何字段", snapshot, overdue)
        assertEquals(GtdPosition.NEXT_ACTION, overdue.position)
        assertNull(overdue.closedOutcome)
        assertNull(overdue.closedAt)
    }

    // ---------- TaskTimeRules.formatTaskDueLabel ----------

    @Test
    fun AC_D_09_format_due_label_covers_null_date_only_24h_time_and_today_tomorrow() {
        assertNull(TaskTimeRules.formatTaskDueLabel(dueDate = null, dueTime = null, today = today))
        assertNull(
            "无日期时即使传入时刻也必须返回 null",
            TaskTimeRules.formatTaskDueLabel(dueDate = null, dueTime = LocalTime.of(9, 30), today = today),
        )

        // 不传 today 时固定输出“M月d日”与可选 24h“ HH:mm”。
        assertEquals("11月27日", TaskTimeRules.formatTaskDueLabel(today, null, today = null))
        assertEquals(
            "11月27日 09:05",
            TaskTimeRules.formatTaskDueLabel(today, LocalTime.of(9, 5), today = null),
        )
        assertEquals(
            "12月1日 23:59",
            TaskTimeRules.formatTaskDueLabel(LocalDate.of(2026, 12, 1), LocalTime.of(23, 59), today = null),
        )
        assertEquals(
            "1月3日 00:00",
            TaskTimeRules.formatTaskDueLabel(LocalDate.of(2027, 1, 3), LocalTime.of(0, 0), today = null),
        )

        // 传入 today 时支持“今天”/“明天”，其余日期仍用“M月d日”。
        assertEquals("今天", TaskTimeRules.formatTaskDueLabel(today, null, today = today))
        assertEquals(
            "今天 18:00",
            TaskTimeRules.formatTaskDueLabel(today, LocalTime.of(18, 0), today = today),
        )
        assertEquals("明天", TaskTimeRules.formatTaskDueLabel(today.plusDays(1), null, today = today))
        assertEquals(
            "明天 08:30",
            TaskTimeRules.formatTaskDueLabel(today.plusDays(1), LocalTime.of(8, 30), today = today),
        )
        assertEquals(
            "11月26日 09:00",
            TaskTimeRules.formatTaskDueLabel(today.minusDays(1), LocalTime.of(9, 0), today = today),
        )
        assertEquals(
            "11月29日",
            TaskTimeRules.formatTaskDueLabel(today.plusDays(2), null, today = today),
        )
    }

    // ---------- TodoViewModel：overdueTaskIds + 防重入幂等 + 失败回滚 ----------

    @Test
    fun FR_G_09_AC_D_09_viewmodel_computes_overdueTaskIds_without_auto_closing() = runTest {
        val taskRepo = InMemoryTaskRepository()
        val projectRepo = InMemoryProjectRepository(taskRepo)
        val settings = FakeSettingsRepository()
        val clock = FakeClock(now)

        val overdueDateId = taskRepo.insert(
            task(title = "昨日到期", dueDate = today.minusDays(1)),
        )
        val overdueTimeId = taskRepo.insert(
            task(title = "今日已过时", dueDate = today, dueTime = LocalTime.of(10, 0)),
        )
        taskRepo.insert(task(title = "今日未过时", dueDate = today, dueTime = LocalTime.of(18, 0)))
        taskRepo.insert(task(title = "无截止时间", dueDate = null))
        val closedPastId = taskRepo.insert(
            task(
                title = "已结项过去日期",
                position = GtdPosition.ENDED,
                closedOutcome = ClosedOutcome.COMPLETED,
                dueDate = today.minusDays(2),
            ),
        )

        val vm = TodoViewModel(
            taskRepository = taskRepo,
            projectRepository = projectRepo,
            settingsRepository = settings,
            taskCommands = TaskCommands(taskRepo, projectRepo, clock),
            projectCommands = ProjectCommands(projectRepo, taskRepo, clock),
            clock = clock,
            zoneId = zone,
        )

        val inboxState = vm.uiState.first { it.visibleTasks.size == 4 }
        assertEquals(today, inboxState.referenceDate)
        assertEquals(setOf(overdueDateId, overdueTimeId), inboxState.overdueTaskIds)
        // 逾期任务不得自动结项。
        assertEquals(GtdPosition.INBOX, taskRepo.get(overdueDateId)?.position)
        assertNull(taskRepo.get(overdueDateId)?.closedOutcome)

        // 切至已结束清单：已结项过去日期任务不计入 overdueTaskIds。
        vm.selectPosition(GtdPosition.ENDED)
        val endedState = vm.uiState.first { it.position == GtdPosition.ENDED }
        assertEquals(listOf(closedPastId), endedState.visibleTasks.map { it.id })
        assertTrue("已结束清单任务不应标逾期", endedState.overdueTaskIds.isEmpty())
    }

    @Test
    fun FR_G_14_AC_T_17_completeTask_is_reentrant_safe_and_idempotent() = runTest {
        val delegate = InMemoryTaskRepository()
        val gate = CompletableDeferred<Unit>()
        var updateCalls = 0
        val gatedRepo = object : TaskRepository by delegate {
            override suspend fun update(task: Task) {
                updateCalls += 1
                gate.await()
                delegate.update(task)
            }
        }
        val projectRepo = InMemoryProjectRepository(delegate)
        val settings = FakeSettingsRepository()
        val clock = FakeClock(now)

        val taskId = delegate.insert(task(title = "防重入任务"))
        val vm = TodoViewModel(
            taskRepository = gatedRepo,
            projectRepository = projectRepo,
            settingsRepository = settings,
            taskCommands = TaskCommands(gatedRepo, projectRepo, clock),
            projectCommands = ProjectCommands(projectRepo, gatedRepo, clock),
            clock = clock,
            zoneId = zone,
        )
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }

        // 连续三次触发同一任务的完成：在首次写库挂起期间只允许执行一次命令。
        vm.completeTask(taskId)
        vm.completeTask(taskId)
        vm.completeTask(taskId)

        assertTrue("进行中任务应处于 inFlightCompletingIds", taskId in vm.uiState.value.inFlightCompletingIds)
        assertEquals("防重入期内只应提交一次 update", 1, updateCalls)

        // 放行后单次落入已结束，且清空进行中状态。
        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse("写库完成后应移出 inFlightCompletingIds", taskId in vm.uiState.value.inFlightCompletingIds)
        assertNull(vm.uiState.value.taskActionError)
        assertEquals(1, updateCalls)
        val saved = delegate.get(taskId)
        assertEquals(GtdPosition.ENDED, saved?.position)
        assertEquals(ClosedOutcome.COMPLETED, saved?.closedOutcome)

        collector.cancel()
    }

    @Test
    fun FR_G_14_AC_T_17_completeTask_failure_rolls_back_state_and_exposes_error() = runTest {
        val delegate = InMemoryTaskRepository()
        val failingRepo = object : TaskRepository by delegate {
            override suspend fun update(task: Task) {
                throw IllegalStateException("模拟磁盘写入失败")
            }
        }
        val projectRepo = InMemoryProjectRepository(delegate)
        val settings = FakeSettingsRepository()
        val clock = FakeClock(now)

        val taskId = delegate.insert(task(title = "写库失败任务"))
        val vm = TodoViewModel(
            taskRepository = failingRepo,
            projectRepository = projectRepo,
            settingsRepository = settings,
            taskCommands = TaskCommands(failingRepo, projectRepo, clock),
            projectCommands = ProjectCommands(projectRepo, failingRepo, clock),
            clock = clock,
            zoneId = zone,
        )
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        val beforeVersion = vm.uiState.value.completingResetVersion

        vm.completeTask(taskId)
        advanceUntilIdle()

        val stateAfter = vm.uiState.value
        assertFalse("失败后必须释放 inFlightCompletingIds", taskId in stateAfter.inFlightCompletingIds)
        assertEquals(
            "失败后必须递增 completingResetVersion 以恢复原任务行显示",
            beforeVersion + 1,
            stateAfter.completingResetVersion,
        )
        assertNotNull("失败后必须透出错误提示，不得假成功", stateAfter.taskActionError)
        val persisted = delegate.get(taskId)
        assertEquals("失败后任务仍应留在收件箱", GtdPosition.INBOX, persisted?.position)
        assertNull("失败后任务不得被标记已结项", persisted?.closedOutcome)

        // 关闭错误提示后清空 error。
        vm.dismissTaskActionError()
        assertNull(vm.uiState.value.taskActionError)

        // 不存在的任务同样回滚并提示错误。
        val versionBeforeNotFound = vm.uiState.value.completingResetVersion
        vm.completeTask(TaskId(999L))
        advanceUntilIdle()
        assertEquals(versionBeforeNotFound + 1, vm.uiState.value.completingResetVersion)
        assertNotNull(vm.uiState.value.taskActionError)

        collector.cancel()
    }

    @Test
    fun FR_G_14_AC_T_17_abandonTask_persists_abandoned_and_reports_error_on_failure() = runTest {
        val delegate = InMemoryTaskRepository()
        var failNext = false
        val repo = object : TaskRepository by delegate {
            override fun observeAll(): Flow<List<Task>> = delegate.observeAll()
            override suspend fun update(task: Task) {
                if (failNext) throw IllegalStateException("模拟放弃写入失败")
                delegate.update(task)
            }
        }
        val projectRepo = InMemoryProjectRepository(delegate)
        val settings = FakeSettingsRepository()
        val clock = FakeClock(now)

        val okId = delegate.insert(task(title = "可放弃任务"))
        val failId = delegate.insert(task(title = "放弃失败任务"))
        val vm = TodoViewModel(
            taskRepository = repo,
            projectRepository = projectRepo,
            settingsRepository = settings,
            taskCommands = TaskCommands(repo, projectRepo, clock),
            projectCommands = ProjectCommands(projectRepo, repo, clock),
            clock = clock,
            zoneId = zone,
        )
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }

        vm.abandonTask(okId)
        advanceUntilIdle()
        assertEquals(GtdPosition.ENDED, delegate.get(okId)?.position)
        assertEquals(ClosedOutcome.ABANDONED, delegate.get(okId)?.closedOutcome)
        assertNull(vm.uiState.value.taskActionError)

        failNext = true
        vm.abandonTask(failId)
        advanceUntilIdle()
        assertEquals(GtdPosition.INBOX, delegate.get(failId)?.position)
        assertNull(delegate.get(failId)?.closedOutcome)
        assertNotNull(vm.uiState.value.taskActionError)

        collector.cancel()
    }
}
