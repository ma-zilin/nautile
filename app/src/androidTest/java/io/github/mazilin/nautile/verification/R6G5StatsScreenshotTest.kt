package io.github.mazilin.nautile.verification

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.domain.model.ChartSlice
import io.github.mazilin.nautile.domain.model.DayTimeStats
import io.github.mazilin.nautile.domain.model.LocalDateRange
import io.github.mazilin.nautile.domain.model.StatsPeriod
import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.domain.rule.TimeStats
import io.github.mazilin.nautile.ui.screen.log.LogMode
import io.github.mazilin.nautile.ui.screen.log.LogModeOverlayHost
import io.github.mazilin.nautile.ui.screen.log.LogScreen
import io.github.mazilin.nautile.ui.screen.log.LogUiState
import io.github.mazilin.nautile.ui.screen.log.StatsUiState
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R6-G6-V3 证据截图（不提交）：统计饼图、模式选择器、范围面板，浅色与深色。
 *
 * 截图写入应用 external files 目录，由 adb pull 到 `证据材料/待整理/R6-G6/`。
 * 截图只作视觉核对，不替代断言；判定见报告中的人工清单。
 */
@RunWith(AndroidJUnit4::class)
class R6G5StatsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val createdAt = LogG4.instant(LogG4.today, 7)

    private fun category(id: Long, name: String): TimeCategory = TimeCategory(
        id = TimeCategoryId(id),
        name = name,
        parentId = null,
        sortOrder = id.toInt(),
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun log(id: Long, categoryId: TimeCategoryId, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): TimeLog =
        TimeLog(
            id = TimeLogId(id),
            categoryId = categoryId,
            startAt = LogG4.instant(LogG4.today, startHour, startMinute),
            endAt = LogG4.instant(LogG4.today, endHour, endMinute),
            createdAt = createdAt,
            updatedAt = createdAt,
        )

    private fun statsState(): LogUiState {
        val study = category(1, "学习")
        val a = category(2, "并行A")
        val b = category(3, "并行B")
        val categories = listOf(study, a, b)
        val logs = listOf(
            log(1, study.id, 8, 0, 10, 0),
            log(2, a.id, 9, 0, 10, 0),
            log(3, b.id, 9, 30, 10, 30),
        )
        val day: DayTimeStats = TimeStats.computeDay(
            logs,
            categories,
            LogG4.today,
            LogG4.zone,
            LogG4.instant(LogG4.today, 12),
        )
        val names = categories.associate { it.id to it.name }
        val slices: List<ChartSlice> =
            TimeStats.chartSlices(day.parents, names, day.denominator, day.untracked)
        val stats = StatsUiState(
            today = LogG4.today,
            period = StatsPeriod.Day(LogG4.today),
            range = LocalDateRange(LogG4.today, LogG4.today),
            day = day,
            slices = slices,
        )
        return LogUiState(
            mode = LogMode.STATS,
            today = LogG4.today,
            categories = categories,
            stats = stats,
            now = LogG4.instant(LogG4.today, 12),
            zoneId = LogG4.zone,
        )
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        composeRule.waitForIdle()
        Thread.sleep(400)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: error("takeScreenshot 返回 null（$name）")
        val context = instrumentation.targetContext
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "g6_$name.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        assertTrue("截图应写入且有内容：${file.absolutePath}", file.length() > 0L)
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun setStats(dark: Boolean) {
        val state = statsState()
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LogScreen(state = state, modifier = Modifier.testTag("g6_stats_host"))
                }
            }
        }
        awaitTag("stats_pie_chart")
    }

    @Test
    fun g6_stats_pie_light() {
        setStats(dark = false)
        capture("stats_pie_light")
    }

    @Test
    fun g6_stats_pie_dark() {
        setStats(dark = true)
        capture("stats_pie_dark")
    }

    private fun setSelector(dark: Boolean) {
        val state = statsState()
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LogScreen(state = state)
                        LogModeOverlayHost(
                            modeSelectorOpen = true,
                            periodPanelOpen = false,
                            mode = LogMode.STATS,
                            stats = state.stats,
                            onModeSelected = {},
                            onDismissModeSelector = {},
                            onStartChanged = {},
                            onEndChanged = {},
                            onConfirmPeriod = {},
                            onDismissPeriodPanel = {},
                        )
                    }
                }
            }
        }
        awaitTag("log_mode_selector")
    }

    @Test
    fun g6_mode_selector_light() {
        setSelector(dark = false)
        capture("mode_selector_light")
    }

    @Test
    fun g6_mode_selector_dark() {
        setSelector(dark = true)
        capture("mode_selector_dark")
    }

    private fun setPeriodPanel(dark: Boolean) {
        val base = statsState()
        val stats = base.stats.copy(customStart = LogG4.today.minusDays(6), customEnd = LogG4.today)
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LogScreen(state = base)
                        LogModeOverlayHost(
                            modeSelectorOpen = false,
                            periodPanelOpen = true,
                            mode = LogMode.STATS,
                            stats = stats,
                            onModeSelected = {},
                            onDismissModeSelector = {},
                            onStartChanged = {},
                            onEndChanged = {},
                            onConfirmPeriod = {},
                            onDismissPeriodPanel = {},
                        )
                    }
                }
            }
        }
        awaitTag("stats_period_panel")
    }

    @Test
    fun g6_period_panel_light() {
        setPeriodPanel(dark = false)
        capture("period_panel_light")
    }

    @Test
    fun g6_period_panel_dark() {
        setPeriodPanel(dark = true)
        capture("period_panel_dark")
    }
}
