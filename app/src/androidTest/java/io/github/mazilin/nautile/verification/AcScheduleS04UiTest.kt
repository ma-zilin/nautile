package io.github.mazilin.nautile.verification

import androidx.lifecycle.viewModelScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleViewModel
import java.time.LocalDate
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R6-G2-V2 · AC-S-04 部分（模拟器 Compose UI）：无课程数据下的三来源筛选与空态。
 *
 * 已确认口径（D-new1、决定 b、边界 e）：新建只允许普通来源；“课程表”只是标签；
 * 标签不参与筛选。夹具为“无 COURSE 来源数据 + 一条手动标了课程表标签的普通日程”。
 * 预期依据：需求基线 v2.16 AC-S-04（L532）、PhaseA_FR_AC子集 §4.3/§4.5。
 */
@RunWith(AndroidJUnit4::class)
class AcScheduleS04UiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today: LocalDate = LocalDate.of(2026, 11, 25)

    private lateinit var database: NautileDatabase
    private lateinit var viewModel: ScheduleViewModel
    private var emptyDatabase: NautileDatabase? = null
    private var emptyViewModel: ScheduleViewModel? = null

    @Before
    fun setUp() {
        database = ScheduleG2TestSupport.createDatabase()
        ScheduleG2TestSupport.seedCourseTag(database)
        ScheduleG2TestSupport.seed(
            database,
            listOf(
                ScheduleG2TestSupport.event(
                    id = 1L,
                    title = "标了课程表标签的普通日程",
                    date = today,
                    startHour = 9,
                    endHour = 10,
                    tagId = ScheduleG2TestSupport.COURSE_TAG_ID,
                ),
            ),
        )
        viewModel = ScheduleG2TestSupport.newViewModel(database, today)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        emptyViewModel?.viewModelScope?.cancel()
        emptyDatabase?.close()
        database.close()
    }

    private fun launch() {
        composeRule.setContent { ScheduleG2Screen(viewModel) }
        composeRule.waitUntil(5_000) { !viewModel.uiState.value.isLoading }
        composeRule.waitForIdle()
    }

    @Test
    fun acS04_courseFilterShowsEmptyState_andTagDoesNotCountAsCourseSource() {
        launch()
        // 默认“合并”：手动日程可见，并显示“手动创建 · 课程表”，来源仍是手动创建。
        composeRule.onNodeWithTag("schedule_event_1").assertIsDisplayed()
        composeRule.onNodeWithText("手动创建 · 课程表").assertExists()

        composeRule.onNodeWithTag("schedule_source_course").performClick()
        composeRule.waitForIdle()

        // 课程表来源无数据：空态，不凭空出现课程，也不把带课程表标签的普通日程误当课程。
        composeRule.onNodeWithTag("schedule_empty_state").assertIsDisplayed()
        composeRule.onNodeWithText("课程表暂无日程").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_event_1").assertDoesNotExist()
        assertEquals(0, viewModel.uiState.value.events.size)

        // 仅普通：只显示普通日程（标签不改变来源）。
        composeRule.onNodeWithTag("schedule_source_regular").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("schedule_event_1").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_empty_state").assertDoesNotExist()
        assertEquals(1, viewModel.uiState.value.events.size)

        // 合并：仍只显示普通日程（无课程数据）。
        composeRule.onNodeWithTag("schedule_source_merged").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("schedule_event_1").assertIsDisplayed()
        assertEquals(1, viewModel.uiState.value.events.size)
    }

    @Test
    fun acS04_noTagFilterEntryAndOnlyThreeSourceChips() {
        launch()

        // 三个来源按钮存在；本轮不做标签筛选，不出现“无标签”等标签筛选入口。
        composeRule.onNodeWithTag("schedule_source_course").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_source_merged").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_source_regular").assertIsDisplayed()
        composeRule.onAllNodesWithText("无标签").assertCountEquals(0)
        composeRule.onAllNodesWithText("标签筛选").assertCountEquals(0)
    }

    @Test
    fun acS04_emptyDatabase_allThreeFiltersShowEmptyAndControlsRemainUsable() {
        val emptyDb = ScheduleG2TestSupport.createDatabase()
        val emptyVm = ScheduleG2TestSupport.newViewModel(emptyDb, today)
        emptyDatabase = emptyDb
        emptyViewModel = emptyVm

        composeRule.setContent { ScheduleG2Screen(emptyVm) }
        composeRule.waitUntil(5_000) { !emptyVm.uiState.value.isLoading }
        composeRule.waitForIdle()

        listOf(
            "schedule_source_course" to "课程表暂无日程",
            "schedule_source_merged" to "点右下角新建一条日程",
            "schedule_source_regular" to "点右下角新建一条日程",
        ).forEach { (chipTag, description) ->
            composeRule.onNodeWithTag(chipTag).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("schedule_empty_state").assertIsDisplayed()
            composeRule.onNodeWithText(description).assertIsDisplayed()
            composeRule.onNodeWithTag("schedule_event_1").assertDoesNotExist()
        }

        // 日期条、视图按钮、新建入口仍可用（边界 a）。
        composeRule.onNodeWithTag("schedule_date_strip").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_view_button").assertIsDisplayed()
        composeRule.onNodeWithTag("add_schedule").assertIsDisplayed().assertHasClickAction()
    }
}
