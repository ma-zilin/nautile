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
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleViewModel
import java.time.LocalDate
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R6-G2-V2 · AC-S-03 日/三天/周视图切换、跨周翻页、标题与范围一致（模拟器 Compose UI）。
 *
 * 设备：emulator-5554（模拟器，非真机）。固定时钟 2026-11-25（周三）与 Asia/Shanghai，
 * 变量只有用户操作；日期格、列数、标题均为可执行断言。
 * 预期依据：需求基线 v2.16 AC-S-03（L531）、冻结架构 §3.5（周一为周首）。
 */
@RunWith(AndroidJUnit4::class)
class AcScheduleS03UiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today: LocalDate = LocalDate.of(2026, 11, 25)

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

    @Test
    fun acS03_dayView_showsOnlySelectedDayAndRangeTitleMatches() {
        launch()

        composeRule.onNodeWithTag("schedule_range_title")
            .assertTextEquals("2026年11月25日 周三")
        composeRule.onNodeWithTag("schedule_grid_column_0").assertExists()
        composeRule.onNodeWithTag("schedule_grid_column_1").assertDoesNotExist()
        // 拒绝：日视图显示次日（明天的事件不得出现）。
        composeRule.onNodeWithTag("schedule_event_1").assertExists()
        composeRule.onNodeWithTag("schedule_event_2").assertDoesNotExist()
        composeRule.onNodeWithTag("schedule_hour_row_0").assertExists()
    }

    @Test
    fun acS03_threeDayView_showsSelectedPlusTwoAndRangeTitleMatches() {
        launch()
        switchView("三天")

        composeRule.onNodeWithTag("schedule_range_title")
            .assertTextEquals("11月25日 – 11月27日")
        composeRule.onNodeWithTag("schedule_grid_column_0").assertExists()
        composeRule.onNodeWithTag("schedule_grid_column_1").assertExists()
        composeRule.onNodeWithTag("schedule_grid_column_2").assertExists()
        // 拒绝：三天视图显示第 4 天。
        composeRule.onNodeWithTag("schedule_grid_column_3").assertDoesNotExist()
        composeRule.onNodeWithTag("schedule_event_1").assertExists()
        composeRule.onNodeWithTag("schedule_event_2").assertExists()
    }

    @Test
    fun acS03_weekView_showsSameWeekSevenDaysStartingMonday() {
        launch()
        switchView("周")

        composeRule.onNodeWithTag("schedule_range_title")
            .assertTextEquals("11月23日 – 11月29日")
        (0 until 7).forEach { index ->
            composeRule.onNodeWithTag("schedule_grid_column_$index").assertExists()
        }
        // 拒绝：周视图不是同一周 7 天（出现第 8 列）。
        composeRule.onNodeWithTag("schedule_grid_column_7").assertDoesNotExist()
        // 周一列头为 11/23，证明周首为周一。
        composeRule.onNodeWithText("11/23").assertExists()
        composeRule.onNodeWithTag("schedule_event_1").assertExists()
        composeRule.onNodeWithTag("schedule_event_2").assertExists()
        composeRule.onNodeWithContentDescription("视图：日/三天/周，当前：周").assertIsDisplayed()
    }

    @Test
    fun acS03_weekNextPage_updatesTitleAndRangeConsistently() {
        launch()
        switchView("周")
        composeRule.onNodeWithTag("schedule_date_2026-11-25").assertIsSelected()

        composeRule.onNodeWithContentDescription("下一周").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("schedule_range_title")
            .assertTextEquals("11月30日 – 12月6日")
        // 跨周后旧周事件不再显示，列数仍为 7。
        composeRule.onNodeWithTag("schedule_event_1").assertDoesNotExist()
        composeRule.onNodeWithTag("schedule_grid_column_6").assertExists()
        composeRule.onNodeWithTag("schedule_grid_column_7").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("上一周").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("schedule_range_title")
            .assertTextEquals("11月23日 – 11月29日")
        composeRule.onNodeWithTag("schedule_event_1").assertExists()
    }

    @Test
    fun acS03_weekSteppingAcrossYear_keepsTitleAndRangeConsistent() {
        launch()
        switchView("周")

        repeat(5) {
            composeRule.onNodeWithContentDescription("下一周").performClick()
            composeRule.waitForIdle()
        }

        // 2026-12-30 所在周：2026-12-28（周一）—2027-01-03（周日）。
        composeRule.onNodeWithTag("schedule_range_title")
            .assertTextEquals("12月28日 – 1月3日")
        composeRule.onNodeWithText("12/28").assertExists()
        composeRule.onNodeWithTag("schedule_grid_column_6").assertExists()
        composeRule.onNodeWithTag("schedule_grid_column_7").assertDoesNotExist()
    }
}
