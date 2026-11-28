package io.github.mazilin.nautile.verification

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.repository.RoomTimeCategoryRepository
import io.github.mazilin.nautile.data.repository.RoomTimeLogRepository
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.usecase.TimeLogCommands
import io.github.mazilin.nautile.interaction.AppTab
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.Overlay
import io.github.mazilin.nautile.interaction.OverlayBackResult
import io.github.mazilin.nautile.interaction.OverlayBackStack
import io.github.mazilin.nautile.ui.AppRoot
import io.github.mazilin.nautile.ui.screen.common.PlaceholderScreen
import io.github.mazilin.nautile.ui.screen.log.LogMode
import io.github.mazilin.nautile.ui.screen.log.LogModeOverlayHost
import io.github.mazilin.nautile.ui.screen.log.LogScreen
import io.github.mazilin.nautile.ui.screen.log.LogViewModel
import io.github.mazilin.nautile.ui.screen.log.RunningTimerBar
import io.github.mazilin.nautile.ui.theme.NautileTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R6-G5-V1 · 统计页与模式选择器自动 UI（模拟器；AC-L-03/06/07/15/16/19、AC-T-20/25）。
 *
 * 装配与 `MainActivity` 同构：AppRoot + 真实 OverlayBackStack + LogViewModel + LogModeOverlayHost；
 * 系统返回由一个 OnBackPressedCallback 调 OverlayBackStack.back()，面板内子视图由 Compose
 * BackHandler 先消费（与生产返回层级一致）。数据库为内存 Room，时钟/时区固定。
 *
 * AC-T-20 在基线标【人工评审】；本类只作“自动 UI 辅助”，观感与手势体验仍列人工清单。
 */
@RunWith(AndroidJUnit4::class)
class AcStatsG5UiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: io.github.mazilin.nautile.data.db.NautileDatabase
    private lateinit var categoryRepository: RoomTimeCategoryRepository
    private lateinit var logRepository: RoomTimeLogRepository
    private lateinit var clock: G4Clock
    private lateinit var viewModel: LogViewModel
    private val navigation = NavigationCoordinator()
    private val overlays = OverlayBackStack()

    private var study: TimeCategoryId = TimeCategoryId(0)
    private var parallelA: TimeCategoryId = TimeCategoryId(0)
    private var parallelB: TimeCategoryId = TimeCategoryId(0)

    @Before
    fun setUp() {
        database = LogG4.freshDatabase(context)
        categoryRepository = RoomTimeCategoryRepository(database)
        logRepository = RoomTimeLogRepository(database)
        clock = G4Clock(LogG4.instant(LogG4.today, 12))
        viewModel = LogViewModel(
            logRepository = logRepository,
            categoryRepository = categoryRepository,
            settingsRepository = G4FakeSettingsRepository(),
            commands = TimeLogCommands(logRepository, categoryRepository, clock),
            clock = clock,
            zoneId = LogG4.zone,
        )
        runBlocking {
            study = LogG4.seedCategory(categoryRepository, "学习")
            parallelA = LogG4.seedCategory(categoryRepository, "并行A")
            parallelB = LogG4.seedCategory(categoryRepository, "并行B")
        }
        navigation.selectTab(AppTab.LOG)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun launch() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.addCallback(
                composeRule.activity,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        if (overlays.back() == OverlayBackResult.NoOverlay) {
                            isEnabled = false
                            composeRule.activity.onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }
                },
            )
        }
        composeRule.setContent { Host() }
        composeRule.waitUntil(5_000) { !viewModel.uiState.value.isLoading }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Host() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val overlayEntry by overlays.top.collectAsStateWithLifecycle()
        val activeTab by navigation.activeTab.collectAsStateWithLifecycle()
        val modeSelectorOpen = overlayEntry?.overlay == Overlay.LogModeSelector
        val periodPanelOpen = overlayEntry?.overlay == Overlay.StatsPeriodSelector

        NautileTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        navigation = navigation,
                        overlays = overlays,
                        scrollNavigationVisible = true,
                        tabContent = { tab ->
                            when (tab) {
                                AppTab.LOG -> LogScreen(
                                    state = state,
                                    onCategoryTap = viewModel::onCategoryQuickTap,
                                    onShowAllCategories = {},
                                    onOpenLog = {},
                                    onOpenManual = viewModel::onOpenManual,
                                    onDismissMessage = viewModel::onDismissMessage,
                                    onOpenModeSelector = {
                                        viewModel.onOpenModeSelector()
                                        val opened = overlays.open(Overlay.LogModeSelector)
                                        if (!opened) viewModel.onDismissModeSelector()
                                    },
                                    onSelectDayPeriod = viewModel::onSelectDayPeriod,
                                    onSelectMonthPeriod = viewModel::onSelectMonthPeriod,
                                    onSelectYearPeriod = viewModel::onSelectYearPeriod,
                                    onOpenPeriodPanel = {
                                        viewModel.onOpenPeriodPanel()
                                        val opened = overlays.open(Overlay.StatsPeriodSelector)
                                        if (!opened) viewModel.onDismissPeriodPanel()
                                    },
                                    onToggleStatsParent = viewModel::onStatsParentToggled,
                                    onLeaveScreen = viewModel::onLeaveScreen,
                                )

                                else -> PlaceholderScreen(title = tab.title, onOpenSettings = {})
                            }
                        },
                        fullScreenContent = { },
                        bottomBarContent = {
                            if (activeTab == AppTab.LOG && state.running.isNotEmpty()) {
                                Box(modifier = Modifier.testTag("timer_bar_scope")) {
                                    RunningTimerBar(
                                        running = state.running,
                                        onTimerTap = viewModel::onRunningTimerTap,
                                    )
                                }
                            }
                        },
                    )
                    // 与 MainActivity 一致：覆盖层关闭 → 同步 ViewModel；ViewModel 关闭 → 关覆盖层。
                    LaunchedEffect(modeSelectorOpen) {
                        if (!modeSelectorOpen && state.modeSelectorOpen) {
                            viewModel.onDismissModeSelector()
                        }
                    }
                    LaunchedEffect(periodPanelOpen) {
                        if (!periodPanelOpen && state.stats.panelOpen) {
                            viewModel.onDismissPeriodPanel()
                        }
                    }
                    LaunchedEffect(state.modeSelectorOpen) {
                        if (!state.modeSelectorOpen && overlays.current?.overlay == Overlay.LogModeSelector) {
                            overlays.close()
                        }
                    }
                    LaunchedEffect(state.stats.panelOpen) {
                        if (!state.stats.panelOpen && overlays.current?.overlay == Overlay.StatsPeriodSelector) {
                            overlays.close()
                        }
                    }
                    LogModeOverlayHost(
                        modeSelectorOpen = modeSelectorOpen,
                        periodPanelOpen = periodPanelOpen,
                        mode = state.mode,
                        stats = state.stats,
                        onModeSelected = { mode ->
                            viewModel.onModeSelected(mode)
                            overlays.close()
                        },
                        onDismissModeSelector = { overlays.close() },
                        onStartChanged = viewModel::onCustomStartChanged,
                        onEndChanged = viewModel::onCustomEndChanged,
                        onConfirmPeriod = viewModel::onConfirmCustomRange,
                        onDismissPeriodPanel = { overlays.close() },
                    )
                }
            }
        }
    }

    private fun awaitTag(tag: String, present: Boolean = true, timeout: Long = 5_000) {
        composeRule.waitUntil(timeout) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present
        }
        composeRule.waitForIdle()
    }

    private fun awaitText(text: String, substring: Boolean = false, timeout: Long = 5_000) {
        composeRule.waitUntil(timeout) {
            composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun openStats() {
        composeRule.onNodeWithTag("log_mode_indicator").performClick()
        awaitTag("log_mode_selector")
        composeRule.onNodeWithTag("log_mode_stats").performClick()
        awaitTag("log_mode_selector", present = false)
        awaitTag("stats_content")
    }

    private fun seedAcL16Fixture() {
        runBlocking {
            LogG4.seedLog(logRepository, study, LogG4.instant(LogG4.today, 8), LogG4.instant(LogG4.today, 10))
            LogG4.seedLog(logRepository, parallelA, LogG4.instant(LogG4.today, 9), LogG4.instant(LogG4.today, 10))
            LogG4.seedLog(
                logRepository,
                parallelB,
                LogG4.instant(LogG4.today, 9, 30),
                LogG4.instant(LogG4.today, 10, 30),
            )
        }
    }

    // ---------------- 数值夹具 → UI 呈现 ----------------

    @Test
    fun AC_L_16_L_19_pieLegendAndOverlapNoteMatchFixture() {
        seedAcL16Fixture()
        launch()
        openStats()

        composeRule.onNodeWithTag("stats_pie_chart").assertIsDisplayed()
        composeRule.onNodeWithTag("stats_overlap_note").assertIsDisplayed()
        composeRule.onNodeWithText("含重叠累计 · 合计 25h 30m").assertIsDisplayed()

        // 图例：2h 8%、1h 4%、1h 4%、21h 30m 84%，合计 100%。
        composeRule.onNodeWithText("2h · 8%").assertIsDisplayed()
        composeRule.onAllNodesWithText("1h · 4%").assertCountEquals(2)
        composeRule.onNodeWithText("21h 30m · 84%").assertIsDisplayed()
        composeRule.onNodeWithText("未追踪").assertIsDisplayed()
    }

    @Test
    fun AC_L_07_switchDayMonthYearCustomKeepsPie() {
        seedAcL16Fixture()
        launch()
        openStats()

        composeRule.onNodeWithText("2026-11-28").assertIsDisplayed()
        composeRule.onNodeWithTag("stats_pie_chart").assertIsDisplayed()

        composeRule.onNodeWithTag("stats_period_month").performClick()
        awaitText("2026年11月（截至11/28）")
        composeRule.onNodeWithTag("stats_pie_chart").assertIsDisplayed()

        composeRule.onNodeWithTag("stats_period_year").performClick()
        awaitText("2026年（截至11/28）")
        composeRule.onNodeWithTag("stats_pie_chart").assertIsDisplayed()

        composeRule.onNodeWithTag("stats_period_custom").performClick()
        awaitTag("stats_period_panel")
        composeRule.onNodeWithTag("stats_custom_confirm").performClick()
        awaitTag("stats_period_panel", present = false)
        awaitText("11/28–11/28")
        composeRule.onNodeWithTag("stats_pie_chart").assertIsDisplayed()
    }

    @Test
    fun AC_L_03_parentRowShowsThreeLogsAndExpandsTo100PercentShares() {
        val parent = runBlocking { LogG4.seedCategory(categoryRepository, "学习（父）") }
        val math = runBlocking { LogG4.seedCategory(categoryRepository, "数学", parentId = parent) }
        val english = runBlocking { LogG4.seedCategory(categoryRepository, "英语", parentId = parent) }
        runBlocking {
            LogG4.seedLog(logRepository, parent, LogG4.instant(LogG4.today, 8), LogG4.instant(LogG4.today, 9))
            LogG4.seedLog(logRepository, math, LogG4.instant(LogG4.today, 9), LogG4.instant(LogG4.today, 10))
            LogG4.seedLog(logRepository, english, LogG4.instant(LogG4.today, 10), LogG4.instant(LogG4.today, 11))
        }
        launch()
        openStats()

        val rowTag = "stats_parent_${parent.value}"
        awaitText("合计 3h · 3 条")
        composeRule.onNodeWithText("合计 3h · 3 条").assertIsDisplayed()
        composeRule.onNodeWithTag(rowTag).performClick()

        awaitText("直属")
        composeRule.onNodeWithText("数学").assertIsDisplayed()
        composeRule.onNodeWithText("英语").assertIsDisplayed()
        composeRule.onAllNodesWithText("1h · 34%").assertCountEquals(1)
        composeRule.onAllNodesWithText("1h · 33%").assertCountEquals(2)

        composeRule.onNodeWithTag(rowTag).performClick()
        awaitTag("stats_parent_${parent.value}")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("直属").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun AC_L_06_crossMidnightIsOneRecordAndTenMinutesPerDay() {
        runBlocking {
            LogG4.seedLog(
                logRepository,
                study,
                LogG4.instant(LogG4.today.minusDays(1), 23, 50),
                LogG4.instant(LogG4.today, 0, 10),
            )
        }
        launch()

        // 记录模式仍是一条，起止显示 23:50–00:10。
        composeRule.waitUntil(5_000) { viewModel.uiState.value.logs.size == 1 }
        composeRule.onNodeWithText("23:50–00:10").assertIsDisplayed()

        openStats()
        // 今天（11/28）只计入 00:00—00:10 的 10 分钟；分母 24h，未追踪 23h50m。
        composeRule.onNodeWithText("10m · 1%").assertIsDisplayed()
        composeRule.onNodeWithText("23h 50m · 99%").assertIsDisplayed()
        assertEquals("跨天仍是一条记录", 1, viewModel.uiState.value.logs.size)
    }

    @Test
    fun AC_L_15_emptyRangeShowsEmptyStateAndUntrackedWithoutNaN() {
        launch()
        openStats()

        composeRule.onNodeWithTag("stats_empty").assertIsDisplayed()
        composeRule.onAllNodesWithTag("stats_pie_chart").assertCountEquals(0)
        composeRule.onNodeWithText("未追踪").assertIsDisplayed()
        composeRule.onNodeWithText("24h · 100%").assertIsDisplayed()
        composeRule.onAllNodesWithText("NaN", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("含重叠累计 · 合计 24h").assertIsDisplayed()
    }

    // ---------------- 模式选择器（AC-T-20） ----------------

    @Test
    fun AC_T_20_buttonOpensSelectorWithoutRotating_andRightSwipeDoesNotOpen() {
        launch()
        composeRule.onNodeWithTag("log_mode_indicator").performClick()
        awaitTag("log_mode_selector")
        assertEquals("打开选择器不轮换模式", LogMode.RECORD, viewModel.uiState.value.mode)

        composeRule.onNodeWithTag("log_mode_selector_close").performClick()
        awaitTag("log_mode_selector", present = false)
        assertEquals("关闭后仍为记录", LogMode.RECORD, viewModel.uiState.value.mode)

        composeRule.onRoot().performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("log_mode_selector").assertCountEquals(0)
        assertEquals("右滑不切换模式", LogMode.RECORD, viewModel.uiState.value.mode)
    }

    @Test
    fun AC_T_20_selectorOverlayCoversNavigationAndTimerBar() {
        launch()
        viewModel.onCategoryQuickTap(study)
        awaitTag("timer_bar_scope")
        composeRule.onNodeWithTag("tab_LOG").assertIsDisplayed()

        composeRule.onNodeWithTag("log_mode_indicator").performClick()
        awaitTag("log_mode_selector")

        // 遮罩铺满全屏、主覆盖层独占：导航与计时条不可见/不可达。
        composeRule.onAllNodesWithTag("tab_LOG").assertCountEquals(0)
        composeRule.onAllNodesWithTag("tab_TODO").assertCountEquals(0)
        composeRule.onAllNodesWithTag("timer_bar_scope").assertCountEquals(0)
        composeRule.onNodeWithTag("log_mode_selector").assertIsDisplayed()

        composeRule.onNodeWithTag("log_mode_stats").performClick()
        awaitTag("log_mode_selector", present = false)
        awaitTag("stats_content")
        assertEquals(LogMode.STATS, viewModel.uiState.value.mode)
        // 关闭后导航与计时条恢复，计时未结束。
        composeRule.onNodeWithTag("tab_LOG").assertIsDisplayed()
        awaitTag("timer_bar_scope")
    }

    // ---------------- 返回层级 / 重置（AC-T-25） ----------------

    @Test
    fun AC_L_07_backFromDatePanelReturnsToFormThenClosesPanel() {
        launch()
        openStats()
        composeRule.onNodeWithTag("stats_period_custom").performClick()
        awaitTag("stats_period_panel")

        composeRule.onNodeWithTag("stats_custom_start").performClick()
        awaitTag("stats_date_panel")
        pressBack()
        awaitTag("stats_date_panel", present = false)
        composeRule.onNodeWithTag("stats_period_panel").assertIsDisplayed()

        pressBack()
        awaitTag("stats_period_panel", present = false)
        composeRule.onNodeWithTag("stats_content").assertIsDisplayed()
        assertEquals("关闭范围面板不改变统计模式", LogMode.STATS, viewModel.uiState.value.mode)
    }

    @Test
    fun AC_T_25_twoEntriesRestoreToday() {
        seedAcL16Fixture()
        launch()

        openStats()
        composeRule.onNodeWithTag("stats_period_month").performClick()
        awaitText("2026年11月（截至11/28）")

        // 第一次：切回记录再进统计 → 今日。
        composeRule.onNodeWithTag("log_mode_indicator").performClick()
        awaitTag("log_mode_selector")
        composeRule.onNodeWithTag("log_mode_record").performClick()
        awaitTag("log_mode_selector", present = false)
        openStats()
        awaitText("2026-11-28")

        // 第二次：离开日志页再进统计 → 今日。
        composeRule.onNodeWithTag("stats_period_year").performClick()
        awaitText("2026年（截至11/28）")
        composeRule.onNodeWithTag("tab_TODO").performClick()
        awaitTag("stats_content", present = false)
        composeRule.onNodeWithTag("tab_LOG").performClick()
        awaitTag("log_mode_indicator")
        openStats()
        awaitText("2026-11-28")
        assertTrue(viewModel.uiState.value.stats.period is io.github.mazilin.nautile.domain.model.StatsPeriod.Day)
    }

    private fun pressBack() {
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }
}
