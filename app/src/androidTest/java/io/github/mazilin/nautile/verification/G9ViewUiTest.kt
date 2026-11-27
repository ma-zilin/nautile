package io.github.mazilin.nautile.verification

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.repository.RoomProjectRepository
import io.github.mazilin.nautile.data.repository.RoomTaskRepository
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.model.ViewClassification
import io.github.mazilin.nautile.domain.model.ViewOptions
import io.github.mazilin.nautile.domain.model.ViewSort
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
 * G9 视图菜单与手动拖拽的界面行为（CMP-39—CMP-42、AC-T-08）。
 *
 * 预期依据 `docs/需求基线.md` v2.2 与用户 2026-11-25 的 G9 口径：
 * - 视图按钮打开“分类 / 排序”两组单选，当前项打勾，锚定浮层不遮罩列表；
 * - 选择立即生效，列表结构/顺序随分类与排序变化；
 * - 手动排序且非搜索态才允许长按纵向拖拽，落位写 0..n-1；
 * - 非手动模式与搜索态不能重排；搜索态不显示 0 结果组。
 *
 * 运行环境：emulator-5554（API 36，模拟器，非真机）。纯视觉像素（遮罩透明度）
 * 不作自动判定，报告单列人工核对项。
 */
@RunWith(AndroidJUnit4::class)
class G9ViewUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-25T12:00:00Z")
    private val clock = Clock { now }

    private lateinit var database: NautileDatabase
    private lateinit var taskRepository: RoomTaskRepository
    private lateinit var projectRepository: RoomProjectRepository
    private lateinit var settings: G9MemorySettingsRepository
    private lateinit var vm: TodoViewModel

    private val manualOrderWrites = mutableListOf<Map<TaskId, Long>>()

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

    private fun seedTask(
        title: String,
        priority: Priority = Priority.NONE,
        dueDate: LocalDate? = null,
        createdSeconds: Long = 0L,
        manualOrder: Long? = null,
    ): TaskId = runBlocking {
        val createdAt = now.plusSeconds(createdSeconds)
        taskRepository.insert(
            Task(
                id = TaskId(0L),
                title = title,
                position = GtdPosition.INBOX,
                priority = priority,
                dueDate = dueDate,
                manualOrder = manualOrder,
                createdAt = createdAt,
                updatedAt = createdAt,
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
            writeManualOrders = { orders ->
                manualOrderWrites += orders
                taskRepository.updateManualOrders(orders)
            },
        )
    }

    private fun setVmScreen() {
        composeRule.setContent {
            NautileTheme {
                val state by vm.uiState.collectAsState()
                TodoScreen(
                    state = state,
                    onViewClassificationSelected = vm::selectViewClassification,
                    onViewSortSelected = vm::selectViewSort,
                    onReorderGroup = vm::reorderGroup,
                    onOpenSearch = vm::openSearch,
                    onSearchQueryChanged = vm::onSearchQueryChanged,
                    onClearSearch = vm::clearSearch,
                    onCloseSearch = vm::closeSearch,
                    onDismissViewError = vm::dismissViewError,
                )
            }
        }
    }

    private fun awaitState(description: String, predicate: (TodoUiState) -> Boolean): TodoUiState {
        composeRule.waitUntil(10_000) { predicate(vm.uiState.value) }
        assertTrue(description, predicate(vm.uiState.value))
        return vm.uiState.value
    }

    private fun taskOrder(): List<String> =
        vm.uiState.value.groups.flatMap { group -> group.tasks }.map { it.title }

    private fun topOf(text: String): Float =
        composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun g9_view_menu_shows_two_groups_with_selected_marks_and_no_full_screen_scrim() {
        val task = Task(
            id = TaskId(1L),
            title = "可见任务",
            position = GtdPosition.INBOX,
            createdAt = now,
            updatedAt = now,
        )
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = TodoUiState(
                        position = GtdPosition.INBOX,
                        groups = listOf(TaskGroup(null, listOf(task))),
                        visibleTasks = listOf(task),
                        searchAvailable = true,
                        viewOptionsAvailable = true,
                        // R4 G6：手动排序入口隐藏；本轮验收以“已存 manual 回退 CREATED”的展示为准。
                        viewOptions = ViewOptions(ViewClassification.PROJECT, ViewSort.CREATED),
                    ),
                )
            }
        }

        composeRule.onNodeWithContentDescription("视图选项").assertIsDisplayed().performClick()

        composeRule.onNodeWithText("分类").assertIsDisplayed()
        composeRule.onNodeWithText("排序").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("分类：项目").assertIsSelected()
        composeRule.onNodeWithContentDescription("分类：优先级").assertIsNotSelected()
        composeRule.onNodeWithContentDescription("排序：创建时间").assertIsSelected()
        composeRule.onNodeWithContentDescription("排序：截止日期").assertIsNotSelected()
        // NO-19：视图菜单不显示“手动”入口，也不显示拖拽手柄提示。
        composeRule.onNodeWithContentDescription("排序：手动").assertDoesNotExist()
        composeRule.onAllNodesWithText("手动").assertCountEquals(0)

        // 锚定浮层无遮罩：菜单打开时列表内容仍可见（像素级 alpha 由人工核对）。
        composeRule.onNodeWithText("可见任务").assertIsDisplayed()
    }

    @Test
    fun g9_menu_selection_callbacks_fire_immediately() {
        var classification: ViewClassification? = null
        var sort: ViewSort? = null
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = TodoUiState(
                        viewOptionsAvailable = true,
                        viewOptions = ViewOptions.Default,
                    ),
                    onViewClassificationSelected = { classification = it },
                    onViewSortSelected = { sort = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("视图选项").performClick()
        composeRule.onNodeWithContentDescription("分类：优先级").performClick()
        composeRule.onNodeWithContentDescription("排序：截止日期").performClick()

        composeRule.runOnIdle {
            assertEquals(ViewClassification.PRIORITY, classification)
            assertEquals(ViewSort.DUE, sort)
        }
    }

    @Test
    fun g9_switching_classification_and_sort_changes_list_structure() {
        seedTask("高晚任务", priority = Priority.HIGH, dueDate = LocalDate.parse("2026-12-03"), createdSeconds = 1)
        seedTask("高早任务", priority = Priority.HIGH, dueDate = LocalDate.parse("2026-12-01"), createdSeconds = 2)
        seedTask("低任务", priority = Priority.LOW, dueDate = LocalDate.parse("2026-12-05"), createdSeconds = 3)
        createViewModel()
        setVmScreen()
        awaitState("任务就绪") { it.visibleTasks.size == 3 }

        composeRule.onNodeWithContentDescription("视图选项").performClick()
        composeRule.onNodeWithContentDescription("分类：优先级").performClick()
        val priorityState = awaitState("切到优先级分类") {
            it.viewOptions.classification == ViewClassification.PRIORITY
        }
        assertEquals(
            listOf("高优先级", "低优先级"),
            priorityState.groups.map { it.headerTitle },
        )
        // 默认手动排序下，高优先级组内按创建时间倒序：高早任务(2) 在 高晚任务(1) 前。
        assertTrue("默认组内顺序", topOf("高早任务") < topOf("高晚任务"))

        composeRule.onNodeWithContentDescription("视图选项").performClick()
        composeRule.onNodeWithContentDescription("排序：截止日期").performClick()
        awaitState("切到截止日期排序") { it.viewOptions.sort == ViewSort.DUE }
        // 截止日期排序后高优先级组内 12-01 在前、12-03 在后。
        assertTrue("截止排序应改变组内顺序", topOf("高早任务") < topOf("高晚任务"))
        assertEquals(
            listOf("高早任务", "高晚任务", "低任务"),
            taskOrder(),
        )
    }

    /**
     * R4 G6（NO-19、CMP-40.2）：手动排序入口暂缓，长按纵向拖拽不得重排、不得写库。
     * 旧用例曾断言“长按拖拽写 0..n-1”；本轮按基线 v2.15 更新为“不可达”，数据路径保留由
     * [G9ViewModelViewOptionsTest] 的代码级用例覆盖。
     */
    @Test
    fun g6r4_manual_drag_entry_unreachable_and_no_reorder() {
        seedTask("任务一", createdSeconds = 1)
        seedTask("任务二", createdSeconds = 2)
        seedTask("任务三", createdSeconds = 3)
        createViewModel()
        setVmScreen()
        awaitState("任务就绪") { it.visibleTasks.size == 3 }
        assertEquals("默认顺序（创建时间倒序）", listOf("任务三", "任务二", "任务一"), taskOrder())

        // 长按首行后向下拖过一行：入口已关闭，不应发生交换或写库。
        val rowPx = with(composeRule.density) { 64.dp.toPx() }
        composeRule.onNodeWithText("任务三").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, rowPx * 1.2f))
            advanceEventTime(120)
            up()
        }
        composeRule.waitForIdle()
        assertEquals("拖拽入口不可达，顺序不变", listOf("任务三", "任务二", "任务一"), taskOrder())
        assertEquals("拖拽入口不可达，不得写库", 0, manualOrderWrites.size)
    }

    @Test
    fun g9_non_manual_mode_cannot_reorder() {
        val tasks = listOf(
            task(1L, "任务一", createdSeconds = 1),
            task(2L, "任务二", createdSeconds = 2),
            task(3L, "任务三", createdSeconds = 3),
        )
        var reorderCalls = 0
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = TodoUiState(
                        position = GtdPosition.INBOX,
                        groups = listOf(TaskGroup(null, tasks)),
                        visibleTasks = tasks,
                        searchAvailable = true,
                        viewOptionsAvailable = true,
                        viewOptions = ViewOptions(ViewClassification.PROJECT, ViewSort.CREATED),
                    ),
                    onReorderGroup = { _, _ -> reorderCalls += 1 },
                )
            }
        }

        val rowPx = with(composeRule.density) { 64.dp.toPx() }
        composeRule.onNodeWithText("任务三").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, rowPx * 1.2f))
            advanceEventTime(120)
            up()
        }
        composeRule.waitForIdle()
        assertEquals("非手动模式不得触发重排", 0, reorderCalls)
    }

    @Test
    fun g9_search_state_cannot_reorder_and_hides_zero_result_groups() {
        seedTask("高任务", priority = Priority.HIGH, createdSeconds = 1)
        seedTask("无任务", priority = Priority.NONE, createdSeconds = 2)
        createViewModel()
        setVmScreen()
        awaitState("任务就绪") { it.visibleTasks.size == 2 }

        vm.selectViewClassification(ViewClassification.PRIORITY)
        awaitState("切到优先级分类") { it.viewOptions.classification == ViewClassification.PRIORITY }
        val optionsBefore = vm.uiState.value.viewOptions

        vm.openSearch()
        vm.onSearchQueryChanged("高")
        val searching = awaitState("搜索高任务") { it.searchActive && it.searchQuery == "高" }

        composeRule.onNodeWithText("高优先级").assertIsDisplayed()
        composeRule.onNodeWithText("无优先级").assertDoesNotExist()
        assertEquals("搜索不改变视图选项", optionsBefore, searching.viewOptions)

        // 搜索态尝试重排：不得写入。
        val groupKey = searching.groups.first().key
        vm.reorderGroup(groupKey, listOf(TaskId(1L)))
        composeRule.waitForIdle()
        assertEquals("搜索态不得触发手动排序写入", 0, manualOrderWrites.size)
    }

    private fun task(id: Long, title: String, createdSeconds: Long): Task {
        val createdAt = now.plusSeconds(createdSeconds)
        return Task(
            id = TaskId(id),
            title = title,
            position = GtdPosition.INBOX,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
    }
}
