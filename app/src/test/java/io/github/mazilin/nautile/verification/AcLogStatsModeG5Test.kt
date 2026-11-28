package io.github.mazilin.nautile.verification

import androidx.lifecycle.viewModelScope
import io.github.mazilin.nautile.domain.model.StatsPeriod
import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.domain.usecase.TimeCategoryCommands
import io.github.mazilin.nautile.domain.usecase.TimeLogCommands
import io.github.mazilin.nautile.testing.FakeClock
import io.github.mazilin.nautile.testing.FakeSettingsRepository
import io.github.mazilin.nautile.testing.InMemoryTimeCategoryRepository
import io.github.mazilin.nautile.testing.InMemoryTimeLogRepository
import io.github.mazilin.nautile.ui.screen.log.LogMode
import io.github.mazilin.nautile.ui.screen.log.LogUiState
import io.github.mazilin.nautile.ui.screen.log.LogViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R6-G5-V1 · 统计状态与模式选择（AC-T-20 / AC-T-25，辅以 AC-L-07/11/15/19 的状态层核对）。
 *
 * 预期来自冻结规格：标题按钮只打开半屏选择器、不直接轮换；切记录/离开日志后再次进入
 * 统计恢复今日；非法自定义范围留在面板并提示；运行中实时计入、结束后不回退。
 * 时钟固定为 [FakeClock]，时区固定，避免真实时间波动。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AcLogStatsModeG5Test {

    private val dispatcher = UnconfinedTestDispatcher()
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 11, 28)
    private val now: Instant = today.atTime(10, 0).atZone(zone).toInstant()

    private lateinit var clock: FakeClock
    private lateinit var logStore: InMemoryTimeLogRepository
    private lateinit var categoryStore: InMemoryTimeCategoryRepository
    private lateinit var settings: FakeSettingsRepository
    private lateinit var logCommands: TimeLogCommands
    private val viewModels = mutableListOf<LogViewModel>()

    private var study: TimeCategoryId = TimeCategoryId(0)
    private var parallelA: TimeCategoryId = TimeCategoryId(0)
    private var parallelB: TimeCategoryId = TimeCategoryId(0)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        clock = FakeClock(now)
        logStore = InMemoryTimeLogRepository()
        categoryStore = InMemoryTimeCategoryRepository(logStore)
        settings = FakeSettingsRepository()
        logCommands = TimeLogCommands(logStore, categoryStore, clock)
        val categoryCommands = TimeCategoryCommands(categoryStore, clock)
        study = seedCategory(categoryCommands, "学习")
        parallelA = seedCategory(categoryCommands, "并行A")
        parallelB = seedCategory(categoryCommands, "并行B")
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun seedCategory(commands: TimeCategoryCommands, name: String): TimeCategoryId {
        val result = kotlinx.coroutines.runBlocking { commands.create(name, null) }
        return (result as io.github.mazilin.nautile.domain.usecase.TimeCategoryCommandResult.Success)
            .category.id
    }

    private fun newViewModel(): LogViewModel = LogViewModel(
        logRepository = logStore,
        categoryRepository = categoryStore,
        settingsRepository = settings,
        commands = TimeLogCommands(logStore, categoryStore, clock),
        clock = clock,
        zoneId = zone,
    ).also { viewModels += it }

    private suspend fun LogViewModel.awaitState(
        description: String,
        predicate: (LogUiState) -> Boolean,
    ): LogUiState {
        val state = uiState.first(predicate)
        assertTrue(description, predicate(state))
        return state
    }

    private fun at(hour: Int, minute: Int = 0): Instant =
        today.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private suspend fun seedAcL16Fixture() {
        listOf(
            TimeLog(TimeLogId(0), study, at(8), at(10), at(8), at(8)),
            TimeLog(TimeLogId(0), parallelA, at(9), at(10), at(9), at(9)),
            TimeLog(TimeLogId(0), parallelB, at(9, 30), at(10, 30), at(9, 30), at(9, 30)),
        ).forEach { logStore.insert(it) }
    }

    // ---------------- AC-T-20 ----------------

    @Test
    fun AC_T_20_modeButtonOnlyOpensSelectorWithoutRotating() = runTest(dispatcher) {
        val viewModel = newViewModel()
        val initial = viewModel.awaitState("初始为记录模式") { !it.isLoading }
        assertEquals(LogMode.RECORD, initial.mode)
        assertFalse(initial.modeSelectorOpen)

        viewModel.onOpenModeSelector()
        val opened = viewModel.awaitState("标题按钮打开选择器") { it.modeSelectorOpen }
        assertEquals("打开选择器不轮换模式", LogMode.RECORD, opened.mode)

        viewModel.onDismissModeSelector()
        val dismissed = viewModel.awaitState("关闭选择器") { !it.modeSelectorOpen }
        assertEquals("关闭后仍是记录模式", LogMode.RECORD, dismissed.mode)

        viewModel.onModeSelected(LogMode.STATS)
        val stats = viewModel.awaitState("点统计切换并关闭选择器") {
            it.mode == LogMode.STATS && !it.modeSelectorOpen
        }
        assertTrue("默认日/今天", stats.stats.period is StatsPeriod.Day)
        assertEquals(today, (stats.stats.period as StatsPeriod.Day).date)
        assertFalse(stats.stats.panelOpen)
    }

    @Test
    fun AC_T_20_selectingModeAlwaysResetsToToday() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onModeSelected(LogMode.STATS)
        viewModel.awaitState("进入统计") { it.mode == LogMode.STATS && !it.isLoading }

        viewModel.onSelectMonthPeriod()
        val month = viewModel.awaitState("切到月") { it.stats.period is StatsPeriod.Month }
        assertEquals(YearMonth.of(2026, 11), (month.stats.period as StatsPeriod.Month).yearMonth)

        viewModel.onModeSelected(LogMode.RECORD)
        val record = viewModel.awaitState("切回记录") { it.mode == LogMode.RECORD }
        assertTrue("切回记录即恢复日", record.stats.period is StatsPeriod.Day)
        assertEquals(today, (record.stats.period as StatsPeriod.Day).date)
    }

    // ---------------- AC-T-25 ----------------

    @Test
    fun AC_T_25_twoEntriesAfterLeavingBothRestoreToday() = runTest(dispatcher) {
        val viewModel = newViewModel()

        // 第一次：选月 → 切记录 → 再进统计。
        viewModel.onModeSelected(LogMode.STATS)
        viewModel.awaitState("进入统计") { it.mode == LogMode.STATS && !it.isLoading }
        viewModel.onSelectMonthPeriod()
        viewModel.awaitState("切到月") { it.stats.period is StatsPeriod.Month }
        viewModel.onModeSelected(LogMode.RECORD)
        viewModel.onModeSelected(LogMode.STATS)
        val firstRecovery = viewModel.awaitState("第一次恢复今日") {
            it.mode == LogMode.STATS && it.stats.period is StatsPeriod.Day
        }
        assertEquals(today, (firstRecovery.stats.period as StatsPeriod.Day).date)

        // 第二次：选年 → 离开日志 → 再进统计。
        viewModel.onSelectYearPeriod()
        viewModel.awaitState("切到年") { it.stats.period is StatsPeriod.Year }
        viewModel.onLeaveScreen()
        viewModel.awaitState("离开日志回到记录") { it.mode == LogMode.RECORD }
        viewModel.onModeSelected(LogMode.STATS)
        val secondRecovery = viewModel.awaitState("第二次恢复今日") {
            it.mode == LogMode.STATS && it.stats.period is StatsPeriod.Day
        }
        assertEquals(today, (secondRecovery.stats.period as StatsPeriod.Day).date)
        assertFalse("离开后不保留自定义草稿", secondRecovery.stats.panelOpen)
    }

    // ---------------- AC-L-07 / AC-L-11 面板状态 ----------------

    @Test
    fun AC_L_11_invalidCustomRangeStaysOpenWithMessage() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onModeSelected(LogMode.STATS)
        viewModel.awaitState("进入统计") { it.mode == LogMode.STATS && !it.isLoading }

        viewModel.onOpenPeriodPanel()
        val opened = viewModel.awaitState("打开自定义面板") { it.stats.panelOpen }
        assertEquals("默认今天", today, opened.stats.customStart)
        assertEquals("默认今天", today, opened.stats.customEnd)

        viewModel.onCustomStartChanged(today.minusDays(366))
        val tooLong = viewModel.awaitState("367 天应提示") {
            it.stats.validationError != null
        }
        assertEquals("367 天拒绝", "统计范围最长 366 天", tooLong.stats.validationError)

        viewModel.onConfirmCustomRange()
        val blocked = viewModel.awaitState("非法范围不关闭") {
            it.stats.validationError != null && it.stats.panelOpen
        }
        assertTrue("周期未被改成非法范围", blocked.stats.period is StatsPeriod.Day)

        viewModel.onCustomStartChanged(today.minusDays(1))
        viewModel.awaitState("改回合法起止") { it.stats.validationError == null }
        viewModel.onConfirmCustomRange()
        val custom = viewModel.awaitState("合法范围关闭并生效") {
            it.stats.period is StatsPeriod.Custom && !it.stats.panelOpen
        }
        val period = custom.stats.period as StatsPeriod.Custom
        assertEquals(today.minusDays(1), period.start)
        assertEquals(today, period.end)
        assertEquals(2, custom.stats.range.dayCount)
    }

    @Test
    fun AC_L_11_futureAndReversedEndAreRejectedByPanel() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onModeSelected(LogMode.STATS)
        viewModel.awaitState("进入统计") { it.mode == LogMode.STATS && !it.isLoading }
        viewModel.onOpenPeriodPanel()
        viewModel.awaitState("打开面板") { it.stats.panelOpen }

        viewModel.onCustomEndChanged(today.plusDays(1))
        val future = viewModel.awaitState("未来结束拒绝") { it.stats.validationError != null }
        assertEquals("结束日期不能晚于今天", future.stats.validationError)

        viewModel.onCustomStartChanged(today)
        viewModel.onCustomEndChanged(today.minusDays(1))
        val reversed = viewModel.awaitState("结束早于开始拒绝") {
            it.stats.validationError == "结束日期不能早于开始日期"
        }
        assertTrue(reversed.stats.panelOpen)
    }

    // ---------------- 状态层数值核对 ----------------

    @Test
    fun AC_L_16_statsStateExposesExactFixtureNumbers() = runTest(dispatcher) {
        seedAcL16Fixture()
        val viewModel = newViewModel()
        viewModel.onModeSelected(LogMode.STATS)

        val stats = viewModel.awaitState("统计就绪") {
            it.mode == LogMode.STATS && !it.isLoading && it.stats.day != null
        }.stats

        assertEquals("2026-11-28", stats.rangeTitle)
        assertEquals(240L, stats.totalAccumulated.toMinutes())
        assertEquals(1530L, stats.denominator.toMinutes())
        assertEquals(1290L, stats.untracked.toMinutes())
        assertEquals(4, stats.slices.size)
        assertEquals("含未追踪片", 1, stats.slices.count { it.untracked })
    }

    @Test
    fun AC_L_15_runningTimerIsCountedLiveInStats() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onCategoryQuickTap(study)
        viewModel.awaitState("开始计时") { it.running.size == 1 }

        viewModel.onModeSelected(LogMode.STATS)
        viewModel.awaitState("进入统计") { it.mode == LogMode.STATS && !it.isLoading }

        clock.advanceSeconds(30 * 60)
        val halfHour = viewModel.awaitState("运行中实时计入 30 分钟") {
            it.stats.totalAccumulated.toMinutes() == 30L
        }
        assertEquals("分母 24h", 1440L, halfHour.stats.denominator.toMinutes())
        assertEquals("未追踪 23h30m", 1410L, halfHour.stats.untracked.toMinutes())

        viewModel.onRunningTimerTap(study)
        viewModel.awaitState("结束计时") { it.running.isEmpty() }
        clock.advanceSeconds(60 * 60)
        val afterStop = viewModel.awaitState("结束统计就绪") { it.stats.day != null }
        assertEquals("结束后保持 30 分钟，不回退", 30L, afterStop.stats.totalAccumulated.toMinutes())
    }

    @Test
    fun AC_L_19_restartKeepsSameStatsNumbers() = runTest(dispatcher) {
        seedAcL16Fixture()
        val first = newViewModel()
        first.onModeSelected(LogMode.STATS)
        val before = first.awaitState("第一次统计") {
            it.mode == LogMode.STATS && !it.isLoading && it.stats.day != null
        }.stats

        val reopened = newViewModel()
        reopened.onModeSelected(LogMode.STATS)
        val after = reopened.awaitState("重启后统计") {
            it.mode == LogMode.STATS && !it.isLoading && it.stats.day != null
        }.stats

        assertEquals(before.denominator, after.denominator)
        assertEquals(before.untracked, after.untracked)
        assertEquals(before.duplicateDuration, after.duplicateDuration)
        assertEquals(before.totalAccumulated, after.totalAccumulated)
        assertEquals(
            before.slices.map { it.duration to it.fraction },
            after.slices.map { it.duration to it.fraction },
        )
    }

    @Test
    fun AC_L_07_multiDayStateUsesRangeFormula() = runTest(dispatcher) {
        clock.advanceSeconds(Duration.ofDays(1).seconds)
        val secondDay = clock.now().atZone(zone).toLocalDate()
        logStore.insert(TimeLog(TimeLogId(0), study, at(8), at(9), at(8), at(8)))
        logStore.insert(
            TimeLog(
                TimeLogId(0),
                study,
                secondDay.atTime(8, 0).atZone(zone).toInstant(),
                secondDay.atTime(9, 0).atZone(zone).toInstant(),
                now,
                now,
            ),
        )

        val viewModel = newViewModel()
        viewModel.onModeSelected(LogMode.STATS)
        viewModel.awaitState("进入统计") { it.mode == LogMode.STATS && !it.isLoading }
        viewModel.onOpenPeriodPanel()
        viewModel.onCustomStartChanged(today)
        viewModel.onCustomEndChanged(secondDay)
        viewModel.onConfirmCustomRange()

        val stats = viewModel.awaitState("自定义两日生效") {
            it.stats.period is StatsPeriod.Custom && !it.stats.panelOpen
        }.stats
        assertEquals(2, stats.range.dayCount)
        assertEquals("24h×2", 2880L, stats.denominator.toMinutes())
        assertEquals("已覆盖 2h", 120L, stats.totalAccumulated.toMinutes())
        assertEquals("未追踪 46h", 2760L, stats.untracked.toMinutes())
        assertEquals("两日都有数据", 2, stats.rangeStats?.days?.size)
    }
}
