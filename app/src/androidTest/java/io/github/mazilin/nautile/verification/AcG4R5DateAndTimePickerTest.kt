package io.github.mazilin.nautile.verification

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.repository.RoomProjectRepository
import io.github.mazilin.nautile.data.repository.RoomTaskRepository
import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import io.github.mazilin.nautile.domain.rule.TaskTimeRules
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.ui.screen.todo.NewTaskSheet
import io.github.mazilin.nautile.ui.screen.todo.TodoViewModel
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R5-G4 独立验收（模拟器 UI + 几何防重叠 + 截图取证）：
 * 日期表头重叠修复 + 24h 时间严格校验 + M3 24h 时间圆盘选择页 + 分层系统返回。
 *
 * 验收依据：
 * - `docs/需求基线.md` v2.16（FR-D-02、FR-M-01、AC-D-07、AC-M-01）
 * - `.plans/设计/03_交互与动效规格.md`（§2.5、§4 G4）
 *
 * 运行环境说明：在 emulator-5554（API 36，模拟器，非真机）执行；
 * 截图写入 `Downloads/nautile_evidence/r5g4_*.png`，拉取至 `docs/验证记录/门禁/` 人工目视核对。
 */
@RunWith(AndroidJUnit4::class)
class AcG4R5DateAndTimePickerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now: Instant = Instant.parse("2026-11-27T09:00:00Z")
    private val clock = Clock { now }

    private lateinit var database: NautileDatabase
    private lateinit var taskRepository: RoomTaskRepository
    private lateinit var projectRepository: RoomProjectRepository
    private lateinit var vm: TodoViewModel
    private var keyboardController: SoftwareKeyboardController? = null
    private var sheetDismissedCount = 0

    @Before
    fun setUp() {
        sheetDismissedCount = 0
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NautileDatabase::class.java,
        ).allowMainThreadQueries().build()
        taskRepository = RoomTaskRepository(database)
        projectRepository = RoomProjectRepository(database)
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
            settingsRepository = MemorySettingsRepository(),
            taskCommands = TaskCommands(taskRepository, projectRepository, clock),
            projectCommands = ProjectCommands(projectRepository, taskRepository, clock),
        )
    }

    private fun setNewTaskHost(
        darkTheme: Boolean = false,
        viewportWidth: Dp? = null,
    ) {
        composeRule.setContent {
            keyboardController = LocalSoftwareKeyboardController.current
            NautileTheme(darkTheme = darkTheme) {
                val state by vm.newTask.collectAsState()
                val projects by vm.activeProjects.collectAsState()
                // 模拟宿主外层 BackHandler：仅当内部子视图未拦截时才关闭新建面板。
                BackHandler(enabled = state != null) {
                    sheetDismissedCount += 1
                    if (vm.requestNewTaskDismiss()) vm.closeNewTask()
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    val hostModifier = if (viewportWidth != null) {
                        Modifier.width(viewportWidth).fillMaxSize()
                    } else {
                        Modifier.fillMaxSize()
                    }
                    Surface(modifier = hostModifier) {
                        state?.let { current ->
                            NewTaskSheet(
                                state = current,
                                projects = projects,
                                onKindChanged = vm::onNewTaskKindChanged,
                                onTargetChanged = vm::onNewTaskTargetChanged,
                                onTitleChanged = vm::onNewTaskTitleChanged,
                                onDetailChanged = vm::onNewTaskDetailChanged,
                                onProjectNameChanged = vm::onNewProjectNameChanged,
                                onPriorityChanged = vm::onNewTaskPriorityChanged,
                                onDueDateChanged = vm::onNewTaskDueDateChanged,
                                onDueTimeChanged = vm::onNewTaskDueTimeChanged,
                                onProjectChanged = vm::onNewTaskProjectChanged,
                                onProjectDraftChanged = vm::onNewTaskProjectDraftChanged,
                                onCreateProjectForTask = vm::createProjectForNewTask,
                                onClearDetail = { vm.onNewTaskDetailChanged("") },
                                onCreate = { vm.createNewTask {} },
                                onCreateProject = { vm.createProject {} },
                                onCancel = vm::closeNewTask,
                                onDismissRequest = {
                                    sheetDismissedCount += 1
                                    if (vm.requestNewTaskDismiss()) vm.closeNewTask()
                                },
                                visible = true,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openNewTaskAndCalendar() {
        composeRule.runOnIdle { vm.openNewTask() }
        composeRule.waitUntil(5_000) { vm.newTask.value != null }
        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("选择截止日期").assertIsDisplayed()
        composeRule.waitForIdle()
    }

    private fun selectDay(date: LocalDate) {
        val monthEn = date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val labelEn = "$monthEn ${date.dayOfMonth}, ${date.year}"
        val labelZh = "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
        val matcher = (hasText(labelEn, substring = true) or hasText(labelZh, substring = true)) and
            hasClickAction()
        val clickable = composeRule.onAllNodes(matcher)
        val nodes = clickable.fetchSemanticsNodes()
        assertTrue(
            "日期 $labelEn / $labelZh 不可点击：\n" +
                composeRule.onRoot(useUnmergedTree = true).printToString(),
            nodes.isNotEmpty(),
        )
        clickable.onFirst().performClick()
    }

    private fun pressSystemBack() {
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun collectAllNodes(root: SemanticsNode): List<SemanticsNode> {
        val list = mutableListOf<SemanticsNode>()
        fun dfs(node: SemanticsNode) {
            list += node
            node.children.forEach { dfs(it) }
        }
        dfs(root)
        return list
    }

    /**
     * 实测 DatePicker 星期表头行与第一排日期格边界，严禁任何垂直重叠（G4-A1 / R5-3）。
     */
    private fun assertCalendarWeekdayHeaderDoesNotOverlapFirstDateRow() {
        composeRule.waitForIdle()
        val root = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val allNodes = collectAllNodes(root)

        val weekdayNames = buildSet {
            val locales = listOf(Locale.getDefault(), Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE)
            for (day in DayOfWeek.entries) {
                for (locale in locales) {
                    add(day.getDisplayName(TextStyle.FULL, locale))
                    add(day.getDisplayName(TextStyle.SHORT, locale))
                }
            }
        }

        // M3 DatePicker 的 WeekDays 行：非点击节点，contentDescription 为星期名（如 Sunday..Saturday）。
        val weekdayNodes = allNodes.filter { node ->
            val descs = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            val clickable = node.config.contains(SemanticsActions.OnClick)
            !clickable &&
                node.boundsInRoot.height > 0f &&
                descs.any { desc -> weekdayNames.any { name -> desc.equals(name, ignoreCase = true) } }
        }

        // M3 DatePicker 日期格：可点击节点，文本含当月数字 1..31 且位于日历区域内。
        val today = LocalDate.now()
        val monthEn = today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val monthZh = "${today.year}年${today.monthValue}月"
        val dateCellNodes = allNodes.filter { node ->
            if (!node.config.contains(SemanticsActions.OnClick)) return@filter false
            if (node.boundsInRoot.height <= 0f) return@filter false
            val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            val descs = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            val combined = texts + descs
            combined.any { str ->
                str.contains(monthEn, ignoreCase = true) || str.contains(monthZh)
            } && combined.any { str -> str.any { it.isDigit() } }
        }

        val treeDump = composeRule.onRoot(useUnmergedTree = true).printToString()
        assertEquals(
            "应测量到 7 个星期表头节点，实际 ${weekdayNodes.size}：\n$treeDump",
            7,
            weekdayNodes.size,
        )
        assertTrue(
            "应测量到当月至少 28 个日期格节点，实际 ${dateCellNodes.size}：\n$treeDump",
            dateCellNodes.size >= 28,
        )

        val weekdayMaxBottom = weekdayNodes.maxOf { it.boundsInRoot.bottom }
        val firstDateMinTop = dateCellNodes.minOf { it.boundsInRoot.top }

        assertTrue(
            "星期表头底部不得与第一排日期顶部垂直重叠：" +
                "weekdayMaxBottom=$weekdayMaxBottom, firstDateMinTop=$firstDateMinTop",
            weekdayMaxBottom <= firstDateMinTop + 0.5f,
        )
    }

    private fun captureAndSaveEvidence(filePrefix: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val fileName = "r5g4_$filePrefix.png"
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

    // ---- 1. 防重叠几何验证（默认屏宽 + 360dp 窄屏） + 浅/深色日历截图 ----

    @Test
    fun AC_D_07_G4_A1_calendar_weekday_header_does_not_overlap_first_date_row_light() {
        createViewModel()
        setNewTaskHost(darkTheme = false)
        openNewTaskAndCalendar()

        assertCalendarWeekdayHeaderDoesNotOverlapFirstDateRow()
        captureAndSaveEvidence("calendar_light")
    }

    @Test
    fun AC_D_07_G4_A1_calendar_weekday_header_does_not_overlap_at_360dp_viewport() {
        createViewModel()
        setNewTaskHost(darkTheme = false, viewportWidth = 360.dp)
        openNewTaskAndCalendar()

        assertCalendarWeekdayHeaderDoesNotOverlapFirstDateRow()
    }

    @Test
    fun AC_D_07_G4_V1_calendar_dark_screenshot_and_no_overlap() {
        createViewModel()
        setNewTaskHost(darkTheme = true)
        openNewTaskAndCalendar()

        assertCalendarWeekdayHeaderDoesNotOverlapFirstDateRow()
        captureAndSaveEvidence("calendar_dark")
    }

    // ---- 2. 时间行禁用/启用 + 24h 严格校验 + 确认回填 + 清除时间 + 报错/圆盘截图 ----

    @Test
    fun AC_D_07_AC_M_01_time_row_validation_confirm_and_clear() {
        createViewModel()
        setNewTaskHost(darkTheme = false)
        openNewTaskAndCalendar()

        // 2.1 未选日期时“时间”行禁用且点击不可进入圆盘。
        composeRule.onNodeWithTag("date_panel_time_row")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("先选择日期，再设置时间").assertExists()
        composeRule.onNodeWithTag("date_panel_time_row").performClick()
        composeRule.onNodeWithTag("time_picker_panel").assertDoesNotExist()

        // 2.2 选择日期后“时间”行启用，显示“未设置”；点击进入 24h 圆盘。
        val today = LocalDate.now()
        selectDay(today)
        composeRule.waitUntil(5_000) { vm.newTask.value?.dueDate == today }
        composeRule.onNodeWithTag("date_panel_time_row")
            .performScrollTo()
            .assertIsEnabled()
            .assertTextContains("未设置", substring = true)
            .performClick()

        composeRule.onNodeWithTag("time_picker_panel").assertIsDisplayed()
        composeRule.onNodeWithText("选择时间").assertIsDisplayed()
        captureAndSaveEvidence("time_picker_light")

        // 2.3 严格 24h 校验：24:00 / 25:99 / 9:30 均不被静默修正、内联报错、确认禁用。
        val invalidInputs = listOf("24:00", "25:99", "9:30")
        invalidInputs.forEach { bad ->
            composeRule.onNodeWithTag("time_picker_input")
                .performScrollTo()
                .performTextReplacement(bad)
            composeRule.waitForIdle()
            // 文本绝不被静默修正。
            val editableText = composeRule.onNodeWithTag("time_picker_input")
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.EditableText)
                ?.text
            assertEquals("非法时间 \"$bad\" 不得被静默修正", bad, editableText)
            composeRule.onNodeWithTag("time_picker_confirm")
                .performScrollTo()
                .assertIsNotEnabled()
            composeRule.onNodeWithTag("time_picker_error", useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()
                .assertTextContains(TaskTimeRules.INVALID_TIME_MESSAGE)
            if (bad == "25:99") {
                composeRule.runOnIdle { keyboardController?.hide() }
                composeRule.waitForIdle()
                captureAndSaveEvidence("time_picker_invalid")
            }
        }

        // 2.4 改为合法 18:45 → 错误消失、确认启用 → 点确认返回日历且时间行显示 18:45。
        composeRule.onNodeWithTag("time_picker_input")
            .performScrollTo()
            .performTextReplacement("18:45")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("time_picker_error", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("time_picker_confirm")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeRule.waitUntil(5_000) { vm.newTask.value?.dueTime == LocalTime.of(18, 45) }
        composeRule.onNodeWithTag("time_picker_panel").assertDoesNotExist()
        composeRule.onNodeWithText("选择截止日期").assertExists()
        composeRule.onNodeWithTag("date_panel_time_row")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("18:45", substring = true)

        // 2.5 点击清除时间 → 恢复“未设置”、清除图标消失、dueTime == null、保留 dueDate。
        composeRule.onNodeWithTag("date_panel_clear_time")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(5_000) { vm.newTask.value?.dueTime == null }
        assertEquals("清除时间不应清除已选日期", today, vm.newTask.value?.dueDate)
        composeRule.onNodeWithTag("time_picker_panel").assertDoesNotExist()
        composeRule.onNodeWithTag("date_panel_clear_time").assertDoesNotExist()
        composeRule.onNodeWithTag("date_panel_time_row")
            .performScrollTo()
            .assertTextContains("未设置", substring = true)
    }

    // ---- 3. 24h 时间圆盘深色截图 ----

    @Test
    fun AC_M_01_G4_V1_time_picker_dark_screenshot() {
        createViewModel()
        setNewTaskHost(darkTheme = true)
        openNewTaskAndCalendar()

        val today = LocalDate.now()
        selectDay(today)
        composeRule.waitUntil(5_000) { vm.newTask.value?.dueDate == today }
        composeRule.onNodeWithTag("date_panel_time_row")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("time_picker_panel").assertIsDisplayed()
        captureAndSaveEvidence("time_picker_dark")
    }

    // ---- 4. 系统返回分层递退：圆盘 → 日历 → 新建主面板（一次仅退一层） ----

    @Test
    fun AC_D_07_AC_M_01_system_back_steps_down_one_layer_at_a_time() {
        createViewModel()
        setNewTaskHost(darkTheme = false)
        openNewTaskAndCalendar()

        val today = LocalDate.now()
        selectDay(today)
        composeRule.waitUntil(5_000) { vm.newTask.value?.dueDate == today }
        composeRule.onNodeWithTag("date_panel_time_row")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("time_picker_panel").assertIsDisplayed()

        // 第 1 次系统返回：仅从时间圆盘退回日历视图，不关面板。
        pressSystemBack()
        composeRule.onNodeWithTag("time_picker_panel").assertDoesNotExist()
        composeRule.onNodeWithText("选择截止日期").assertIsDisplayed()
        assertEquals("第 1 次返回不得关闭新建面板", 0, sheetDismissedCount)
        assertNotNull("第 1 次返回后新建面板状态应保留", vm.newTask.value)

        // 第 2 次系统返回：仅从日历视图退回新建主面板，不关面板。
        pressSystemBack()
        composeRule.onNodeWithText("选择截止日期").assertDoesNotExist()
        composeRule.onNodeWithText("新建").assertIsDisplayed()
        composeRule.onNodeWithText("创建").assertIsDisplayed()
        assertEquals("第 2 次返回不得关闭新建面板", 0, sheetDismissedCount)
        assertNotNull("第 2 次返回后新建面板状态应保留", vm.newTask.value)

        // 第 3 次系统返回：此时才关闭新建主面板。
        pressSystemBack()
        assertEquals("第 3 次返回才应触发关闭新建面板", 1, sheetDismissedCount)
        assertNull("第 3 次返回后新建面板应关闭", vm.newTask.value)
    }

    /** 仅测试使用：设置仓库替身，避免连接真实 DataStore。 */
    private class MemorySettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(UserSettings())
        override val settings: Flow<UserSettings> = state

        override suspend fun setDefaultPage(page: AppPage) {
            state.update { it.copy(defaultPage = page) }
        }

        override suspend fun setLastPage(page: AppPage) {
            state.update { it.copy(lastPage = page) }
        }

        override suspend fun setLastGtdPosition(position: GtdPosition) {
            state.update { it.copy(lastGtdPosition = position) }
        }

        override suspend fun setPredictiveBackEnabled(enabled: Boolean) {
            state.update { it.copy(predictiveBackEnabled = enabled) }
        }

        override suspend fun setViewOptions(position: GtdPosition, options: String) {
            state.update {
                it.copy(perModuleViewOptions = it.perModuleViewOptions + (position to options))
            }
        }
    }
}
