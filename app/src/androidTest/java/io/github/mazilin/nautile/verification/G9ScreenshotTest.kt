package io.github.mazilin.nautile.verification

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.model.ViewClassification
import io.github.mazilin.nautile.domain.model.ViewOptions
import io.github.mazilin.nautile.domain.model.ViewSort
import io.github.mazilin.nautile.domain.rule.TaskGrouping
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoUiState
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G9 证据截图（不提交）：视图菜单、优先级分组、截止日期分组、手动拖拽中间态。
 *
 * 用 Compose 测试宿主渲染 [TodoScreen]；截图写入应用 external files 目录，
 * 再由 adb pull 到 `docs/验证记录/门禁/`。截图只作视觉核对，不替代断言。
 */
@RunWith(AndroidJUnit4::class)
class G9ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-25T12:00:00Z")

    private fun project(id: Long, name: String) = Project(
        id = ProjectId(id),
        name = name,
        createdAt = now,
        updatedAt = now,
    )

    private fun task(
        id: Long,
        title: String,
        projectId: ProjectId? = null,
        priority: Priority = Priority.NONE,
        dueDate: LocalDate? = null,
        createdSeconds: Long = id,
    ) = Task(
        id = TaskId(id),
        title = title,
        projectId = projectId,
        position = GtdPosition.INBOX,
        priority = priority,
        dueDate = dueDate,
        createdAt = now.plusSeconds(createdSeconds),
        updatedAt = now,
    )

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // 写入公共下载目录（MediaStore）：应用卸载后仍保留，便于 adb pull 到证据目录。
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "g9_$name.png")
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

    private fun state(
        groups: List<io.github.mazilin.nautile.domain.rule.TaskGroup>,
        options: ViewOptions = ViewOptions.Default,
    ) = TodoUiState(
        position = GtdPosition.INBOX,
        groups = groups,
        visibleTasks = groups.flatMap { it.tasks },
        searchAvailable = true,
        viewOptionsAvailable = true,
        viewOptions = options,
    )

    @Test
    fun G9_screenshot_view_menu() {
        val course = project(1L, "课程项目")
        val tasks = listOf(
            task(1L, "概率论刷题", course.id),
            task(2L, "买牛奶"),
        )
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = state(
                        TaskGrouping.byView(
                            tasks, listOf(course),
                            ViewOptions(ViewClassification.PRIORITY, ViewSort.DUE),
                            includeEmptyProjects = true,
                        ),
                        options = ViewOptions(ViewClassification.PRIORITY, ViewSort.DUE),
                    ),
                )
            }
        }
        composeRule.onNodeWithContentDescription("视图选项").performClick()
        composeRule.onNodeWithText("分类").assertExists()
        capture("view_menu")
    }

    @Test
    fun G9_screenshot_priority_groups() {
        val tasks = listOf(
            task(1L, "高优先级任务A", priority = Priority.HIGH, dueDate = LocalDate.parse("2026-12-01")),
            task(2L, "高优先级任务B", priority = Priority.HIGH, dueDate = LocalDate.parse("2026-12-03")),
            task(3L, "中优先级任务", priority = Priority.MEDIUM),
            task(4L, "无优先级任务急", priority = Priority.NONE),
        )
        val options = ViewOptions(ViewClassification.PRIORITY, ViewSort.DUE)
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = state(
                        TaskGrouping.byView(tasks, emptyList(), options, includeEmptyProjects = false),
                        options,
                    ),
                )
            }
        }
        composeRule.onNodeWithText("高优先级").assertExists()
        capture("priority_groups")
    }

    @Test
    fun G9_screenshot_due_groups() {
        val tasks = listOf(
            task(1L, "今天任务", dueDate = LocalDate.parse("2026-12-01")),
            task(2L, "稍后任务", dueDate = LocalDate.parse("2026-12-05")),
            task(3L, "无日期任务"),
            task(4L, "同日任务", dueDate = LocalDate.parse("2026-12-05")),
        )
        val options = ViewOptions(ViewClassification.DUE, ViewSort.PRIORITY)
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = state(
                        TaskGrouping.byView(tasks, emptyList(), options, includeEmptyProjects = false),
                        options,
                    ),
                )
            }
        }
        composeRule.onNodeWithText("无截止日期").assertExists()
        capture("due_groups")
    }

    @Test
    fun G9_screenshot_manual_drag_mid_state() {
        val tasks = listOf(
            task(1L, "拖拽任务一", createdSeconds = 1),
            task(2L, "拖拽任务二", createdSeconds = 2),
            task(3L, "拖拽任务三", createdSeconds = 3),
        )
        val options = ViewOptions(ViewClassification.PROJECT, ViewSort.MANUAL)
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = state(
                        TaskGrouping.byView(tasks, emptyList(), options, includeEmptyProjects = false),
                        options = options,
                    ),
                    onReorderGroup = { _, _ -> },
                )
            }
        }
        // 默认手动序为创建时间倒序：任务三在最上；向下拖过一行与任务二交换。
        composeRule.onNodeWithText("拖拽任务三").assertExists()
        val rowPx = with(composeRule.density) { 64.dp.toPx() }
        composeRule.onNodeWithText("拖拽任务三").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, rowPx * 1.2f))
            advanceEventTime(120)
        }
        composeRule.waitUntil(5_000) {
            val secondTop = composeRule.onNodeWithText("拖拽任务二")
                .fetchSemanticsNode().boundsInRoot.top
            val thirdTop = composeRule.onNodeWithText("拖拽任务三")
                .fetchSemanticsNode().boundsInRoot.top
            secondTop < thirdTop
        }
        capture("manual_drag_mid")
        composeRule.onNodeWithText("拖拽任务二").performTouchInput { up() }
    }
}
