package io.github.mazilin.nautile.verification

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
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
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.rule.TaskGroup
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoUiState
import io.github.mazilin.nautile.ui.screen.todo.TodoViewModel
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R5-G3 独立验收（模拟器 UI + 截图）：任务行内容组成 + Checkbox 220ms 完成退场 + 左右滑动对调。
 *
 * 依据：《需求基线》v2.16（FR-G-09、FR-G-13、FR-G-14、FR-D-03、BR-38、BR-46、BR-60、
 * AC-T-16、AC-T-17、AC-D-09）与 `.plans/设计/03_交互与动效规格.md`（§2.2、§3.3.2、§4 G2）。
 *
 * 运行环境说明：在模拟器（非真机）执行；截图写入 Downloads/nautile_evidence/r5g3_*.png，
 * 由外部 adb pull 至 docs/验证记录/门禁/ 供人工逐张目视核对。
 */
@RunWith(AndroidJUnit4::class)
class AcG3R5RowCheckboxSwipeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 11, 27)
    private val nowTime: LocalTime = LocalTime.of(14, 30)
    private val now: Instant = LocalDateTime.of(today, nowTime).atZone(zone).toInstant()
    private val clock = Clock { now }

    private lateinit var database: NautileDatabase
    private lateinit var taskRepository: RoomTaskRepository
    private lateinit var projectRepository: RoomProjectRepository
    private lateinit var settings: G9MemorySettingsRepository
    private lateinit var vm: TodoViewModel

    private var errorColor = 0
    private var primaryContainer = 0
    private var onPrimaryContainer = 0
    private var errorContainer = 0
    private var onErrorContainer = 0

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

    private fun createViewModel() {
        vm = TodoViewModel(
            taskRepository = taskRepository,
            projectRepository = projectRepository,
            settingsRepository = settings,
            taskCommands = TaskCommands(taskRepository, projectRepository, clock),
            projectCommands = ProjectCommands(projectRepository, taskRepository, clock),
            clock = clock,
            zoneId = zone,
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

    private fun seedTask(
        title: String,
        detail: String? = null,
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
        projectId: ProjectId? = null,
        position: GtdPosition = GtdPosition.INBOX,
        closedOutcome: ClosedOutcome? = null,
        createdOffsetSeconds: Long = 0L,
    ): TaskId = runBlocking {
        taskRepository.insert(
            Task(
                id = TaskId(0L),
                title = title,
                detail = detail,
                projectId = projectId,
                position = if (closedOutcome != null) GtdPosition.ENDED else position,
                positionBeforeClose = if (closedOutcome != null) position else null,
                closedOutcome = closedOutcome,
                closedAt = if (closedOutcome != null) now else null,
                closedOn = if (closedOutcome != null) today else null,
                priority = Priority.NONE,
                dueDate = dueDate,
                dueTime = dueTime,
                createdAt = now.plusSeconds(createdOffsetSeconds),
                updatedAt = now.plusSeconds(createdOffsetSeconds),
            ),
        )
    }

    private fun captureBitmap(): Bitmap {
        composeRule.waitForIdle()
        return composeRule.onRoot().captureToImage().asAndroidBitmap()
    }

    /** 将截图写入媒体库 Downloads/nautile_evidence/r5g3_<name>.png，供 adb pull 核对。 */
    private fun saveEvidence(name: String, bitmap: Bitmap = captureBitmap()) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val fileName = "r5g3_$name.png"
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/nautile_evidence"

        resolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(fileName),
        )

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
        }
        val uri = requireNotNull(
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
        ) { "无法创建截图输出：$fileName" }
        resolver.openOutputStream(uri)!!.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("截图应可读：$uri", resolver.openInputStream(uri) != null)
    }

    private fun closeTo(actual: Int, expected: Int, tolerance: Int = 18): Boolean {
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

    private fun countTint(
        bitmap: Bitmap,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        expected: Int,
        tolerance: Int = 24,
    ): Int {
        var count = 0
        var y = top.roundToInt()
        while (y <= bottom) {
            var x = left.roundToInt()
            while (x <= right) {
                if (x in 0 until bitmap.width && y in 0 until bitmap.height &&
                    closeTo(bitmap.getPixel(x, y), expected, tolerance = tolerance)
                ) {
                    count += 1
                }
                x += 2
            }
            y += 2
        }
        return count
    }

    private fun withHeldSwipe(
        title: String,
        dxDp: Float,
        releaseDp: Float = 0f,
        onHold: (Bitmap) -> Unit,
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
            onHold(captureBitmap())
        } finally {
            val releasePx = releaseDp * density
            if (releasePx != dxPx) {
                for (step in 7 downTo 0) {
                    val x = startX + dxPx + (releasePx - dxPx) * (8 - step) / 8f
                    send(MotionEvent.ACTION_MOVE, x)
                    SystemClock.sleep(20)
                }
            }
            send(MotionEvent.ACTION_UP, startX + releasePx)
            SystemClock.sleep(150)
            composeRule.waitForIdle()
        }
    }

    private fun setLiveTodoHost(
        darkTheme: Boolean = false,
        onOpenSelector: () -> Unit = {},
        onOpenTaskDetail: (TaskId) -> Unit = {},
        onCaptureDensity: (Density) -> Unit = {},
    ) {
        composeRule.setContent {
            NautileTheme(darkTheme = darkTheme) {
                val colors = MaterialTheme.colorScheme
                val currentDensity = LocalDensity.current
                SideEffect {
                    errorColor = colors.error.toArgb()
                    primaryContainer = colors.primaryContainer.toArgb()
                    onPrimaryContainer = colors.onPrimaryContainer.toArgb()
                    errorContainer = colors.errorContainer.toArgb()
                    onErrorContainer = colors.onErrorContainer.toArgb()
                    onCaptureDensity(currentDensity)
                }
                val state by vm.uiState.collectAsState()
                TodoScreen(
                    state = state,
                    onOpenSelector = onOpenSelector,
                    onCompleteTask = vm::completeTask,
                    onAbandonTask = vm::abandonTask,
                    onRequestProjectClose = vm::requestProjectClose,
                    onConfirmProjectClose = vm::confirmProjectClose,
                    onCancelProjectClose = vm::cancelProjectClose,
                    onSwitchPosition = vm::selectPosition,
                    onOpenTaskDetail = onOpenTaskDetail,
                    onDismissViewError = vm::dismissViewError,
                )
            }
        }
    }

    /**
     * AC-D-09 / FR-G-09 / BR-38：任务行内容组成——前置 Checkbox（≥48dp）、按需详情（无则不占行）、
     * 按需截止时间（无则不显示时间行）、逾期 error 色 + “已逾期 · ”前缀且不自动结项。
     */
    @Test
    fun AC_D_09_FR_G_09_row_content_checkbox_detail_due_and_overdue_highlight() {
        val projectId = seedProject("具身智能课程")
        val fullId = seedTask(
            title = "提交实验报告",
            detail = "汇总仿真轨迹图、误差曲线与消融对比结果，核对参考文献格式与附录代码说明",
            dueDate = today.plusDays(1),
            dueTime = LocalTime.of(9, 30),
            projectId = projectId,
            createdOffsetSeconds = 1,
        )
        val minimalId = seedTask(
            title = "买牛奶",
            detail = null,
            dueDate = null,
            dueTime = null,
            projectId = projectId,
            createdOffsetSeconds = 2,
        )
        val overdueId = seedTask(
            title = "复核概率论错题",
            detail = "第三章条件分布与期望习题 1-6",
            dueDate = today.minusDays(1),
            dueTime = LocalTime.of(18, 0),
            projectId = null,
            createdOffsetSeconds = 3,
        )
        val todayDueId = seedTask(
            title = "整理周会记录",
            detail = null,
            dueDate = today,
            dueTime = LocalTime.of(20, 0),
            projectId = null,
            createdOffsetSeconds = 4,
        )

        createViewModel()
        lateinit var density: Density
        setLiveTodoHost(darkTheme = false, onCaptureDensity = { density = it })

        composeRule.waitUntil(5_000) { vm.uiState.value.visibleTasks.size == 4 }
        composeRule.waitForIdle()

        // 1) 前置 Checkbox 存在、启用、未勾选且触控 ≥48×48dp。
        listOf(fullId, minimalId, overdueId, todayDueId).forEach { id ->
            val cb = composeRule.onNodeWithTag("task_checkbox_${id.value}")
            cb.assertIsDisplayed().assertIsEnabled().assertIsOff()
            val bounds = cb.fetchSemanticsNode().boundsInRoot
            val wDp = bounds.width / density.density
            val hDp = bounds.height / density.density
            assertTrue("Checkbox 宽度应 ≥48dp，实测=$wDp", wDp >= 47.5f)
            assertTrue("Checkbox 高度应 ≥48dp，实测=$hDp", hDp >= 47.5f)
        }

        // 2) 有详情/截止时间的任务均显示；无详情/无截止时间的任务不渲染对应节点且行高更紧凑。
        // 详情文本随行主体合并语义（03 规格 §1.3），故以 useUnmergedTree=true 校验精确 tag，并核对合并树可读。
        composeRule.onNodeWithTag("task_detail_preview_${fullId.value}", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("汇总仿真轨迹图", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("task_due_${fullId.value}").assertIsDisplayed()
        composeRule.onNodeWithText("明天 09:30").assertIsDisplayed()

        composeRule.onNodeWithTag("task_detail_preview_${minimalId.value}", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag("task_due_${minimalId.value}", useUnmergedTree = true)
            .assertDoesNotExist()

        val fullRowHeight = composeRule.onNodeWithTag("task_row_${fullId.value}")
            .fetchSemanticsNode().boundsInRoot.height
        val minimalRowHeight = composeRule.onNodeWithTag("task_row_${minimalId.value}")
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            "无详情无截止行高($minimalRowHeight)应显著小于完整行高($fullRowHeight)",
            minimalRowHeight < fullRowHeight - 16f,
        )

        // 3) 逾期任务显示“已逾期 · ”语义前缀 + error 角色像素高亮，且不自动结项。
        composeRule.onNodeWithTag("task_due_${overdueId.value}").assertIsDisplayed()
        composeRule.onNodeWithText("已逾期 · 11月26日 18:00").assertIsDisplayed()
        composeRule.onNodeWithText("今天 20:00").assertIsDisplayed()

        val bitmap = captureBitmap()
        val dueBounds = composeRule.onNodeWithTag("task_due_${overdueId.value}")
            .fetchSemanticsNode().boundsInRoot
        val errorPixels = countTint(
            bitmap = bitmap,
            left = dueBounds.left,
            right = dueBounds.right,
            top = dueBounds.top,
            bottom = dueBounds.bottom,
            expected = errorColor,
        )
        assertTrue("逾期截止行应包含 error 语义色像素（实测=$errorPixels）", errorPixels >= 15)

        val overdueSaved = runBlocking { taskRepository.get(overdueId) }
        assertEquals("逾期任务不得自动结项", GtdPosition.INBOX, overdueSaved?.position)
        assertNull("逾期任务 closedOutcome 应为 null", overdueSaved?.closedOutcome)

        saveEvidence("rows_light", bitmap)
    }

    /**
     * AC-T-17 / FR-G-14：点按任务行 Checkbox 触发 220ms 完成退场并落入“已结束”，且不误触打开详情。
     */
    @Test
    fun AC_T_17_FR_G_14_checkbox_click_completes_into_ended_without_opening_detail() {
        val taskId = seedTask(title = "点框完成任务", detail = "核对完成退场动画后落库")
        createViewModel()
        var openedDetail: TaskId? = null
        setLiveTodoHost(onOpenTaskDetail = { openedDetail = it })

        composeRule.waitUntil(5_000) { vm.uiState.value.visibleTasks.size == 1 }
        composeRule.onNodeWithTag("task_checkbox_${taskId.value}").performClick()

        composeRule.waitUntil(5_000) {
            runBlocking { taskRepository.get(taskId)?.position == GtdPosition.ENDED }
        }
        composeRule.waitForIdle()

        assertNull("点击 Checkbox 不应误触发打开任务详情", openedDetail)
        composeRule.onNodeWithText("点框完成任务").assertDoesNotExist()
        val persisted = runBlocking { taskRepository.get(taskId) }
        assertEquals(GtdPosition.ENDED, persisted?.position)
        assertEquals(ClosedOutcome.COMPLETED, persisted?.closedOutcome)

        // 切至已结束清单：任务出现、显示“已完成”标签，Checkbox 呈已勾选且禁用。
        composeRule.runOnIdle { vm.selectPosition(GtdPosition.ENDED) }
        composeRule.waitUntil(5_000) { vm.uiState.value.position == GtdPosition.ENDED }
        composeRule.onNodeWithText("点框完成任务").assertIsDisplayed()
        composeRule.onNodeWithText("已完成").assertIsDisplayed()
        composeRule.onNodeWithTag("task_checkbox_${taskId.value}")
            .assertIsOn()
            .assertIsNotEnabled()
    }

    /**
     * AC-T-17 / BR-46 / BR-60：右滑任务行露出左侧 primaryContainer + ic_check + “完成”，
     * 超阈值松手触发完成进入“已结束”，绝不拉出 GTD 左抽屉。
     */
    @Test
    fun AC_T_17_BR_60_right_swipe_completes_shows_primary_band_and_does_not_open_drawer() {
        val taskId = seedTask(
            title = "右滑完成任务",
            detail = "向右滑动露出左侧完成色条",
            dueDate = today,
            dueTime = LocalTime.of(19, 0),
        )
        createViewModel()
        var drawerOpened = false
        setLiveTodoHost(onOpenSelector = { drawerOpened = true })

        composeRule.waitUntil(5_000) { vm.uiState.value.visibleTasks.size == 1 }
        composeRule.waitForIdle()

        val density = composeRule.density.density
        val rowNode = composeRule.onNodeWithTag("task_row_${taskId.value}").fetchSemanticsNode()
        val rowLeft = with(composeRule.density) { 16.dp.toPx() }
        val centerY = rowNode.boundsInRoot.center.y

        // 右滑 130dp 保持截帧后退回 0 松手：校验左侧 primaryContainer + ic_check 像素，不提交。
        withHeldSwipe("右滑完成任务", dxDp = 130f, releaseDp = 0f) { bitmap ->
            assertPixel(bitmap, rowLeft + 6f * density, centerY, primaryContainer, "右滑左侧完成色条")
            val checkPixels = countTint(
                bitmap = bitmap,
                left = rowLeft + 12f * density,
                right = rowLeft + 44f * density,
                top = centerY - 14f * density,
                bottom = centerY + 14f * density,
                expected = onPrimaryContainer,
            )
            assertTrue("右滑应渲染 ic_check 图标（像素=$checkPixels）", checkPixels >= 20)
            saveEvidence("swipe_right_complete", bitmap)
        }
        assertFalse("右滑任务行绝不可拉出 GTD 左抽屉", drawerOpened)
        assertEquals(GtdPosition.INBOX, runBlocking { taskRepository.get(taskId)?.position })

        // 超阈值右滑松手：进入完成流并落入已结束，且仍不打开抽屉。
        composeRule.onNodeWithTag("task_row_${taskId.value}").performTouchInput { swipeRight() }
        composeRule.waitUntil(5_000) {
            runBlocking { taskRepository.get(taskId)?.position == GtdPosition.ENDED }
        }
        composeRule.waitForIdle()

        assertFalse("超阈值右滑任务行绝不可拉出 GTD 左抽屉", drawerOpened)
        val saved = runBlocking { taskRepository.get(taskId) }
        assertEquals(GtdPosition.ENDED, saved?.position)
        assertEquals(ClosedOutcome.COMPLETED, saved?.closedOutcome)
    }

    /**
     * AC-T-17 / BR-46：左滑任务行露出右侧 errorContainer + ic_close + “放弃”，
     * 超阈值松手触发放弃进入“已结束”并在已结束页显示“已放弃”。
     */
    @Test
    fun AC_T_17_BR_46_left_swipe_abandons_shows_error_band_and_displays_abandoned_in_ended() {
        val doneBefore = seedTask(
            title = "此前已完成任务",
            detail = "保留于已结束清单用于对比展示",
            dueDate = today.minusDays(2),
            closedOutcome = ClosedOutcome.COMPLETED,
            createdOffsetSeconds = 1,
        )
        val taskId = seedTask(
            title = "左滑放弃任务",
            detail = "向左滑动露出右侧放弃色条",
            dueDate = today.plusDays(1),
            createdOffsetSeconds = 2,
        )
        createViewModel()
        setLiveTodoHost()

        composeRule.waitUntil(5_000) { vm.uiState.value.visibleTasks.size == 1 }
        composeRule.waitForIdle()

        val density = composeRule.density.density
        val rootWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val rowNode = composeRule.onNodeWithTag("task_row_${taskId.value}").fetchSemanticsNode()
        val rowRight = rootWidth - with(composeRule.density) { 16.dp.toPx() }
        val centerY = rowNode.boundsInRoot.center.y

        withHeldSwipe("左滑放弃任务", dxDp = -130f, releaseDp = 0f) { bitmap ->
            assertPixel(bitmap, rowRight - 6f * density, centerY, errorContainer, "左滑右侧放弃色条")
            val closePixels = countTint(
                bitmap = bitmap,
                left = rowRight - 44f * density,
                right = rowRight - 12f * density,
                top = centerY - 14f * density,
                bottom = centerY + 14f * density,
                expected = onErrorContainer,
            )
            assertTrue("左滑应渲染 ic_close 图标（像素=$closePixels）", closePixels >= 20)
            saveEvidence("swipe_left_abandon", bitmap)
        }
        assertEquals(GtdPosition.INBOX, runBlocking { taskRepository.get(taskId)?.position })

        // 超阈值左滑放弃，落入已结束清单。
        composeRule.onNodeWithTag("task_row_${taskId.value}").performTouchInput { swipeLeft() }
        composeRule.waitUntil(5_000) {
            runBlocking { taskRepository.get(taskId)?.position == GtdPosition.ENDED }
        }
        composeRule.waitForIdle()

        val saved = runBlocking { taskRepository.get(taskId) }
        assertEquals(GtdPosition.ENDED, saved?.position)
        assertEquals(ClosedOutcome.ABANDONED, saved?.closedOutcome)

        // 切至已结束清单：同时验证“已完成”与“已放弃”标签并截图留证。
        composeRule.runOnIdle { vm.selectPosition(GtdPosition.ENDED) }
        composeRule.waitUntil(5_000) { vm.uiState.value.visibleTasks.size == 2 }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("此前已完成任务").assertIsDisplayed()
        composeRule.onNodeWithText("左滑放弃任务").assertIsDisplayed()
        composeRule.onNodeWithText("已完成").assertIsDisplayed()
        composeRule.onNodeWithText("已放弃").assertIsDisplayed()
        composeRule.onNodeWithTag("task_checkbox_${doneBefore.value}").assertIsOn().assertIsNotEnabled()
        composeRule.onNodeWithTag("task_checkbox_${taskId.value}").assertIsOff().assertIsNotEnabled()

        saveEvidence("ended_list")
    }

    /**
     * AC-T-17 / FR-G-14：项目行右滑完成 / 左滑放弃均先弹影响数量确认弹窗，确认后批量结项。
     */
    @Test
    fun AC_T_17_project_row_right_complete_and_left_abandon_show_count_confirmation_dialog() {
        val completeProjId = seedProject("完成项目")
        val c1 = seedTask("完成子任务1", projectId = completeProjId, createdOffsetSeconds = 1)
        val c2 = seedTask("完成子任务2", projectId = completeProjId, createdOffsetSeconds = 2)

        val abandonProjId = seedProject("放弃项目")
        val a1 = seedTask("放弃子任务1", projectId = abandonProjId, createdOffsetSeconds = 3)
        val a2 = seedTask("放弃子任务2", projectId = abandonProjId, createdOffsetSeconds = 4)

        createViewModel()
        setLiveTodoHost()
        composeRule.waitUntil(5_000) { vm.uiState.value.visibleTasks.size == 4 }
        composeRule.waitForIdle()

        // 项目行右滑 → 完成确认弹窗（含影响数量 2），先取消不改数据。
        composeRule.onNodeWithText("完成项目（2）").performTouchInput { swipeRight() }
        composeRule.onNodeWithText("将完成 2 个子待办").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()
        assertEquals(ProjectStatus.ACTIVE, runBlocking { projectRepository.get(completeProjId)?.status })

        // 再次右滑并确认 → 项目与 2 个子待办批量完成。
        composeRule.onNodeWithText("完成项目（2）").performTouchInput { swipeRight() }
        composeRule.onNodeWithText("将完成 2 个子待办").assertIsDisplayed()
        composeRule.onNodeWithText("确认").performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { projectRepository.get(completeProjId)?.status == ProjectStatus.COMPLETED }
        }
        assertEquals(ClosedOutcome.COMPLETED, runBlocking { taskRepository.get(c1)?.closedOutcome })
        assertEquals(ClosedOutcome.COMPLETED, runBlocking { taskRepository.get(c2)?.closedOutcome })

        // 项目行左滑 → 放弃确认弹窗（含影响数量 2），确认后批量放弃。
        composeRule.onNodeWithText("放弃项目（2）").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("将放弃 2 个子待办").assertIsDisplayed()
        composeRule.onNodeWithText("确认").performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { projectRepository.get(abandonProjId)?.status == ProjectStatus.ABANDONED }
        }
        assertEquals(ClosedOutcome.ABANDONED, runBlocking { taskRepository.get(a1)?.closedOutcome })
        assertEquals(ClosedOutcome.ABANDONED, runBlocking { taskRepository.get(a2)?.closedOutcome })
    }

    /**
     * AC-T-17 / FR-G-14 / FR-G-09：已结项行 Checkbox 与左右滑均禁用，过去日期不标逾期，但仍可点入详情。
     */
    @Test
    fun AC_T_17_FR_G_14_closed_rows_disable_checkbox_and_swipe_and_no_overdue_red() {
        val completedTask = Task(
            id = TaskId(21L),
            title = "已完成历史任务",
            detail = "完成后的详情仍可查看",
            position = GtdPosition.ENDED,
            positionBeforeClose = GtdPosition.INBOX,
            closedOutcome = ClosedOutcome.COMPLETED,
            closedAt = now,
            closedOn = today,
            dueDate = today.minusDays(3),
            dueTime = LocalTime.of(9, 0),
            createdAt = now,
            updatedAt = now,
        )
        val abandonedTask = Task(
            id = TaskId(22L),
            title = "已放弃历史任务",
            position = GtdPosition.ENDED,
            positionBeforeClose = GtdPosition.INBOX,
            closedOutcome = ClosedOutcome.ABANDONED,
            closedAt = now,
            closedOn = today,
            dueDate = today.minusDays(2),
            createdAt = now,
            updatedAt = now,
        )

        var completedCalls = 0
        var abandonedCalls = 0
        var openedDetail: TaskId? = null

        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = TodoUiState(
                        position = GtdPosition.ENDED,
                        groups = listOf(TaskGroup(null, listOf(completedTask, abandonedTask))),
                        visibleTasks = listOf(completedTask, abandonedTask),
                        referenceDate = today,
                        // 即使外部误传入已结项 ID，UI 层亦不得标红逾期。
                        overdueTaskIds = setOf(completedTask.id, abandonedTask.id),
                    ),
                    onCompleteTask = { completedCalls += 1 },
                    onAbandonTask = { abandonedCalls += 1 },
                    onOpenTaskDetail = { openedDetail = it },
                )
            }
        }

        composeRule.onNodeWithTag("task_checkbox_21").assertIsOn().assertIsNotEnabled()
        composeRule.onNodeWithTag("task_checkbox_22").assertIsOff().assertIsNotEnabled()
        composeRule.onNodeWithText("已完成").assertIsDisplayed()
        composeRule.onNodeWithText("已放弃").assertIsDisplayed()
        assertTrue(
            "已结项任务不得显示“已逾期 · ”前缀",
            composeRule.onAllNodesWithText("已逾期", substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )

        composeRule.onNodeWithTag("task_row_21").performTouchInput { swipeRight() }
        composeRule.onNodeWithTag("task_row_21").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("task_row_22").performTouchInput { swipeRight() }
        composeRule.onNodeWithTag("task_row_22").performTouchInput { swipeLeft() }
        composeRule.runOnIdle {
            assertEquals("已结项行不得触发完成", 0, completedCalls)
            assertEquals("已结项行不得触发放弃", 0, abandonedCalls)
        }

        composeRule.onNodeWithText("已完成历史任务").performClick()
        composeRule.runOnIdle { assertEquals(TaskId(21L), openedDetail) }
    }

    /** 深色主题任务行组成截图（Checkbox、详情、正常截止、逾期高亮）。 */
    @Test
    fun AC_D_09_r5g3_rows_dark_screenshot() {
        val projectId = seedProject("具身智能课程")
        seedTask(
            title = "提交实验报告",
            detail = "汇总仿真轨迹图、误差曲线与消融对比结果",
            dueDate = today.plusDays(1),
            dueTime = LocalTime.of(9, 30),
            projectId = projectId,
            createdOffsetSeconds = 1,
        )
        seedTask(
            title = "买牛奶",
            projectId = projectId,
            createdOffsetSeconds = 2,
        )
        seedTask(
            title = "复核概率论错题",
            detail = "第三章条件分布与期望习题 1-6",
            dueDate = today.minusDays(1),
            dueTime = LocalTime.of(18, 0),
            createdOffsetSeconds = 3,
        )

        createViewModel()
        setLiveTodoHost(darkTheme = true)
        composeRule.waitUntil(5_000) { vm.uiState.value.visibleTasks.size == 3 }
        composeRule.onNodeWithText("已逾期 · 11月26日 18:00").assertIsDisplayed()
        saveEvidence("rows_dark")
    }
}
