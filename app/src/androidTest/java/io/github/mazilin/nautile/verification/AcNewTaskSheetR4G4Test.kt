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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.ui.screen.todo.NewTaskKind
import io.github.mazilin.nautile.ui.screen.todo.NewTaskSheet
import io.github.mazilin.nautile.ui.screen.todo.TodoViewModel
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R4 G4 闸门：创建面板四项属性工具栏（`docs/需求基线.md` v2.15 的 AC-D-07、
 * `docs/需求变更记录.md` 4.25、CMP-30/31）。
 *
 * 覆盖口径：
 * - 工具栏恰为日期、优先级、项目、清单四项，始终“图标＋文字”；无“详情”图标、无目标清单选择块；
 * - 未设置显示属性名，设置后显示所选值摘要，清除后回到属性名；
 * - 详情固定标题下方第二行；项目创建只填名称；
 * - 清单默认当前清单（当前为 ENDED 时默认收件箱），菜单可选、ENDED 禁用；
 * - 菜单打开时保持键盘与焦点，可继续输入；
 * - 无效标题阻止创建并提示；项目名称为空阻止创建。
 *
 * 工具栏文字位于 `clearAndSetSemantics` 按钮内，只在未合并语义树可断言（合并树只暴露完整语义）。
 * 使用 Room 内存库 + 真实 [TodoViewModel]，不复制业务规则、不修改生产代码。
 * 运行环境：emulator-5554（API 36，模拟器，非真机）。
 */
@RunWith(AndroidJUnit4::class)
class AcNewTaskSheetR4G4Test {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-26T09:00:00Z")
    private val clock = Clock { now }

    private lateinit var database: NautileDatabase
    private lateinit var taskRepository: RoomTaskRepository
    private lateinit var projectRepository: RoomProjectRepository
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
                // uiState 为 WhileSubscribed：订阅后当前清单切换才会反映到状态，与生产页面一致。
                vm.uiState.collectAsState()
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

    /** 工具栏按钮的文字只在未合并语义树可见，并须完整落在按钮边界内。 */
    private fun assertToolbarLabel(description: String, label: String) {
        val textNode = composeRule.onNode(
            hasText(label) and hasAnyAncestor(hasContentDescription(description, substring = true)),
            useUnmergedTree = true,
        )
        textNode.assertIsDisplayed()
        val itemBounds = composeRule
            .onNodeWithContentDescription(description, substring = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val textBounds = textNode.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "标签“$label”应渲染在 $description 按钮内：text=$textBounds item=$itemBounds",
            textBounds.left >= itemBounds.left - 1f &&
                textBounds.right <= itemBounds.right + 1f &&
                textBounds.top >= itemBounds.top - 1f &&
                textBounds.bottom <= itemBounds.bottom + 1f,
        )
    }

    // ---- CMP-30（R4 G4）：工具栏恰四项且每项“图标＋文字” ----

    @Test
    fun R4G4_toolbar_has_exactly_four_entries_with_visible_labels() {
        createViewModel()
        setSheet()
        openSheet()

        val descriptions = listOf(
            "设置截止日期，当前：未设置",
            "设置优先级，当前：无",
            "设置项目，当前：无项目",
            "移动到 GTD 清单，当前：收件箱",
        )
        descriptions.forEach { description ->
            composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
            composeRule.onAllNodes(hasContentDescription(description)).assertCountEquals(1)
        }
        // 无“详情”图标，也无标签/提醒/重复/更多/语音等额外入口。
        composeRule.onAllNodes(hasContentDescription("编辑详情", substring = true))
            .assertCountEquals(0)
        listOf("标签", "提醒", "重复", "更多", "语音").forEach { forbidden ->
            composeRule.onAllNodesWithText(forbidden).assertCountEquals(0)
        }

        // 四项同处一行并按日期/优先级/项目/清单从左到右排列（同一工具栏）。
        val bounds = descriptions.map {
            composeRule.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot
        }
        bounds.forEach { bound ->
            assertTrue(
                "四项应在同一行，top=${bounds.first().top} vs ${bound.top}",
                abs(bound.top - bounds.first().top) <= 2f,
            )
        }
        bounds.zipWithNext().forEach { (left, right) ->
            assertTrue("工具栏顺序应为日期→优先级→项目→清单", right.left > left.left)
        }

        // 未设置显示属性名：日期、优先级、项目；清单显示当前清单名。
        assertToolbarLabel("设置截止日期，当前：未设置", "日期")
        assertToolbarLabel("设置优先级，当前：无", "优先级")
        assertToolbarLabel("设置项目，当前：无项目", "项目")
        assertToolbarLabel("移动到 GTD 清单，当前：收件箱", "收件箱")
    }

    // ---- AC-D-07 / FR-D-02：无详情图标、无目标清单块、详情固定第二行 ----

    @Test
    fun R4G4_no_detail_icon_no_target_picker_and_detail_is_second_row() {
        createViewModel()
        setSheet()
        openSheet()

        composeRule.onAllNodes(hasContentDescription("编辑详情", substring = true))
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("目标清单（已结束不可选）").assertCountEquals(0)
        composeRule.onAllNodesWithText("清单：", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("调整详情").assertCountEquals(0)

        // 待办模式恰有标题、详情两个输入；详情紧接标题下方，且位于工具栏之上。
        val editable = composeRule.onAllNodes(hasSetTextAction())
        editable.assertCountEquals(2)
        val titleBounds = editable.onFirst().fetchSemanticsNode().boundsInRoot
        val detailBounds = composeRule.onNodeWithTag("new_task_detail")
            .fetchSemanticsNode().boundsInRoot
        val gapDp = (detailBounds.top - titleBounds.bottom) / composeRule.density.density
        assertTrue("详情应紧接标题下一行，实际间距 ${gapDp}dp", abs(gapDp) <= 2f)
        val toolbarTop = composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "详情行应在属性工具栏上方：detail.bottom=${detailBounds.bottom} toolbar.top=$toolbarTop",
            detailBounds.bottom <= toolbarTop + 1f,
        )
    }

    // ---- 未设置=属性名，设置后=摘要，清除后回到属性名 ----

    @Test
    fun R4G4_date_label_switches_between_name_and_summary_then_back() {
        createViewModel()
        setSheet()
        openSheet()
        assertToolbarLabel("设置截止日期，当前：未设置", "日期")

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
        assertToolbarLabel("设置截止日期，当前：今天 09:30", "今天 09:30")
        composeRule.onAllNodes(
            hasText("日期") and
                hasAnyAncestor(hasContentDescription("设置截止日期", substring = true)),
            useUnmergedTree = true,
        ).assertCountEquals(0)

        // 清除日期同时清空时刻，摘要回到属性名“日期”。
        composeRule.onNodeWithContentDescription("设置截止日期，当前：今天 09:30").performClick()
        composeRule.onNodeWithText("清除截止日期").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            vm.newTask.value?.dueDate == null && vm.newTask.value?.dueTime == null
        }
        composeRule.onNodeWithTag("date_panel_back").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置").assertIsDisplayed()
        assertToolbarLabel("设置截止日期，当前：未设置", "日期")
    }

    @Test
    fun R4G4_priority_label_switches_between_name_and_summary() {
        createViewModel()
        setSheet()
        openSheet()
        assertToolbarLabel("设置优先级，当前：无", "优先级")

        composeRule.onNodeWithContentDescription("设置优先级，当前：无").performClick()
        composeRule.onNodeWithContentDescription("优先级：高").performClick()
        composeRule.onNodeWithContentDescription("设置优先级，当前：高").assertIsDisplayed()
        assertToolbarLabel("设置优先级，当前：高", "高")
        composeRule.onAllNodes(
            hasText("优先级") and
                hasAnyAncestor(hasContentDescription("设置优先级", substring = true)),
            useUnmergedTree = true,
        ).assertCountEquals(0)

        composeRule.onNodeWithContentDescription("设置优先级，当前：高").performClick()
        composeRule.onNodeWithContentDescription("优先级：无").performClick()
        composeRule.onNodeWithContentDescription("设置优先级，当前：无").assertIsDisplayed()
        assertToolbarLabel("设置优先级，当前：无", "优先级")
    }

    @Test
    fun R4G4_project_label_switches_between_name_and_summary() {
        seedProject("课程项目")
        createViewModel()
        setSheet()
        openSheet()
        composeRule.waitUntil(5_000) {
            vm.activeProjects.value.any { it.name == "课程项目" }
        }
        assertToolbarLabel("设置项目，当前：无项目", "项目")

        composeRule.onNodeWithContentDescription("设置项目，当前：无项目").performClick()
        composeRule.onNodeWithContentDescription("项目：课程项目").performClick()
        composeRule.onNodeWithContentDescription("设置项目，当前：课程项目").assertIsDisplayed()
        assertToolbarLabel("设置项目，当前：课程项目", "课程项目")

        composeRule.onNodeWithContentDescription("设置项目，当前：课程项目").performClick()
        composeRule.onNodeWithContentDescription("项目：无项目").performClick()
        composeRule.onNodeWithContentDescription("设置项目，当前：无项目").assertIsDisplayed()
        assertToolbarLabel("设置项目，当前：无项目", "项目")
    }

    // ---- 清单：默认当前、ENDED 默认收件箱、菜单可选且 ENDED 禁用 ----

    @Test
    fun R4G4_checklist_defaults_to_current_position_and_menu_selects() {
        createViewModel()
        setSheet()
        composeRule.runOnIdle { vm.selectPosition(GtdPosition.WAITING) }
        composeRule.waitUntil(5_000) { vm.uiState.value.position == GtdPosition.WAITING }
        openSheet()
        composeRule.runOnIdle {
            assertEquals(GtdPosition.WAITING, vm.newTask.value?.targetPosition)
        }
        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：等待中").assertIsDisplayed()
        assertToolbarLabel("移动到 GTD 清单，当前：等待中", "等待中")

        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：等待中").performClick()
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

        composeRule.onNodeWithContentDescription("移动到 GTD 清单：将来／也许").performClick()
        composeRule.waitUntil(5_000) {
            vm.newTask.value?.targetPosition == GtdPosition.SOMEDAY
        }
        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：将来／也许")
            .assertIsDisplayed()
        assertToolbarLabel("移动到 GTD 清单，当前：将来／也许", "将来／也许")
    }

    @Test
    fun R4G4_ended_position_defaults_checklist_to_inbox_and_blocks_ended() {
        createViewModel()
        setSheet()
        composeRule.runOnIdle { vm.selectPosition(GtdPosition.ENDED) }
        composeRule.waitUntil(5_000) { vm.uiState.value.position == GtdPosition.ENDED }
        openSheet()
        composeRule.runOnIdle {
            assertEquals(
                "ENDED 清单内新建时目标清单应默认收件箱",
                GtdPosition.INBOX,
                vm.newTask.value?.targetPosition,
            )
        }
        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：收件箱")
            .assertIsDisplayed()
        assertToolbarLabel("移动到 GTD 清单，当前：收件箱", "收件箱")

        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：收件箱").performClick()
        composeRule.onNodeWithContentDescription("已结束，已结束不可直接移入")
            .assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("移动到 GTD 清单：下一步行动").performClick()
        composeRule.waitUntil(5_000) {
            vm.newTask.value?.targetPosition == GtdPosition.NEXT_ACTION
        }
    }

    // ---- CMP-31：菜单打开时保持键盘与焦点，可继续输入 ----

    @Test
    fun R4G4_keyboard_stays_open_and_typing_continues_after_menu() {
        createViewModel()
        setSheet()
        openSheet()

        val titleField = composeRule.onAllNodes(hasSetTextAction()).onFirst()
        titleField.performClick()
        titleField.assertIsFocused()
        titleField.performTextInput("任务")
        composeRule.waitUntil(5_000) { vm.newTask.value?.title == "任务" }

        composeRule.onNodeWithContentDescription("设置优先级，当前：无").performClick()
        composeRule.onNodeWithContentDescription("优先级：高").assertIsDisplayed()
        titleField.assertIsFocused()

        // 焦点与输入能力保持：菜单未收起键盘、未抢焦点。
        titleField.performTextInput("续写")
        composeRule.waitUntil(5_000) { vm.newTask.value?.title == "任务续写" }
        composeRule.onNodeWithText("创建").assertIsDisplayed()
    }

    // ---- AC-D-07：无效标题阻止创建并提示 ----

    @Test
    fun R4G4_blank_title_blocks_create_and_shows_error() {
        createViewModel()
        setSheet()
        openSheet()

        composeRule.onNodeWithText("创建").performClick()
        composeRule.onAllNodesWithText("标题不能为空").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("新建").assertIsDisplayed()
        assertEquals(0, runBlocking { taskRepository.observeAll().first() }.size)
    }

    // ---- AC-D-07 / 4.25：项目创建只填名称，名称为空阻止 ----

    @Test
    fun R4G4_project_create_only_name_and_blank_blocks() {
        createViewModel()
        setSheet()
        openSheet()

        composeRule.onNodeWithText("项目").performClick()
        composeRule.waitUntil(5_000) { vm.newTask.value?.kind == NewTaskKind.PROJECT }

        // 只填名称：仅一个输入；无详情行、无属性工具栏。
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        composeRule.onNodeWithText("项目名称").assertIsDisplayed()
        composeRule.onNodeWithText("详情（可选）").assertDoesNotExist()
        composeRule.onAllNodes(hasContentDescription("设置截止日期", substring = true))
            .assertCountEquals(0)

        composeRule.onNodeWithText("创建项目").performClick()
        composeRule.onAllNodesWithText("项目名称不能为空").onFirst().assertIsDisplayed()
        assertNotNull("无效项目名不得关闭面板", vm.newTask.value)
        assertEquals(0, runBlocking { projectRepository.observeAll().first() }.size)

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("研究项目")
        composeRule.waitUntil(5_000) { vm.newTask.value?.projectName == "研究项目" }
        composeRule.onNodeWithText("创建项目").performClick()
        composeRule.waitUntil(10_000) { vm.newTask.value == null }

        val projects = runBlocking { projectRepository.observeAll().first() }
        assertEquals("只创建一条项目", 1, projects.size)
        assertEquals("研究项目", projects.single().name)
        assertEquals(ProjectStatus.ACTIVE, projects.single().status)
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
