package io.github.mazilin.nautile.verification

import androidx.activity.OnBackPressedCallback
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.activity.ComponentActivity
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.repository.RoomScheduleRepository
import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleSourceFilter
import io.github.mazilin.nautile.domain.model.TimeRange
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.ScheduleRepository
import io.github.mazilin.nautile.domain.usecase.ScheduleCommands
import io.github.mazilin.nautile.interaction.AppTab
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.Overlay
import io.github.mazilin.nautile.interaction.OverlayBackResult
import io.github.mazilin.nautile.interaction.OverlayBackStack
import io.github.mazilin.nautile.ui.AppRoot
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleOverlayHost
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleScreen
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleViewModel
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R6-G3-V1 · 模拟器 Compose UI：日程新建/详情（AC-D-06/07/08、AC-S-04 剩余、AC-M-01 辅助）。
 *
 * 装配与 `MainActivity` 同构：AppRoot + 真实 OverlayBackStack + ScheduleViewModel + 覆盖层宿主；
 * 系统返回由一个 OnBackPressedCallback 调 OverlayBackStack.back()，与 BackDispatcher 的覆盖层分支一致。
 * 数据库为内存 Room，时钟/时区固定，不污染实际数据。
 */
@RunWith(AndroidJUnit4::class)
class AcScheduleG3SheetsUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 11, 25)
    private val fixedNow: Instant = today.atTime(10, 0).atZone(zone).toInstant()

    private val navigation = NavigationCoordinator()
    private val overlays = OverlayBackStack()

    private lateinit var database: NautileDatabase
    private lateinit var repository: ScheduleRepository
    private lateinit var viewModel: ScheduleViewModel

    @Before
    fun setUp() {
        database = ScheduleG2TestSupport.createDatabase()
        ScheduleG2TestSupport.seedCourseTag(database)
        repository = RoomScheduleRepository(database)
        val start = today.atTime(9, 0).atZone(zone).toInstant()
        val end = today.atTime(10, 0).atZone(zone).toInstant()
        runBlocking {
            repository.insert(
                ScheduleEvent(
                    id = ScheduleId(0),
                    title = "原日程",
                    note = null,
                    startAt = start,
                    endAt = end,
                    source = ScheduleSource.REGULAR,
                    tagId = null,
                    seriesId = null,
                    originalStartAt = null,
                    createdAt = start,
                    updatedAt = start,
                ),
            )
        }
        viewModel = ScheduleViewModel(
            repository = repository,
            clock = Clock { fixedNow },
            zoneId = zone,
            commands = ScheduleCommands(repository, Clock { fixedNow }),
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        database.close()
    }

    private fun launch() {
        // OnBackPressedDispatcher 必须在主线程注册（测试夹具错误修正，非业务问题）。
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.addCallback(
                composeRule.activity,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        // 与 MainActivity 的覆盖层分支一致：Blocked 已消费但保留面板。
                        if (overlays.back() == OverlayBackResult.NoOverlay) {
                            isEnabled = false
                            composeRule.activity.onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }
                },
            )
        }
        composeRule.setContent { G3Harness(viewModel, overlays, navigation) }
        composeRule.runOnIdle { navigation.selectTab(AppTab.SCHEDULE) }
        composeRule.waitUntil(5_000) { !viewModel.uiState.value.isLoading }
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun openNew() {
        composeRule.onNodeWithTag("add_schedule").performClick()
        composeRule.waitUntil(5_000) { viewModel.editor.value != null }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("new_schedule_sheet").assertIsDisplayed()
    }

    private fun openDetail() {
        // 时间网格初始停在 00:00；先滚动到事件块，避免点击落在视口外。
        composeRule.onNodeWithTag("schedule_time_grid")
            .performScrollToNode(hasTestTag("schedule_event_1"))
        composeRule.onNodeWithTag("schedule_event_1").performClick()
        composeRule.waitUntil(5_000) { viewModel.editor.value != null }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("schedule_detail_sheet").assertIsDisplayed()
    }

    private fun stored(): ScheduleEvent? = runBlocking { repository.get(ScheduleId(1L)) }

    private fun storedCount(): Int = runBlocking {
        repository.observeInRange(anyRange(), ScheduleSourceFilter.MERGED).first().size
    }

    private fun anyRange(): TimeRange = TimeRange(
        startInclusive = today.minusDays(30).atStartOfDay(zone).toInstant(),
        endExclusive = today.plusDays(30).atStartOfDay(zone).toInstant(),
    )

    private fun waitForStored(description: String, predicate: (ScheduleEvent?) -> Boolean) {
        composeRule.waitUntil(5_000) { predicate(stored()) }
    }

    // ---------- AC-D-06：详情半屏、编辑即保存、导航隐藏、草稿保留 ----------

    @Test
    fun acD06_detailSheet_hidesNavigation_andEachFieldSavesImmediately() {
        launch()
        composeRule.onNodeWithTag("tab_SCHEDULE").assertExists()

        openDetail()
        composeRule.onNodeWithTag("tab_SCHEDULE").assertDoesNotExist()

        // 标题：改后切到备注字段（切换字段即保存）。
        composeRule.onNodeWithTag("schedule_title_field").performTextClearance()
        composeRule.onNodeWithTag("schedule_title_field").performTextInput("改后标题")
        composeRule.onNodeWithTag("schedule_note_field").performClick()
        waitForStored("标题切换字段即保存") { it?.title == "改后标题" }
        // 备注：输入后切回标题字段即保存。
        composeRule.onNodeWithTag("schedule_note_field").performTextInput("备注草稿")
        composeRule.onNodeWithTag("schedule_title_field").performClick()
        waitForStored("备注切换字段即保存") { it?.note == "备注草稿" }

        // 标签：选择“课程表”，来源仍普通。
        composeRule.onNodeWithTag("schedule_tag_course").performClick()
        waitForStored("标签选择即保存") { it?.tagId?.value == ScheduleG2TestSupport.COURSE_TAG_ID }
        composeRule.onNodeWithText("普通").assertIsDisplayed()

        // 结束时间：圆盘直输 11:30 并确认，立即回填并保存。
        composeRule.onNodeWithTag("schedule_end_time").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("schedule_time_picker").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("schedule_time_input").performTextReplacement("11:30")
        composeRule.onNodeWithTag("schedule_time_input").assertTextContains("11:30", substring = true)
        composeRule.onNodeWithTag("schedule_time_confirm").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(
            "圆盘确认应回填结束时间",
            LocalTime.of(11, 30),
            viewModel.editor.value?.endTime,
        )
        waitForStored("时间确认即保存") {
            it?.endAt?.atZone(zone)?.toLocalTime() == LocalTime.of(11, 30)
        }
        // 确认后仍在“结束日期”日历视图，返回表单再验证日期行。
        pressBack()
        composeRule.onNodeWithTag("schedule_detail_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_detail_sheet").assertIsDisplayed()

        // 日期行打开日历面板，返回回到表单（日期落库由 JVM 用例覆盖）。
        composeRule.onNodeWithTag("schedule_start_date").performScrollTo().performClick()
        composeRule.onNodeWithTag("schedule_date_picker").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithTag("schedule_title_field").assertIsDisplayed()

        // 关闭后重开，草稿已保存为新值（导航隐藏不丢草稿）。
        composeRule.onNodeWithTag("schedule_close").performClick()
        composeRule.waitUntil(5_000) { viewModel.editor.value == null }
        composeRule.onNodeWithTag("tab_SCHEDULE").assertExists()

        openDetail()
        composeRule.onNodeWithTag("schedule_title_field").assertTextContains("改后标题", substring = true)
        composeRule.onNodeWithTag("tab_SCHEDULE").assertDoesNotExist()
    }

    @Test
    fun acD06_deleteConfirm_cancelKeeps_thenConfirmRemovesAndCloses() {
        launch()
        openDetail()

        composeRule.onNodeWithTag("schedule_delete").performClick()
        composeRule.onNodeWithText("删除后不可恢复").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithTag("schedule_detail_sheet").assertIsDisplayed()
        assertNotNull("取消删除后记录仍在", stored())

        composeRule.onNodeWithTag("schedule_delete").performClick()
        composeRule.onNodeWithTag("schedule_delete_confirm").performClick()
        composeRule.waitUntil(5_000) { viewModel.editor.value == null }
        composeRule.onNodeWithTag("schedule_detail_sheet").assertDoesNotExist()
        composeRule.onNodeWithTag("tab_SCHEDULE").assertExists()
        assertEquals(0, storedCount())
    }

    @Test
    fun acD06_writeFailure_showsErrorAndNoSuccessText() {
        launch()
        openDetail()
        // 制造写入失败的真实分支：记录在面板打开后被删除 → updateField 返回 NotFound。
        runBlocking { repository.delete(ScheduleId(1L)) }

        composeRule.onNodeWithTag("schedule_title_field").performTextClearance()
        composeRule.onNodeWithTag("schedule_title_field").performTextInput("失败分支标题")
        composeRule.onNodeWithTag("schedule_note_field").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("日程不存在").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("日程不存在").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("已保存").assertCountEquals(0)
        composeRule.onAllNodesWithText("保存成功").assertCountEquals(0)
        composeRule.onNodeWithTag("schedule_detail_sheet").assertIsDisplayed()
    }

    // ---------- AC-D-08：无效字段两次都被阻止，修正后可离开 ----------

    @Test
    fun acD08_invalidTitle_blocksSwitchAndTwoCloses_thenCorrectionAllowsLeave() {
        launch()
        openDetail()

        composeRule.onNodeWithTag("schedule_title_field").performTextClearance()
        composeRule.onNodeWithTag("schedule_note_field").performClick()
        // 字段下方提示与“阻止关闭”提示可能同时存在，至少一条可见即满足“提示”。
        composeRule.onAllNodesWithText("标题不能为空").onFirst().assertIsDisplayed()

        pressBack()
        composeRule.onNodeWithTag("schedule_detail_sheet").assertIsDisplayed()
        // 字段下方提示与“阻止关闭”提示可能同时存在，至少一条可见即满足“提示”。
        composeRule.onAllNodesWithText("标题不能为空").onFirst().assertIsDisplayed()

        pressBack()
        composeRule.onNodeWithTag("schedule_detail_sheet").assertIsDisplayed()
        // 字段下方提示与“阻止关闭”提示可能同时存在，至少一条可见即满足“提示”。
        composeRule.onAllNodesWithText("标题不能为空").onFirst().assertIsDisplayed()
        assertEquals("无效值不得写库", "原日程", stored()?.title)

        // 字段保持可编辑，修正后可离开。
        composeRule.onNodeWithTag("schedule_title_field").performTextInput("修正后标题")
        composeRule.onNodeWithTag("schedule_title_field").assertTextContains("修正后标题", substring = true)
        pressBack()
        composeRule.waitUntil(5_000) { viewModel.editor.value == null }
        composeRule.onNodeWithTag("schedule_detail_sheet").assertDoesNotExist()
        waitForStored("修正值应保存") { it?.title == "修正后标题" }
        composeRule.onNodeWithTag("tab_SCHEDULE").assertExists()
    }

    @Test
    fun acD08_invalidTimeRange_blocksCloseWithoutWriting_thenFixAndLeave() {
        launch()
        openDetail()

        // 结束 08:00 早于开始 09:00：确认后内联报错，不写库。
        composeRule.onNodeWithTag("schedule_end_time").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("schedule_time_picker").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("schedule_time_input").performTextReplacement("08:00")
        composeRule.onNodeWithTag("schedule_time_input").assertTextContains("08:00", substring = true)
        composeRule.onNodeWithTag("schedule_time_confirm").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(
            "圆盘确认应回填结束时间（非法起止不写库）",
            LocalTime.of(8, 0),
            viewModel.editor.value?.endTime,
        )
        pressBack() // 日期视图
        pressBack() // 表单
        composeRule.onAllNodesWithText("结束时间必须晚于开始时间").onFirst().assertIsDisplayed()
        pressBack() // 关闭被阻止
        composeRule.onNodeWithTag("schedule_detail_sheet").assertIsDisplayed()
        assertEquals(
            "非法起止不得写库",
            LocalTime.of(10, 0),
            stored()?.endAt?.atZone(zone)?.toLocalTime(),
        )

        // 修正为 10:30 后可关闭。
        composeRule.onNodeWithTag("schedule_end_time").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("schedule_time_picker").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("schedule_time_input").performTextReplacement("10:30")
        composeRule.onNodeWithTag("schedule_time_confirm").performScrollTo().performClick()
        composeRule.waitForIdle()
        pressBack() // 日期视图 → 表单
        pressBack() // 表单 → 关闭（有效）
        composeRule.waitUntil(5_000) { viewModel.editor.value == null }
        waitForStored("修正后应保存") {
            it?.endAt?.atZone(zone)?.toLocalTime() == LocalTime.of(10, 30)
        }
    }

    // ---------- AC-D-07：新建入口、取消、无效阻止、原位插入 ----------

    @Test
    fun acD07_entryAboveNavigation_andCancelGeneratesNothing() {
        launch()

        val fab = composeRule.onNodeWithTag("add_schedule").getUnclippedBoundsInRoot()
        val nav = composeRule.onNodeWithTag("tab_SCHEDULE").getUnclippedBoundsInRoot()
        assertTrue(
            "新建入口应位于导航上方（FAB 底 ${fab.bottom} 应不高于导航项顶 ${nav.top}）",
            fab.bottom.value <= nav.top.value,
        )

        openNew()
        // 字段下方提示与“阻止关闭”提示可能同时存在，至少一条可见即满足“提示”。
        composeRule.onAllNodesWithText("标题不能为空").onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag("tab_SCHEDULE").assertDoesNotExist()

        composeRule.onNodeWithTag("schedule_cancel").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { viewModel.editor.value == null }
        composeRule.onNodeWithTag("new_schedule_sheet").assertDoesNotExist()
        composeRule.onNodeWithTag("tab_SCHEDULE").assertExists()
        assertEquals("取消不生成半成品（AC-D-07）", 1, storedCount())
    }

    @Test
    fun acD07_invalidStaysAndBackBlocked_thenCreateInsertsInPlaceKeepingFilter() {
        launch()
        composeRule.onNodeWithTag("schedule_source_regular").performClick()
        composeRule.onNodeWithTag("schedule_source_regular").assertIsSelected()

        openNew()
        pressBack()
        composeRule.onNodeWithTag("new_schedule_sheet").assertIsDisplayed()
        // 字段下方提示与“阻止关闭”提示可能同时存在，至少一条可见即满足“提示”。
        composeRule.onAllNodesWithText("标题不能为空").onFirst().assertIsDisplayed()
        assertEquals("无效信息不得生成记录", 1, storedCount())

        composeRule.onNodeWithTag("schedule_title_field").performTextInput("新建日程A")
        composeRule.onNodeWithTag("schedule_create").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { viewModel.editor.value == null }
        composeRule.onNodeWithTag("new_schedule_sheet").assertDoesNotExist()
        val created = runBlocking { repository.observeInRange(anyRange(), ScheduleSourceFilter.MERGED).first() }
            .first { it.title == "新建日程A" }
        composeRule.waitUntil(5_000) {
            viewModel.uiState.value.events.any { it.event.id.value == created.id.value }
        }
        // 不跳页、不改筛选（AC-D-07）。
        composeRule.onNodeWithTag("schedule_source_regular").assertIsSelected()
        assertEquals(2, storedCount())
        assertEquals("新建来源固定普通", ScheduleSource.REGULAR, created.source)
    }

    // ---------- AC-M-01：严格 24h 输入与分层返回（辅助核对） ----------

    @Test
    fun acM01_strict24hInputs_showInlineErrorBlockConfirmWithoutSilentFix() {
        launch()
        openNew()
        composeRule.onNodeWithTag("schedule_start_time").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("schedule_time_picker").fetchSemanticsNodes().isNotEmpty()
        }

        listOf("25:00", "12:60", "abc").forEach { value ->
            composeRule.onNodeWithTag("schedule_time_input").performTextReplacement(value)
            composeRule.waitForIdle()
            composeRule.onNodeWithText("时间需为 24 小时制 HH:mm（00:00–23:59）").assertIsDisplayed()
            composeRule.onNodeWithTag("schedule_time_confirm").assertIsNotEnabled()
            // 不静默修正：输入框仍保留原文本。
            composeRule.onNodeWithTag("schedule_time_input").assertTextContains(value)
        }

        composeRule.onNodeWithTag("schedule_time_input").performTextReplacement("08:15")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("schedule_time_confirm").assertIsEnabled()
        composeRule.onNodeWithTag("schedule_time_confirm").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("08:15").assertCountEquals(1)
    }

    @Test
    fun acM01_systemBackClosesTimeDial_thenCalendar_thenSheet() {
        launch()
        openNew()
        // 直接写入标题，避免测试输入焦点/IME 干扰分层返回断言（非交互断言项）。
        composeRule.runOnIdle { viewModel.onEditorTitleChanged("分层返回") }

        composeRule.onNodeWithTag("schedule_start_time").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("schedule_time_picker").fetchSemanticsNodes().isNotEmpty()
        }

        pressBack()
        composeRule.onNodeWithTag("schedule_date_picker").assertIsDisplayed()
        composeRule.onAllNodesWithTag("schedule_time_picker").assertCountEquals(0)

        pressBack()
        composeRule.onNodeWithTag("schedule_title_field").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_date_picker").assertDoesNotExist()

        pressBack()
        composeRule.waitUntil(5_000) { viewModel.editor.value == null }
        composeRule.onNodeWithTag("new_schedule_sheet").assertDoesNotExist()
        composeRule.onNodeWithTag("tab_SCHEDULE").assertExists()
        assertEquals("仅返回关闭不生成记录", 1, storedCount())
    }

    // ---------- AC-S-04 剩余：标签只作字段，不改来源、无标签筛选 ----------

    @Test
    fun acS04_tagChipChangesTagOnly_andNoTagFilterEntry() {
        launch()
        openNew()
        composeRule.onNodeWithTag("schedule_tag_none").assertIsSelected()
        composeRule.onAllNodesWithText("标签筛选").assertCountEquals(0)
        // 来源本轮及以后只读（用户 2026-11-28）：只显示“普通”，不提供可点的修改入口或课程来源选项。
        composeRule.onNodeWithTag("schedule_source_value").assertHasNoClickAction()
        composeRule.onAllNodesWithText("课程").assertCountEquals(0)

        composeRule.onNodeWithTag("schedule_tag_course").performClick()
        composeRule.onNodeWithTag("schedule_tag_course").assertIsSelected()
        composeRule.onNodeWithTag("schedule_source_value").assertTextContains("普通")

        composeRule.onNodeWithTag("schedule_title_field").performTextInput("打课程表标签的普通日程")
        composeRule.onNodeWithTag("schedule_create").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { viewModel.editor.value == null }
        val created = runBlocking { repository.observeInRange(anyRange(), ScheduleSourceFilter.MERGED).first() }
            .first { it.title == "打课程表标签的普通日程" }
        composeRule.waitUntil(5_000) {
            viewModel.uiState.value.events.any { it.event.id.value == created.id.value }
        }
        assertEquals("标签不改变来源（AC-S-04）", ScheduleSource.REGULAR, created.source)
        assertEquals(ScheduleG2TestSupport.COURSE_TAG_ID, created.tagId?.value)

        // 来源筛选按来源而非标签：课程表来源仍为空态。
        composeRule.onNodeWithTag("schedule_source_course").performClick()
        composeRule.onNodeWithTag("schedule_empty_state").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_source_regular").performClick()
        composeRule.onNodeWithTag("schedule_time_grid")
            .performScrollToNode(hasTestTag("schedule_event_${created.id.value}"))
        composeRule.onNodeWithTag("schedule_event_${created.id.value}").assertIsDisplayed()
        composeRule.onAllNodesWithText("标签筛选").assertCountEquals(0)
    }
}

/** 与 MainActivity 同构的最小装配：AppRoot + 真实覆盖层宿主。 */
@Composable
private fun G3Harness(
    viewModel: ScheduleViewModel,
    overlays: OverlayBackStack,
    navigation: NavigationCoordinator,
) {
    NautileTheme {
        val state by viewModel.uiState.collectAsState()
        val editor by viewModel.editor.collectAsState()
        AppRoot(
            navigation = navigation,
            overlays = overlays,
            tabContent = { tab ->
                if (tab == AppTab.SCHEDULE) {
                    ScheduleScreen(
                        state = state,
                        onDateSelected = viewModel::onDateSelected,
                        onRangeStep = viewModel::onRangeStep,
                        onToday = viewModel::onToday,
                        onViewModeSelected = viewModel::onViewModeSelected,
                        onSourceFilterSelected = viewModel::onSourceFilterSelected,
                        onOpenNew = {
                            viewModel.onOpenNew()
                            val opened = overlays.open(
                                Overlay.NewSchedule,
                                canDismiss = { viewModel.dismissEditor() },
                            )
                            if (!opened) viewModel.closeEditor()
                        },
                        onOpenDetail = { id ->
                            viewModel.onOpenDetail(id)
                            val opened = overlays.open(
                                Overlay.ScheduleDetail(id.value),
                                canDismiss = { viewModel.dismissEditor() },
                            )
                            if (!opened) viewModel.closeEditor()
                        },
                        onDismissMessage = viewModel::onDismissMessage,
                    )
                } else {
                    Text("其他页")
                }
            },
            fullScreenContent = {},
            overlayContent = {
                ScheduleOverlayHost(
                    viewModel = viewModel,
                    editor = editor,
                    onOverlayClosed = { overlays.close() },
                )
            },
        )
    }
}
