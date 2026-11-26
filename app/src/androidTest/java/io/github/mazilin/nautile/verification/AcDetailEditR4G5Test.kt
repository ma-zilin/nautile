package io.github.mazilin.nautile.verification

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
import io.github.mazilin.nautile.ui.screen.todo.ProjectDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.ProjectEditSheet
import io.github.mazilin.nautile.ui.screen.todo.TaskDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.TaskEditSheet
import io.github.mazilin.nautile.ui.screen.todo.TodoViewModel
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
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
 * R4 G5 闸门：详情头部“编辑”入口与同构编辑面板
 * （`docs/需求基线.md` v2.15 AC-D-07、FR-D-02/03、CMP-35/36/37.1，`docs/需求变更记录.md` 4.25）。
 *
 * 覆盖口径：
 * - 任务/项目详情头部“编辑”图标语义明确，点按打开下半屏编辑面板并预填当前值；
 * - 编辑面板与创建同构：标题、固定第二行详情、四项“图标＋文字”属性，无标签/提醒/重复入口；
 * - 点“保存”一次提交全部改动，详情与仓库一致；失败保留输入；
 * - 空标题/空项目名阻止保存与关闭并提示；
 * - 返回顺序：先关面板内菜单，再关编辑面板，详情保持；关闭编辑不落库未保存草稿；
 * - 项目已结项：保存触发重开确认，确认后只重试一次；再次失败提示且不再弹确认；
 * - 已结项任务的 GTD 清单入口禁用，ENDED 不可写入。
 *
 * 使用 Room 内存库 + 真实 [TodoViewModel]；宿主为 ComponentActivity，系统返回经
 * OnBackPressedDispatcher 触发，与生产返回分发一致。不改生产代码、不复制业务规则。
 * 运行环境：emulator-5554（API 36，模拟器，非真机）。
 */
@RunWith(AndroidJUnit4::class)
class AcDetailEditR4G5Test {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now: Instant = Instant.parse("2026-11-26T09:00:00Z")
    private val clock = Clock { now }

    private lateinit var database: NautileDatabase
    private lateinit var taskRepository: RoomTaskRepository
    private lateinit var projectRepository: RoomProjectRepository
    private lateinit var projectCommands: ProjectCommands
    private lateinit var vm: TodoViewModel

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
        vm.viewModelScope.cancel()
        database.close()
    }

    private fun createViewModel(
        taskRepo: TaskRepository = taskRepository,
        projectRepo: ProjectRepository = projectRepository,
    ) {
        projectCommands = ProjectCommands(projectRepo, taskRepo, clock)
        vm = TodoViewModel(
            taskRepository = taskRepo,
            projectRepository = projectRepo,
            settingsRepository = MemorySettingsRepository(),
            taskCommands = TaskCommands(taskRepo, projectRepo, clock),
            projectCommands = projectCommands,
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
        title: String,
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
                position = position,
                positionBeforeClose = positionBeforeClose,
                priority = priority,
                dueDate = dueDate,
                dueTime = dueTime,
                closedOutcome = closedOutcome,
                closedAt = if (closedOutcome == null) null else now,
                closedOn = if (closedOutcome == null) null else LocalDate.now(),
                createdAt = now,
                updatedAt = now,
                detail = detail,
            ),
        )
    }

    /** 与 MainActivity 相同层级：详情在编辑面板下方，编辑打开时详情 visible=false。 */
    private fun setHost() {
        composeRule.setContent {
            NautileTheme {
                // WhileSubscribed 流需订阅后才发射真实状态（与生产页面一致）。
                vm.uiState.collectAsState()
                val taskDetail by vm.taskDetail.collectAsState()
                val taskEdit by vm.taskEdit.collectAsState()
                val projectDetail by vm.projectDetail.collectAsState()
                val projectEdit by vm.projectEdit.collectAsState()
                val projects by vm.activeProjects.collectAsState()
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        taskDetail?.let { detail ->
                            TaskDetailSheet(
                                state = detail,
                                onTitleChanged = vm::onTaskTitleChanged,
                                onCommitTitle = vm::commitTaskTitle,
                                onReopen = { vm.reopenTask(detail.task.id) },
                                onCloseRequested = {
                                    if (vm.requestDetailLeave()) vm.closeDetail()
                                },
                                onDetailChanged = vm::onTaskDetailChanged,
                                onCommitDetail = vm::commitTaskDetail,
                                visible = taskDetail != null && taskEdit == null,
                                projects = projects,
                                onPriorityChanged = vm::onTaskPriorityChanged,
                                onDueDateChanged = vm::onTaskDueDateChanged,
                                onDueTimeChanged = vm::onTaskDueTimeChanged,
                                onProjectChanged = vm::onTaskProjectChanged,
                                onGtdChanged = vm::moveTaskToGtdFromDetail,
                                onProjectDraftChanged = vm::onTaskProjectDraftChanged,
                                onCreateProjectForTask = vm::createProjectForTaskDetail,
                                onClearDetail = vm::clearTaskDetail,
                                onOpenEdit = vm::openTaskEdit,
                            )
                        }
                        taskEdit?.let { edit ->
                            TaskEditSheet(
                                state = edit,
                                projects = projects,
                                onTitleChanged = vm::onTaskEditTitleChanged,
                                onDetailChanged = vm::onTaskEditDetailChanged,
                                onPriorityChanged = vm::onTaskEditPriorityChanged,
                                onDueDateChanged = vm::onTaskEditDueDateChanged,
                                onDueTimeChanged = vm::onTaskEditDueTimeChanged,
                                onProjectChanged = vm::onTaskEditProjectChanged,
                                onProjectDraftChanged = vm::onTaskEditProjectDraftChanged,
                                onCreateProjectForTask = vm::createProjectForTaskEdit,
                                onPositionChanged = vm::onTaskEditPositionChanged,
                                onSave = vm::saveTaskEdit,
                                onDismissRequest = { vm.requestTaskEditDismiss() },
                                visible = true,
                            )
                        }
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
                                onCloseRequested = {
                                    if (vm.requestDetailLeave()) vm.closeDetail()
                                },
                                onOpenEdit = vm::openProjectEdit,
                                visible = projectDetail != null && projectEdit == null,
                            )
                        }
                        projectEdit?.let { edit ->
                            ProjectEditSheet(
                                state = edit,
                                onNameChanged = vm::onProjectEditNameChanged,
                                onSave = vm::saveProjectEdit,
                                onDismissRequest = { vm.requestProjectEditDismiss() },
                                visible = true,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openTaskDetail(taskId: TaskId) {
        composeRule.runOnIdle { vm.openTaskDetail(taskId) }
        composeRule.waitUntil(5_000) { vm.taskDetail.value != null }
    }

    private fun openTaskEdit() {
        composeRule.onNodeWithContentDescription("编辑任务").performClick()
        composeRule.waitUntil(5_000) { vm.taskEdit.value != null }
        composeRule.onNodeWithTag("task_edit_title").assertIsDisplayed()
        // 详情让位退出后只剩编辑面板，避免两层面板同文案造成歧义。
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("关闭").fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun openProjectDetail(projectId: ProjectId) {
        composeRule.runOnIdle { vm.openProjectDetail(projectId) }
        composeRule.waitUntil(5_000) { vm.projectDetail.value != null }
    }

    private fun openProjectEdit() {
        composeRule.onNodeWithContentDescription("编辑项目").performClick()
        composeRule.waitUntil(5_000) { vm.projectEdit.value != null }
        composeRule.onNodeWithTag("project_edit_name").assertIsDisplayed()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("关闭").fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun replaceText(tag: String, text: String) {
        composeRule.onNodeWithTag(tag).performTextReplacement(text)
        composeRule.waitForIdle()
    }

    private fun assertToolbarLabel(description: String, label: String) {
        composeRule.onNode(
            hasText(label) and hasAnyAncestor(hasContentDescription(description, substring = true)),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    private fun readTask(id: TaskId): Task? = runBlocking { taskRepository.get(id) }

    private fun readProject(id: ProjectId): Project? = runBlocking { projectRepository.get(id) }

    // ---- 预填：编辑图标语义、同构面板、四属性与当前值 ----

    @Test
    fun G5R4_task_edit_icon_opens_isomorphic_panel_prefilled() {
        val projectId = seedProject("课程项目")
        val dueDate = LocalDate.now()
        val taskId = seedTask(
            title = "概率论刷题",
            detail = "第三章习题 1-5",
            priority = Priority.HIGH,
            dueDate = dueDate,
            dueTime = LocalTime.of(9, 30),
            projectId = projectId,
            position = GtdPosition.WAITING,
        )
        createViewModel()
        setHost()
        openTaskDetail(taskId)

        // 编辑入口：语义明确的按钮，且详情里只有一个。
        composeRule.onNodeWithContentDescription("编辑任务").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("编辑任务").assertCountEquals(1)

        openTaskEdit()

        // 预填：状态与输入框均为当前值。
        val edit = vm.taskEdit.value
        assertNotNull(edit)
        assertEquals("概率论刷题", edit!!.title)
        assertEquals("第三章习题 1-5", edit.detail)
        assertEquals(Priority.HIGH, edit.priority)
        assertEquals(dueDate, edit.dueDate)
        assertEquals(LocalTime.of(9, 30), edit.dueTime)
        assertEquals(projectId, edit.projectId)
        assertEquals(GtdPosition.WAITING, edit.position)
        composeRule.onNodeWithTag("task_edit_title").assertTextContains("概率论刷题")
        composeRule.onNodeWithTag("task_edit_detail").assertTextContains("第三章习题 1-5")

        // 同构工具栏：恰四项图标＋文字，同为日期/优先级/项目/清单，且按序排列。
        val descriptions = listOf(
            "设置截止日期，当前：今天 09:30",
            "设置优先级，当前：高",
            "设置项目，当前：课程项目",
            "移动到 GTD 清单，当前：等待中",
        )
        descriptions.forEach { description ->
            composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
            composeRule.onAllNodes(hasContentDescription(description)).assertCountEquals(1)
        }
        assertToolbarLabel("设置截止日期，当前：今天 09:30", "今天 09:30")
        assertToolbarLabel("设置优先级，当前：高", "高")
        assertToolbarLabel("设置项目，当前：课程项目", "课程项目")
        assertToolbarLabel("移动到 GTD 清单，当前：等待中", "等待中")
        val bounds = descriptions.map {
            composeRule.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot
        }
        bounds.forEach { bound ->
            assertTrue(
                "四项应在同一行，top=${bounds.first().top} vs ${bound.top}",
                kotlin.math.abs(bound.top - bounds.first().top) <= 2f,
            )
        }
        bounds.zipWithNext().forEach { (left, right) ->
            assertTrue("工具栏顺序应为日期→优先级→项目→清单", right.left > left.left)
        }
        // 不出现标签/提醒/重复/更多/语音等额外入口。
        listOf("标签", "提醒", "重复", "更多", "语音").forEach { forbidden ->
            composeRule.onAllNodesWithText(forbidden).assertCountEquals(0)
        }
    }

    // ---- 一次保存：全部字段落库，详情与新值一致 ----

    @Test
    fun G5R4_task_save_commits_all_fields_and_detail_shows_new_values() {
        val courseProject = seedProject("课程项目")
        val researchProject = seedProject("研究项目")
        val taskId = seedTask(
            title = "概率论刷题",
            detail = "第三章习题 1-5",
            priority = Priority.HIGH,
            dueDate = LocalDate.now(),
            dueTime = LocalTime.of(9, 30),
            projectId = courseProject,
            position = GtdPosition.WAITING,
        )
        createViewModel()
        setHost()
        openTaskDetail(taskId)
        openTaskEdit()

        replaceText("task_edit_title", "概率论整理")
        replaceText("task_edit_detail", "整理错题 3-5")

        composeRule.onNodeWithContentDescription("设置优先级，当前：高").performClick()
        composeRule.onNodeWithContentDescription("优先级：中").performClick()
        composeRule.waitUntil(5_000) { vm.taskEdit.value?.priority == Priority.MEDIUM }

        composeRule.onNodeWithContentDescription("设置项目，当前：课程项目").performClick()
        composeRule.onNodeWithContentDescription("项目：研究项目").performClick()
        composeRule.waitUntil(5_000) { vm.taskEdit.value?.projectId == researchProject }

        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：等待中").performClick()
        composeRule.onNodeWithContentDescription("移动到 GTD 清单：将来／也许").performClick()
        composeRule.waitUntil(5_000) { vm.taskEdit.value?.position == GtdPosition.SOMEDAY }

        // 清除日期（同时清空时刻，CMP-32）。
        composeRule.onNodeWithContentDescription("设置截止日期，当前：今天 09:30").performClick()
        composeRule.onNodeWithText("清除截止日期").performClick()
        composeRule.waitUntil(5_000) {
            vm.taskEdit.value?.dueDate == null && vm.taskEdit.value?.dueTime == null
        }
        composeRule.onNodeWithText("返回").performClick()

        // 单次“保存”提交全部改动。
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(10_000) { vm.taskEdit.value == null }

        val saved = readTask(taskId)
        assertNotNull(saved)
        assertEquals("概率论整理", saved!!.title)
        assertEquals("整理错题 3-5", saved.detail)
        assertEquals(Priority.MEDIUM, saved.priority)
        assertNull("清除日期后应为 null", saved.dueDate)
        assertNull("清除日期后时刻应为 null", saved.dueTime)
        assertEquals(researchProject, saved.projectId)
        assertEquals(GtdPosition.SOMEDAY, saved.position)

        // 详情回流新值，编辑面板关闭后详情仍在。
        val detail = vm.taskDetail.value
        assertNotNull(detail)
        assertEquals("概率论整理", detail!!.task.title)
        assertEquals("整理错题 3-5", detail.task.detail)
        assertEquals("研究项目", detail.projectName)
        composeRule.onNodeWithText("任务详情").assertIsDisplayed()
        composeRule.onNodeWithText("概率论整理").assertIsDisplayed()
        composeRule.onAllNodesWithText("取消").assertCountEquals(0)
    }

    // ---- 无效标题：保存与两次关闭尝试均被阻止并提示 ----

    @Test
    fun G5R4_task_blank_title_blocks_save_and_dismiss_with_hint() {
        val taskId = seedTask(title = "概率论刷题", detail = "第三章习题")
        createViewModel()
        setHost()
        openTaskDetail(taskId)
        openTaskEdit()

        replaceText("task_edit_title", "")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.onAllNodesWithText("标题不能为空").onFirst().assertIsDisplayed()
        composeRule.waitUntil(5_000) { vm.taskEdit.value?.titleInvalid == true }
        assertNotNull("空标题不得关闭编辑面板", vm.taskEdit.value)
        assertEquals("标题不能为空", vm.taskEdit.value?.error)

        // 关闭尝试 1：取消按钮被阻止。
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()
        assertNotNull("取消不得关闭空标题面板", vm.taskEdit.value)

        // 关闭尝试 2：系统返回被阻止。
        pressBack()
        assertNotNull("返回不得关闭空标题面板", vm.taskEdit.value)
        composeRule.onAllNodesWithText("标题不能为空").onFirst().assertIsDisplayed()
        // 仓库未被写入，草稿仍在面板内。
        assertEquals("概率论刷题", readTask(taskId)?.title)
    }

    // ---- 返回顺序：先关菜单，再关编辑面板；未保存草稿不落库 ----

    @Test
    fun G5R4_task_back_closes_menu_then_panel_and_discards_draft() {
        val taskId = seedTask(title = "概率论刷题", priority = Priority.HIGH)
        createViewModel()
        setHost()
        openTaskDetail(taskId)
        openTaskEdit()

        replaceText("task_edit_title", "未保存草稿")
        composeRule.onNodeWithContentDescription("设置优先级，当前：高").performClick()
        composeRule.onNodeWithContentDescription("优先级：中").assertIsDisplayed()

        // 第一次返回：只关菜单，编辑面板与草稿保留。
        pressBack()
        composeRule.onAllNodesWithContentDescription("优先级：中").assertCountEquals(0)
        assertNotNull("菜单关闭后编辑面板应保留", vm.taskEdit.value)
        composeRule.onNodeWithTag("task_edit_title").assertTextContains("未保存草稿")

        // 第二次返回：关闭编辑面板，详情仍在，草稿不落库。
        pressBack()
        composeRule.waitUntil(5_000) { vm.taskEdit.value == null }
        assertNotNull("编辑面板关闭后详情应保留", vm.taskDetail.value)
        composeRule.onNodeWithText("任务详情").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("编辑任务").assertIsDisplayed()
        assertEquals("未保存草稿不得写库", "概率论刷题", readTask(taskId)?.title)
        composeRule.onNodeWithTag("task_edit_title").assertDoesNotExist()
    }

    // ---- 保存失败：保留输入并提示 ----

    @Test
    fun G5R4_task_save_failure_keeps_input_and_panel_open() {
        val taskId = seedTask(title = "概率论刷题")
        createViewModel()
        setHost()
        openTaskDetail(taskId)
        openTaskEdit()

        replaceText("task_edit_title", "改名后删除")
        // 面板打开期间任务被删除：保存得到 NotFound，应保留输入并提示。
        composeRule.runOnIdle { runBlocking { taskRepository.delete(taskId) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(5_000) { vm.taskEdit.value?.saving == false && vm.taskEdit.value?.error != null }
        assertEquals("任务不存在", vm.taskEdit.value?.error)
        assertNotNull("失败不得关闭编辑面板", vm.taskEdit.value)
        composeRule.onNodeWithTag("task_edit_title").assertTextContains("改名后删除")
        composeRule.onAllNodesWithText("任务不存在").onFirst().assertIsDisplayed()
    }

    // ---- 项目已结项：重开确认后只重试一次（成功） ----

    @Test
    fun G5R4_task_closed_project_prompts_reopen_and_retries_once() {
        seedProject("课程项目")
        val originProject = seedProject("原项目")
        val targetProject = seedProject("目标项目")
        val taskId = seedTask(title = "概率论刷题", projectId = originProject)
        createViewModel()
        setHost()
        openTaskDetail(taskId)
        openTaskEdit()

        // 先选目标项目（此时活跃），保存前项目被结项：保存触发重开确认。
        composeRule.onNodeWithContentDescription("设置项目，当前：原项目").performClick()
        composeRule.onNodeWithContentDescription("项目：目标项目").performClick()
        composeRule.waitUntil(5_000) { vm.taskEdit.value?.projectId == targetProject }
        composeRule.runOnIdle {
            runBlocking { projectCommands.complete(targetProject, confirmed = true) }
        }
        composeRule.waitUntil(5_000) { readProject(targetProject)?.status == ProjectStatus.COMPLETED }

        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(10_000) {
            vm.uiState.value.projectReopenPrompt?.projectId == targetProject
        }
        assertNotNull("项目已结项时应弹重开确认", vm.taskEdit.value)
        composeRule.waitUntil(5_000) { vm.taskEdit.value?.saving == false }

        composeRule.runOnIdle { vm.confirmProjectReopen() }
        composeRule.waitUntil(10_000) { vm.taskEdit.value == null }

        assertEquals("确认后项目应重开", ProjectStatus.ACTIVE, readProject(targetProject)?.status)
        assertEquals("确认后只重试一次并保存", targetProject, readTask(taskId)?.projectId)
        assertNull("成功重试后不应再弹确认", vm.uiState.value.projectReopenPrompt)
        composeRule.onNodeWithText("任务详情").assertIsDisplayed()
    }

    // ---- 项目已结项：重试仍失败时只重试一次，不再弹确认 ----

    @Test
    fun G5R4_task_reopen_retry_only_once_then_errors() {
        val originProject = seedProject("原项目")
        val targetProject = seedProject("目标项目")
        val taskId = seedTask(title = "概率论刷题", projectId = originProject)
        // 替身：让目标项目重开后读取仍为“已结项”，模拟保存期间项目状态再次变化。
        val stubbornRepository = ForcedClosedProjectRepository(projectRepository, targetProject)
        createViewModel(projectRepo = stubbornRepository)
        setHost()
        openTaskDetail(taskId)
        openTaskEdit()

        composeRule.onNodeWithContentDescription("设置项目，当前：原项目").performClick()
        composeRule.onNodeWithContentDescription("项目：目标项目").performClick()
        composeRule.waitUntil(5_000) { vm.taskEdit.value?.projectId == targetProject }
        composeRule.runOnIdle {
            runBlocking { projectCommands.complete(targetProject, confirmed = true) }
        }
        composeRule.waitUntil(5_000) { readProject(targetProject)?.status == ProjectStatus.COMPLETED }
        // 进入“状态被再次改变”的情形：此后读取目标项目始终为已完成。
        stubbornRepository.forceClosed = true

        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(10_000) {
            vm.uiState.value.projectReopenPrompt?.projectId == targetProject
        }

        composeRule.runOnIdle { vm.confirmProjectReopen() }
        composeRule.waitUntil(10_000) { vm.taskEdit.value?.error == "项目状态已变化，请重试" }

        assertNotNull("重试失败应保留编辑面板", vm.taskEdit.value)
        assertNull("不得再次弹出重开确认", vm.uiState.value.projectReopenPrompt)
        composeRule.onAllNodesWithText("项目状态已变化，请重试").onFirst().assertIsDisplayed()
        assertTrue("任务未写入目标项目", readTask(taskId)?.projectId != targetProject)
    }

    // ---- 已结项任务：清单入口禁用，ENDED 不可写入 ----

    @Test
    fun G5R4_closed_task_blocks_checklist_entry_and_ended_target() {
        val taskId = seedTask(
            title = "已结项任务",
            position = GtdPosition.ENDED,
            closedOutcome = ClosedOutcome.COMPLETED,
            positionBeforeClose = GtdPosition.INBOX,
        )
        createViewModel()
        setHost()
        openTaskDetail(taskId)
        openTaskEdit()

        val checklist = composeRule.onNodeWithContentDescription("已结项，请在详情重新打开")
        checklist.assertIsDisplayed().assertIsNotEnabled()
        // 入口禁用，不会展开清单菜单（CMP-35）。
        composeRule.onAllNodesWithContentDescription("移动到 GTD 清单：收件箱").assertCountEquals(0)

        // 业务守卫：ENDED 写入被忽略（CMP-35）。
        composeRule.runOnIdle { vm.onTaskEditPositionChanged(GtdPosition.ENDED) }
        assertEquals(GtdPosition.ENDED, vm.taskEdit.value?.position)
        composeRule.runOnIdle { vm.onTaskEditPositionChanged(GtdPosition.NEXT_ACTION) }
        assertEquals(GtdPosition.NEXT_ACTION, vm.taskEdit.value?.position)
    }

    // ---- 项目编辑：预填、改名落库并同步详情与列表 ----

    @Test
    fun G5R4_project_edit_icon_opens_prefilled_and_rename_updates_detail_and_list() {
        val projectId = seedProject("课程项目")
        seedTask(title = "概率论刷题", projectId = projectId)
        createViewModel()
        setHost()
        openProjectDetail(projectId)

        composeRule.onNodeWithContentDescription("编辑项目").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("编辑项目").assertCountEquals(1)
        openProjectEdit()

        assertEquals("课程项目", vm.projectEdit.value?.name)
        composeRule.onNodeWithTag("project_edit_name").assertTextContains("课程项目")
        // 项目面板只编辑名称：无属性工具栏、无详情行。
        composeRule.onAllNodes(hasContentDescription("设置截止日期", substring = true))
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("详情（可选）").assertCountEquals(0)
        listOf("标签", "提醒", "重复").forEach { forbidden ->
            composeRule.onAllNodesWithText(forbidden).assertCountEquals(0)
        }

        replaceText("project_edit_name", "课程项目改名")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(10_000) { vm.projectEdit.value == null }

        assertEquals("课程项目改名", readProject(projectId)?.name)
        assertEquals("项目详情应回流新名", "课程项目改名", vm.projectDetail.value?.project?.name)
        assertTrue(
            "列表分组应显示新名",
            vm.uiState.value.groups.any { it.project?.name == "课程项目改名" },
        )
        composeRule.onNodeWithText("项目详情").assertIsDisplayed()
        composeRule.onNodeWithText("课程项目改名").assertIsDisplayed()
    }

    // ---- 项目编辑：空名阻止；返回只关面板，详情保持且不落库 ----

    @Test
    fun G5R4_project_blank_name_blocks_and_back_keeps_detail_without_saving() {
        val projectId = seedProject("课程项目")
        createViewModel()
        setHost()
        openProjectDetail(projectId)
        openProjectEdit()

        replaceText("project_edit_name", "")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.onAllNodesWithText("项目名称不能为空").onFirst().assertIsDisplayed()
        composeRule.waitUntil(5_000) { vm.projectEdit.value?.nameInvalid == true }
        assertNotNull("空名不得保存关闭", vm.projectEdit.value)

        // 关闭尝试 1：取消被阻止；关闭尝试 2：系统返回被阻止。
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()
        assertNotNull("取消不得关闭空名面板", vm.projectEdit.value)
        pressBack()
        assertNotNull("返回不得关闭空名面板", vm.projectEdit.value)
        assertEquals("空名不得写库", "课程项目", readProject(projectId)?.name)

        // 填写有效名称后返回：只关编辑面板，详情仍在，未保存草稿不落库。
        replaceText("project_edit_name", "未保存新名")
        pressBack()
        composeRule.waitUntil(5_000) { vm.projectEdit.value == null }
        assertNotNull("编辑面板关闭后项目详情应保留", vm.projectDetail.value)
        composeRule.onNodeWithText("项目详情").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("编辑项目").assertIsDisplayed()
        assertEquals("未保存新名不得写库", "课程项目", readProject(projectId)?.name)
    }

    /** 只测试使用：开关打开后目标项目读取恒为“已完成”，用于验证重开重试只执行一次。 */
    private class ForcedClosedProjectRepository(
        private val delegate: RoomProjectRepository,
        private val forcedClosedId: ProjectId,
    ) : ProjectRepository {
        var forceClosed: Boolean = false

        override fun observeAll(): Flow<List<Project>> = delegate.observeAll()

        override suspend fun get(id: ProjectId): Project? =
            delegate.get(id)?.let {
                if (forceClosed && id == forcedClosedId) {
                    it.copy(status = ProjectStatus.COMPLETED)
                } else {
                    it
                }
            }

        override suspend fun insert(project: Project): ProjectId = delegate.insert(project)

        override suspend fun update(project: Project) = delegate.update(project)

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
