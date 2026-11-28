package io.github.mazilin.nautile.verification

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.ui.screen.schedule.NewScheduleSheet
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleDateTimePanel
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleDetailSheet
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleEditorField
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleEditorMode
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleEditorUiState
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R6-G3-V2 证据截图（不提交）：新建/详情浅深色、日期面板、24h 圆盘、无效字段、删除确认。
 *
 * 截图写入应用 external files 目录，再由 adb pull 到 `证据材料/待整理/R6-G3/`。
 * 截图只作视觉核对，不替代断言；判定见 G3-V2 人工清单。
 */
@RunWith(AndroidJUnit4::class)
class R6G3ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today: LocalDate = LocalDate.of(2026, 11, 25)
    private val courseTagId = ScheduleTagId(ScheduleG2TestSupport.COURSE_TAG_ID)

    private fun createState(title: String = "概率论", saveError: String? = null) = ScheduleEditorUiState(
        mode = ScheduleEditorMode.CREATE,
        scheduleId = null,
        title = title,
        note = "",
        startDate = today,
        startTime = LocalTime.of(9, 0),
        endDate = today,
        endTime = LocalTime.of(10, 30),
        source = ScheduleSource.MANUAL,
        tagId = null,
        courseTagId = courseTagId,
        saveError = saveError,
    )

    private fun editState() = ScheduleEditorUiState(
        mode = ScheduleEditorMode.EDIT,
        scheduleId = ScheduleId(1L),
        title = "概率论",
        note = "复习第三章",
        startDate = today,
        startTime = LocalTime.of(9, 0),
        endDate = today,
        endTime = LocalTime.of(10, 30),
        source = ScheduleSource.MANUAL,
        tagId = null,
        courseTagId = courseTagId,
    )

    private fun capture(name: String) {
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        composeRule.waitForIdle()
        // 等待窗口真正呈现一帧，避免首个用例截到未绘制的空背景。
        Thread.sleep(400)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // 全屏截图：包含 32% 遮罩与对话框窗口，便于人工核对浅/深色。
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: error("takeScreenshot 返回 null（$name）")
        val context = instrumentation.targetContext
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "g3_$name.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("截图应写入且有内容：${file.absolutePath}", file.length() > 0L)
    }

    /** 确认目标面板已完成入场（避免截到空背景）。 */
    private fun awaitTag(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun setNewSheet(state: ScheduleEditorUiState, dark: Boolean) {
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NewScheduleSheet(
                        state = state,
                        onTitleChanged = {},
                        onCommitTitle = {},
                        onNoteChanged = {},
                        onCommitNote = {},
                        onStartDateSelected = {},
                        onEndDateSelected = {},
                        onTimeConfirmed = { _, _ -> },
                        onTimeCleared = {},
                        onCourseTagSelected = {},
                        onNoTagSelected = {},
                        onCreate = {},
                        onCancel = {},
                        onDismissRequest = {},
                        canDismiss = { true },
                    )
                }
            }
        }
    }

    private fun setDetailSheet(state: ScheduleEditorUiState, dark: Boolean) {
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScheduleDetailSheet(
                        state = state,
                        onTitleChanged = {},
                        onCommitTitle = {},
                        onNoteChanged = {},
                        onCommitNote = {},
                        onStartDateSelected = {},
                        onEndDateSelected = {},
                        onTimeConfirmed = { _, _ -> },
                        onTimeCleared = {},
                        onCourseTagSelected = {},
                        onNoTagSelected = {},
                        onCloseRequested = {},
                        onDelete = {},
                        onDismissRequest = {},
                        canDismiss = { true },
                    )
                }
            }
        }
    }

    @Test
    fun g3_screenshots_light() {
        setNewSheet(createState(), dark = false)
        awaitTag("new_schedule_sheet")
        capture("new_light")
    }

    @Test
    fun g3_screenshots_dark() {
        setNewSheet(createState(), dark = true)
        awaitTag("new_schedule_sheet")
        capture("new_dark")
    }

    @Test
    fun g3_screenshot_detail_light() {
        setDetailSheet(editState(), dark = false)
        awaitTag("schedule_detail_sheet")
        capture("detail_light")
    }

    @Test
    fun g3_screenshot_detail_dark() {
        setDetailSheet(editState(), dark = true)
        awaitTag("schedule_detail_sheet")
        capture("detail_dark")
    }

    @Test
    fun g3_screenshot_date_panel_light_and_dark() {
        setPanel(startWithTime = false, dark = false)
        awaitTag("schedule_date_picker")
        capture("date_panel_light")
    }

    @Test
    fun g3_screenshot_date_panel_dark() {
        setPanel(startWithTime = false, dark = true)
        awaitTag("schedule_date_picker")
        capture("date_panel_dark")
    }

    @Test
    fun g3_screenshot_time_dial_light() {
        setPanel(startWithTime = true, dark = false)
        awaitTag("schedule_time_picker")
        capture("time_dial_light")
    }

    @Test
    fun g3_screenshot_time_dial_dark() {
        setPanel(startWithTime = true, dark = true)
        awaitTag("schedule_time_picker")
        capture("time_dial_dark")
    }

    @Test
    fun g3_screenshot_invalid_light() {
        setNewSheet(createState(title = "   "), dark = false)
        awaitTag("new_schedule_sheet")
        capture("invalid_title_light")
    }

    @Test
    fun g3_screenshot_invalid_dark() {
        setNewSheet(createState(title = "   "), dark = true)
        awaitTag("new_schedule_sheet")
        capture("invalid_title_dark")
    }

    @Test
    fun g3_screenshot_delete_confirm_light() {
        setDetailSheet(editState(), dark = false)
        awaitTag("schedule_detail_sheet")
        composeRule.onNodeWithTag("schedule_delete").performClick()
        composeRule.onNodeWithText("删除后不可恢复").assertIsDisplayed()
        capture("delete_confirm_light")
    }

    @Test
    fun g3_screenshot_delete_confirm_dark() {
        setDetailSheet(editState(), dark = true)
        awaitTag("schedule_detail_sheet")
        composeRule.onNodeWithTag("schedule_delete").performClick()
        composeRule.onNodeWithText("删除后不可恢复").assertIsDisplayed()
        capture("delete_confirm_dark")
    }

    private fun setPanel(startWithTime: Boolean, dark: Boolean) {
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(24.dp).testTag("g3_panel_host")) {
                        ScheduleDateTimePanel(
                            field = ScheduleEditorField.START,
                            date = today,
                            time = LocalTime.of(9, 0),
                            startWithTime = startWithTime,
                            onDateSelected = {},
                            onTimeConfirmed = {},
                            onTimeCleared = {},
                            onBackToForm = {},
                        )
                    }
                }
            }
        }
    }
}
