package io.github.mazilin.nautile.verification

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.repository.RoomProjectRepository
import io.github.mazilin.nautile.data.repository.RoomTaskRepository
import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.ui.screen.todo.NewTaskSheet
import io.github.mazilin.nautile.ui.screen.todo.NewTaskUiState
import io.github.mazilin.nautile.ui.screen.todo.ProjectDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.TaskDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.TodoViewModel
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
 * R5-G5 独立验收（模拟器 UI + 持久化 + 截图取证）：
 * 待办详情与新建同构统一（R5-8）+ 项目详情 chevron 纯图标折叠与去头部编辑按钮（R5-9）。
 *
 * 验收依据：
 * - `docs/需求基线.md` v2.16（4.27：`FR-D-01`、`FR-D-02`、`FR-D-03`、`FR-G-19`、`BR-68`、`BR-75`、
 *   `AC-D-06`、`AC-D-07`、`AC-D-08`、`AC-D-09`、`AC-T-29`）
 * - `.plans/设计/03_交互与动效规格.md`（§2.4 同构待办详情、§2.6 项目详情、§4 G5）
 *
 * 运行环境：emulator-5554（API 36，模拟器，非真机）。
 * 证据截图写入 `Downloads/nautile_evidence/r5g5_*.png`，拉取至 `docs/验证记录/门禁/` 人工目视核对。
 */
@RunWith(AndroidJUnit4::class)
class AcG5R5UnifiedDetailAndProjectTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now: Instant = Instant.parse("2026-11-27T09:00:00Z")
    private val clock = Clock { now }

    private lateinit var database: NautileDatabase
    private lateinit var taskRepository: RoomTaskRepository
    private lateinit var projectRepository: RoomProjectRepository
    private lateinit var vm: TodoViewModel
    private var keyboardController: SoftwareKeyboardController? = null

    @Before
    fun setUp() {
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

    private fun seedProject(
        name: String,
        status: ProjectStatus = ProjectStatus.ACTIVE,
    ): ProjectId = runBlocking {
        projectRepository.insert(
            Project(
                id = ProjectId(0L),
                name = name,
                status = status,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun seedTask(
        title: String = "概率论刷题",
        detail: String? = null,
        priority: Priority = Priority.NONE,
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
        projectId: ProjectId? = null,
        position: GtdPosition = GtdPosition.INBOX,
        closedOutcome: ClosedOutcome? = null,
        positionBeforeClose: GtdPosition? = null,
    ): TaskId = runBlocking {
        taskRepository.insert(
            Task(
                id = TaskId(0L),
                title = title,
                projectId = projectId,
                position = if (closedOutcome != null) GtdPosition.ENDED else position,
                positionBeforeClose = if (closedOutcome != null) {
                    positionBeforeClose ?: position
                } else {
                    null
                },
                priority = priority,
                dueDate = dueDate,
                dueTime = dueTime,
                closedOutcome = closedOutcome,
                closedAt = if (closedOutcome != null) now else null,
                closedOn = if (closedOutcome != null) LocalDate.of(2026, 11, 27) else null,
                createdAt = now,
                updatedAt = now,
                detail = detail,
            ),
        )
    }

    private fun dbTask(id: TaskId): Task? = runBlocking { taskRepository.get(id) }

    private fun dbProject(id: ProjectId): Project? = runBlocking { projectRepository.get(id) }

    private fun waitTask(id: TaskId, predicate: (Task) -> Boolean) {
        composeRule.waitUntil(10_000) {
            runBlocking { taskRepository.get(id)?.let(predicate) == true }
        }
    }

    private fun waitProject(id: ProjectId, predicate: (Project) -> Boolean) {
        composeRule.waitUntil(10_000) {
            runBlocking { projectRepository.get(id)?.let(predicate) == true }
        }
    }

    /** 与 MainActivity 同层接线：详情返回守卫由 TodoViewModel.requestDetailLeave 驱动。 */
    private fun setDetailHost(darkTheme: Boolean = false) {
        composeRule.setContent {
            keyboardController = LocalSoftwareKeyboardController.current
            NautileTheme(darkTheme = darkTheme) {
                vm.uiState.collectAsState()
                val taskDetail by vm.taskDetail.collectAsState()
                val projectDetail by vm.projectDetail.collectAsState()
                val projects by vm.activeProjects.collectAsState()

                val requestClose: () -> Unit = {
                    if (vm.requestDetailLeave()) vm.closeDetail()
                }
                // 外层系统返回：面板内子菜单/日历/时间圆盘会先拦截；落到此处才校验并尝试关闭面板。
                BackHandler(enabled = taskDetail != null || projectDetail != null) {
                    requestClose()
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        projectDetail?.let { detail ->
                            ProjectDetailSheet(
                                state = detail,
                                onNameChanged = vm::onProjectNameChanged,
                                onCommitName = vm::commitProjectName,
                                onToggleCollapsed = {
                                    vm.toggleProjectCollapsed(detail.project.id)
                                },
                                onOpenChild = { childId ->
                                    vm.closeDetail()
                                    vm.openTaskDetail(childId)
                                },
                                onCloseRequested = requestClose,
                                onReopen = { vm.reopenProject(detail.project.id) },
                                visible = true,
                            )
                        }
                        taskDetail?.let { detail ->
                            TaskDetailSheet(
                                state = detail,
                                onTitleChanged = vm::onTaskTitleChanged,
                                onCommitTitle = vm::commitTaskTitle,
                                onReopen = { vm.reopenTask(detail.task.id) },
                                onCloseRequested = requestClose,
                                onDetailChanged = vm::onTaskDetailChanged,
                                onCommitDetail = vm::commitTaskDetail,
                                visible = true,
                                projects = projects,
                                onPriorityChanged = vm::onTaskPriorityChanged,
                                onDueDateChanged = vm::onTaskDueDateChanged,
                                onDueTimeChanged = vm::onTaskDueTimeChanged,
                                onProjectChanged = vm::onTaskProjectChanged,
                                onGtdChanged = vm::moveTaskToGtdFromDetail,
                                onProjectDraftChanged = vm::onTaskProjectDraftChanged,
                                onCreateProjectForTask = vm::createProjectForTaskDetail,
                            )
                        }
                    }
                }
            }
        }
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

    private fun pressSystemBack() {
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun tapScrimForNode(tag: String) {
        composeRule.runOnIdle { keyboardController?.hide() }
        composeRule.waitForIdle()
        composeRule.onNode(isRoot() and hasAnyDescendant(hasTestTag(tag)))
            .performTouchInput { click(Offset(12f, 12f)) }
        composeRule.waitForIdle()
    }

    private fun selectDay(date: LocalDate) {
        val monthEn = date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val labelEn = "$monthEn ${date.dayOfMonth}, ${date.year}"
        val labelZh = "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
        val matcher = (hasText(labelEn, substring = true) or hasText(labelZh, substring = true)) and
            hasClickAction()
        val clickable = composeRule.onAllNodes(matcher)
        assertTrue(
            "日期 $labelEn / $labelZh 不可点击：\n" +
                composeRule.onRoot(useUnmergedTree = true).printToString(),
            clickable.fetchSemanticsNodes().isNotEmpty(),
        )
        clickable.onFirst().performClick()
    }

    private fun assertToolbarItemHasLabel(tag: String, expectedLabel: String) {
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        composeRule.onNode(
            hasText(expectedLabel) and hasAnyAncestor(hasTestTag(tag)),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    /** 截图保存到公共 Download/nautile_evidence/r5g5_*.png，覆盖同名旧文件便于 adb pull。 */
    private fun captureAndSaveEvidence(name: String) {
        composeRule.waitForIdle()
        val bitmap: Bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "r5g5_$name.png"
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/nautile_evidence"
        val resolver = context.contentResolver
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
        val written = resolver.openInputStream(uri)?.use { it.read() } ?: -1
        assertTrue("截图应写入且非空：$uri", written > 0)
        println("R5G5_SCREENSHOT name=$name file=$fileName uri=$uri")
    }

    // ==================== R5-8：待办同构详情 ====================

    @Test
    fun AC_D_06_AC_D_07_task_detail_isomorphic_layout_no_edit_button_and_four_icon_text_attrs() {
        val taskId = seedTask(title = "概率论刷题", position = GtdPosition.NEXT_ACTION)
        createViewModel()
        setDetailHost()
        openTaskDetail(taskId)

        // 顶部无任何编辑按钮，仅有顶部关闭图标按钮 detail_close_button。
        composeRule.onAllNodes(hasContentDescription("编辑", substring = true)).assertCountEquals(0)
        composeRule.onNodeWithTag("detail_close_button")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("关闭")
        // 活跃任务无底部“关闭”文字按钮，也不显示“重新打开”。
        composeRule.onAllNodesWithText("关闭").assertCountEquals(0)
        composeRule.onAllNodesWithText("重新打开").assertCountEquals(0)

        // 第一行标题、固定第二行详情同时可见，且详情位于标题正下方。
        val titleBounds = composeRule.onNodeWithTag("task_detail_title")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val detailBounds = composeRule.onNodeWithTag("task_detail_detail")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "详情输入框应固定在标题输入框下方：detail.top=${detailBounds.top} <= title.bottom=${titleBounds.bottom}",
            detailBounds.top >= titleBounds.bottom - 1f,
        )

        // 四属性工具栏均存在且图标+文字（未设置显示属性名，清单显示当前清单名），无第 5 项“编辑详情”。
        assertToolbarItemHasLabel("attr_date", "日期")
        assertToolbarItemHasLabel("attr_priority", "优先级")
        assertToolbarItemHasLabel("attr_project", "项目")
        assertToolbarItemHasLabel("attr_checklist", "下一步行动")
        composeRule.onAllNodes(hasContentDescription("编辑详情", substring = true))
            .assertCountEquals(0)
        listOf("标签", "提醒", "重复", "更多", "语音").forEach { forbidden ->
            composeRule.onAllNodesWithText(forbidden).assertCountEquals(0)
        }
    }

    @Test
    fun AC_D_06_AC_D_09_BR_68_task_title_and_detail_edit_save_and_clear_persist_across_reopen() {
        val taskId = seedTask(title = "旧标题", detail = "旧详情内容")
        createViewModel()
        setDetailHost()
        openTaskDetail(taskId)

        // 第一行标题编辑即保存（切换到详情框触发失焦提交）。
        composeRule.onNodeWithTag("task_detail_title").performTextReplacement("新标题保存")
        composeRule.onNodeWithTag("task_detail_detail").performClick()
        waitTask(taskId) { it.title == "新标题保存" }

        // 第二行详情编辑即保存（IME Done 提交）。
        composeRule.onNodeWithTag("task_detail_detail").performTextReplacement("第二行新详情")
        composeRule.onNodeWithTag("task_detail_detail").performImeAction()
        waitTask(taskId) { it.detail == "第二行新详情" }

        // 关闭并重开验证持久化。
        composeRule.onNodeWithTag("detail_close_button").performClick()
        composeRule.waitUntil(5_000) { vm.taskDetail.value == null }
        openTaskDetail(taskId)
        composeRule.onNodeWithTag("task_detail_title").assertTextContains("新标题保存")
        composeRule.onNodeWithTag("task_detail_detail").assertTextContains("第二行新详情")

        // 详情清空并失焦保存：落库为 null，重开仍为空。
        composeRule.onNodeWithTag("task_detail_detail").performTextReplacement("")
        composeRule.onNodeWithTag("task_detail_title").performClick()
        waitTask(taskId) { it.detail == null }

        composeRule.onNodeWithTag("detail_close_button").performClick()
        composeRule.waitUntil(5_000) { vm.taskDetail.value == null }
        openTaskDetail(taskId)
        assertNull("重开后详情应为 null", dbTask(taskId)?.detail)
        assertEquals("", vm.taskDetail.value?.detailValue)
    }

    @Test
    fun AC_D_06_AC_D_07_task_detail_four_attributes_save_immediately_including_24h_time_and_guards() {
        val activeProjectId = seedProject("活跃项目")
        val closedProjectId = seedProject("已结项项目", ProjectStatus.COMPLETED)
        val taskId = seedTask(title = "属性联测任务", position = GtdPosition.INBOX)
        createViewModel()
        setDetailHost()
        openTaskDetail(taskId)
        composeRule.waitUntil(5_000) { vm.activeProjects.value.any { it.id == activeProjectId } }

        // 1. 日期 + 24h 时间圆盘选择即保存 + 分层返回（圆盘 → 日历 → 表单）。
        val today = LocalDate.now()
        composeRule.onNodeWithTag("attr_date").performClick()
        composeRule.onNodeWithText("选择截止日期").assertIsDisplayed()
        selectDay(today)
        waitTask(taskId) { it.dueDate == today }
        composeRule.onNodeWithTag("date_panel_time_row")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag("time_picker_panel").assertIsDisplayed()
        // 测试分层返回：在时间圆盘按返回先回到日历，再进圆盘设置 14:30。
        pressSystemBack()
        composeRule.onNodeWithTag("time_picker_panel").assertDoesNotExist()
        composeRule.onNodeWithText("选择截止日期").assertIsDisplayed()

        composeRule.onNodeWithTag("date_panel_time_row").performScrollTo().performClick()
        composeRule.onNodeWithTag("time_picker_input")
            .performScrollTo()
            .performTextInput("14:30")
        composeRule.onNodeWithTag("time_picker_confirm")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        waitTask(taskId) { it.dueTime == LocalTime.of(14, 30) }
        pressSystemBack()
        composeRule.onNodeWithText("选择截止日期").assertDoesNotExist()
        assertToolbarItemHasLabel("attr_date", "今天 14:30")

        // 2. 优先级选择即保存。
        composeRule.onNodeWithTag("attr_priority").performClick()
        composeRule.onNodeWithContentDescription("优先级：高").performClick()
        waitTask(taskId) { it.priority == Priority.HIGH }
        assertToolbarItemHasLabel("attr_priority", "高")

        // 3. 项目选择即保存（仅列出活跃项目，不出现已结项项目）。
        composeRule.onNodeWithTag("attr_project").performClick()
        composeRule.onNodeWithContentDescription("项目：活跃项目").assertIsDisplayed()
        composeRule.onAllNodesWithText("已结项项目").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("项目：活跃项目").performClick()
        waitTask(taskId) { it.projectId == activeProjectId }
        assertToolbarItemHasLabel("attr_project", "活跃项目")

        // 4. 清单选择即保存 + ENDED 禁用不可直移。
        composeRule.onNodeWithTag("attr_checklist").performClick()
        composeRule.onNodeWithContentDescription("已结束，已结束不可直接移入")
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("移动到 GTD 清单：等待中").performClick()
        waitTask(taskId) { it.position == GtdPosition.WAITING }
        assertToolbarItemHasLabel("attr_checklist", "等待中")

        // 5. 项目重开守卫：任务属于已结项项目时移动清单触发 ProjectReopenPrompt，确认后项目重开并完成移动。
        val taskInClosedProject = seedTask(
            title = "归属已结项项目任务",
            projectId = closedProjectId,
            position = GtdPosition.INBOX,
        )
        openTaskDetail(taskInClosedProject)
        composeRule.onNodeWithTag("attr_checklist").performClick()
        composeRule.onNodeWithContentDescription("移动到 GTD 清单：下一步行动").performClick()
        val prompt = runBlocking {
            withTimeout(5_000) {
                vm.uiState.first { it.projectReopenPrompt != null }.projectReopenPrompt!!
            }
        }
        assertEquals(closedProjectId, prompt.projectId)
        assertEquals(GtdPosition.INBOX, dbTask(taskInClosedProject)?.position)

        composeRule.runOnIdle { vm.confirmProjectReopen() }
        waitProject(closedProjectId) { it.status == ProjectStatus.ACTIVE }
        waitTask(taskInClosedProject) { it.position == GtdPosition.NEXT_ACTION }
    }

    @Test
    fun AC_D_08_BR_75_task_blank_title_blocks_close_button_scrim_and_back_until_fixed() {
        val taskId = seedTask(title = "原有效标题")
        createViewModel()
        setDetailHost()
        openTaskDetail(taskId)

        composeRule.onNodeWithTag("task_detail_title").performTextReplacement("   ")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("标题不能为空").assertIsDisplayed()

        // 路径 1：顶部关闭图标按钮被阻止。
        composeRule.onNodeWithTag("detail_close_button").performClick()
        composeRule.waitForIdle()
        assertNotNull("空标题不得被关闭按钮关闭", vm.taskDetail.value)
        composeRule.onNodeWithText("请先修正无效标题").assertIsDisplayed()

        // 路径 2：系统返回被阻止。
        pressSystemBack()
        assertNotNull("空标题不得被系统返回关闭", vm.taskDetail.value)
        composeRule.onNodeWithText("标题不能为空").assertIsDisplayed()

        // 路径 3：点遮罩被阻止。
        tapScrimForNode("task_detail_title")
        assertNotNull("空标题不得被遮罩点按关闭", vm.taskDetail.value)
        composeRule.onNodeWithText("请先修正无效标题").assertIsDisplayed()
        assertEquals("空标题不得写入仓库", "原有效标题", dbTask(taskId)?.title)

        // 修正为有效标题并提交后可顺利关闭。
        composeRule.onNodeWithTag("task_detail_title").performTextReplacement("已修正标题")
        composeRule.onNodeWithTag("task_detail_title").performImeAction()
        waitTask(taskId) { it.title == "已修正标题" }
        composeRule.waitUntil(5_000) { vm.taskDetail.value?.canLeave == true }

        composeRule.onNodeWithTag("detail_close_button").performClick()
        composeRule.waitUntil(5_000) { vm.taskDetail.value == null }
        assertEquals("已修正标题", dbTask(taskId)?.title)
    }

    @Test
    fun AC_T_29_FR_G_19_closed_task_reopen_restores_position_and_reenables_checklist() {
        val taskId = seedTask(
            title = "已完成任务",
            position = GtdPosition.ENDED,
            closedOutcome = ClosedOutcome.COMPLETED,
            positionBeforeClose = GtdPosition.NEXT_ACTION,
        )
        createViewModel()
        setDetailHost()
        openTaskDetail(taskId)

        // 已结项任务清单入口禁用且显示“重新打开”。
        composeRule.onNodeWithTag("attr_checklist")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .assertContentDescriptionEquals("已结项，请在详情重新打开")
        composeRule.onNodeWithText("重新打开").assertIsDisplayed().performClick()

        waitTask(taskId) {
            it.closedOutcome == null &&
                it.closedAt == null &&
                it.position == GtdPosition.NEXT_ACTION
        }
        composeRule.waitUntil(5_000) { vm.taskDetail.value?.canReopen == false }
        composeRule.onAllNodesWithText("重新打开").assertCountEquals(0)
        composeRule.onNodeWithTag("attr_checklist").assertIsEnabled()
        assertToolbarItemHasLabel("attr_checklist", "下一步行动")
    }

    // ==================== R5-9：项目详情 ====================

    @Test
    fun AC_D_06_AC_D_08_AC_T_16_project_detail_no_edit_inline_rename_blank_guard_and_chevron_toggle() {
        val projectId = seedProject("课程项目")
        val child1 = seedTask(title = "子任务一", projectId = projectId, position = GtdPosition.NEXT_ACTION)
        val child2 = seedTask(
            title = "子任务二",
            projectId = projectId,
            closedOutcome = ClosedOutcome.COMPLETED,
            positionBeforeClose = GtdPosition.INBOX,
        )
        createViewModel()
        setDetailHost()
        openProjectDetail(projectId)

        // 1. 无头部编辑按钮，仅顶部关闭图标按钮 project_detail_close_button。
        composeRule.onAllNodes(hasContentDescription("编辑", substring = true)).assertCountEquals(0)
        composeRule.onNodeWithTag("project_detail_close_button")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("关闭")

        // 2. 子任务折叠按钮为纯 chevron 图标按钮：无可见文字“展开子任务/收起子任务”，带 contentDescription 且 ≥48dp。
        composeRule.onAllNodesWithText("收起子任务").assertCountEquals(0)
        composeRule.onAllNodesWithText("展开子任务").assertCountEquals(0)
        val toggleNode = composeRule.onNodeWithTag("project_children_toggle")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("收起子任务")
        val toggleBounds = toggleNode.fetchSemanticsNode().boundsInRoot
        val toggleWidthDp = toggleBounds.width / composeRule.density.density
        val toggleHeightDp = toggleBounds.height / composeRule.density.density
        assertTrue("折叠图标触控宽应 ≥48dp，实际 ${toggleWidthDp}dp", toggleWidthDp >= 48f)
        assertTrue("折叠图标触控高应 ≥48dp，实际 ${toggleHeightDp}dp", toggleHeightDp >= 48f)

        // 初始展开态可见子任务；点击收起后子任务隐藏、contentDescription 切换为“展开子任务”。
        composeRule.onNodeWithTag("project_child_${child1.value}").assertIsDisplayed()
        composeRule.onNodeWithTag("project_child_${child2.value}").assertIsDisplayed()
        toggleNode.performClick()
        composeRule.waitUntil(5_000) { vm.projectDetail.value?.collapsed == true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("project_children_toggle")
            .assertContentDescriptionEquals("展开子任务")
        composeRule.onAllNodesWithText("展开子任务").assertCountEquals(0)
        composeRule.onNodeWithTag("project_child_${child1.value}").assertDoesNotExist()
        composeRule.onNodeWithTag("project_child_${child2.value}").assertDoesNotExist()

        // 再次点击展开，子任务恢复可见且数据不变。
        composeRule.onNodeWithTag("project_children_toggle").performClick()
        composeRule.waitUntil(5_000) { vm.projectDetail.value?.collapsed == false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("project_children_toggle")
            .assertContentDescriptionEquals("收起子任务")
        composeRule.onNodeWithTag("project_child_${child1.value}").assertIsDisplayed()
        composeRule.onNodeWithTag("project_child_${child2.value}").assertIsDisplayed()

        // 3. 项目名称内联编辑即保存（重开验证持久化）。
        composeRule.onNodeWithTag("project_detail_name").performTextReplacement("课程项目重命名")
        composeRule.onNodeWithTag("project_detail_name").performImeAction()
        waitProject(projectId) { it.name == "课程项目重命名" }

        composeRule.onNodeWithTag("project_detail_close_button").performClick()
        composeRule.waitUntil(5_000) { vm.projectDetail.value == null }
        openProjectDetail(projectId)
        composeRule.onNodeWithTag("project_detail_name").assertTextContains("课程项目重命名")

        // 4. 空名称阻止关闭（关闭按钮、系统返回、遮罩三条路径均阻止）。
        composeRule.onNodeWithTag("project_detail_name").performTextReplacement("   ")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("项目名称不能为空").assertIsDisplayed()

        composeRule.onNodeWithTag("project_detail_close_button").performClick()
        composeRule.waitForIdle()
        assertNotNull("空名不得被关闭按钮关闭", vm.projectDetail.value)
        composeRule.onNodeWithText("请先修正无效项目名称").assertIsDisplayed()

        pressSystemBack()
        assertNotNull("空名不得被系统返回关闭", vm.projectDetail.value)

        tapScrimForNode("project_detail_name")
        assertNotNull("空名不得被遮罩点按关闭", vm.projectDetail.value)
        assertEquals("空名不得写库", "课程项目重命名", dbProject(projectId)?.name)

        // 修正项目名后可关闭。
        composeRule.onNodeWithTag("project_detail_name").performTextReplacement("最终项目名")
        composeRule.onNodeWithTag("project_detail_name").performImeAction()
        waitProject(projectId) { it.name == "最终项目名" }
        composeRule.waitUntil(5_000) { vm.projectDetail.value?.canLeave == true }
        composeRule.onNodeWithTag("project_detail_close_button").performClick()
        composeRule.waitUntil(5_000) { vm.projectDetail.value == null }
    }

    @Test
    fun AC_D_06_project_detail_closed_project_reopen_restores_active_without_changing_children() {
        val projectId = seedProject("已结项研究项目", ProjectStatus.COMPLETED)
        val childId = seedTask(
            title = "已结项子任务",
            projectId = projectId,
            closedOutcome = ClosedOutcome.COMPLETED,
            positionBeforeClose = GtdPosition.NEXT_ACTION,
        )
        createViewModel()
        setDetailHost()
        openProjectDetail(projectId)

        composeRule.onNodeWithText("重新打开").assertIsDisplayed().performClick()
        waitProject(projectId) { it.status == ProjectStatus.ACTIVE }
        composeRule.waitUntil(5_000) { vm.projectDetail.value?.canReopen == false }
        composeRule.onAllNodesWithText("重新打开").assertCountEquals(0)
        assertEquals(
            "重开项目不得自动改写子任务结项态",
            ClosedOutcome.COMPLETED,
            dbTask(childId)?.closedOutcome,
        )
    }

    // ==================== G5-V1 / G5-V2 视觉验收截图 ====================

    private fun seedRichTaskAndOpen(darkTheme: Boolean) {
        val projectId = seedProject("课程项目")
        val taskId = seedTask(
            title = "概率论第三章复习",
            detail = "重点整理条件概率、全概率公式与贝叶斯习题 3-1 至 3-8",
            priority = Priority.HIGH,
            dueDate = LocalDate.now(),
            dueTime = LocalTime.of(14, 30),
            projectId = projectId,
            position = GtdPosition.NEXT_ACTION,
        )
        createViewModel()
        setDetailHost(darkTheme = darkTheme)
        openTaskDetail(taskId)
        composeRule.waitUntil(5_000) { vm.taskDetail.value?.projectName == "课程项目" }
    }

    @Test
    fun G5_V1_screenshot_task_detail_light() {
        seedRichTaskAndOpen(darkTheme = false)
        composeRule.onNodeWithTag("task_detail_title").assertIsDisplayed()
        captureAndSaveEvidence("task_detail_light")
    }

    @Test
    fun G5_V1_screenshot_task_detail_dark() {
        seedRichTaskAndOpen(darkTheme = true)
        composeRule.onNodeWithTag("task_detail_title").assertIsDisplayed()
        captureAndSaveEvidence("task_detail_dark")
    }

    @Test
    fun G5_V1_screenshot_new_task_light_for_isomorphic_comparison() {
        val project = Project(
            id = ProjectId(7L),
            name = "课程项目",
            status = ProjectStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )
        composeRule.setContent {
            NautileTheme(darkTheme = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NewTaskSheet(
                        state = NewTaskUiState(
                            position = GtdPosition.NEXT_ACTION,
                            targetPosition = GtdPosition.NEXT_ACTION,
                            title = "概率论第三章复习",
                            detail = "重点整理条件概率、全概率公式与贝叶斯习题 3-1 至 3-8",
                            priority = Priority.HIGH,
                            dueDate = LocalDate.now(),
                            dueTime = LocalTime.of(14, 30),
                            projectId = project.id,
                        ),
                        projects = listOf(project),
                    )
                }
            }
        }
        composeRule.onNodeWithTag("new_task_title").assertIsDisplayed()
        captureAndSaveEvidence("new_task_light")
    }

    @Test
    fun G5_V2_screenshot_project_detail_expanded_and_collapsed_light() {
        val projectId = seedProject("机器人算法课程项目")
        val child1 = seedTask(
            title = "整理卡尔曼滤波推导笔记",
            projectId = projectId,
            position = GtdPosition.NEXT_ACTION,
        )
        seedTask(
            title = "完成实验二数据采集",
            projectId = projectId,
            closedOutcome = ClosedOutcome.COMPLETED,
            positionBeforeClose = GtdPosition.NEXT_ACTION,
        )
        createViewModel()
        setDetailHost(darkTheme = false)
        openProjectDetail(projectId)

        composeRule.onNodeWithTag("project_child_${child1.value}").assertIsDisplayed()
        captureAndSaveEvidence("project_detail_expanded_light")

        composeRule.onNodeWithTag("project_children_toggle").performClick()
        composeRule.waitUntil(5_000) { vm.projectDetail.value?.collapsed == true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("project_child_${child1.value}").assertDoesNotExist()
        captureAndSaveEvidence("project_detail_collapsed_light")
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
