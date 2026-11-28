package io.github.mazilin.nautile.verification

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleViewModel
import java.time.LocalDate
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R6-G2-V2 · 模拟器截图（浅/深色 × 日/三天/周 + 来源筛选空态）。
 *
 * 仅作人工视觉核对材料，不替代 connected 断言，也不代表 200% 字体或真机结论。
 * 输出到设备 Download/nautile_evidence，再 adb pull 到 `证据材料/原始截图/R6-G2/`（不入库）。
 */
@RunWith(AndroidJUnit4::class)
class G2ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today: LocalDate = LocalDate.of(2026, 11, 25)

    private lateinit var database: NautileDatabase
    private lateinit var viewModel: ScheduleViewModel

    @Before
    fun setUp() {
        database = ScheduleG2TestSupport.createDatabase()
        ScheduleG2TestSupport.seedCourseTag(database)
        ScheduleG2TestSupport.seed(
            database,
            listOf(
                ScheduleG2TestSupport.event(1L, "概率论", today, 8, 9),
                ScheduleG2TestSupport.event(
                    id = 2L,
                    title = "小组会议",
                    date = today,
                    startHour = 13,
                    endHour = 14,
                    tagId = ScheduleG2TestSupport.COURSE_TAG_ID,
                ),
                ScheduleG2TestSupport.event(3L, "实验报告", today.plusDays(1), 10, 11),
                ScheduleG2TestSupport.event(4L, "英语听力", today.plusDays(2), 15, 16),
            ),
        )
        viewModel = ScheduleG2TestSupport.newViewModel(database, today)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        database.close()
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/nautile_evidence",
            )
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        requireNotNull(uri) { "无法创建截图输出：$name" }
        resolver.openOutputStream(uri)!!.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("截图应写入：$uri", resolver.openInputStream(uri) != null)
    }

    private fun switchView(label: String) {
        composeRule.onNodeWithTag("schedule_view_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(label).performClick()
        composeRule.waitForIdle()
    }

    private fun captureSet(prefix: String, dark: Boolean) {
        composeRule.setContent { ScheduleG2Screen(viewModel, dark = dark) }
        composeRule.waitUntil(5_000) { !viewModel.uiState.value.isLoading }
        composeRule.waitForIdle()

        capture("${prefix}_day")
        switchView("三天")
        capture("${prefix}_three_day")
        switchView("周")
        capture("${prefix}_week")
        composeRule.onNodeWithTag("schedule_source_course").performClick()
        composeRule.waitForIdle()
        capture("${prefix}_course_empty")
    }

    @Test
    fun g2_light_screenshots_day_threeDay_week_and_courseEmpty() {
        captureSet(prefix = "g2_light", dark = false)
    }

    @Test
    fun g2_dark_screenshots_day_threeDay_week_and_courseEmpty() {
        captureSet(prefix = "g2_dark", dark = true)
    }
}
