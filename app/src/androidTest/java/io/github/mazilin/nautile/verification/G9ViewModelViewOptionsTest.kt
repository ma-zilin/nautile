package io.github.mazilin.nautile.verification

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
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
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoUiState
import io.github.mazilin.nautile.ui.screen.todo.TodoViewModel
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G9 视图选项与手动排序的 ViewModel 行为（CMP-39—CMP-41、AC-T-08、AC-T-13）。
 *
 * 预期依据 `docs/需求基线.md` v2.2 与用户 2026-11-25 的 G9 口径：
 * - 默认分类=项目、排序=手动；未排序任务按创建时间倒序（与旧默认展示一致）；
 * - 选择即生效并写入当前 GTD 模块，模块之间互不串值，重建 ViewModel 后读回；
 * - 写入失败回退旧值并提示，不显示成功；
 * - 手动排序仅 manual 且非搜索态接受，落位写 0..n-1，失败回滚并提示；
 * - 搜索只过滤可见性，不改变选项；完成/放弃任务不改变选项。
 *
 * 使用内存 Room + 真实 [TodoViewModel]，不复制业务规则。
 * 运行环境：emulator-5554（API 36，模拟器，非真机）。
 */
@RunWith(AndroidJUnit4::class)
class G9ViewModelViewOptionsTest {

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
    private var failManualWrites = false

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
        position: GtdPosition = GtdPosition.INBOX,
        priority: Priority = Priority.NONE,
        dueDate: LocalDate? = null,
        createdSeconds: Long = 0L,
    ): TaskId = runBlocking {
        val createdAt = now.plusSeconds(createdSeconds)
        taskRepository.insert(
            Task(
                id = TaskId(0L),
                title = title,
                position = position,
                priority = priority,
                dueDate = dueDate,
                createdAt = createdAt,
                updatedAt = createdAt,
            ),
        )
    }

    private fun createViewModel(writer: (suspend (Map<TaskId, Long>) -> Unit)? = null) {
        vm = TodoViewModel(
            taskRepository = taskRepository,
            projectRepository = projectRepository,
            settingsRepository = settings,
            taskCommands = TaskCommands(taskRepository, projectRepository, clock),
            projectCommands = ProjectCommands(projectRepository, taskRepository, clock),
            writeManualOrders = writer ?: { orders ->
                manualOrderWrites += orders
                if (failManualWrites) throw IOException("模拟手动排序写入失败")
                taskRepository.updateManualOrders(orders)
            },
        )
    }

    private fun setScreen() {
        composeRule.setContent {
            NautileTheme {
                val state by vm.uiState.collectAsState()
                TodoScreen(state = state)
            }
        }
    }

    private fun awaitState(description: String, predicate: (TodoUiState) -> Boolean): TodoUiState {
        composeRule.waitUntil(10_000) { predicate(vm.uiState.value) }
        assertTrue(description, predicate(vm.uiState.value))
        return vm.uiState.value
    }

    private fun taskIdsInGroup(groupIndex: Int = 0): List<Long> =
        vm.uiState.value.groups[groupIndex].tasks.map { it.id.value }

    @Test
    fun g9_default_is_project_manual_with_created_desc_order() {
        seedTask("旧任务", createdSeconds = 1)
        seedTask("中任务", createdSeconds = 2)
        seedTask("新任务", createdSeconds = 3)
        createViewModel()
        setScreen()

        val state = awaitState("三条任务进入当前清单") { it.visibleTasks.size == 3 }
        assertEquals("默认分类=项目", ViewClassification.PROJECT, state.viewOptions.classification)
        assertEquals("默认排序=手动", ViewSort.MANUAL, state.viewOptions.sort)
        assertTrue("视图入口应可用", state.viewOptionsAvailable)
        assertEquals("默认仍按项目分组", 1, state.groups.size)
        assertEquals(
            "未排序任务按创建时间倒序（旧默认展示）",
            listOf(3L, 2L, 1L),
            taskIdsInGroup(),
        )
    }

    @Test
    fun g9_select_options_applies_immediately_and_persists_per_module() {
        seedTask("高任务", priority = Priority.HIGH, dueDate = LocalDate.parse("2026-12-03"), createdSeconds = 1)
        seedTask("低任务", priority = Priority.LOW, dueDate = LocalDate.parse("2026-12-01"), createdSeconds = 2)
        createViewModel()
        setScreen()
        awaitState("任务就绪") { it.visibleTasks.size == 2 }

        vm.selectViewClassification(ViewClassification.PRIORITY)
        val priorityState = awaitState("切到优先级分类") {
            it.viewOptions.classification == ViewClassification.PRIORITY
        }
        assertEquals(
            listOf("高优先级", "低优先级"),
            priorityState.groups.map { it.headerTitle },
        )
        assertEquals(
            "选择即写入当前模块",
            "classification=priority;sort=manual",
            settings.viewOptionsWrites.last().second,
        )
        assertEquals(GtdPosition.INBOX, settings.viewOptionsWrites.last().first)

        vm.selectViewSort(ViewSort.DUE)
        val dueSortState = awaitState("切到截止日期排序") { it.viewOptions.sort == ViewSort.DUE }
        assertEquals(
            "排序写入与分类组成同一稳定文本",
            "classification=priority;sort=due",
            settings.viewOptionsWrites.last().second,
        )
        assertEquals("高优先级仍在前", "高优先级", dueSortState.groups.first().headerTitle)

        // 切换清单：等待中模块使用自己的默认选项，不继承收件箱。
        vm.selectPosition(GtdPosition.WAITING)
        val waitingState = awaitState("切到等待中") { it.position == GtdPosition.WAITING }
        assertEquals(ViewOptions.Default, waitingState.viewOptions)
        runBlocking {
            val inboxStored = settings.settings.first().perModuleViewOptions
            assertEquals(
                ViewOptions(ViewClassification.PRIORITY, ViewSort.DUE),
                ViewOptions.fromSettings(inboxStored, GtdPosition.INBOX),
            )
        }

        // 在等待中改排序，不得覆盖收件箱。
        vm.selectViewSort(ViewSort.CREATED)
        awaitState("等待中排序写入") { it.viewOptions.sort == ViewSort.CREATED }
        runBlocking {
            val stored = settings.settings.first().perModuleViewOptions
            assertEquals(
                ViewOptions(ViewClassification.PRIORITY, ViewSort.DUE),
                ViewOptions.fromSettings(stored, GtdPosition.INBOX),
            )
            assertEquals(
                ViewOptions(ViewClassification.PROJECT, ViewSort.CREATED),
                ViewOptions.fromSettings(stored, GtdPosition.WAITING),
            )
        }
    }

    @Test
    fun g9_saved_options_are_restored_by_new_view_model() {
        seedTask("任务", createdSeconds = 1)
        runBlocking {
            settings.setViewOptions(
                GtdPosition.INBOX,
                ViewOptions(ViewClassification.DUE, ViewSort.PRIORITY).encode(),
            )
        }
        createViewModel()
        setScreen()

        val state = awaitState("读取已保存选项") {
            it.viewOptions == ViewOptions(ViewClassification.DUE, ViewSort.PRIORITY)
        }
        assertEquals("已保存选项应在新实例生效", ViewSort.PRIORITY, state.viewOptions.sort)
        assertEquals(ViewClassification.DUE, state.viewOptions.classification)
    }

    @Test
    fun g9_write_failure_reverts_to_previous_option_and_shows_error() {
        seedTask("任务A", createdSeconds = 1)
        seedTask("任务B", createdSeconds = 2)
        createViewModel()
        setScreen()
        awaitState("任务就绪") { it.visibleTasks.size == 2 }
        settings.failViewOptionsWrites = true

        vm.selectViewSort(ViewSort.CREATED)
        val failed = awaitState("写入失败应提示") { it.viewOptionsError != null }

        assertEquals("失败后回退旧排序", ViewSort.MANUAL, failed.viewOptions.sort)
        assertEquals(
            "列表顺序回到旧排序",
            listOf(2L, 1L),
            taskIdsInGroup(),
        )
        assertTrue(
            "提示内容应说明保存失败",
            failed.viewOptionsError.orEmpty().contains("视图选项保存失败"),
        )

        vm.dismissViewError()
        awaitState("关闭提示") { it.viewOptionsError == null }
    }

    @Test
    fun g9_manual_reorder_writes_zero_based_orders_and_persists() {
        val first = seedTask("任务一", createdSeconds = 1)
        val second = seedTask("任务二", createdSeconds = 2)
        val third = seedTask("任务三", createdSeconds = 3)
        createViewModel()
        setScreen()
        val state = awaitState("任务就绪") { it.visibleTasks.size == 3 }

        assertEquals("默认手动序按创建时间倒序", listOf(3L, 2L, 1L), taskIdsInGroup())
        val groupKey = state.groups[0].key

        vm.reorderGroup(groupKey, listOf(first, second, third))

        val reordered = awaitState("落位后顺序更新") { taskIdsInGroup() == listOf(1L, 2L, 3L) }
        assertEquals("写入一次批量事务", 1, manualOrderWrites.size)
        assertEquals(
            "落位写 0..n-1",
            mapOf(first to 0L, second to 1L, third to 2L),
            manualOrderWrites.single(),
        )
        assertNull("列表无错误提示", reordered.manualOrderError)

        runBlocking {
            assertEquals(0L, taskRepository.get(first)?.manualOrder)
            assertEquals(1L, taskRepository.get(second)?.manualOrder)
            assertEquals(2L, taskRepository.get(third)?.manualOrder)
        }
    }

    @Test
    fun g9_manual_reorder_failure_rolls_back_and_shows_error() {
        val first = seedTask("任务一", createdSeconds = 1)
        val second = seedTask("任务二", createdSeconds = 2)
        val third = seedTask("任务三", createdSeconds = 3)
        failManualWrites = true
        createViewModel()
        setScreen()
        val state = awaitState("任务就绪") { it.visibleTasks.size == 3 }
        val groupKey = state.groups[0].key

        vm.reorderGroup(groupKey, listOf(first, second, third))
        val failed = awaitState("写入失败应提示") { it.manualOrderError != null }

        assertEquals(
            "失败后回滚到原顺序",
            listOf(3L, 2L, 1L),
            taskIdsInGroup(),
        )
        assertTrue(
            "提示内容应说明已恢复",
            failed.manualOrderError.orEmpty().contains("手动排序保存失败"),
        )
        runBlocking {
            listOf(first, second, third).forEach { id ->
                assertNull("失败不得写库", taskRepository.get(id)?.manualOrder)
            }
        }
    }

    @Test
    fun g9_reorder_is_ignored_when_sort_is_not_manual_or_in_search() {
        val first = seedTask("任务一", createdSeconds = 1)
        val second = seedTask("任务二", createdSeconds = 2)
        val third = seedTask("任务三", createdSeconds = 3)
        createViewModel()
        setScreen()
        awaitState("任务就绪") { it.visibleTasks.size == 3 }
        val groupKey = vm.uiState.value.groups[0].key

        vm.selectViewSort(ViewSort.CREATED)
        awaitState("切到创建时间排序") { it.viewOptions.sort == ViewSort.CREATED }
        val beforeNonManual = taskIdsInGroup()
        vm.reorderGroup(groupKey, listOf(first, second, third))
        composeRule.waitForIdle()
        assertEquals("非手动模式不得写入", 0, manualOrderWrites.size)
        assertEquals("非手动模式顺序不变", beforeNonManual, taskIdsInGroup())

        // 搜索态即使排序=手动也不接受重排。
        vm.selectViewSort(ViewSort.MANUAL)
        awaitState("切回手动排序") { it.viewOptions.sort == ViewSort.MANUAL }
        val optionsBeforeSearch = vm.uiState.value.viewOptions
        vm.openSearch()
        vm.onSearchQueryChanged("任务")
        val searching = awaitState("进入搜索态") { it.searchActive && it.searchQuery == "任务" }
        vm.reorderGroup(groupKey, listOf(first, second, third))
        composeRule.waitForIdle()
        assertEquals("搜索态不得写入", 0, manualOrderWrites.size)
        assertEquals("搜索不改变排序选项", optionsBeforeSearch, searching.viewOptions)
    }

    @Test
    fun g9_complete_and_abandon_do_not_change_view_options() {
        val first = seedTask("完成任务", createdSeconds = 1)
        val second = seedTask("放弃任务", createdSeconds = 2)
        createViewModel()
        setScreen()
        awaitState("任务就绪") { it.visibleTasks.size == 2 }

        vm.selectViewClassification(ViewClassification.PRIORITY)
        awaitState("切到优先级分类") {
            it.viewOptions.classification == ViewClassification.PRIORITY
        }
        vm.selectViewSort(ViewSort.PRIORITY)
        val before = awaitState("选项生效") {
            it.viewOptions == ViewOptions(ViewClassification.PRIORITY, ViewSort.PRIORITY)
        }
        val writesBefore = settings.viewOptionsWrites.size

        vm.completeTask(first)
        awaitState("完成任务离开当前清单") { it.visibleTasks.size == 1 }
        vm.abandonTask(second)
        awaitState("放弃任务离开当前清单") { it.visibleTasks.isEmpty() }

        assertEquals("完成/放弃不改选项", before.viewOptions, vm.uiState.value.viewOptions)
        assertEquals("完成/放弃不额外写选项", writesBefore, settings.viewOptionsWrites.size)
        assertNotEquals("选项不应被重置", ViewOptions.Default, vm.uiState.value.viewOptions)
        assertFalse(vm.uiState.value.searchActive)
    }
}
