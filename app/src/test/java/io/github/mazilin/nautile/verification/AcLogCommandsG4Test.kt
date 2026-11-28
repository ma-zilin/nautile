package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.domain.model.TimerMode
import io.github.mazilin.nautile.domain.usecase.TimeCategoryCommands
import io.github.mazilin.nautile.domain.usecase.TimeCategoryCommandResult
import io.github.mazilin.nautile.domain.usecase.TimeCategoryError
import io.github.mazilin.nautile.domain.usecase.TimeLogCommandResult
import io.github.mazilin.nautile.domain.usecase.TimeLogCommands
import io.github.mazilin.nautile.domain.usecase.TimeLogError
import io.github.mazilin.nautile.testing.FakeClock
import io.github.mazilin.nautile.testing.InMemoryTimeCategoryRepository
import io.github.mazilin.nautile.testing.InMemoryTimeLogRepository
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R6-G4-V1 · 日志写入命令（AC-L-02、AC-T-21）。
 *
 * 预期来自基线 v2.16 与冻结规格：补录/编辑/删除作用于整条；同分类再次开始只提示；
 * 点计时条结束按 `max(now, start)` 保存且无暂停；时间全部取注入的 [FakeClock]。
 */
class AcLogCommandsG4Test {

    private val now: Instant = Instant.parse("2026-11-28T02:00:00Z")

    private lateinit var logStore: InMemoryTimeLogRepository
    private lateinit var categoryStore: InMemoryTimeCategoryRepository
    private lateinit var commands: TimeLogCommands
    private lateinit var categoryCommands: TimeCategoryCommands
    private lateinit var clock: FakeClock

    private var study: TimeCategoryId = TimeCategoryId(0)
    private var work: TimeCategoryId = TimeCategoryId(0)

    @Before
    fun setUp() = runBlocking {
        clock = FakeClock(now)
        logStore = InMemoryTimeLogRepository()
        categoryStore = InMemoryTimeCategoryRepository(logStore)
        commands = TimeLogCommands(logStore, categoryStore, clock)
        categoryCommands = TimeCategoryCommands(categoryStore, clock)

        study = category("学习")
        work = category("工作")
    }

    private suspend fun category(name: String): TimeCategoryId {
        val result = categoryCommands.create(name, null) as TimeCategoryCommandResult.Success
        return result.category.id
    }

    private suspend fun manual(categoryId: TimeCategoryId, start: Instant, end: Instant): TimeLog =
        (commands.createManual(categoryId, start, end) as TimeLogCommandResult.Success).log

    @Test
    fun AC_L_02_create_manual_yesterday_14_to_15_keeps_order_and_no_duplicate() = runTest {
        val start = Instant.parse("2026-11-27T06:00:00Z") // 上海 14:00
        val end = Instant.parse("2026-11-27T07:00:00Z") // 上海 15:00
        val created = manual(study, start, end)

        assertEquals("补录不得产生重复记录", 1, logStore.snapshot.size)
        assertEquals(start, created.startAt)
        assertEquals(end, created.endAt)
        assertEquals(study, created.categoryId)

        val todayLog = manual(work, now.minusSeconds(3600), now)
        val ordered = logStore.observeAllDescending().first()
        assertEquals("今天记录在前、昨天记录在后", listOf(todayLog.id, created.id), ordered.map { it.id })
    }

    @Test
    fun AC_L_02_update_changes_category_and_range_without_new_row() = runTest {
        val created = manual(study, now.minusSeconds(7200), now.minusSeconds(3600))
        val newStart = now.minusSeconds(10_800)
        val newEnd = now.minusSeconds(7200)

        val result = commands.update(created.id, work, newStart, newEnd)

        assertTrue(result is TimeLogCommandResult.Success)
        val stored = logStore.get(created.id)
        assertEquals(work, stored?.categoryId)
        assertEquals(newStart, stored?.startAt)
        assertEquals(newEnd, stored?.endAt)
        assertEquals("编辑不得生成重复记录", 1, logStore.snapshot.size)
        assertEquals(created.id, logStore.observeAllDescending().first().single().id)
    }

    @Test
    fun AC_L_02_reject_invalid_manual_and_update_keeps_original() = runTest {
        val created = manual(study, now.minusSeconds(7200), now.minusSeconds(3600))
        val writesBefore = logStore.writeCount

        val equalRange = commands.createManual(study, now, now)
        assertEquals(
            TimeLogCommandResult.Invalid(TimeLogError.END_NOT_AFTER_START),
            equalRange,
        )
        val reversed = commands.update(created.id, work, now, now.minusSeconds(600))
        assertEquals(
            TimeLogCommandResult.Invalid(TimeLogError.END_NOT_AFTER_START),
            reversed,
        )
        assertEquals("非法输入不得写库", writesBefore, logStore.writeCount)
        assertEquals(study, logStore.get(created.id)?.categoryId)
    }

    @Test
    fun AC_L_02_unknown_category_rejected() = runTest {
        assertEquals(
            TimeLogCommandResult.Invalid(TimeLogError.CATEGORY_NOT_FOUND),
            commands.createManual(TimeCategoryId(404), now, now.plusSeconds(600)),
        )
        assertEquals(0, logStore.snapshot.size)
    }

    @Test
    fun AC_T_21_start_second_time_same_category_only_prompts() = runTest {
        val first = commands.startTimer(study) as TimeLogCommandResult.Success
        clock.advanceSeconds(90)

        val second = commands.startTimer(study)

        assertTrue("同分类运行中再点只提示", second is TimeLogCommandResult.AlreadyRunning)
        assertEquals(first.log.id, (second as TimeLogCommandResult.AlreadyRunning).logId)
        assertEquals(1, logStore.snapshot.size)
        assertEquals("开始时刻不得被重置", now, logStore.get(first.log.id)?.startAt)
        assertEquals(1, logStore.observeRunning().first().size)
    }

    @Test
    fun AC_T_21_stop_saves_end_and_restart_creates_new_record() = runTest {
        val started = commands.startTimer(study) as TimeLogCommandResult.Success
        clock.advanceSeconds(3_600)

        val stopped = commands.stopTimer(study) as TimeLogCommandResult.Success
        assertEquals(now.plusSeconds(3_600), stopped.log.endAt)
        assertEquals(started.log.id, stopped.log.id)
        assertTrue("结束后不在运行中", logStore.observeRunning().first().isEmpty())

        clock.advanceSeconds(60)
        val restarted = commands.startTimer(study) as TimeLogCommandResult.Success
        assertNotEquals("再次开始必须生成新记录", started.log.id, restarted.log.id)
        assertEquals(2, logStore.snapshot.size)
    }

    @Test
    fun AC_T_21_stop_without_running_returns_not_running() = runTest {
        assertEquals(TimeLogCommandResult.NotRunning, commands.stopTimer(study))
        assertEquals(0, logStore.snapshot.size)
    }

    @Test
    fun AC_T_21_single_mode_is_not_supported_in_phase_a() = runTest {
        assertEquals(
            TimeLogCommandResult.Invalid(TimeLogError.UNSUPPORTED_MODE),
            commands.startTimer(study, TimerMode.SINGLE),
        )
        assertEquals("单活动模式不得写入任何计时", 0, logStore.snapshot.size)
    }

    @Test
    fun AC_T_21_running_log_cannot_be_edited_or_deleted_directly() = runTest {
        val started = commands.startTimer(study) as TimeLogCommandResult.Success

        assertEquals(
            TimeLogCommandResult.Invalid(TimeLogError.RUNNING_LOG),
            commands.update(started.log.id, work, now, now.plusSeconds(600)),
        )
        assertEquals(
            TimeLogCommandResult.Invalid(TimeLogError.RUNNING_LOG),
            commands.delete(started.log.id),
        )
        assertEquals("运行中记录不得被改写/删除", 1, logStore.snapshot.size)
    }

    @Test
    fun AC_L_02_delete_completed_record_removes_it() = runTest {
        val created = manual(study, now.minusSeconds(7200), now.minusSeconds(3600))

        assertEquals(TimeLogCommandResult.Deleted(created.id), commands.delete(created.id))
        assertEquals(0, logStore.snapshot.size)
    }

    @Test
    fun AC_T_21_no_pause_api_in_commands() {
        // 「无暂停」的编译期证据：命令类只暴露开始/结束/补录/编辑/删除，不存在暂停入口。
        val names = TimeLogCommands::class.java.declaredMethods.map { it.name.lowercase() }
        assertTrue("不得出现暂停能力：$names", names.none { "pause" in it || "resume" in it })
        val modelFields = TimeLog::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue("记录模型不得保存暂停状态：$modelFields", modelFields.none { "pause" in it })
        val categoryNames = TimeCategory::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue("分类模型不得保存暂停状态：$categoryNames", categoryNames.none { "pause" in it })
    }

    @Test
    fun AC_T_21_elapsed_is_wall_clock_not_accumulated() {
        val start = Instant.parse("2026-11-27T22:00:00Z")
        val later = Instant.parse("2026-11-28T02:00:00Z")
        // 墙钟口径：重启后 elapsed 直接由 start 与当前时刻计算，不接受“累计值”续算。
        assertEquals(
            java.time.Duration.ofHours(4),
            io.github.mazilin.nautile.domain.rule.TimeLogRules.elapsed(start, later),
        )
    }
}
