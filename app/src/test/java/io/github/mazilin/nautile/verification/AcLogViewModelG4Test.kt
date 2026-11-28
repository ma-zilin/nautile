package io.github.mazilin.nautile.verification

import androidx.lifecycle.viewModelScope
import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.domain.model.TimeRange
import io.github.mazilin.nautile.domain.repository.StartTimerOutcome
import io.github.mazilin.nautile.domain.repository.StopTimerOutcome
import io.github.mazilin.nautile.domain.repository.TimeCategoryRepository
import io.github.mazilin.nautile.domain.repository.TimeLogRepository
import io.github.mazilin.nautile.domain.usecase.TimeCategoryCommands
import io.github.mazilin.nautile.domain.usecase.TimeLogCommands
import io.github.mazilin.nautile.testing.FakeClock
import io.github.mazilin.nautile.testing.FakeSettingsRepository
import io.github.mazilin.nautile.testing.InMemoryTimeCategoryRepository
import io.github.mazilin.nautile.testing.InMemoryTimeLogRepository
import io.github.mazilin.nautile.ui.screen.log.LogUiState
import io.github.mazilin.nautile.ui.screen.log.LogViewModel
import io.github.mazilin.nautile.ui.screen.log.RunningTimerUi
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R6-G4-V1 · 日志页计时与恢复（AC-T-21、AC-T-22、AC-L-05 模拟器等价部分）。
 *
 * 预期来自基线 v2.16 与冻结规格：点分类开始；同分类再点只提示；点计时条结束并保存；
 * 再次开始生成新记录；不同分类可并行且每分类最多一段；无暂停；
 * 重启（新建 ViewModel 复用同一仓库）后按 `start_at` 墙钟继续，而非累计值续算。
 * 时钟固定为 [FakeClock]，时区固定，避免真实时间波动。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AcLogViewModelG4Test {

    private val dispatcher = UnconfinedTestDispatcher()
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 11, 28)
    private val startInstant: Instant = today.atTime(10, 0).atZone(zone).toInstant()

    private lateinit var clock: FakeClock
    private lateinit var logStore: InMemoryTimeLogRepository
    private lateinit var categoryStore: InMemoryTimeCategoryRepository
    private lateinit var settings: FakeSettingsRepository
    private lateinit var logCommands: TimeLogCommands
    private val viewModels = mutableListOf<LogViewModel>()

    private var study: TimeCategoryId = TimeCategoryId(0)
    private var work: TimeCategoryId = TimeCategoryId(0)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        clock = FakeClock(startInstant)
        logStore = InMemoryTimeLogRepository()
        categoryStore = InMemoryTimeCategoryRepository(logStore)
        settings = FakeSettingsRepository()
        logCommands = TimeLogCommands(logStore, categoryStore, clock)
        val categoryCommands = TimeCategoryCommands(categoryStore, clock)
        study = seedCategory(categoryCommands, "学习")
        work = seedCategory(categoryCommands, "工作")
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

    private fun newViewModel(
        logs: TimeLogRepository = logStore,
        categories: TimeCategoryRepository = categoryStore,
    ): LogViewModel = LogViewModel(
        logRepository = logs,
        categoryRepository = categories,
        settingsRepository = settings,
        commands = TimeLogCommands(logs, categories, clock),
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

    @Test
    fun AC_T_21_quickTapStarts_andSameCategoryTapOnlyPrompts() = runTest(dispatcher) {
        val viewModel = newViewModel()

        viewModel.onCategoryQuickTap(study)
        val started = viewModel.awaitState("点分类应开始计时") {
            it.running.any { timer -> timer.categoryId == study }
        }
        val running = started.running.single { it.categoryId == study }
        assertEquals("开始时刻取注入时钟", startInstant, running.startedAt)

        clock.advanceSeconds(5)
        viewModel.onCategoryQuickTap(study)
        val prompted = viewModel.awaitState("同分类再点只提示") {
            it.message == "该分类已在计时"
        }

        assertEquals("不得出现第二段", 1, prompted.running.size)
        assertEquals("开始时刻不得重置", startInstant, logStore.snapshot.single().startAt)
        assertEquals("不得产生新记录", 1, logStore.snapshot.size)
        assertNull("仍处于运行中", logStore.snapshot.single().endAt)
    }

    @Test
    fun AC_T_21_runningTapStopsAndSaves_thenRestartCreatesNewRecord() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onCategoryQuickTap(study)
        viewModel.awaitState("先开始") { it.running.size == 1 }

        clock.advanceSeconds(120)
        viewModel.onRunningTimerTap(study)
        val stopped = viewModel.awaitState("点计时条应结束并保存") {
            it.message?.startsWith("已结束并保存") == true
        }

        assertTrue("结束后无运行中", stopped.running.isEmpty())
        val saved = logStore.snapshot.single()
        assertEquals(startInstant.plusSeconds(120), saved.endAt)
        assertEquals(Duration.ofMinutes(2), stopped.logs.single().duration)

        clock.advanceSeconds(60)
        viewModel.onCategoryQuickTap(study)
        val restarted = viewModel.awaitState("再次开始应生成新记录") {
            it.running.size == 1
        }
        assertNotEquals("再次开始必须生成新记录", saved.id.value, restarted.running.single().logId.value)
        assertEquals(2, logStore.snapshot.size)
        assertEquals("新记录开始于再次点按时刻", clock.now(), restarted.running.single().startedAt)
    }

    @Test
    fun AC_T_22_parallelCategories_eachAtMostOne_andStopOneKeepsOther() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onCategoryQuickTap(study)
        clock.advanceSeconds(30)
        viewModel.onCategoryQuickTap(work)

        val parallel = viewModel.awaitState("不同分类可并行") { it.running.size == 2 }
        assertEquals(setOf(study, work), parallel.running.map { it.categoryId }.toSet())

        // 每分类最多一段：两个分类各再点一次，仍只有 2 段。
        viewModel.onCategoryQuickTap(study)
        viewModel.onCategoryQuickTap(work)
        val stillTwo = viewModel.awaitState("每分类仍只有一段") { it.running.size == 2 }
        assertEquals(2, stillTwo.running.size)
        assertEquals(2, logStore.snapshot.size)

        clock.advanceSeconds(90)
        viewModel.onRunningTimerTap(study)
        val oneLeft = viewModel.awaitState("结束一个分类") {
            it.running.size == 1 && it.running.single().categoryId == work
        }
        assertEquals("另一个分类不得被结束", 1, oneLeft.running.size)
        val studyLog = logStore.snapshot.single { it.categoryId == study }
        val workLog = logStore.snapshot.single { it.categoryId == work }
        assertEquals("被结束的分类已保存", startInstant.plusSeconds(120), studyLog.endAt)
        assertNull("未点击的分类继续运行", workLog.endAt)
    }

    @Test
    fun AC_T_22_messageDismissDoesNotStopTimer() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onCategoryQuickTap(study)
        viewModel.awaitState("先开始") { it.running.size == 1 }

        viewModel.onDismissMessage()
        val state = viewModel.awaitState("提示消失后计时仍在") { it.message == null }
        assertEquals(1, state.running.size)
        assertNull(logStore.snapshot.single().endAt)
    }

    @Test
    fun AC_L_05_resumeAfterRecreate_usesWallClockNotAccumulated() = runTest(dispatcher) {
        val first = newViewModel()
        first.onCategoryQuickTap(study)
        first.awaitState("先开始") { it.running.size == 1 }
        val original = logStore.snapshot.single()

        // 模拟进程被杀后重开：同一持久仓库、同一开始时刻，新建 ViewModel。
        clock.advanceSeconds(15 * 60)
        val reopened = newViewModel()
        val resumed = reopened.awaitState("重开后运行中记录应恢复") {
            it.running.size == 1 && it.running.single().elapsed >= Duration.ofMinutes(15)
        }

        val timer = resumed.running.single()
        assertEquals("记录未丢", original.id, timer.logId)
        assertEquals("开始时刻不变", startInstant, timer.startedAt)
        assertEquals("按墙钟计算已用（15 分钟，误差不超过 1 分钟）", 15 * 60L, timer.elapsed.seconds)
        assertEquals(Duration.ofMinutes(15), resumed.logs.single().duration)
        assertNull("重启后仍为运行中，不产生暂停态", logStore.snapshot.single().endAt)
    }

    @Test
    fun AC_T_21_noPauseApiOrPauseStateInLogLayer() {
        val viewModelMethods = LogViewModel::class.java.declaredMethods.map { it.name.lowercase() }
        assertTrue("ViewModel 不得提供暂停/恢复：$viewModelMethods", viewModelMethods.none {
            "pause" in it || "resume" in it
        })
        val uiFields = LogUiState::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue("UI 状态不得保存暂停：$uiFields", uiFields.none { "pause" in it })
        val timerFields = RunningTimerUi::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue("计时模型不得保存暂停：$timerFields", timerFields.none { "pause" in it })
    }

    @Test
    fun AC_T_21_startFailureShowsErrorWithoutCrashOrSuccess() = runTest(dispatcher) {
        val failing = object : TimeLogRepository by logStore {
            override suspend fun startTimer(
                categoryId: TimeCategoryId,
                draft: TimeLog,
            ): StartTimerOutcome = throw IllegalStateException("数据库不可用")
        }
        val viewModel = newViewModel(logs = failing)

        viewModel.onCategoryQuickTap(study)

        val state = viewModel.awaitState("仓库异常应转为失败提示") {
            it.message == "开始失败，请重试"
        }
        assertFalse("不得显示成功", state.message?.startsWith("已开始") == true)
        assertTrue("不得留下运行中记录", logStore.snapshot.isEmpty())
    }

    @Test
    fun AC_T_22_elapsedNeverNegativeWhenClockRollsBack() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onCategoryQuickTap(study)
        val before = viewModel.awaitState("先开始") { it.running.size == 1 }

        // 设备时间回拨：同一次订阅内 now 取回拨后的时刻，elapsed 不得为负。
        val rollbackClock = FakeClock(startInstant.minusSeconds(3_600))
        val rolledBack = LogViewModel(
            logRepository = logStore,
            categoryRepository = categoryStore,
            settingsRepository = settings,
            commands = TimeLogCommands(logStore, categoryStore, rollbackClock),
            clock = rollbackClock,
            zoneId = zone,
        ).also { viewModels += it }
        val state = rolledBack.awaitState("回拨后应恢复运行中") { it.running.size == 1 }
        assertEquals(Duration.ZERO, state.running.single().elapsed)
        assertTrue(before.running.isNotEmpty())
        assertTrue("其它实例仍可读运行中", logStore.observeRunning().first().isNotEmpty())
    }

    @Test
    fun AC_L_02_historyOrderDescendingAfterManualCreate() = runTest(dispatcher) {
        val viewModel = newViewModel()
        clock.advanceSeconds(0)
        logCommands.createManual(
            study,
            today.minusDays(1).atTime(LocalTime.of(14, 0)).atZone(zone).toInstant(),
            today.minusDays(1).atTime(LocalTime.of(15, 0)).atZone(zone).toInstant(),
        )
        logCommands.createManual(
            work,
            today.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant(),
            today.atTime(LocalTime.of(10, 0)).atZone(zone).toInstant(),
        )

        val state = viewModel.awaitState("倒序加载") { it.logs.size == 2 }
        assertEquals("最新在上", work, state.logs.first().log.categoryId)
        assertEquals("昨天记录在后", study, state.logs.last().log.categoryId)
    }
}
