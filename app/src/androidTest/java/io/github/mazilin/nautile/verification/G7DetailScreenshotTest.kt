package io.github.mazilin.nautile.verification

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.ui.screen.todo.TaskDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.TaskDetailUiState
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G7 证据截图（不提交）：详情属性工具栏初始、优先级菜单、日期面板、详情菜单、摘要态。
 *
 * 使用 Compose 测试宿主渲染 [TaskDetailSheet]；截图写入应用 external files 目录，
 * 再由 adb pull 到 `docs/验证记录/门禁/`。截图只作视觉核对，不替代断言。
 */
@RunWith(AndroidJUnit4::class)
class G7DetailScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-25T09:00:00Z")

    private fun task(
        title: String = "概率论刷题",
        position: GtdPosition = GtdPosition.INBOX,
        priority: Priority = Priority.NONE,
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
        detail: String? = null,
    ) = Task(
        id = TaskId(1L),
        title = title,
        position = position,
        priority = priority,
        dueDate = dueDate,
        dueTime = dueTime,
        createdAt = now,
        updatedAt = now,
        detail = detail,
    )

    private fun setSheet(state: TaskDetailUiState) {
        composeRule.setContent {
            NautileTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TaskDetailSheet(
                        state = state,
                        onTitleChanged = {},
                        onCommitTitle = {},
                        onReopen = {},
                        onCloseRequested = {},
                        visible = true,
                    )
                }
            }
        }
    }

    private fun state(
        task: Task,
        projectName: String? = null,
    ) = TaskDetailUiState(
        task = task,
        titleDraft = null,
        titleInvalid = false,
        saving = false,
        error = null,
        leaveBlocked = false,
        canReopen = false,
        projectName = projectName,
    )

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "g7_$name.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("截图应写入且有内容：${file.absolutePath}", file.length() > 0L)
    }

    @Test
    fun G7_screenshot_toolbar_initial() {
        setSheet(state(task()))
        capture("detail_toolbar_initial")
    }

    @Test
    fun G7_screenshot_priority_menu() {
        setSheet(state(task()))
        composeRule.onNodeWithContentDescription("设置优先级，当前：无").performClick()
        capture("detail_priority_menu")
    }

    @Test
    fun G7_screenshot_date_panel() {
        setSheet(state(task()))
        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置").performClick()
        capture("detail_date_panel")
    }

    @Test
    fun G7_screenshot_detail_inline_field() {
        setSheet(state(task(detail = "先看条件概率，再做习题 3-5")))
        capture("detail_inline_field")
    }

    @Test
    fun G7_screenshot_summary_state() {
        setSheet(
            state(
                task(
                    position = GtdPosition.WAITING,
                    priority = Priority.HIGH,
                    dueDate = LocalDate.now(),
                    dueTime = LocalTime.of(9, 30),
                    detail = "先看条件概率，再做习题 3-5",
                ),
                projectName = "课程项目",
            ),
        )
        capture("detail_summary_state")
    }
}
