package io.github.mazilin.nautile.verification

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.ui.screen.todo.ProjectEditSheet
import io.github.mazilin.nautile.ui.screen.todo.ProjectEditUiState
import io.github.mazilin.nautile.ui.screen.todo.TaskEditSheet
import io.github.mazilin.nautile.ui.screen.todo.TaskEditUiState
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
 * R4 G5 证据截图（不提交）：任务编辑面板、项目编辑面板。
 *
 * 使用 Compose 测试宿主渲染 [TaskEditSheet] / [ProjectEditSheet]；截图写入应用 external files 目录，
 * 再由 adb pull 到 `docs/验证记录/门禁/`。截图只作视觉核对，不替代断言。
 */
@RunWith(AndroidJUnit4::class)
class G5R4EditScreenshotTest {

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

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "g5r4_$name.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("截图应写入且有内容：${file.absolutePath}", file.length() > 0L)
    }

    @Test
    fun G5R4_screenshot_task_edit() {
        composeRule.setContent {
            NautileTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TaskEditSheet(
                        state = TaskEditUiState(
                            taskId = TaskId(1L),
                            title = "概率论刷题",
                            detail = "第三章习题 1-5",
                            priority = Priority.HIGH,
                            dueDate = LocalDate.now(),
                            dueTime = LocalTime.of(9, 30),
                            projectId = ProjectId(7L),
                            projectName = "课程项目",
                            position = GtdPosition.WAITING,
                        ),
                        projects = projects,
                    )
                }
            }
        }
        capture("task_edit")
    }

    @Test
    fun G5R4_screenshot_project_edit() {
        composeRule.setContent {
            NautileTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ProjectEditSheet(
                        state = ProjectEditUiState(
                            projectId = ProjectId(7L),
                            name = "课程项目",
                        ),
                    )
                }
            }
        }
        capture("project_edit")
    }
}
