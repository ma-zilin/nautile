package io.github.mazilin.nautile.verification

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.ui.screen.log.CategoryPickerState
import io.github.mazilin.nautile.ui.screen.log.LogDetailSheet
import io.github.mazilin.nautile.ui.screen.log.LogDetailUiState
import io.github.mazilin.nautile.ui.screen.log.LogEditorSheet
import io.github.mazilin.nautile.ui.screen.log.LogEditorUiState
import io.github.mazilin.nautile.ui.screen.log.LogScreen
import io.github.mazilin.nautile.ui.screen.log.LogUiState
import io.github.mazilin.nautile.ui.screen.log.RunningTimerBar
import io.github.mazilin.nautile.ui.screen.log.RunningTimerUi
import io.github.mazilin.nautile.ui.screen.log.TimeLogRow
import io.github.mazilin.nautile.ui.screen.settings.CategoryManagementRow
import io.github.mazilin.nautile.ui.screen.settings.CategoryManagementScreen
import io.github.mazilin.nautile.ui.screen.settings.CategoryManagementUiState
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.io.File
import java.time.Duration
import java.time.LocalTime
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R6-G4-V2 证据截图（不提交）：记录列表/分类快捷入口、补录与详情、计时条、分类管理，浅色与深色。
 *
 * 截图写入应用 external files 目录，由 V2 脚本 adb pull 到 `证据材料/待整理/R6-G4/`。
 * 截图只作视觉核对，不替代断言；判定见报告中的人工清单。
 */
@RunWith(AndroidJUnit4::class)
class R6G4LogScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val study = TimeCategory(
        id = TimeCategoryId(1),
        name = "学习",
        parentId = null,
        sortOrder = 0,
        createdAt = LogG4.now,
        updatedAt = LogG4.now,
    )

    private val work = TimeCategory(
        id = TimeCategoryId(2),
        name = "工作",
        parentId = null,
        sortOrder = 1,
        createdAt = LogG4.now,
        updatedAt = LogG4.now,
    )

    private val math = TimeCategory(
        id = TimeCategoryId(3),
        name = "概率论",
        parentId = TimeCategoryId(1),
        sortOrder = 0,
        createdAt = LogG4.now,
        updatedAt = LogG4.now,
    )

    private fun log(id: Long, categoryId: TimeCategoryId, dayOffset: Long, startHour: Int, endHour: Int): TimeLogRow {
        val start = LogG4.instant(LogG4.today.minusDays(dayOffset), startHour)
        val end = LogG4.instant(LogG4.today.minusDays(dayOffset), endHour)
        return TimeLogRow(
            log = TimeLog(
                id = TimeLogId(id),
                categoryId = categoryId,
                startAt = start,
                endAt = end,
                createdAt = start,
                updatedAt = start,
            ),
            categoryPath = if (categoryId == math.id) "学习 / 概率论" else "学习",
            duration = Duration.between(start, end),
            running = false,
        )
    }

    private fun recordState(): LogUiState {
        val runningLog = TimeLog(
            id = TimeLogId(9),
            categoryId = work.id,
            startAt = LogG4.now.minusSeconds(1_930),
            endAt = null,
            createdAt = LogG4.now.minusSeconds(1_930),
            updatedAt = LogG4.now.minusSeconds(1_930),
        )
        return LogUiState(
            today = LogG4.today,
            logs = listOf(
                log(1, study.id, 0, 9, 10),
                log(2, math.id, 0, 8, 9),
                log(3, work.id, 1, 14, 15),
                TimeLogRow(
                    log = runningLog,
                    categoryPath = "工作",
                    duration = Duration.ofSeconds(1_930),
                    running = true,
                ),
            ),
            categories = listOf(study, work, math),
            recentCategoryIds = listOf(study.id, work.id, math.id),
            running = listOf(
                RunningTimerUi(
                    logId = runningLog.id,
                    categoryId = work.id,
                    categoryName = "工作",
                    parentName = null,
                    startedAt = runningLog.startAt,
                    elapsed = Duration.ofSeconds(1_930),
                ),
            ),
            now = LogG4.now,
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
        val file = File(dir, "g4_$name.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        assertTrue("截图应写入且有内容：${file.absolutePath}", file.length() > 0L)
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun setLogScreen(dark: Boolean) {
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LogScreen(state = recordState(), modifier = Modifier.weight(1f))
                        RunningTimerBar(
                            running = recordState().running,
                            onTimerTap = {},
                            modifier = Modifier.testTag("screenshot_timer_bar"),
                        )
                    }
                }
            }
        }
        awaitTag("log_record_list")
    }

    @Test
    fun g4_log_records_and_quick_entry_light() {
        setLogScreen(dark = false)
        composeRule.onNodeWithTag("log_category_quick_1").assertIsDisplayed()
        capture("log_records_light")
    }

    @Test
    fun g4_log_records_and_quick_entry_dark() {
        setLogScreen(dark = true)
        composeRule.onNodeWithTag("log_category_quick_1").assertIsDisplayed()
        capture("log_records_dark")
    }

    /** 空历史（已有分类、尚无日志）：验证“全部分类”入口常驻（G4-DEF-01 修复证据）。 */
    private fun setEmptyHistory(dark: Boolean) {
        val emptyState = LogUiState(
            today = LogG4.today,
            logs = emptyList(),
            categories = listOf(study, work, math),
            recentCategoryIds = emptyList(),
            running = emptyList(),
            now = LogG4.now,
            zoneId = LogG4.zone,
        )
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LogScreen(state = emptyState)
                }
            }
        }
        awaitTag("log_empty_state")
        composeRule.onNodeWithTag("log_all_categories").assertIsDisplayed()
    }

    @Test
    fun g4_log_empty_history_light() {
        setEmptyHistory(dark = false)
        capture("log_empty_history_light")
    }

    @Test
    fun g4_log_empty_history_dark() {
        setEmptyHistory(dark = true)
        capture("log_empty_history_dark")
    }

    private fun setEditor(dark: Boolean) {
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LogEditorSheet(
                        state = LogEditorUiState(
                            categoryId = study.id,
                            startDate = LogG4.today.minusDays(1),
                            startTime = LocalTime.of(14, 0),
                            endTime = LocalTime.of(15, 0),
                        ),
                        pickerState = CategoryPickerState(
                            categories = listOf(study, work, math),
                            recentCategoryIds = listOf(study.id, work.id),
                            selectedId = study.id,
                        ),
                        onCategorySelected = {},
                        onDateSelected = {},
                        onTimeConfirmed = { _, _ -> },
                        onTimeCleared = {},
                        onSave = {},
                        onCancel = {},
                        onDismissRequest = {},
                        canDismiss = { true },
                    )
                }
            }
        }
        awaitTag("log_editor_sheet")
    }

    @Test
    fun g4_log_editor_light() {
        setEditor(dark = false)
        capture("editor_light")
    }

    @Test
    fun g4_log_editor_dark() {
        setEditor(dark = true)
        capture("editor_dark")
    }

    private fun setDetail(dark: Boolean) {
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LogDetailSheet(
                        state = LogDetailUiState(
                            logId = TimeLogId(1),
                            categoryId = math.id,
                            startDate = LogG4.today,
                            startTime = LocalTime.of(8, 0),
                            endDate = LogG4.today,
                            endTime = LocalTime.of(9, 0),
                            running = false,
                        ),
                        pickerState = CategoryPickerState(
                            categories = listOf(study, work, math),
                            recentCategoryIds = listOf(study.id),
                            selectedId = math.id,
                        ),
                        now = LogG4.now,
                        zoneId = LogG4.zone,
                        onCategorySelected = {},
                        onDateSelected = {},
                        onTimeConfirmed = { _, _ -> },
                        onTimeCleared = {},
                        onEditBlocked = {},
                        onDelete = {},
                        onCloseRequested = {},
                        onDismissRequest = {},
                        canDismiss = { true },
                    )
                }
            }
        }
        awaitTag("log_detail_sheet")
    }

    @Test
    fun g4_log_detail_light() {
        setDetail(dark = false)
        capture("detail_light")
    }

    @Test
    fun g4_log_detail_dark() {
        setDetail(dark = true)
        capture("detail_dark")
    }

    private fun setTimerBar(dark: Boolean) {
        val running = listOf(
            RunningTimerUi(TimeLogId(1), study.id, "学习", null, LogG4.now, Duration.ofSeconds(1_930)),
            RunningTimerUi(TimeLogId(2), work.id, "工作", null, LogG4.now, Duration.ofSeconds(640)),
            RunningTimerUi(TimeLogId(3), math.id, "概率论", "学习", LogG4.now, Duration.ofSeconds(125)),
        )
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                        RunningTimerBar(running = running, onTimerTap = {})
                        Text("导航占位（真实页面中计时条紧贴导航上方）", modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
        awaitTag("running_timer_1")
    }

    @Test
    fun g4_timer_bar_light() {
        setTimerBar(dark = false)
        capture("timer_bar_light")
    }

    @Test
    fun g4_timer_bar_dark() {
        setTimerBar(dark = true)
        capture("timer_bar_dark")
    }

    private fun setCategoryManagement(dark: Boolean) {
        val state = CategoryManagementUiState(
            isLoading = false,
            rows = listOf(
                CategoryManagementRow(study, depth = 0, childCount = 1, logCount = 3),
                CategoryManagementRow(math, depth = 1, childCount = 0, logCount = 1),
                CategoryManagementRow(work, depth = 0, childCount = 0, logCount = 0),
            ),
        )
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CategoryManagementScreen(
                        state = state,
                        onBack = {},
                        onCreateTopLevel = {},
                        onCreateChild = {},
                        onEdit = {},
                        onRequestDelete = {},
                        onNameChanged = {},
                        onSave = {},
                        onCloseEdit = {},
                        onConfirmDelete = {},
                        onDismissDeleteConfirm = {},
                        onDismissGuard = {},
                        onDismissMessage = {},
                    )
                }
            }
        }
        awaitTag("category_row_1")
    }

    @Test
    fun g4_category_management_light() {
        setCategoryManagement(dark = false)
        capture("category_management_light")
    }

    @Test
    fun g4_category_management_dark() {
        setCategoryManagement(dark = true)
        capture("category_management_dark")
    }
}
