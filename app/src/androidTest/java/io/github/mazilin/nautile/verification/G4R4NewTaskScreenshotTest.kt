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
import io.github.mazilin.nautile.ui.screen.todo.NewTaskKind
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
 * R4 G4 证据截图（不提交）：工具栏未设置态、设置摘要态、清单菜单、项目创建态。
 *
 * 使用 Compose 测试宿主渲染 [NewTaskSheet]；截图写入应用 external files 目录，
 * 再由 adb pull 到 `docs/验证记录/门禁/`。截图只作视觉核对，不替代断言。
 */
@RunWith(AndroidJUnit4::class)
class G4R4NewTaskScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-26T09:00:00Z")

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
        val file = File(dir, "g4r4_$name.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("截图应写入且有内容：${file.absolutePath}", file.length() > 0L)
    }

    @Test
    fun G4R4_screenshot_toolbar_unset() {
        setSheet(NewTaskUiState(position = GtdPosition.INBOX, targetPosition = GtdPosition.INBOX))
        capture("toolbar_unset")
    }

    @Test
    fun G4R4_screenshot_toolbar_summary() {
        setSheet(
            NewTaskUiState(
                position = GtdPosition.INBOX,
                targetPosition = GtdPosition.WAITING,
                title = "复习概率论",
                detail = "第三章习题",
                priority = Priority.HIGH,
                dueDate = LocalDate.now(),
                dueTime = LocalTime.of(9, 30),
                projectId = ProjectId(7L),
            ),
            projects = projects,
        )
        capture("toolbar_summary")
    }

    @Test
    fun G4R4_screenshot_checklist_menu() {
        setSheet(NewTaskUiState(position = GtdPosition.INBOX, targetPosition = GtdPosition.INBOX))
        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：收件箱").performClick()
        capture("checklist_menu")
    }

    @Test
    fun G4R4_screenshot_project_create() {
        setSheet(
            NewTaskUiState(
                kind = NewTaskKind.PROJECT,
                position = GtdPosition.INBOX,
                projectName = "研究项目",
            ),
        )
        capture("project_create")
    }
}
