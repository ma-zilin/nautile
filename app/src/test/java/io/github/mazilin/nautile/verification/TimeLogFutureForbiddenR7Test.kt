package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.usecase.TimeCategoryCommandResult
import io.github.mazilin.nautile.domain.usecase.TimeCategoryCommands
import io.github.mazilin.nautile.domain.usecase.TimeLogCommandResult
import io.github.mazilin.nautile.domain.usecase.TimeLogCommands
import io.github.mazilin.nautile.domain.usecase.TimeLogError
import io.github.mazilin.nautile.testing.FakeClock
import io.github.mazilin.nautile.testing.InMemoryTimeCategoryRepository
import io.github.mazilin.nautile.testing.InMemoryTimeLogRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R7-G1 · 时间日志未来时间禁令与相邻异色落库验证（Q3、#22、架构 §4.2）。
 *
 * 覆盖：
 * - createManual 与 update 边界：endAt == now 允许；endAt == now + 1ms / startAt == now + 1ms 拒绝并返回 FUTURE_TIME_FORBIDDEN；
 * - createManual 与 startTimer 自动落库非空 colorIndex 且避让同日相邻/重叠；
 * - update 与 stopTimer 保持既有 colorIndex 不跳色。
 */
class TimeLogFutureForbiddenR7Test {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Instant = Instant.parse("2026-11-28T06:00:00Z") // 上海 14:00:00

    private lateinit var clock: FakeClock
    private lateinit var logStore: InMemoryTimeLogRepository
    private lateinit var categoryStore: InMemoryTimeCategoryRepository
    private lateinit var commands: TimeLogCommands
    private lateinit var categoryCommands: TimeCategoryCommands

    private var study: TimeCategoryId = TimeCategoryId(0)
    private var work: TimeCategoryId = TimeCategoryId(0)
    private var sport: TimeCategoryId = TimeCategoryId(0)

    @Before
    fun setUp() = runBlocking {
        clock = FakeClock(now)
        logStore = InMemoryTimeLogRepository()
        categoryStore = InMemoryTimeCategoryRepository(logStore)
        commands = TimeLogCommands(logStore, categoryStore, clock, zone)
        categoryCommands = TimeCategoryCommands(categoryStore, clock)

        study = (categoryCommands.create("学习", null) as TimeCategoryCommandResult.Success).category.id
        work = (categoryCommands.create("工作", null) as TimeCategoryCommandResult.Success).category.id
        sport = (categoryCommands.create("运动", null) as TimeCategoryCommandResult.Success).category.id
    }

    @Test
    fun r7_createManual_allowsEndAtExactNow_andRejectsFutureByOneMillisecond() = runTest {
        // 边界放行：endAt == now 允许。
        val exactNow = commands.createManual(study, now.minusSeconds(1800), now)
        assertTrue("endAt == now 必须允许，实际=$exactNow", exactNow is TimeLogCommandResult.Success)
        val writesAfterValid = logStore.writeCount

        // 边界拒绝 1：endAt == now + 1ms。
        val futureEnd = commands.createManual(study, now.minusSeconds(600), now.plusMillis(1))
        assertEquals(
            TimeLogCommandResult.Invalid(TimeLogError.FUTURE_TIME_FORBIDDEN),
            futureEnd,
        )

        // 边界拒绝 2：startAt == now + 1ms（且 endAt > startAt）。
        val futureStart = commands.createManual(study, now.plusMillis(1), now.plusMillis(2))
        assertEquals(
            TimeLogCommandResult.Invalid(TimeLogError.FUTURE_TIME_FORBIDDEN),
            futureStart,
        )

        assertEquals("未来时间拒绝分支不得写库", writesAfterValid, logStore.writeCount)
        assertEquals(1, logStore.snapshot.size)
    }

    @Test
    fun r7_update_allowsEndAtExactNow_andRejectsFutureWithoutMutatingStoredRecord() = runTest {
        val originalStart = now.minusSeconds(7200)
        val originalEnd = now.minusSeconds(3600)
        val created = (commands.createManual(study, originalStart, originalEnd)
            as TimeLogCommandResult.Success).log
        val originalColor = created.colorIndex
        assertNotNull("新建补录必须落库非空 colorIndex", originalColor)

        val writesBefore = logStore.writeCount

        // 拒绝 endAt == now + 1ms。
        assertEquals(
            TimeLogCommandResult.Invalid(TimeLogError.FUTURE_TIME_FORBIDDEN),
            commands.update(created.id, work, originalStart, now.plusMillis(1)),
        )
        // 拒绝 startAt == now + 1ms。
        assertEquals(
            TimeLogCommandResult.Invalid(TimeLogError.FUTURE_TIME_FORBIDDEN),
            commands.update(created.id, work, now.plusMillis(1), now.plusSeconds(60)),
        )
        assertEquals("未来时间更新拒绝不得写库", writesBefore, logStore.writeCount)
        assertEquals(study, logStore.get(created.id)?.categoryId)
        assertEquals(originalEnd, logStore.get(created.id)?.endAt)

        // 放行 endAt == now，并保持原 colorIndex 不跳色。
        val updated = commands.update(created.id, work, now.minusSeconds(1800), now)
        assertTrue("endAt == now 更新必须允许，实际=$updated", updated is TimeLogCommandResult.Success)
        val stored = logStore.get(created.id)!!
        assertEquals(work, stored.categoryId)
        assertEquals(now, stored.endAt)
        assertEquals("update 不得重算导致色块跳色", originalColor, stored.colorIndex)
    }

    @Test
    fun r7_createManual_assignsNonNullColorIndexAvoidingAdjacentAndOverlapping() = runTest {
        val first = (commands.createManual(study, now.minusSeconds(7200), now.minusSeconds(3600))
            as TimeLogCommandResult.Success).log
        val adjacentAfter = (commands.createManual(study, now.minusSeconds(3600), now.minusSeconds(1800))
            as TimeLogCommandResult.Success).log
        val overlapping = (commands.createManual(work, now.minusSeconds(4800), now.minusSeconds(2400))
            as TimeLogCommandResult.Success).log

        val c1 = requireNotNull(first.colorIndex) { "首条补录 colorIndex 不可为 null" }
        val c2 = requireNotNull(adjacentAfter.colorIndex) { "相邻补录 colorIndex 不可为 null" }
        val c3 = requireNotNull(overlapping.colorIndex) { "重叠补录 colorIndex 不可为 null" }

        assertTrue(c1 in 0 until 8)
        assertTrue(c2 in 0 until 8)
        assertTrue(c3 in 0 until 8)
        assertNotEquals("紧邻补录色槽必须互异", c1, c2)
        assertNotEquals("重叠补录不得与前段撞色", c1, c3)
        assertNotEquals("重叠补录不得与后段撞色", c2, c3)
    }

    @Test
    fun r7_startTimer_assignsDistinctColorsForParallelTimers_andStopKeepsColor() = runTest {
        // 先有一条紧邻刚刚结束的日志（结束于 now）。
        val predecessor = (commands.createManual(sport, now.minusSeconds(1800), now)
            as TimeLogCommandResult.Success).log
        val predColor = requireNotNull(predecessor.colorIndex)

        // 并行启动两个计时（同一时刻 now）。
        val timer1 = (commands.startTimer(study) as TimeLogCommandResult.Success).log
        val timer2 = (commands.startTimer(work) as TimeLogCommandResult.Success).log

        val c1 = requireNotNull(timer1.colorIndex) { "计时 1 必须分配非空 colorIndex" }
        val c2 = requireNotNull(timer2.colorIndex) { "计时 2 必须分配非空 colorIndex" }
        assertTrue(c1 in 0 until 8)
        assertTrue(c2 in 0 until 8)
        assertNotEquals("开始计时应避让紧邻前驱", predColor, c1)
        assertNotEquals("并行计时之间不得撞色", c1, c2)
        assertNotEquals("并行计时 2 也应避让紧邻前驱", predColor, c2)

        // 结束计时后 colorIndex 保持不变。
        clock.advanceSeconds(600)
        val stopped = (commands.stopTimer(study) as TimeLogCommandResult.Success).log
        assertEquals("结束计时不得改变原色槽", c1, stopped.colorIndex)
        assertEquals(c1, logStore.get(timer1.id)?.colorIndex)
    }
}
