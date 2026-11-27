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
import androidx.compose.ui.test.hasAnyAncestor
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
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.ui.screen.todo.NewTaskSheet
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G6 闸门：新建面板属性工具栏与锚定菜单（CMP-30—CMP-37）。
 *
 * 预期依据（`docs/需求基线.md` v2.15 的 AC-D-07、`docs/需求变更记录.md` 4.25、
 * `.plans/设计/01_视觉与动效标准.md` §13）：
 * - CMP-30（R4 G4 口径）：恰四个入口（日期、优先级、项目、清单），始终“图标＋文字”；
 *   未设置显示属性名、设置后显示所选值摘要；无详情图标、无标签/提醒/重复/更多/语音；
 * - CMP-31：菜单为面板内浮层、无遮罩、点击不收起键盘；单项触控 ≥48×48dp；
 * - CMP-32/33/34/35：日期与可选时间可清除；优先级四级；项目为无 + 活跃项目 + 新建项目；
 *   清单五项且 ENDED 禁用；
 * - CMP-36（R4 口径）：详情固定标题下方第二行，编辑详情不改变其他摘要；
 * - AC-T-13：优先级与截止日期创建后保存；AC-D-07：空白标题阻止创建并提示。
 *
 * 使用 Room 内存库 + 真实 [TodoViewModel] 验证“创建一次写入”，不复制业务规则。
 * 运行环境：emulator-5554（API 36，模拟器，非真机）。
 */
@RunWith(AndroidJUnit4::class)
class AcNewTaskToolbarG6Test {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-25T09:00:00Z")
    private val clock = Clock { now }

    private lateinit var database: NautileDatabase
    private lateinit var taskRepository: RoomTaskRepository
    private lateinit var projectRepository: RoomProjectRepository
    private lateinit var vm: TodoViewModel

    private val toolbarDescriptions = listOf(
        "设置截止日期",
        "设置优先级",
        "设置项目",
        "移动到 GTD 清单",
    )

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

    private fun createViewModel() {
        vm = TodoViewModel(
            taskRepository = taskRepository,
            projectRepository = projectRepository,
            settingsRepository = MemorySettingsRepository(),
            taskCommands = TaskCommands(taskRepository, projectRepository, clock),
            projectCommands = ProjectCommands(projectRepository, taskRepository, clock),
        )
    }

    private fun seedProject(name: String, status: ProjectStatus = ProjectStatus.ACTIVE): ProjectId =
        runBlocking {
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

    private fun setSheet() {
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
    }

    private fun openSheet() {
        composeRule.runOnIdle { vm.openNewTask() }
        composeRule.waitUntil(5_000) { vm.newTask.value != null }
        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置").assertIsDisplayed()
    }

    private fun selectDay(date: LocalDate) {
        val month = date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val label = "$month ${date.dayOfMonth}, ${date.year}"
        val clickable = composeRule.onAllNodes(hasText(label, substring = true) and hasClickAction())
        val nodes = clickable.fetchSemanticsNodes()
        assertTrue(
            "日期 $label 不可点击：\n" + composeRule.onRoot(useUnmergedTree = true).printToString(),
            nodes.isNotEmpty(),
        )
        clickable.onFirst().performClick()
    }

    // ---- CMP-30（R4 G4 口径）：工具栏恰四项、图标＋文字、无额外入口、无详情图标 ----

    @Test
    fun G6_CMP30_toolbar_has_exactly_four_entries_with_labels() {
        createViewModel()
        setSheet()
        openSheet()

        toolbarDescriptions.forEach { prefix ->
            composeRule.onNodeWithContentDescription(prefix, substring = true).assertIsDisplayed()
            // 每个入口只有一个节点，没有重复入口。
            composeRule.onAllNodes(hasContentDescription(prefix, substring = true))
                .assertCountEquals(1)
        }
        // R4 G4：四项始终“图标＋文字”，未设置显示属性名（AC-D-07、4.25）。
        assertToolbarLabel("设置截止日期", "日期")
        assertToolbarLabel("设置优先级", "优先级")
        assertToolbarLabel("设置项目", "项目")
        assertToolbarLabel("移动到 GTD 清单", "收件箱")
        // 详情不再是工具栏入口。
        composeRule.onAllNodes(hasContentDescription("编辑详情", substring = true))
            .assertCountEquals(0)
        listOf("标签", "提醒", "重复", "更多", "语音").forEach { forbidden ->
            composeRule.onAllNodesWithText(forbidden).assertCountEquals(0)
        }
    }

    /** 工具栏“图标＋文字”的文字节点位于清空语义的按钮内，只在未合并语义树中可见。 */
    private fun assertToolbarLabel(description: String, label: String) {
        val node = composeRule.onNode(
            hasText(label) and hasAnyAncestor(hasContentDescription(description, substring = true)),
            useUnmergedTree = true,
        )
        node.assertIsDisplayed()
        val itemBounds = composeRule
            .onNodeWithContentDescription(description, substring = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val textBounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "标签“$label”应渲染在 $description 按钮内：text=$textBounds item=$itemBounds",
            textBounds.left >= itemBounds.left - 1f &&
                textBounds.right <= itemBounds.right + 1f &&
                textBounds.top >= itemBounds.top - 1f &&
                textBounds.bottom <= itemBounds.bottom + 1f,
        )
    }

    @Test
    fun G6_CMP31_toolbar_items_are_at_least_48dp() {
        createViewModel()
        setSheet()
        openSheet()

        toolbarDescriptions.forEach { prefix ->
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

    // ---- CMP-32 / AC-T-13：日期 + 可选时间 + 清除 ----

    @Test
    fun G6_CMP32_date_and_time_summary_then_clear() {
        createViewModel()
        setSheet()
        openSheet()

        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置").performClick()
        composeRule.onNodeWithText("选择截止日期").assertIsDisplayed()
        // 无日期时“时间”行禁用，并说明原因（v2.16 / R5-4 / CMP-32）。
        composeRule.onNodeWithTag("date_panel_time_row")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("先选择日期，再设置时间").assertExists()

        val today = LocalDate.now()
        selectDay(today)
        composeRule.waitUntil(5_000) { vm.newTask.value?.dueDate != null }
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
        composeRule.waitUntil(5_000) { vm.newTask.value?.dueTime == LocalTime.of(9, 30) }
        composeRule.onNodeWithTag("date_panel_back").performScrollTo().performClick()

        composeRule.onNodeWithContentDescription("设置截止日期，当前：今天 09:30")
            .assertIsDisplayed()

        // 清除日期同时清空时刻，摘要回到“未设置”（CMP-32）。
        composeRule.onNodeWithContentDescription("设置截止日期，当前：今天 09:30").performClick()
        composeRule.onNodeWithText("清除截止日期").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            vm.newTask.value?.dueDate == null && vm.newTask.value?.dueTime == null
        }
        composeRule.onNodeWithTag("date_panel_back").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置")
            .assertIsDisplayed()
    }

    // ---- CMP-33：优先级四项 + 清除即“无” ----

    @Test
    fun G6_CMP33_priority_four_levels_then_clear_to_none() {
        createViewModel()
        setSheet()
        openSheet()

        composeRule.onNodeWithContentDescription("设置优先级，当前：无").performClick()
        listOf("优先级：高", "优先级：中", "优先级：低", "优先级：无").forEach { description ->
            composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("优先级：高").performClick()
        composeRule.onNodeWithContentDescription("设置优先级，当前：高").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("设置优先级，当前：高").performClick()
        composeRule.onNodeWithContentDescription("优先级：无").performClick()
        composeRule.onNodeWithContentDescription("设置优先级，当前：无").assertIsDisplayed()
    }

    // ---- CMP-34：项目菜单（无 + 活跃项目 + 新建项目；已结项不可选） ----

    @Test
    fun G6_CMP34_project_menu_only_active_and_selectable() {
        seedProject("课程项目", ProjectStatus.ACTIVE)
        seedProject("已结项项目", ProjectStatus.COMPLETED)
        createViewModel()
        setSheet()
        openSheet()
        composeRule.waitUntil(5_000) {
            vm.activeProjects.value.any { it.name == "课程项目" }
        }

        composeRule.onNodeWithContentDescription("设置项目，当前：无项目").performClick()
        composeRule.onNodeWithText("无项目").assertIsDisplayed()
        composeRule.onNodeWithText("课程项目").assertIsDisplayed()
        composeRule.onNodeWithText("新建项目").assertIsDisplayed()
        composeRule.onNodeWithText("已结项项目").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("项目：课程项目").performClick()
        composeRule.onNodeWithContentDescription("设置项目，当前：课程项目").assertIsDisplayed()
    }

    @Test
    fun G6_CMP34_new_project_from_menu_is_filled_back() {
        createViewModel()
        setSheet()
        openSheet()

        composeRule.onNodeWithContentDescription("设置项目，当前：无项目").performClick()
        composeRule.onNodeWithText("新建项目").performClick()
        composeRule.onAllNodes(hasSetTextAction()).onLast().performTextInput("研究项目")
        composeRule.onAllNodesWithText("创建").onLast().performClick()

        composeRule.waitUntil(5_000) {
            vm.newTask.value?.projectId != null &&
                vm.activeProjects.value.any { it.name == "研究项目" }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("设置项目，当前：研究项目").assertIsDisplayed()

        val projects = runBlocking { projectRepository.observeAll().first() }
        assertTrue(
            "新建项目应写入活跃项目",
            projects.any { it.name == "研究项目" && it.status == ProjectStatus.ACTIVE },
        )
    }

    // ---- CMP-35：GTD 清单五项，ENDED 禁用 ----

    @Test
    fun G6_CMP35_checklist_five_entries_and_ended_disabled() {
        createViewModel()
        setSheet()
        openSheet()

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
        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：等待中")
            .assertIsDisplayed()
    }

    // ---- CMP-36（R4 G4 口径）：详情固定第二行，编辑详情不改变其他摘要 ----

    @Test
    fun G6_CMP36_detail_second_row_edit_does_not_change_other_summaries() {
        createViewModel()
        setSheet()
        openSheet()

        composeRule.onNodeWithContentDescription("设置优先级，当前：无").performClick()
        composeRule.onNodeWithContentDescription("优先级：高").performClick()
        composeRule.onNodeWithContentDescription("设置优先级，当前：高").assertIsDisplayed()

        // 详情固定为标题下方第二行，不作为工具栏入口（AC-D-07、4.25）。
        val editable = composeRule.onAllNodes(hasSetTextAction())
        editable.assertCountEquals(2)
        val titleBounds = editable.onFirst().fetchSemanticsNode().boundsInRoot
        val detailBounds = composeRule.onNodeWithTag("new_task_detail")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "详情应为标题下一行：title=$titleBounds detail=$detailBounds",
            detailBounds.top >= titleBounds.bottom - 1f,
        )
        composeRule.onNodeWithTag("new_task_detail").assertIsDisplayed()

        composeRule.onNodeWithTag("new_task_detail").performClick()
        composeRule.onNodeWithTag("new_task_detail").assertIsFocused()
        composeRule.onNodeWithTag("new_task_detail").performTextInput("第一行详情")
        composeRule.waitUntil(5_000) { vm.newTask.value?.detail == "第一行详情" }

        composeRule.onNodeWithContentDescription("设置优先级，当前：高").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：收件箱")
            .assertIsDisplayed()
        composeRule.onAllNodes(hasContentDescription("编辑详情", substring = true))
            .assertCountEquals(0)

        composeRule.onNodeWithTag("new_task_detail").performTextReplacement("")
        composeRule.waitUntil(5_000) { vm.newTask.value?.detail == "" }
        composeRule.onNodeWithContentDescription("设置优先级，当前：高").assertIsDisplayed()
    }

    // ---- CMP-31：键盘不收起，面板仍可用（不做像素断言） ----

    @Test
    fun G6_CMP31_menu_keeps_input_focus_and_panel_usable() {
        createViewModel()
        setSheet()
        openSheet()

        val titleField = composeRule.onAllNodes(hasSetTextAction()).onFirst()
        titleField.performClick()
        titleField.assertIsFocused()

        composeRule.onNodeWithContentDescription("设置优先级，当前：无")
            .performClick()
        composeRule.onNodeWithContentDescription("优先级：高").assertIsDisplayed()
        titleField.assertIsFocused()
        composeRule.onNodeWithText("新建").assertIsDisplayed()
        composeRule.onNodeWithText("创建").assertIsDisplayed()
    }

    // ---- AC-T-13：创建时一次写入全部属性 ----

    @Test
    fun G6_AC_T13_create_writes_all_attributes_once() {
        val projectId = seedProject("课程项目", ProjectStatus.ACTIVE)
        createViewModel()
        setSheet()
        openSheet()
        composeRule.waitUntil(5_000) {
            vm.activeProjects.value.any { it.id == projectId }
        }

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("综合任务")
        composeRule.onNodeWithTag("new_task_detail").performTextInput("备注内容")

        composeRule.onNodeWithContentDescription("设置优先级，当前：无").performClick()
        composeRule.onNodeWithContentDescription("优先级：高").performClick()
        composeRule.onNodeWithContentDescription("设置优先级，当前：高").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("设置项目，当前：无项目").performClick()
        composeRule.onNodeWithContentDescription("项目：课程项目").performClick()
        composeRule.onNodeWithContentDescription("设置项目，当前：课程项目").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：收件箱").performClick()
        composeRule.onNodeWithContentDescription("移动到 GTD 清单：等待中").performClick()

        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置").performClick()
        val today = LocalDate.now()
        selectDay(today)
        composeRule.waitUntil(5_000) { vm.newTask.value?.dueDate != null }
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
        assertEquals("创建一次只应写入一条记录", 1, tasks.size)
        val saved = tasks.single()
        assertEquals("综合任务", saved.title)
        assertEquals(Priority.HIGH, saved.priority)
        assertEquals(today, saved.dueDate)
        assertEquals(LocalTime.of(9, 30), saved.dueTime)
        assertEquals(projectId, saved.projectId)
        assertEquals(GtdPosition.WAITING, saved.position)
        assertEquals("备注内容", saved.detail)
        assertNull(saved.manualOrder)
        assertEquals(now, saved.createdAt)
    }

    // ---- AC-D-07：空白标题阻止创建并提示 ----

    @Test
    fun G6_AC_D07_blank_title_blocks_create_and_shows_error() {
        createViewModel()
        setSheet()
        openSheet()

        composeRule.onNodeWithText("创建").performClick()

        composeRule.onAllNodesWithText("标题不能为空").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("新建").assertIsDisplayed()
        val tasks = runBlocking { taskRepository.observeAll().first() }
        assertEquals("空白标题不得生成记录", 0, tasks.size)
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
