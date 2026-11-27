package io.github.mazilin.nautile.verification

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
import io.github.mazilin.nautile.domain.repository.ProjectRepository
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import io.github.mazilin.nautile.domain.repository.TaskRepository
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.ui.screen.todo.NewTaskSheet
import io.github.mazilin.nautile.ui.screen.todo.ProjectReopenPrompt
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G7 闸门：任务详情属性编辑（CMP-30—CMP-37、AC-D-06/07/08/09、AC-T-13）。
 *
 * 预期依据（`docs/需求基线.md` v2.14、`.plans/设计/01_视觉与动效标准.md` §13）：
 * - CMP-30/37：详情复用新建面板同一套五图标与语义，恰五项，无标签/提醒/重复/更多/语音；
 * - CMP-31：每项触控 ≥48×48dp；菜单/日期面板不叠第二层遮罩；
 * - CMP-32/33/34/35/36：日期+可选时间、优先级四档、项目（无+活跃）、GTD 五项（ENDED 禁用）、
 *   详情编辑/清空；选择或清除立即写库并更新摘要；
 * - CMP-34/BR-80/AC-T-28：已结项项目触发重开确认，确认后只重试原命令一次；
 * - CMP-37/BR-68：保存失败保留已选值并显示错误；无效标题阻止离开并提示。
 *
 * 使用 Room 内存库 + 真实 [TodoViewModel] 驱动组件，不复制业务规则；
 * “失败仓库”只在仓库接口层做可控替身（不改生产代码）。
 * 运行环境：emulator-5554（API 36，模拟器，非真机）。
 */
@RunWith(AndroidJUnit4::class)
class G7DetailAttributeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-25T09:00:00Z")
    private val clock = Clock { now }

    private lateinit var database: NautileDatabase
    private lateinit var taskRepository: RoomTaskRepository
    private lateinit var projectRepository: RoomProjectRepository
    private lateinit var vm: TodoViewModel

    /** 关闭请求是否被父层放行；详情关闭守卫由 ViewModel 判定。 */
    private var closeAccepted = false

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

    private fun createViewModel(
        taskRepo: TaskRepository = taskRepository,
        projectRepo: ProjectRepository = projectRepository,
    ) {
        vm = TodoViewModel(
            taskRepository = taskRepo,
            projectRepository = projectRepo,
            settingsRepository = MemorySettingsRepository(),
            taskCommands = TaskCommands(taskRepo, projectRepo, clock),
            projectCommands = ProjectCommands(projectRepo, taskRepo, clock),
        )
    }

    private fun seedTask(
        title: String = "任务A",
        position: GtdPosition = GtdPosition.INBOX,
        projectId: ProjectId? = null,
        priority: Priority = Priority.NONE,
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
        detail: String? = null,
        closed: Boolean = false,
    ): TaskId = runBlocking {
        taskRepository.insert(
            Task(
                id = TaskId(0L),
                title = title,
                projectId = projectId,
                position = if (closed) GtdPosition.ENDED else position,
                positionBeforeClose = if (closed) position else null,
                priority = priority,
                dueDate = dueDate,
                dueTime = dueTime,
                closedOutcome = if (closed) ClosedOutcome.COMPLETED else null,
                closedAt = if (closed) now else null,
                closedOn = if (closed) LocalDate.of(2026, 11, 25) else null,
                createdAt = now,
                updatedAt = now,
                detail = detail,
            ),
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

    private fun setDetailSheet() {
        composeRule.setContent {
            NautileTheme {
                val state by vm.taskDetail.collectAsState()
                val projects by vm.activeProjects.collectAsState()
                Surface(modifier = Modifier.fillMaxSize()) {
                    state?.let { current ->
                        TaskDetailSheet(
                            state = current,
                            onTitleChanged = vm::onTaskTitleChanged,
                            onCommitTitle = vm::commitTaskTitle,
                            onReopen = { vm.reopenTask(current.task.id) },
                            onCloseRequested = {
                                // 与 MainActivity 宿主一致：先经父层离开校验，放行才关闭。
                                if (vm.requestDetailLeave()) closeAccepted = true
                            },
                            onDetailChanged = vm::onTaskDetailChanged,
                            onCommitDetail = vm::commitTaskDetail,
                            projects = projects,
                            onPriorityChanged = vm::onTaskPriorityChanged,
                            onDueDateChanged = vm::onTaskDueDateChanged,
                            onDueTimeChanged = vm::onTaskDueTimeChanged,
                            onProjectChanged = vm::onTaskProjectChanged,
                            onGtdChanged = vm::moveTaskToGtdFromDetail,
                            onProjectDraftChanged = vm::onTaskProjectDraftChanged,
                            onCreateProjectForTask = vm::createProjectForTaskDetail,
                            onClearDetail = vm::clearTaskDetail,
                            visible = true,
                        )
                    }
                }
            }
        }
    }

    private fun openDetail(taskId: TaskId) {
        composeRule.runOnIdle { vm.openTaskDetail(taskId) }
        composeRule.waitUntil(5_000) { vm.taskDetail.value?.task?.id == taskId }
    }

    private fun waitTask(taskId: TaskId, predicate: (Task) -> Boolean) {
        composeRule.waitUntil(10_000) {
            runBlocking { taskRepository.get(taskId)?.let(predicate) == true }
        }
    }

    private fun dbTask(taskId: TaskId): Task? = runBlocking { taskRepository.get(taskId) }

    private fun projectReopenPrompt(): ProjectReopenPrompt = runBlocking {
        withTimeout(5_000) {
            vm.uiState.first { it.projectReopenPrompt != null }.projectReopenPrompt!!
        }
    }

    private fun selectDay(date: LocalDate) {
        val month = date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val label = "$month ${date.dayOfMonth}, ${date.year}"
        val clickable = composeRule.onAllNodes(hasText(label, substring = true) and hasClickAction())
        assertTrue(
            "日期 $label 不可点击：\n" + composeRule.onRoot(useUnmergedTree = true).printToString(),
            clickable.fetchSemanticsNodes().isNotEmpty(),
        )
        clickable.onFirst().performClick()
    }

    // ---- CMP-30/CMP-37（v2.16 / 4.27 同构统一）：详情工具栏恰四项、无“编辑详情”第 5 图标 ----

    @Test
    fun G7_CMP30_detail_toolbar_has_exactly_four_icon_entries() {
        val taskId = seedTask()
        createViewModel()
        setDetailSheet()
        openDetail(taskId)

        val descriptions = listOf(
            "设置截止日期，当前：未设置",
            "设置优先级，当前：无",
            "设置项目，当前：无项目",
            "移动到 GTD 清单，当前：收件箱",
        )
        descriptions.forEach { description ->
            composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
        }
        // 每个入口只有一个节点，没有重复入口（CMP-37），且不再有“编辑详情”第 5 项。
        listOf("设置截止日期", "设置优先级", "设置项目", "移动到 GTD 清单")
            .forEach { prefix ->
                composeRule.onAllNodes(hasContentDescription(prefix, substring = true))
                    .assertCountEquals(1)
            }
        composeRule.onAllNodes(hasContentDescription("编辑详情", substring = true))
            .assertCountEquals(0)
        listOf("标签", "提醒", "重复", "更多", "语音").forEach { forbidden ->
            composeRule.onAllNodesWithText(forbidden).assertCountEquals(0)
        }
    }

    @Test
    fun G7_CMP31_detail_toolbar_items_are_at_least_48dp() {
        val taskId = seedTask()
        createViewModel()
        setDetailSheet()
        openDetail(taskId)

        listOf("设置截止日期", "设置优先级", "设置项目", "移动到 GTD 清单")
            .forEach { prefix ->
                val bounds = composeRule
                    .onNodeWithContentDescription(prefix, substring = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                val widthDp = bounds.width / composeRule.density.density
                val heightDp = bounds.height / composeRule.density.density
                assertTrue("$prefix 宽度应 ≥48dp，实际 ${widthDp}dp", widthDp >= 48f)
                assertTrue("$prefix 高度应 ≥48dp，实际 ${heightDp}dp", heightDp >= 48f)
            }
    }

    // ---- CMP-33：优先级选择/清除立即写入并更新摘要 ----

    @Test
    fun G7_CMP33_priority_selection_and_clear_persist_and_update_summary() {
        val taskId = seedTask(detail = "保持不变")
        createViewModel()
        setDetailSheet()
        openDetail(taskId)

        composeRule.onNodeWithContentDescription("设置优先级，当前：无").performClick()
        listOf("优先级：高", "优先级：中", "优先级：低", "优先级：无").forEach { description ->
            composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("优先级：高").performClick()
        waitTask(taskId) { it.priority == Priority.HIGH }
        composeRule.onNodeWithContentDescription("设置优先级，当前：高").assertIsDisplayed()

        // 只改优先级：其他字段保持（CMP-34/35 的“不改变其他属性”反向核对）。
        val afterPriority = dbTask(taskId)!!
        assertEquals("任务A", afterPriority.title)
        assertEquals("保持不变", afterPriority.detail)
        assertEquals(GtdPosition.INBOX, afterPriority.position)
        assertNull(afterPriority.projectId)

        composeRule.onNodeWithContentDescription("设置优先级，当前：高").performClick()
        composeRule.onNodeWithContentDescription("优先级：无").performClick()
        waitTask(taskId) { it.priority == Priority.NONE }
        composeRule.onNodeWithContentDescription("设置优先级，当前：无").assertIsDisplayed()
    }

    // ---- CMP-32：日期+时间保存、摘要、清除 ----

    @Test
    fun G7_CMP32_due_date_and_time_saved_and_cleared_with_summary() {
        val taskId = seedTask()
        createViewModel()
        setDetailSheet()
        openDetail(taskId)

        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置").performClick()
        composeRule.onNodeWithText("选择截止日期").assertIsDisplayed()
        // 详情为 60% 半屏，日期组件下移；无日期时“时间”行禁用并说明原因（v2.16 / R5-4 / CMP-32）。
        composeRule.onNodeWithTag("date_panel_time_row")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("先选择日期，再设置时间").assertExists()

        val today = LocalDate.now()
        selectDay(today)
        waitTask(taskId) { it.dueDate == today }
        composeRule.onNodeWithTag("date_panel_time_row")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag("time_picker_panel").assertExists()
        composeRule.onNodeWithTag("time_picker_input")
            .performScrollTo()
            .performTextInput("09:30")
        composeRule.onNodeWithTag("time_picker_confirm")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        waitTask(taskId) { it.dueTime == LocalTime.of(9, 30) }
        composeRule.onNodeWithTag("date_panel_back").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("设置截止日期，当前：今天 09:30")
            .assertIsDisplayed()

        // 清除日期必须同时清空时刻（CMP-32，不允许孤立时刻）。
        composeRule.onNodeWithContentDescription("设置截止日期，当前：今天 09:30").performClick()
        composeRule.onNodeWithText("清除截止日期").performScrollTo().performClick()
        waitTask(taskId) { it.dueDate == null && it.dueTime == null }
        composeRule.onNodeWithTag("date_panel_back").performScrollTo().performClick()
        // 日期面板较高，返回后把内容滚回顶部再核对摘要。
        composeRule.onNodeWithText("任务详情").performScrollTo()
        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置")
            .assertIsDisplayed()
    }

    // ---- CMP-34：项目选择/清除 ----

    @Test
    fun G7_CMP34_project_selection_and_clear_persist() {
        val projectId = seedProject("课程项目")
        val taskId = seedTask()
        createViewModel()
        setDetailSheet()
        openDetail(taskId)
        composeRule.waitUntil(5_000) {
            vm.activeProjects.value.any { it.id == projectId }
        }

        composeRule.onNodeWithContentDescription("设置项目，当前：无项目").performClick()
        composeRule.onNodeWithText("无项目").assertIsDisplayed()
        composeRule.onNodeWithText("课程项目").assertIsDisplayed()
        composeRule.onNodeWithText("新建项目").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("项目：课程项目").performClick()
        waitTask(taskId) { it.projectId == projectId }
        composeRule.onNodeWithContentDescription("设置项目，当前：课程项目").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("设置项目，当前：课程项目").performClick()
        composeRule.onNodeWithContentDescription("项目：无项目").performClick()
        waitTask(taskId) { it.projectId == null }
        composeRule.onNodeWithContentDescription("设置项目，当前：无项目").assertIsDisplayed()
    }

    // ---- CMP-34/AC-T-28：已结项项目触发重开确认，确认后只重试一次 ----

    @Test
    fun G7_CMP34_closed_project_prompts_reopen_then_retries_once_and_succeeds() {
        val projectId = seedProject("已结项项目", ProjectStatus.COMPLETED)
        val taskId = seedTask(projectId = projectId)
        val counting = CountingTaskRepository(taskRepository)
        createViewModel(taskRepo = counting)
        setDetailSheet()
        openDetail(taskId)
        composeRule.onNodeWithContentDescription("设置项目，当前：已结项项目").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：收件箱").performClick()
        composeRule.onNodeWithContentDescription("移动到 GTD 清单：等待中").performClick()

        val prompt = projectReopenPrompt()
        assertEquals(projectId, prompt.projectId)
        assertEquals("首次失败不更新任务", GtdPosition.INBOX, dbTask(taskId)?.position)
        assertEquals("首次失败不更新任务", 0, counting.updateCalls)

        composeRule.runOnIdle { vm.confirmProjectReopen() }
        waitTask(taskId) { it.position == GtdPosition.WAITING }
        composeRule.waitUntil(5_000) {
            runBlocking { projectRepository.get(projectId)?.status == ProjectStatus.ACTIVE }
        }
        // 重试只写一次任务（未循环重试）。
        assertEquals("确认后只重试一次", 1, counting.updateCalls)
        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：等待中")
            .assertIsDisplayed()
    }

    @Test
    fun G7_CMP34_CMP37_retry_happens_only_once_then_error_keeps_value() {
        val projectId = seedProject("已结项项目", ProjectStatus.COMPLETED)
        val taskId = seedTask(projectId = projectId)
        val ignoring = IgnoringReopenProjectRepository(projectRepository, projectId)
        createViewModel(projectRepo = ignoring)
        setDetailSheet()
        openDetail(taskId)

        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：收件箱").performClick()
        composeRule.onNodeWithContentDescription("移动到 GTD 清单：等待中").performClick()
        projectReopenPrompt()

        composeRule.runOnIdle { vm.confirmProjectReopen() }
        // 重开被仓库替身忽略：只重试一次、随后显示错误，不再弹出第二次确认。
        composeRule.waitUntil(5_000) {
            vm.taskDetail.value?.error == "项目状态已变化，请重试"
        }
        composeRule.onNodeWithText("项目状态已变化，请重试").assertIsDisplayed()
        // 重试后同一时间窗内观察确认状态流：不得再次出现重开确认。
        val promptsSeen = runBlocking {
            val seen = mutableListOf<ProjectReopenPrompt?>()
            kotlinx.coroutines.withTimeoutOrNull(800) {
                vm.uiState.collect { seen += it.projectReopenPrompt }
            }
            seen.toList()
        }
        assertTrue("应能观察到详情状态流", promptsSeen.isNotEmpty())
        assertTrue("重试后不应再次弹出重开确认", promptsSeen.all { it == null })
        assertEquals("重开只尝试一次", 1, ignoring.updateCalls)
        assertEquals("任务位置保持不变", GtdPosition.INBOX, dbTask(taskId)?.position)
        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：收件箱")
            .assertIsDisplayed()
    }

    // ---- CMP-35：GTD 清单切换与 ENDED 禁用 ----

    @Test
    fun G7_CMP35_checklist_switch_updates_position_and_ended_is_disabled() {
        val taskId = seedTask()
        createViewModel()
        setDetailSheet()
        openDetail(taskId)

        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：收件箱").performClick()
        listOf(
            "移动到 GTD 清单：收件箱",
            "移动到 GTD 清单：下一步行动",
            "移动到 GTD 清单：等待中",
            "移动到 GTD 清单：将来／也许",
        ).forEach { description ->
            composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("已结束，已结束不可直接移入")
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("已结束不可直接移入").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("移动到 GTD 清单：等待中").performClick()
        waitTask(taskId) { it.position == GtdPosition.WAITING }
        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：等待中")
            .assertIsDisplayed()
    }

    @Test
    fun G7_CMP35_closed_task_checklist_entry_is_disabled() {
        val taskId = seedTask(closed = true)
        createViewModel()
        setDetailSheet()
        openDetail(taskId)

        composeRule.onNodeWithContentDescription("已结项，请在详情重新打开")
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("已结项，请在详情重新打开").performClick()
        // 禁用入口不展开五项菜单。
        composeRule.onNodeWithContentDescription("移动到 GTD 清单：等待中").assertDoesNotExist()
    }

    // ---- CMP-36（v2.16 / 4.27）：固定第二行详情输入框直接编辑与清空写 null ----

    @Test
    fun G7_CMP36_detail_inline_edit_and_clear_writes_null() {
        val taskId = seedTask(detail = "旧详情")
        createViewModel()
        setDetailSheet()
        openDetail(taskId)

        // 不再存在“编辑详情”工具栏菜单，详情在固定第二行 task_detail_detail 直接编辑即保存。
        composeRule.onAllNodes(hasContentDescription("编辑详情", substring = true))
            .assertCountEquals(0)
        composeRule.onNodeWithTag("task_detail_detail")
            .assertIsDisplayed()
            .performClick()
            .assertIsFocused()
        composeRule.onNodeWithTag("task_detail_detail").performTextReplacement("新详情")
        // 切换焦点至标题框触发失焦即保存。
        composeRule.onNodeWithTag("task_detail_title").performClick()
        waitTask(taskId) { it.detail == "新详情" }

        // 清空详情并失焦保存：空串归并为 null。
        composeRule.onNodeWithTag("task_detail_detail").performClick()
        composeRule.onNodeWithTag("task_detail_detail").performTextReplacement("")
        composeRule.onNodeWithTag("task_detail_title").performClick()
        waitTask(taskId) { it.detail == null }
    }

    // ---- CMP-37/AC-D-08：无效标题阻止离开并提示 ----

    @Test
    fun G7_AC_D08_blank_title_blocks_leave_and_keeps_sheet() {
        val taskId = seedTask(title = "任务A")
        createViewModel()
        setDetailSheet()
        openDetail(taskId)

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("   ")
        composeRule.onNodeWithTag("detail_close_button").performClick()

        composeRule.onNodeWithText("标题不能为空").assertIsDisplayed()
        composeRule.onNodeWithText("请先修正无效标题").assertIsDisplayed()
        composeRule.onNodeWithText("任务详情").assertIsDisplayed()
        assertFalse("无效标题不得关闭详情", closeAccepted)
        assertEquals("无效标题不得写入", "任务A", dbTask(taskId)?.title)
    }

    // ---- CMP-37：新建面板属性回归（优先级/日期摘要与创建写入） ----

    @Test
    fun G7_CMP37_new_task_priority_and_due_summary_regression() {
        createViewModel()
        composeRule.setContent {
            NautileTheme {
                val state by vm.newTask.collectAsState()
                val projects by vm.activeProjects.collectAsState()
                Surface(modifier = Modifier.fillMaxSize()) {
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
                                if (vm.requestNewTaskDismiss()) vm.closeNewTask()
                            },
                            visible = true,
                        )
                    }
                }
            }
        }
        composeRule.runOnIdle { vm.openNewTask() }
        composeRule.waitUntil(5_000) { vm.newTask.value != null }
        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置").assertIsDisplayed()

        // 标题通过 ViewModel 写入，避免软键盘弹出后工具栏重排导致点击落点失效（G7 首跑时序失败）。
        composeRule.runOnIdle { vm.onNewTaskTitleChanged("回归任务") }
        composeRule.waitUntil(5_000) { vm.newTask.value?.title == "回归任务" }
        composeRule.onNodeWithContentDescription("设置优先级，当前：无").performClick()
        composeRule.onNodeWithContentDescription("优先级：高").performClick()
        composeRule.onNodeWithContentDescription("设置优先级，当前：高").assertIsDisplayed()

        val today = LocalDate.now()
        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置").performClick()
        selectDay(today)
        composeRule.waitUntil(5_000) { vm.newTask.value?.dueDate == today }
        composeRule.onNodeWithTag("date_panel_time_row")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag("time_picker_input")
            .performScrollTo()
            .performTextInput("09:30")
        composeRule.onNodeWithTag("time_picker_confirm")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(5_000) { vm.newTask.value?.dueTime == LocalTime.of(9, 30) }
        composeRule.onNodeWithTag("date_panel_back").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("设置截止日期，当前：今天 09:30")
            .assertIsDisplayed()

        composeRule.onNodeWithText("创建").performClick()
        composeRule.waitUntil(10_000) { vm.newTask.value == null }
        val tasks = runBlocking { taskRepository.observeAll().first() }
        assertEquals(1, tasks.size)
        assertEquals(Priority.HIGH, tasks.single().priority)
        assertEquals(today, tasks.single().dueDate)
        assertEquals(LocalTime.of(9, 30), tasks.single().dueTime)
    }

    /** 统计任务 update 次数，用于验证“确认后只重试一次”。 */
    private class CountingTaskRepository(
        private val delegate: TaskRepository,
    ) : TaskRepository {
        var updateCalls = 0

        override fun observeByPosition(position: GtdPosition): Flow<List<Task>> =
            delegate.observeByPosition(position)

        override fun observeAll(): Flow<List<Task>> = delegate.observeAll()

        override suspend fun get(id: TaskId): Task? = delegate.get(id)

        override suspend fun insert(task: Task): TaskId = delegate.insert(task)

        override suspend fun update(task: Task) {
            updateCalls += 1
            delegate.update(task)
        }

        override suspend fun delete(id: TaskId) = delegate.delete(id)

        override suspend fun deleteAll(ids: List<TaskId>) = delegate.deleteAll(ids)
    }

    /** 重开项目时忽略写入（模拟重开未生效），用于验证只重试一次。 */
    private class IgnoringReopenProjectRepository(
        private val delegate: ProjectRepository,
        private val ignoredProjectId: ProjectId,
    ) : ProjectRepository {
        var updateCalls = 0

        override fun observeAll(): Flow<List<Project>> = delegate.observeAll()

        override suspend fun get(id: ProjectId): Project? = delegate.get(id)

        override suspend fun insert(project: Project): ProjectId = delegate.insert(project)

        override suspend fun update(project: Project) {
            if (project.id == ignoredProjectId) {
                updateCalls += 1
                return
            }
            delegate.update(project)
        }

        override suspend fun delete(id: ProjectId) = delegate.delete(id)

        override suspend fun updateWithTasks(project: Project, tasks: List<Task>) =
            delegate.updateWithTasks(project, tasks)

        override suspend fun deleteWithTasks(id: ProjectId, taskIds: List<TaskId>) =
            delegate.deleteWithTasks(id, taskIds)
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
