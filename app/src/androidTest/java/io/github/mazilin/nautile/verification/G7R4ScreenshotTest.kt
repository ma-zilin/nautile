package io.github.mazilin.nautile.verification

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.domain.rule.TaskGroup
import io.github.mazilin.nautile.ui.screen.settings.SettingsScreen
import io.github.mazilin.nautile.ui.screen.todo.GtdSelector
import io.github.mazilin.nautile.ui.screen.todo.NewTaskSheet
import io.github.mazilin.nautile.ui.screen.todo.NewTaskUiState
import io.github.mazilin.nautile.ui.screen.todo.TaskDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.TaskDetailUiState
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoUiState
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R4 G7 闸门截图（不提交）：浅/深主题下的待办列表、GTD 抽屉、创建面板（四属性未设置）、
 * 编辑面板、左右滑背景、设置页。
 *
 * 使用 Compose 测试宿主与内存夹具渲染，截图写入应用 external files 目录，再由 adb pull 到
 * `docs/验证记录/门禁/`。截图只作人工视觉核对，不替代 connected 断言；不代表真实数据库状态。
 */
@RunWith(AndroidJUnit4::class)
class G7R4ScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now: Instant = Instant.parse("2026-11-27T06:00:00Z")

    private val project = Project(
        id = ProjectId(7L),
        name = "课程项目",
        status = ProjectStatus.ACTIVE,
        createdAt = now,
        updatedAt = now,
    )

    private fun task(id: Long, title: String, detail: String? = null) = Task(
        id = TaskId(id),
        title = title,
        projectId = if (id <= 3L) ProjectId(7L) else null,
        position = GtdPosition.NEXT_ACTION,
        priority = if (id == 1L) Priority.HIGH else Priority.NONE,
        dueDate = if (id == 1L) LocalDate.now() else null,
        createdAt = now.plusSeconds(id),
        updatedAt = now.plusSeconds(id),
        detail = detail,
    )

    private val projectGroup = TaskGroup(
        project = project,
        tasks = listOf(
            task(1L, "概率论刷题", "第三章习题 1-5"),
            task(2L, "写周报"),
            task(3L, "整理实验数据"),
        ),
    )

    private val ungrouped = TaskGroup(
        project = null,
        tasks = listOf(task(4L, "买牛奶"), task(5L, "预约体检")),
    )

    private val todoState = TodoUiState(
        position = GtdPosition.NEXT_ACTION,
        groups = listOf(projectGroup, ungrouped),
        visibleTasks = projectGroup.tasks + ungrouped.tasks,
        searchAvailable = true,
        viewOptionsAvailable = true,
    )

    private val settings = UserSettings(
        defaultPage = AppPage.TODO,
        predictiveBackEnabled = true,
    )

    private fun capture(name: String) {
        composeRule.waitForIdle()
        captureBitmap(name, composeRule.onRoot().captureToImage().asAndroidBitmap())
    }

    /**
     * 截图写入公共 Download/nautile_evidence：connected 测试结束后 Gradle 会卸载应用，
     * 应用 external files 目录会一起被删除，故改用 MediaStore 保存，便于 adb pull 归档（不提交）。
     */
    private fun captureBitmap(name: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "g7r4_$name.png"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/nautile_evidence",
            )
        }
        val resolver = context.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
            "无法创建截图输出：$fileName"
        }
        resolver.openOutputStream(uri)!!.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val written = resolver.openInputStream(uri)?.use { it.read() } ?: -1
        assertTrue("截图应写入且有内容：$uri", written > 0)
        println("G7R4_SCREENSHOT name=$name uri=$uri")
    }

    private fun rootBitmap(): Bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()

    /** 逐点采样比较两张根视图截图，用于证明“按住滑动时行内容确实位移/露出色条”。 */
    private fun changedPixelCount(a: Bitmap, b: Bitmap): Int {
        if (a.width != b.width || a.height != b.height) return Int.MAX_VALUE
        var changed = 0
        var y = 0
        while (y < a.height) {
            var x = 0
            while (x < a.width) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) changed += 1
                x += 4
            }
            y += 4
        }
        return changed
    }

    /**
     * 真实指针注入：按住标题行分步横向拖动到 [dxDp]（dp），保持不松手执行 [onHold]，
     * 随后退回 0 再松手，避免触发完成/放弃动作。
     * 采用与 G6R4SwipeOverlayTest 相同的注入方式；Compose performTouchInput 拖动在该行
     * 会被纵向列表手势判定吞掉（G7 首跑左滑未位移），故改用已验证的注入路径。
     */
    private fun swipeHeld(title: String, dxDp: Float, onHold: (Bitmap) -> Unit) {
        val density = composeRule.density.density
        val node = composeRule.onNodeWithText(title).fetchSemanticsNode()
        val root = composeRule.onRoot().fetchSemanticsNode()
        val location = IntArray(2)
        composeRule.activity.window.decorView.getLocationOnScreen(location)
        val startX = location[0] + root.boundsInWindow.left + node.boundsInRoot.center.x
        val startY = location[1] + root.boundsInWindow.top + node.boundsInRoot.center.y
        val dxPx = dxDp * density
        val downTime = SystemClock.uptimeMillis()

        fun send(action: Int, x: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, startY, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }

        send(MotionEvent.ACTION_DOWN, startX)
        SystemClock.sleep(60)
        for (step in 1..8) {
            send(MotionEvent.ACTION_MOVE, startX + dxPx * step / 8f)
            SystemClock.sleep(25)
        }
        SystemClock.sleep(200)
        composeRule.waitForIdle()
        try {
            onHold(rootBitmap())
        } finally {
            for (step in 7 downTo 0) {
                send(MotionEvent.ACTION_MOVE, startX + dxPx * step / 8f)
                SystemClock.sleep(20)
            }
            send(MotionEvent.ACTION_UP, startX)
            SystemClock.sleep(120)
            composeRule.waitForIdle()
        }
    }

    private fun setTodo(dark: Boolean) {
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                TodoScreen(state = todoState)
            }
        }
    }

    @Test
    fun g7r4_todo_light() {
        setTodo(dark = false)
        composeRule.onNodeWithText("概率论刷题").assertExists()
        capture("todo_light")
    }

    @Test
    fun g7r4_todo_dark() {
        setTodo(dark = true)
        composeRule.onNodeWithText("概率论刷题").assertExists()
        capture("todo_dark")
    }

    private fun setDrawer(dark: Boolean) {
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                Box(modifier = Modifier.fillMaxSize()) {
                    TodoScreen(state = todoState)
                    GtdSelector(
                        currentPosition = GtdPosition.NEXT_ACTION,
                        tasks = emptyList(),
                        onSelect = {},
                        onClose = {},
                    )
                }
            }
        }
    }

    @Test
    fun g7r4_gtd_drawer_light() {
        setDrawer(dark = false)
        composeRule.onNodeWithText("清单").assertExists()
        capture("gtd_drawer_light")
    }

    @Test
    fun g7r4_gtd_drawer_dark() {
        setDrawer(dark = true)
        composeRule.onNodeWithText("清单").assertExists()
        capture("gtd_drawer_dark")
    }

    private fun setUnsetCreateSheet(dark: Boolean) {
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                NewTaskSheet(
                    state = NewTaskUiState(
                        position = GtdPosition.NEXT_ACTION,
                        targetPosition = GtdPosition.NEXT_ACTION,
                    ),
                    projects = listOf(project),
                )
            }
        }
    }

    @Test
    fun g7r4_create_sheet_unset_light() {
        setUnsetCreateSheet(dark = false)
        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置").assertExists()
        composeRule.onNodeWithContentDescription("设置优先级，当前：无").assertExists()
        composeRule.onNodeWithContentDescription("设置项目，当前：无项目").assertExists()
        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：下一步行动").assertExists()
        capture("create_sheet_unset_light")
    }

    @Test
    fun g7r4_create_sheet_unset_dark() {
        setUnsetCreateSheet(dark = true)
        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置").assertExists()
        capture("create_sheet_unset_dark")
    }

    /** 四属性均已设置：工具栏显示所选值摘要（日期/优先级/项目/清单）。 */
    @Test
    fun g7r4_create_sheet_set_light() {
        composeRule.setContent {
            NautileTheme(darkTheme = false) {
                NewTaskSheet(
                    state = NewTaskUiState(
                        position = GtdPosition.NEXT_ACTION,
                        targetPosition = GtdPosition.NEXT_ACTION,
                        title = "写周报",
                        priority = Priority.HIGH,
                        dueDate = LocalDate.now(),
                        dueTime = LocalTime.of(9, 30),
                        projectId = ProjectId(7L),
                    ),
                    projects = listOf(project),
                )
            }
        }
        composeRule.onNodeWithContentDescription("设置优先级，当前：高").assertExists()
        composeRule.onNodeWithContentDescription("设置截止日期，当前：今天 09:30").assertExists()
        composeRule.onNodeWithContentDescription("设置项目，当前：课程项目").assertExists()
        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：下一步行动").assertExists()
        capture("create_sheet_set_light")
    }

    /** 搜索态：标题行保留，搜索栏在下方，结果命中项目内任务。 */
    @Test
    fun g7r4_search_light() {
        composeRule.setContent {
            NautileTheme(darkTheme = false) {
                TodoScreen(
                    state = todoState.copy(
                        searchActive = true,
                        searchQuery = "概率",
                        groups = listOf(projectGroup.copy(tasks = listOf(projectGroup.tasks.first()))),
                        visibleTasks = listOf(projectGroup.tasks.first()),
                    ),
                )
            }
        }
        composeRule.onNodeWithContentDescription("退出搜索").assertExists()
        composeRule.onNodeWithContentDescription("清除搜索").assertExists()
        composeRule.onNodeWithText("概率论刷题").assertExists()
        capture("search_light")
    }

    /** 视图菜单：分类/排序，排序中不出现“手动”（FR-V-03 暂缓）。 */
    @Test
    fun g7r4_view_menu_light() {
        setTodo(dark = false)
        composeRule.onNodeWithContentDescription("视图选项").performClick()
        composeRule.onNodeWithText("分类").assertExists()
        composeRule.onNodeWithText("排序").assertExists()
        composeRule.onNodeWithText("手动").assertDoesNotExist()
        capture("view_menu_light")
    }

    /** v2.16（4.27 / R5-8）：原独立 TaskEditSheet 已废除，同步为同构 TaskDetailSheet 浅/深截图。 */
    private fun setTaskDetailSheet(dark: Boolean) {
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TaskDetailSheet(
                        state = TaskDetailUiState(
                            task = Task(
                                id = TaskId(1L),
                                title = "概率论刷题",
                                detail = "第三章习题 1-5",
                                priority = Priority.HIGH,
                                dueDate = LocalDate.now(),
                                dueTime = LocalTime.of(9, 30),
                                projectId = ProjectId(7L),
                                position = GtdPosition.NEXT_ACTION,
                                createdAt = now,
                                updatedAt = now,
                            ),
                            titleDraft = null,
                            titleInvalid = false,
                            saving = false,
                            error = null,
                            leaveBlocked = false,
                            canReopen = false,
                            projectName = "课程项目",
                        ),
                        onTitleChanged = {},
                        onCommitTitle = {},
                        onReopen = {},
                        onCloseRequested = {},
                        projects = listOf(project),
                    )
                }
            }
        }
    }

    @Test
    fun g7r4_task_detail_light() {
        setTaskDetailSheet(dark = false)
        composeRule.onNodeWithText("概率论刷题").assertExists()
        capture("task_detail_light")
    }

    @Test
    fun g7r4_task_detail_dark() {
        setTaskDetailSheet(dark = true)
        composeRule.onNodeWithText("概率论刷题").assertExists()
        capture("task_detail_dark")
    }

    /** v2.16（4.26 / R5-7）：按住任务行分步向右拖 80dp，行内容右移、露出左侧“完成”色条与图标（不松手，不触发完成）。 */
    @Test
    fun g7r4_swipe_complete_light() {
        setTodo(dark = false)
        val before = rootBitmap()
        swipeHeld("概率论刷题", 80f) { during ->
            val changed = changedPixelCount(before, during)
            assertTrue("右滑后画面应有位移/露出完成色条：变化采样点=$changed", changed > 2_000)
            captureBitmap("swipe_complete_light", during)
        }
    }

    /** v2.16（4.26 / R5-7）：按住任务行分步向左拖 80dp，行内容左移、露出右侧“放弃”色条与图标（不松手，不触发放弃）。 */
    @Test
    fun g7r4_swipe_abandon_light() {
        setTodo(dark = false)
        val before = rootBitmap()
        swipeHeld("概率论刷题", -80f) { during ->
            val changed = changedPixelCount(before, during)
            assertTrue("左滑后画面应有位移/露出放弃色条：变化采样点=$changed", changed > 2_000)
            captureBitmap("swipe_abandon_light", during)
        }
    }

    @Test
    fun g7r4_swipe_complete_dark() {
        setTodo(dark = true)
        val before = rootBitmap()
        swipeHeld("概率论刷题", 80f) { during ->
            val changed = changedPixelCount(before, during)
            assertTrue("深色右滑后画面应有位移/露出完成色条：变化采样点=$changed", changed > 2_000)
            captureBitmap("swipe_complete_dark", during)
        }
    }

    private fun setSettings(dark: Boolean) {
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        settings = settings,
                        onBack = {},
                        onOpenDefaultPage = {},
                        onPredictiveBackChanged = {},
                    )
                }
            }
        }
    }

    @Test
    fun g7r4_settings_light() {
        setSettings(dark = false)
        composeRule.onNodeWithText("设置").assertExists()
        capture("settings_light")
    }

    @Test
    fun g7r4_settings_dark() {
        setSettings(dark = true)
        composeRule.onNodeWithText("设置").assertExists()
        capture("settings_dark")
    }
}
