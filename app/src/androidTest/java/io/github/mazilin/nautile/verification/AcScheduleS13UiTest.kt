package io.github.mazilin.nautile.verification

import androidx.lifecycle.viewModelScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleViewModel
import java.time.LocalDate
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R6-G2-V2 · AC-S-13 可自动化部分（模拟器 Compose UI）。
 *
 * 覆盖：视图按钮三选一切换、固定小时密度、点击日期条切日期、左右滑动不切日期、切换后数据不变。
 * 双指缩放“不存在”、固定密度观感、200% 字体与浅/深色视觉走查属人工项，单列报告，不计入通过。
 * 预期依据：需求基线 v2.16 AC-S-13（L541）、FR-S-14；设计 05 §3.1（小时高度 60dp）。
 */
@RunWith(AndroidJUnit4::class)
class AcScheduleS13UiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today: LocalDate = LocalDate.of(2026, 11, 25)
    private val density: Float =
        InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density

    private lateinit var database: NautileDatabase
    private lateinit var viewModel: ScheduleViewModel

    @Before
    fun setUp() {
        database = ScheduleG2TestSupport.createDatabase()
        ScheduleG2TestSupport.seed(
            database,
            listOf(
                ScheduleG2TestSupport.event(1L, "今天日程", today, 9, 10),
                ScheduleG2TestSupport.event(2L, "明天日程", today.plusDays(1), 11, 12),
            ),
        )
        viewModel = ScheduleG2TestSupport.newViewModel(database, today)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        database.close()
    }

    private fun launch() {
        composeRule.setContent { ScheduleG2Screen(viewModel) }
        composeRule.waitUntil(5_000) { !viewModel.uiState.value.isLoading }
        composeRule.waitForIdle()
    }

    private fun switchView(label: String) {
        composeRule.onNodeWithTag("schedule_view_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(label).performClick()
        composeRule.waitForIdle()
    }

    private fun hourHeightDp(): Float {
        val heightPx = composeRule.onNodeWithTag("schedule_hour_row_0")
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        return heightPx / density
    }

    @Test
    fun acS13_viewButtonSwitchesThreeModes_withFixedHourHeight() {
        launch()
        val dayHeight = hourHeightDp()
        assertEquals("日视图小时高度应符合 60dp 固定密度", 60f, dayHeight, 0.5f)

        switchView("三天")
        composeRule.onNodeWithContentDescription("视图：日/三天/周，当前：三天").assertIsDisplayed()
        val threeDayHeight = hourHeightDp()
        assertEquals("三天视图小时高度应与日视图相同", dayHeight, threeDayHeight, 0.5f)

        switchView("周")
        composeRule.onNodeWithContentDescription("视图：日/三天/周，当前：周").assertIsDisplayed()
        val weekHeight = hourHeightDp()
        assertEquals("周视图小时高度应与日视图相同", dayHeight, weekHeight, 0.5f)
        assertEquals("三种视图时间密度不得随视图变化", dayHeight, weekHeight, 0.01f)
    }

    @Test
    fun acS13_switchingViewModes_doesNotChangeData() {
        launch()
        val dayTitles = viewModel.uiState.value.events.map { it.event.title }.toSet()
        assertEquals(setOf("今天日程"), dayTitles)

        switchView("周")
        switchView("日")

        composeRule.onNodeWithTag("schedule_event_1").assertExists()
        composeRule.onNodeWithTag("schedule_event_2").assertDoesNotExist()
        assertEquals(
            "切换视图往返后日视图数据应与初始一致",
            dayTitles,
            viewModel.uiState.value.events.map { it.event.title }.toSet(),
        )
    }

    @Test
    fun acS13_tapDateCell_selectsDateAndRefreshesRange() {
        launch()
        composeRule.onNodeWithTag("schedule_date_2026-11-25").assertIsSelected()

        composeRule.onNodeWithTag("schedule_date_2026-11-26").performClick()
        composeRule.waitForIdle()

        assertEquals(today.plusDays(1), viewModel.uiState.value.selectedDate)
        composeRule.onNodeWithTag("schedule_range_title")
            .assertTextEquals("2026年11月26日 周四")
        composeRule.onNodeWithTag("schedule_date_2026-11-26").assertIsSelected()
        composeRule.onNodeWithTag("schedule_event_1").assertDoesNotExist()
        composeRule.onNodeWithTag("schedule_event_2").assertExists()
    }

    @Test
    fun acS13_horizontalSwipeOnDateStrip_doesNotChangeSelectedDateOrData() {
        launch()
        assertEquals(today, viewModel.uiState.value.selectedDate)
        val titleBefore = viewModel.uiState.value.rangeTitle

        composeRule.onNodeWithTag("schedule_date_strip").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("schedule_date_strip").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals("左右滑动日期条不得切日期", today, viewModel.uiState.value.selectedDate)
        assertEquals("左右滑动后范围标题不得变化", titleBefore, viewModel.uiState.value.rangeTitle)
        composeRule.onNodeWithTag("schedule_event_1").assertExists()
        composeRule.onNodeWithTag("schedule_event_2").assertDoesNotExist()
    }

    @Test
    fun acS13_todayButton_returnsToTodayAfterStepping() {
        launch()
        composeRule.onNodeWithContentDescription("后一天").performClick()
        composeRule.waitForIdle()
        assertEquals(today.plusDays(1), viewModel.uiState.value.selectedDate)

        composeRule.onNodeWithTag("schedule_today_button").performClick()
        composeRule.waitForIdle()

        assertEquals(today, viewModel.uiState.value.selectedDate)
        composeRule.onNodeWithTag("schedule_date_2026-11-25").assertIsSelected()
        assertTrue(viewModel.uiState.value.events.isNotEmpty())
    }
}
