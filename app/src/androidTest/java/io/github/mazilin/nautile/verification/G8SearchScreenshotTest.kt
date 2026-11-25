package io.github.mazilin.nautile.verification

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.rule.TaskGrouping
import io.github.mazilin.nautile.domain.rule.TaskSearch
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoUiState
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.io.File
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G8 证据截图（不提交）：搜索输入过滤态与无结果态。
 *
 * 用 Compose 测试宿主渲染 [TodoScreen]；截图写入应用 external files 目录，
 * 再由 adb pull 到 `docs/验证记录/门禁/`。截图只作视觉核对，不替代断言。
 */
@RunWith(AndroidJUnit4::class)
class G8SearchScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-25T12:00:00Z")

    private fun project(id: Long, name: String) = Project(
        id = ProjectId(id),
        name = name,
        createdAt = now,
        updatedAt = now,
    )

    private fun task(id: Long, title: String, projectId: ProjectId? = null) = Task(
        id = TaskId(id),
        title = title,
        projectId = projectId,
        position = GtdPosition.INBOX,
        createdAt = now,
        updatedAt = now,
    )

    private fun setScreen(state: TodoUiState) {
        composeRule.setContent {
            NautileTheme {
                TodoScreen(state = state)
            }
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "g8_$name.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("截图应写入且有内容：${file.absolutePath}", file.length() > 0L)
    }

    @Test
    fun G8_screenshot_search_active_filtered() {
        val course = project(1L, "课程项目")
        val tasks = listOf(
            task(1L, "概率论刷题"),
            task(2L, "概率论作业", course.id),
            task(3L, "买牛奶"),
        )
        val query = "概率"
        setScreen(
            TodoUiState(
                position = GtdPosition.INBOX,
                groups = TaskGrouping.byProject(
                    TaskSearch.filter(tasks, listOf(course), query),
                    listOf(course),
                ),
                visibleTasks = tasks,
                searchAvailable = true,
                searchActive = true,
                searchQuery = query,
            ),
        )
        capture("search_active")
    }

    @Test
    fun G8_screenshot_search_no_result() {
        val tasks = listOf(task(1L, "概率论刷题"), task(2L, "买牛奶"))
        setScreen(
            TodoUiState(
                position = GtdPosition.INBOX,
                groups = emptyList(),
                visibleTasks = tasks,
                searchAvailable = true,
                searchActive = true,
                searchQuery = "不存在的任务",
            ),
        )
        capture("search_no_result")
    }
}
