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
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.ui.screen.todo.NewTaskSheet
import io.github.mazilin.nautile.ui.screen.todo.NewTaskUiState
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
 * G6 证据截图（不提交）：工具栏初始、优先级菜单、日期面板、项目菜单、摘要态。
 *
 * 使用 Compose 测试宿主渲染 [NewTaskSheet]；截图写入应用 external files 目录，
 * 再由 adb pull 到 `docs/验证记录/门禁/`。截图只作视觉核对，不替代断言。
 */
@RunWith(AndroidJUnit4::class)
class G6NewTaskScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-25T09:00:00Z")

    private val projects = listOf(
        Project(
            id = ProjectId(7L),
            name = "课程项目",
            status = ProjectStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        ),
    )

    private fun setSheet(state: NewTaskUiState, projects: List<Project> = emptyList()) {
        composeRule.setContent {
            NautileTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NewTaskSheet(state = state, projects = projects)
                }
            }
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "g6_$name.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("截图应写入且有内容：${file.absolutePath}", file.length() > 0L)
    }

    @Test
    fun G6_screenshot_toolbar_initial() {
        setSheet(NewTaskUiState(position = GtdPosition.INBOX, targetPosition = GtdPosition.INBOX))
        capture("toolbar_initial")
    }

    @Test
    fun G6_screenshot_priority_menu() {
        setSheet(NewTaskUiState(position = GtdPosition.INBOX, targetPosition = GtdPosition.INBOX))
        composeRule.onNodeWithContentDescription("设置优先级，当前：无").performClick()
        capture("priority_menu")
    }

    @Test
    fun G6_screenshot_date_panel() {
        setSheet(NewTaskUiState(position = GtdPosition.INBOX, targetPosition = GtdPosition.INBOX))
        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置").performClick()
        capture("date_panel")
    }

    @Test
    fun G6_screenshot_project_menu() {
        setSheet(
            NewTaskUiState(position = GtdPosition.INBOX, targetPosition = GtdPosition.INBOX),
            projects = projects,
        )
        composeRule.onNodeWithContentDescription("设置项目，当前：无项目").performClick()
        capture("project_menu")
    }

    @Test
    fun G6_screenshot_summary_state() {
        setSheet(
            NewTaskUiState(
                position = GtdPosition.INBOX,
                targetPosition = GtdPosition.WAITING,
                title = "买牛奶",
                detail = "顺便买面包",
                priority = Priority.HIGH,
                dueDate = LocalDate.now(),
                dueTime = LocalTime.of(9, 30),
                projectId = ProjectId(7L),
            ),
            projects = projects,
        )
        capture("summary_state")
    }
}
