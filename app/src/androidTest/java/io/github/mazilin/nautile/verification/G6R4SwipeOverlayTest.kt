package io.github.mazilin.nautile.verification

import android.graphics.Bitmap
import android.content.ContentValues
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.repository.RoomProjectRepository
import io.github.mazilin.nautile.data.repository.RoomTaskRepository
import io.github.mazilin.nautile.domain.model.ClosedOutcome
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
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.rule.TaskGroup
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.ui.screen.todo.ProjectDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.TaskDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoUiState
import io.github.mazilin.nautile.ui.screen.todo.TodoViewModel
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R4 G6 闸门：滑动操作区、弹层关闭与手动排序暂缓。
 *
 * 依据 `docs/需求基线.md` v2.15（FR-G-14、FR-V-03、AC-D-07、AC-D-08）、
 * `docs/需求变更记录.md` 4.25 与设计标准 §14—§15（CMP-05、CMP-40.3、CMP-45/46、CMP-48、NO-19）。
 *
 * 覆盖：
 * - 任务/项目行滑动背景：左“放弃”（errorContainer + close）、右“完成”（primaryContainer + check），
 *   色条与图标用真实像素校验；未超 72dp 回弹、超阈值才触发；
 * - 详情/项目详情点遮罩关闭；无效标题/名称阻止关闭并提示；
 * - 属性菜单点菜单外、系统返回先关菜单；视图菜单同样点外/返回关闭且不显示“手动”；
 * - 已存 MANUAL 回退 CREATED 的界面呈现（ViewModel 级记录与数据保留见 G9ViewModelViewOptionsTest）。
 *
 * 运行环境：emulator-5554（API 36，模拟器，非真机）。像素断言用于色条/图标是否存在，
 * 不用于体验判定；动画观感仍为人工项。
 */
@RunWith(AndroidJUnit4::class)
class G6R4SwipeOverlayTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now: Instant = Instant.parse("2026-11-26T12:00:00Z")
    private val clock = Clock { now }

    private lateinit var database: NautileDatabase
    private lateinit var taskRepository: RoomTaskRepository
    private lateinit var projectRepository: RoomProjectRepository
    private lateinit var settings: G9MemorySettingsRepository
    private lateinit var vm: TodoViewModel

    private var errorContainer = 0
    private var onErrorContainer = 0
    private var primaryContainer = 0
    private var onPrimaryContainer = 0

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NautileDatabase::class.java,
        ).allowMainThreadQueries().build()
        taskRepository = RoomTaskRepository(database)
        projectRepository = RoomProjectRepository(database)
        settings = G9MemorySettingsRepository()
    }

    @After
    fun tearDown() {
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        database.close()
    }

    // ---------- 夹具 ----------

    private fun task(
        id: Long,
        title: String,
        projectId: ProjectId? = null,
        position: GtdPosition = GtdPosition.INBOX,
        createdSeconds: Long = id,
    ) = Task(
        id = TaskId(id),
        title = title,
        projectId = projectId,
        position = position,
        createdAt = now.plusSeconds(createdSeconds),
        updatedAt = now,
    )

    private fun project(id: Long, name: String) = Project(
        id = ProjectId(id),
        name = name,
        status = ProjectStatus.ACTIVE,
        createdAt = now,
        updatedAt = now,
    )

    private fun seedTask(
        title: String,
        projectId: ProjectId? = null,
        position: GtdPosition = GtdPosition.INBOX,
        createdSeconds: Long = 0L,
    ): TaskId = runBlocking {
        taskRepository.insert(
            Task(
                id = TaskId(0L),
                title = title,
                projectId = projectId,
                position = position,
                priority = Priority.NONE,
                createdAt = now.plusSeconds(createdSeconds),
                updatedAt = now.plusSeconds(createdSeconds),
            ),
        )
    }

    private fun seedProject(name: String): ProjectId = runBlocking {
        projectRepository.insert(
            Project(
                id = ProjectId(0L),
                name = name,
                status = ProjectStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun createViewModel() {
        vm = TodoViewModel(
            taskRepository = taskRepository,
            projectRepository = projectRepository,
            settingsRepository = settings,
            taskCommands = TaskCommands(taskRepository, projectRepository, clock),
            projectCommands = ProjectCommands(projectRepository, taskRepository, clock),
        )
    }

    /** 在组合中读取主题 token，作为像素断言的期望值；不复制生产颜色常量。 */
    private fun setTodoScreen(screen: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            NautileTheme {
                val colors = MaterialTheme.colorScheme
                SideEffect {
                    errorContainer = colors.errorContainer.toArgb()
                    onErrorContainer = colors.onErrorContainer.toArgb()
                    primaryContainer = colors.primaryContainer.toArgb()
                    onPrimaryContainer = colors.onPrimaryContainer.toArgb()
                }
                screen()
            }
        }
    }

    private fun todoState(
        groups: List<TaskGroup>,
        tasks: List<Task>,
        options: ViewOptions = ViewOptions(ViewClassification.PROJECT, ViewSort.CREATED),
    ) = TodoUiState(
        position = GtdPosition.INBOX,
        groups = groups,
        visibleTasks = tasks,
        searchAvailable = true,
        viewOptionsAvailable = true,
        viewOptions = options,
    )

    private fun setTaskDetailHost() {
        composeRule.setContent {
            NautileTheme {
                val state by vm.taskDetail.collectAsState()
                Surface(modifier = Modifier.fillMaxSize()) {
                    state?.let { current ->
                        TaskDetailSheet(
                            state = current,
                            onTitleChanged = vm::onTaskTitleChanged,
                            onCommitTitle = vm::commitTaskTitle,
                            onReopen = { vm.reopenTask(current.task.id) },
                            onCloseRequested = {
                                // 与 MainActivity 宿主一致：先经父层离开校验，放行才关闭。
                                if (vm.requestDetailLeave()) vm.closeDetail()
                            },
                            onDetailChanged = vm::onTaskDetailChanged,
                            onCommitDetail = vm::commitTaskDetail,
                            onPriorityChanged = vm::onTaskPriorityChanged,
                            onDueDateChanged = vm::onTaskDueDateChanged,
                            onDueTimeChanged = vm::onTaskDueTimeChanged,
                            onProjectChanged = vm::onTaskProjectChanged,
                            onGtdChanged = vm::moveTaskToGtdFromDetail,
                            onProjectDraftChanged = vm::onTaskProjectDraftChanged,
                            onCreateProjectForTask = vm::createProjectForTaskDetail,
                            onClearDetail = vm::clearTaskDetail,
                        )
                    }
                }
            }
        }
    }

    private fun setProjectDetailHost() {
        composeRule.setContent {
            NautileTheme {
                val state by vm.projectDetail.collectAsState()
                Surface(modifier = Modifier.fillMaxSize()) {
                    state?.let { current ->
                        ProjectDetailSheet(
                            state = current,
                            onNameChanged = vm::onProjectNameChanged,
                            onCommitName = vm::commitProjectName,
                            onToggleCollapsed = { vm.toggleProjectCollapsed(current.project.id) },
                            onOpenChild = {},
                            onCloseRequested = {
                                if (vm.requestDetailLeave()) vm.closeDetail()
                            },
                        )
                    }
                }
            }
        }
    }

    /** 同一测试内先任务后项目：只允许 setContent 一次，故两个详情共用一个宿主。 */
    private fun setBothDetailHosts() {
        composeRule.setContent {
            NautileTheme {
                val taskState by vm.taskDetail.collectAsState()
                val projectState by vm.projectDetail.collectAsState()
                Surface(modifier = Modifier.fillMaxSize()) {
                    taskState?.let { current ->
                        TaskDetailSheet(
                            state = current,
                            onTitleChanged = vm::onTaskTitleChanged,
                            onCommitTitle = vm::commitTaskTitle,
                            onReopen = { vm.reopenTask(current.task.id) },
                            onCloseRequested = {
                                if (vm.requestDetailLeave()) vm.closeDetail()
                            },
                            onDetailChanged = vm::onTaskDetailChanged,
                            onCommitDetail = vm::commitTaskDetail,
                        )
                    }
                    projectState?.let { current ->
                        ProjectDetailSheet(
                            state = current,
                            onNameChanged = vm::onProjectNameChanged,
                            onCommitName = vm::commitProjectName,
                            onToggleCollapsed = { vm.toggleProjectCollapsed(current.project.id) },
                            onOpenChild = {},
                            onCloseRequested = {
                                if (vm.requestDetailLeave()) vm.closeDetail()
                            },
                        )
                    }
                }
            }
        }
    }

    private fun tapScrim() {
        // 详情面板仅占下半屏，左上角是遮罩；锚定菜单打开时该点先被菜单透明层消费。
        composeRule.onRoot().performTouchInput { click(Offset(12f, 12f)) }
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun openTaskDetail(taskId: TaskId) {
        composeRule.runOnIdle { vm.openTaskDetail(taskId) }
        composeRule.waitUntil(5_000) { vm.taskDetail.value?.task?.id == taskId }
        composeRule.waitForIdle()
    }

    private fun openProjectDetail(projectId: ProjectId) {
        composeRule.runOnIdle { vm.openProjectDetail(projectId) }
        composeRule.waitUntil(5_000) { vm.projectDetail.value?.project?.id == projectId }
        composeRule.waitForIdle()
    }

    private fun captureRoot(): Bitmap {
        composeRule.waitForIdle()
        return composeRule.onRoot().captureToImage().asAndroidBitmap()
    }

    /** 证据截图（不提交）：写入公共 Download/nautile_evidence，应用卸载后仍可取；唯一名避免重复。 */
    private fun saveEvidence(name: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "g6r4_${name}_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/nautile_evidence",
            )
        }
        val resolver = context.contentResolver
        val uri = requireNotNull(
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
        ) { "无法创建截图输出：$fileName" }
        resolver.openOutputStream(uri)!!.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("截图应写入：$uri", resolver.openInputStream(uri) != null)
        println("G6R4_SCREENSHOT name=$name file=$fileName uri=$uri")
    }

    // ---------- 像素辅助 ----------

    private fun closeTo(actual: Int, expected: Int, tolerance: Int = 12): Boolean {
        val ar = android.graphics.Color.red(actual)
        val ag = android.graphics.Color.green(actual)
        val ab = android.graphics.Color.blue(actual)
        val er = android.graphics.Color.red(expected)
        val eg = android.graphics.Color.green(expected)
        val eb = android.graphics.Color.blue(expected)
        return abs(ar - er) <= tolerance && abs(ag - eg) <= tolerance && abs(ab - eb) <= tolerance
    }

    private fun assertPixel(bitmap: Bitmap, x: Float, y: Float, expected: Int, label: String) {
        val px = x.roundToInt().coerceIn(0, bitmap.width - 1)
        val py = y.roundToInt().coerceIn(0, bitmap.height - 1)
        val actual = bitmap.getPixel(px, py)
        assertTrue(
            "$label：($px,$py) 期望 #${Integer.toHexString(expected)}，实际 #${Integer.toHexString(actual)}",
            closeTo(actual, expected),
        )
    }

    private fun countTint(bitmap: Bitmap, left: Float, right: Float, top: Float, bottom: Float, expected: Int): Int {
        var count = 0
        var y = top.roundToInt()
        while (y <= bottom) {
            var x = left.roundToInt()
            while (x <= right) {
                if (x in 0 until bitmap.width && y in 0 until bitmap.height &&
                    closeTo(bitmap.getPixel(x, y), expected, tolerance = 20)
                ) {
                    count += 1
                }
                x += 2
            }
            y += 2
        }
        return count
    }

    // ---------- 滑动操作区 ----------

    @Test
    fun G6R4_task_row_swipe_bands_tokens_and_threshold() {
        var completed: TaskId? = null
        var abandoned: TaskId? = null
        val row = task(1L, "滑动任务")
        setTodoScreen {
            TodoScreen(
                state = todoState(listOf(TaskGroup(null, listOf(row))), listOf(row)),
                onCompleteTask = { completed = it },
                onAbandonTask = { abandoned = it },
            )
        }

        // 图标 + 文字同时存在（图标为装饰性，语义由文字承担）。
        composeRule.onNodeWithText("放弃").assertExists()
        composeRule.onNodeWithText("完成").assertExists()

        val density = composeRule.density.density
        val node = composeRule.onNodeWithText("滑动任务").fetchSemanticsNode()
        val rootWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val rowLeft = with(composeRule.density) { 16.dp.toPx() }
        val rowRight = rootWidth - rowLeft
        val centerY = node.boundsInRoot.center.y

        // 右滑 56dp（< 72dp）：露出左侧“放弃”，色条为 errorContainer、图标为 onErrorContainer。
        withHeldSwipe("滑动任务", 56f) {
            val bitmap = captureRoot()
            listOf(-10f, 0f, 10f).forEach { dy ->
                assertPixel(bitmap, rowLeft + 4f * density, centerY + dy * density, errorContainer, "任务左滑色条 errorContainer")
            }
            val iconPixels = countTint(
                bitmap,
                rowLeft + 16f * density,
                rowLeft + 40f * density,
                centerY - 12f * density,
                centerY + 12f * density,
                onErrorContainer,
            )
            assertTrue("左滑应绘制 close 图标（onErrorContainer 像素=$iconPixels）", iconPixels >= 20)
        }
        assertNull("未超阈值不得放弃任务", abandoned)

        // 广角证据截图：露出完整“close 图标 + 放弃”；先退回 0 再松手，不提交动作。
        withHeldSwipe("滑动任务", 140f, releaseDp = 0f) {
            saveEvidence("task_swipe_abandon", captureRoot())
        }
        assertNull("退回后松手不得放弃任务", abandoned)

        // 回弹：释放后表面重新覆盖色条。
        val reset = captureRoot()
        assertNotEquals(
            "未超阈值释放后应回弹，不应继续露出 errorContainer",
            true,
            closeTo(reset.getPixel((rowLeft + 4f * density).roundToInt(), centerY.roundToInt()), errorContainer),
        )

        // 左滑 56dp（< 72dp）：露出右侧“完成”，色条为 primaryContainer、图标为 onPrimaryContainer。
        withHeldSwipe("滑动任务", -56f) {
            val bitmap = captureRoot()
            listOf(-10f, 0f, 10f).forEach { dy ->
                assertPixel(bitmap, rowRight - 4f * density, centerY + dy * density, primaryContainer, "任务左滑色条 primaryContainer")
            }
            val iconPixels = countTint(
                bitmap,
                rowRight - 40f * density,
                rowRight - 16f * density,
                centerY - 12f * density,
                centerY + 12f * density,
                onPrimaryContainer,
            )
            assertTrue("左滑应绘制 check 图标（onPrimaryContainer 像素=$iconPixels）", iconPixels >= 20)
        }
        assertNull("未超阈值不得完成任务", completed)

        // 广角证据截图：露出完整“完成 + check 图标”；先退回 0 再松手，不提交动作。
        withHeldSwipe("滑动任务", -140f, releaseDp = 0f) {
            saveEvidence("task_swipe_complete", captureRoot())
        }
        assertNull("退回后松手不得完成任务", completed)

        // 超过阈值才提交动作（阈值 72dp 未改动）。
        composeRule.onNodeWithText("滑动任务").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals("超过阈值左滑完成任务", TaskId(1L), completed)
        composeRule.onNodeWithText("滑动任务").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertEquals("超过阈值右滑放弃任务", TaskId(1L), abandoned)
    }

    @Test
    fun G6R4_project_row_uses_same_swipe_bands_and_callbacks() {
        val project = project(7L, "课程项目")
        val child = task(1L, "子任务", projectId = project.id).copy()
        var requested: List<Pair<ProjectId, ClosedOutcome>> = emptyList()
        setTodoScreen {
            TodoScreen(
                state = todoState(listOf(TaskGroup(project, listOf(child))), listOf(child)),
                onRequestProjectClose = { id, outcome -> requested = requested + (id to outcome) },
            )
        }

        // 项目行与任务行各一套“放弃/完成”背景（图标+文字），项目行同样生效。
        composeRule.onAllNodesWithText("放弃").assertCountEquals(2)
        composeRule.onAllNodesWithText("完成").assertCountEquals(2)

        val density = composeRule.density.density
        val node = composeRule.onNodeWithText("课程项目（1）").fetchSemanticsNode()
        val rootWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val rowLeft = with(composeRule.density) { 16.dp.toPx() }
        val rowRight = rootWidth - rowLeft
        val centerY = node.boundsInRoot.center.y

        withHeldSwipe("课程项目（1）", 56f) {
            val bitmap = captureRoot()
            assertPixel(bitmap, rowLeft + 4f * density, centerY, errorContainer, "项目行左滑 errorContainer")
        }
        assertTrue("未超阈值不得请求结项项目", requested.isEmpty())

        withHeldSwipe("课程项目（1）", -56f) {
            val bitmap = captureRoot()
            assertPixel(bitmap, rowRight - 4f * density, centerY, primaryContainer, "项目行右滑 primaryContainer")
        }
        assertTrue("未超阈值不得请求结项项目", requested.isEmpty())

        // 广角证据截图：项目行同样显示图标 + 文字；退回 0 再松手，不提交。
        withHeldSwipe("课程项目（1）", 140f, releaseDp = 0f) {
            saveEvidence("project_swipe_abandon", captureRoot())
        }
        withHeldSwipe("课程项目（1）", -140f, releaseDp = 0f) {
            saveEvidence("project_swipe_complete", captureRoot())
        }
        assertTrue("退回后松手不得请求结项项目", requested.isEmpty())

        composeRule.onNodeWithText("课程项目（1）").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(
            "项目行左滑请求完成并先确认数量",
            listOf(project.id to ClosedOutcome.COMPLETED),
            requested,
        )
        composeRule.onNodeWithText("课程项目（1）").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertEquals(
            "项目行右滑请求放弃",
            listOf(project.id to ClosedOutcome.COMPLETED, project.id to ClosedOutcome.ABANDONED),
            requested,
        )
    }

    // ---------- 详情遮罩与无效字段 ----------

    @Test
    fun G6R4_detail_scrim_tap_closes_and_invalid_fields_block_with_prompt() {
        val taskId = seedTask("详情任务", createdSeconds = 1)
        createViewModel()
        setBothDetailHosts()

        openTaskDetail(taskId)
        composeRule.onNodeWithText("任务详情").assertIsDisplayed()
        tapScrim()
        composeRule.waitUntil(5_000) { vm.taskDetail.value == null }
        assertNull("点遮罩应关闭任务详情", vm.taskDetail.value)

        // 无效标题：遮罩点按发出关闭请求，但父层阻止并提示。
        openTaskDetail(taskId)
        composeRule.runOnIdle { vm.onTaskTitleChanged("   ") }
        composeRule.waitUntil(5_000) { vm.taskDetail.value?.titleDraft == "   " }
        tapScrim()
        composeRule.waitUntil(5_000) { vm.taskDetail.value?.leaveBlocked == true }
        assertTrue("无效标题应阻止关闭", vm.taskDetail.value != null)
        composeRule.onNodeWithText("请先修正无效标题").assertIsDisplayed()
        composeRule.onNodeWithText("任务详情").assertIsDisplayed()
        composeRule.runOnIdle { vm.closeDetail() }

        // 项目详情：有效遮罩关闭 + 无效名称阻止。
        val projectId = seedProject("详情项目")
        openProjectDetail(projectId)
        composeRule.onNodeWithText("项目详情").assertIsDisplayed()
        tapScrim()
        composeRule.waitUntil(5_000) { vm.projectDetail.value == null }
        assertNull("点遮罩应关闭项目详情", vm.projectDetail.value)

        openProjectDetail(projectId)
        composeRule.runOnIdle { vm.onProjectNameChanged("   ") }
        composeRule.waitUntil(5_000) { vm.projectDetail.value?.nameDraft == "   " }
        tapScrim()
        composeRule.waitUntil(5_000) { vm.projectDetail.value?.leaveBlocked == true }
        assertTrue("无效项目名称应阻止关闭", vm.projectDetail.value != null)
        composeRule.onNodeWithText("请先修正无效项目名称").assertIsDisplayed()
        composeRule.onNodeWithText("项目详情").assertIsDisplayed()
    }

    // ---------- 菜单：点外与返回先关 ----------

    @Test
    fun G6R4_attribute_menu_outside_tap_and_back_close_menu_first() {
        val taskId = seedTask("菜单任务", createdSeconds = 1)
        createViewModel()
        setTaskDetailHost()
        openTaskDetail(taskId)

        // 点菜单外：只关菜单，详情保持。
        composeRule.onNodeWithContentDescription("设置优先级，当前：无").performClick()
        composeRule.onNodeWithText("高").assertIsDisplayed()
        tapScrim()
        composeRule.onNodeWithText("高").assertDoesNotExist()
        assertTrue("点菜单外不应关闭详情", vm.taskDetail.value != null)
        assertEquals("点菜单外不应改变优先级", Priority.NONE, vm.taskDetail.value?.task?.priority)

        // 系统返回：先关菜单，不关详情。
        composeRule.onNodeWithContentDescription("设置优先级，当前：无").performClick()
        composeRule.onNodeWithText("高").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("高").assertDoesNotExist()
        assertTrue("返回先关菜单，不关详情", vm.taskDetail.value != null)

        // 再次点遮罩才关闭详情（返回链第二层）。
        tapScrim()
        composeRule.waitUntil(5_000) { vm.taskDetail.value == null }
        assertNull("菜单关闭后遮罩可关闭详情", vm.taskDetail.value)
    }

    @Test
    fun G6R4_view_menu_hides_manual_and_closes_on_outside_tap_and_back() {
        val row = task(1L, "视图任务")
        setTodoScreen {
            TodoScreen(state = todoState(listOf(TaskGroup(null, listOf(row))), listOf(row)))
        }

        composeRule.onNodeWithContentDescription("视图选项").performClick()
        composeRule.onNodeWithText("分类").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("排序：创建时间").assertIsSelected()
        composeRule.onNodeWithContentDescription("排序：手动").assertDoesNotExist()
        composeRule.onAllNodesWithText("手动").assertCountEquals(0)
        saveEvidence("view_menu", captureRoot())

        tapScrim()
        composeRule.onNodeWithText("分类").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("视图选项").performClick()
        composeRule.onNodeWithText("排序").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("排序").assertDoesNotExist()
        composeRule.onNodeWithText("视图任务").assertIsDisplayed()
    }

    // ---------- 遮罩关闭中间态截图 ----------

    @Test
    fun G6R4_screenshot_scrim_close_mid_state() {
        val taskId = seedTask("遮罩任务", createdSeconds = 1)
        createViewModel()
        var lastState by mutableStateOf<io.github.mazilin.nautile.ui.screen.todo.TaskDetailUiState?>(null)
        composeRule.setContent {
            NautileTheme {
                val state by vm.taskDetail.collectAsState()
                if (state != null) lastState = state
                lastState?.let { current ->
                    TaskDetailSheet(
                        state = current,
                        onTitleChanged = vm::onTaskTitleChanged,
                        onCommitTitle = vm::commitTaskTitle,
                        onReopen = {},
                        onCloseRequested = { if (vm.requestDetailLeave()) vm.closeDetail() },
                        visible = state != null,
                        onExitFinished = { lastState = null },
                    )
                }
            }
        }
        openTaskDetail(taskId)
        composeRule.mainClock.autoAdvance = false
        // 与点遮罩关闭走同一条可见性路径（遮罩点按关闭已在上面的用例断言）；
        // 这里暂停时钟，只截取退出动画中段的遮罩/面板状态。
        composeRule.runOnUiThread { vm.closeDetail() }
        composeRule.mainClock.advanceTimeBy(120)
        saveEvidence("scrim_close_mid", composeRule.onRoot().captureToImage().asAndroidBitmap())
        composeRule.mainClock.advanceTimeBy(800)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertNull(vm.taskDetail.value)
    }

    // ---------- 真实指针注入：保持按下以截取滑动中间态 ----------

    private fun withHeldSwipe(
        title: String,
        dxDp: Float,
        releaseDp: Float = dxDp,
        onHold: () -> Unit,
    ) {
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
            onHold()
        } finally {
            val releasePx = releaseDp * density
            if (releasePx != dxPx) {
                // 截图后先退回：释放位移低于阈值时不提交动作（用于广角证据截图）。
                for (step in 7 downTo 0) {
                    val x = startX + dxPx + (releasePx - dxPx) * (8 - step) / 8f
                    send(MotionEvent.ACTION_MOVE, x)
                    SystemClock.sleep(20)
                }
            }
            send(MotionEvent.ACTION_UP, startX + releasePx)
            SystemClock.sleep(120)
            composeRule.waitForIdle()
        }
    }
}
