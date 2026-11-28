package io.github.mazilin.nautile.verification

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.repository.RoomTimeCategoryRepository
import io.github.mazilin.nautile.data.repository.RoomTimeLogRepository
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.domain.usecase.TimeLogCommands
import io.github.mazilin.nautile.ui.screen.log.LogOverlayHost
import io.github.mazilin.nautile.ui.screen.log.LogScreen
import io.github.mazilin.nautile.ui.screen.log.LogViewModel
import io.github.mazilin.nautile.ui.theme.NautileTheme
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
 * R6-G4-V2 · AC-L-02 补录/编辑记录（自动 UI）。
 *
 * 夹具：今天 09:00—10:00 一条已完成记录；补录昨天 14:00—15:00，改分类与起止。
 * 预期：倒序位置正确、不产生重复记录、分类型保留。
 *
 * 说明：日期面板通过 UI 打开与关闭（验证入口与返回），具体日期值经 `onEditorDateSelected`
 * 写入——该回调即日期面板确认时调用的同一入口；Material3 日历的逐格点选由既有 G3 用例覆盖。
 */
@RunWith(AndroidJUnit4::class)
class AcLogL02UiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: io.github.mazilin.nautile.data.db.NautileDatabase
    private lateinit var logRepository: RoomTimeLogRepository
    private lateinit var categoryRepository: RoomTimeCategoryRepository
    private lateinit var clock: G4Clock
    private lateinit var viewModel: LogViewModel

    private var study: TimeCategoryId = TimeCategoryId(0)
    private var work: TimeCategoryId = TimeCategoryId(0)

    @Before
    fun setUp() {
        database = LogG4.freshDatabase(context)
        logRepository = RoomTimeLogRepository(database)
        categoryRepository = RoomTimeCategoryRepository(database)
        clock = G4Clock()
        val commands = TimeLogCommands(logRepository, categoryRepository, clock)
        runBlocking {
            study = LogG4.seedCategory(categoryRepository, "学习")
            work = LogG4.seedCategory(categoryRepository, "工作")
            LogG4.seedLog(
                logRepository,
                work,
                LogG4.instant(LogG4.today, 9),
                LogG4.instant(LogG4.today, 10),
            )
        }
        viewModel = LogViewModel(
            logRepository = logRepository,
            categoryRepository = categoryRepository,
            settingsRepository = G4FakeSettingsRepository(),
            commands = commands,
            clock = clock,
            zoneId = LogG4.zone,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Composable
    private fun Host() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val editor by viewModel.editor.collectAsStateWithLifecycle()
        val detail by viewModel.detail.collectAsStateWithLifecycle()
        var overlay by remember { mutableStateOf<String?>(null) }

        NautileTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LogScreen(
                        state = state,
                        onCategoryTap = viewModel::onCategoryQuickTap,
                        onShowAllCategories = { overlay = "picker" },
                        onOpenLog = { id ->
                            viewModel.onOpenLog(id)
                            overlay = "detail"
                        },
                        onOpenManual = {
                            viewModel.onOpenManual()
                            overlay = "editor"
                        },
                        onDismissMessage = viewModel::onDismissMessage,
                    )
                    LogOverlayHost(
                        viewModel = viewModel,
                        editor = if (overlay == "editor") editor else null,
                        detail = if (overlay == "detail") detail else null,
                        uiState = state,
                        pickerOpen = overlay == "picker",
                        onCategoryPicked = { id -> viewModel.onCategoryQuickTap(id) },
                        onPickerClosed = { overlay = null },
                        onOverlayClosed = { overlay = null },
                    )
                }
            }
        }
    }

    private fun awaitTag(tag: String, present: Boolean = true) {
        composeRule.waitUntil(5_000) {
            val exists = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            exists == present
        }
        composeRule.waitForIdle()
    }

    private fun setTimeRow(rowTag: String, value: String) {
        composeRule.onNodeWithTag(rowTag).performClick()
        awaitTag("log_time_panel")
        composeRule.onNodeWithTag("log_time_input").performTextReplacement(value)
        composeRule.onNodeWithTag("log_time_confirm").performClick()
        awaitTag("log_time_panel", present = false)
    }

    @Test
    fun AC_L_02_backfillYesterday_thenEditCategoryAndRange() {
        composeRule.setContent { Host() }
        awaitTag("log_record_1")

        // 打开补录面板。
        composeRule.onNodeWithTag("add_log").performClick()
        awaitTag("log_editor_sheet")

        // 分类：两级选择器点父级“学习”。
        composeRule.onNodeWithTag("log_editor_category").performClick()
        awaitTag("category_picker")
        composeRule.onNodeWithTag("category_parent_${study.value}").performClick()
        awaitTag("category_picker", present = false)
        composeRule.onAllNodesWithText("学习").onFirst().assertIsDisplayed()

        // 日期：面板可打开、可返回；确认回调写入昨天。
        composeRule.onNodeWithTag("log_editor_date").performClick()
        awaitTag("log_date_panel")
        composeRule.onNodeWithTag("log_date_back").performClick()
        awaitTag("log_date_panel", present = false)
        composeRule.runOnIdle { viewModel.onEditorDateSelected(LogG4.today.minusDays(1)) }
        composeRule.onNodeWithText("11月27日").assertIsDisplayed()

        // 起止：14:00—15:00（严格 HH:mm 直输）。
        setTimeRow("log_editor_start_time", "14:00")
        setTimeRow("log_editor_end_time", "15:00")

        composeRule.onNodeWithTag("log_editor_save").performClick()
        awaitTag("log_editor_sheet", present = false)
        awaitTag("log_record_2")

        // 倒序位置：今天 09:00 记录在上，昨天 14:00 记录在下。
        composeRule.onNodeWithText("今天").assertIsDisplayed()
        composeRule.onNodeWithText("昨天").assertIsDisplayed()
        val todayTop = composeRule.onNodeWithTag("log_record_1").fetchSemanticsNode().boundsInRoot.top
        val yesterdayTop = composeRule.onNodeWithTag("log_record_2").fetchSemanticsNode().boundsInRoot.top
        assertTrue("今天记录应位于昨天记录上方", todayTop < yesterdayTop)
        composeRule.onNodeWithTag("log_record_2")
            .assertIsDisplayed()

        // 不产生重复记录；分类型保留。
        val allLogs = runBlocking { logRepository.observeAllDescending().first() }
        assertEquals("补录后应为 2 条记录（不产生重复）", 2, allLogs.size)
        assertEquals(
            "补录归属所选分类",
            study,
            runBlocking { logRepository.get(TimeLogId(2)) }?.categoryId,
        )

        // 编辑：改分类为“工作”、起止改为 13:00—16:00。
        composeRule.onNodeWithTag("log_record_2").performClick()
        awaitTag("log_detail_sheet")
        composeRule.onNodeWithTag("log_detail_category").performClick()
        awaitTag("category_picker")
        composeRule.onNodeWithTag("category_parent_${work.value}").performClick()
        awaitTag("category_picker", present = false)
        setTimeRow("log_detail_start_time", "13:00")
        setTimeRow("log_detail_end_time", "16:00")

        composeRule.waitUntil(5_000) {
            val log = runBlocking { logRepository.get(TimeLogId(2)) }
            log?.categoryId == work &&
                log.endAt == LogG4.instant(LogG4.today.minusDays(1), 16) &&
                log.startAt == LogG4.instant(LogG4.today.minusDays(1), 13)
        }
        val edited = runBlocking { logRepository.get(TimeLogId(2)) }
        assertNotNull(edited)
        assertEquals(
            "改分类后仍只有一条记录",
            2,
            runBlocking { logRepository.observeAllDescending().first().size },
        )
        assertEquals(work, edited?.categoryId)
    }
}
