package io.github.mazilin.nautile.verification

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
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
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.model.ViewClassification
import io.github.mazilin.nautile.domain.model.ViewOptions
import io.github.mazilin.nautile.domain.model.ViewSort
import io.github.mazilin.nautile.domain.rule.TaskGrouping
import io.github.mazilin.nautile.ui.screen.todo.GtdSelector
import io.github.mazilin.nautile.ui.screen.todo.NewTaskSheet
import io.github.mazilin.nautile.ui.screen.todo.NewTaskUiState
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoUiState
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G10 交付验收证据截图（不提交，由 adb pull 到 `docs/验证记录/门禁/`）。
 *
 * 用 Compose 测试宿主以固定夹具渲染真实页面组件：待办浅色（含空组与单独任务）、
 * 待办深色、快速添加工具栏与优先级菜单、全屏 GTD、搜索无结果、视图菜单、手动拖拽中间态。
 * 截图只作视觉核对，不替代 connected 断言；数据为夹具，不代表真实数据库状态。
 */
@RunWith(AndroidJUnit4::class)
class G10ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-25T12:00:00Z")

    private fun project(id: Long, name: String) = Project(
        id = ProjectId(id),
        name = name,
        status = ProjectStatus.ACTIVE,
        createdAt = now,
        updatedAt = now,
    )

    private fun task(
        id: Long,
        title: String,
        projectId: ProjectId? = null,
        priority: Priority = Priority.NONE,
        createdSeconds: Long = id,
    ) = Task(
        id = TaskId(id),
        title = title,
        projectId = projectId,
        position = GtdPosition.INBOX,
        priority = priority,
        createdAt = now.plusSeconds(createdSeconds),
        updatedAt = now,
    )

    private val course = project(1L, "课程项目")
    private val emptyProject = project(2L, "空项目")
    private val projects = listOf(course, emptyProject)
    private val tasks = listOf(
        task(1L, "概率论刷题", course.id),
        task(2L, "买牛奶"),
    )

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "g10_$name.png")
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
        searchActive: Boolean = false,
        searchQuery: String = "",
    ) = TodoUiState(
        position = GtdPosition.INBOX,
        groups = groups,
        visibleTasks = groups.flatMap { it.tasks },
        searchAvailable = true,
        viewOptionsAvailable = true,
        searchActive = searchActive,
        searchQuery = searchQuery,
        viewOptions = options,
    )

    private fun todoContent(dark: Boolean) {
        val options = ViewOptions(ViewClassification.PROJECT, ViewSort.MANUAL)
        val groups = TaskGrouping.byView(tasks, projects, options, includeEmptyProjects = true)
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                TodoScreen(state = state(groups, options))
            }
        }
    }

    @Test
    fun G10_screenshot_todo_light() {
        todoContent(dark = false)
        // 项目组头在合并语义中读作“名称（数量）”（A11Y-23）。
        composeRule.onNodeWithText("空项目（0）").assertExists()
        composeRule.onNodeWithText("单独任务").assertExists()
        capture("todo_light")
    }

    @Test
    fun G10_screenshot_todo_dark() {
        todoContent(dark = true)
        capture("todo_dark")
    }

    @Test
    fun G10_screenshot_quickadd_toolbar() {
        composeRule.setContent {
            NautileTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NewTaskSheet(
                        state = NewTaskUiState(
                            position = GtdPosition.INBOX,
                            targetPosition = GtdPosition.INBOX,
                        ),
                        projects = projects,
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("设置优先级，当前：无").assertExists()
        capture("quickadd_toolbar")
    }

    @Test
    fun G10_screenshot_priority_menu() {
        composeRule.setContent {
            NautileTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NewTaskSheet(
                        state = NewTaskUiState(
                            position = GtdPosition.INBOX,
                            targetPosition = GtdPosition.INBOX,
                        ),
                        projects = projects,
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("设置优先级，当前：无").performClick()
        composeRule.onNodeWithText("高").assertExists()
        capture("priority_menu")
    }

    @Test
    fun G10_screenshot_gtd_fullscreen() {
        composeRule.setContent {
            NautileTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    GtdSelector(
                        currentPosition = GtdPosition.INBOX,
                        tasks = tasks,
                        onSelect = {},
                        onClose = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("下一步行动").assertExists()
        capture("gtd_fullscreen")
    }

    @Test
    fun G10_screenshot_search_no_result() {
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = state(
                        groups = emptyList(),
                        searchActive = true,
                        searchQuery = "不存在的任务",
                    ),
                )
            }
        }
        composeRule.onNodeWithText("无搜索结果").assertExists()
        capture("search_no_result")
    }

    @Test
    fun G10_screenshot_view_menu() {
        val options = ViewOptions(ViewClassification.PROJECT, ViewSort.MANUAL)
        val groups = TaskGrouping.byView(tasks, projects, options, includeEmptyProjects = true)
        composeRule.setContent {
            NautileTheme {
                TodoScreen(state = state(groups, options))
            }
        }
        composeRule.onNodeWithContentDescription("视图选项").performClick()
        composeRule.onNodeWithText("分类").assertExists()
        capture("view_menu")
    }

    @Test
    fun G10_screenshot_manual_drag_mid() {
        val options = ViewOptions(ViewClassification.PROJECT, ViewSort.MANUAL)
        val dragTasks = listOf(
            task(1L, "拖拽任务一", createdSeconds = 1),
            task(2L, "拖拽任务二", createdSeconds = 2),
            task(3L, "拖拽任务三", createdSeconds = 3),
        )
        val groups = TaskGrouping.byView(dragTasks, emptyList(), options, includeEmptyProjects = false)
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = state(groups, options),
                    onReorderGroup = { _, _ -> },
                )
            }
        }
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
