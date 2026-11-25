package io.github.mazilin.nautile.verification

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.repository.RoomProjectRepository
import io.github.mazilin.nautile.data.repository.RoomTaskRepository
import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoViewModel
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G8 闸门：待办搜索交互（CMP-38、FR-N-14/15、BR-76）。
 *
 * 预期依据（`docs/需求基线.md` v2.2、用户 2026-11-25 决定）：
 * - 头部搜索入口可用，点击进入搜索栏；输入实时过滤当前清单（标题或项目名）；
 * - 无命中显示“无搜索结果”，不显示清单空态；清除只清空查询并保留搜索栏；
 * - 退出搜索恢复完整清单与滚动位置（本测试核对内容/顺序）；
 * - 搜索只影响可见性：不写任务字段、不改排序、不显示 0 结果项目组；
 * - 只过滤当前 GTD 清单，不搜详情/标签（当前 Task 无标签字段）。
 *
 * 使用内存 Room + 真实 [TodoViewModel] 驱动 [TodoScreen]，不复制业务规则。
 * 运行环境：emulator-5554（API 36，模拟器，非真机）。
 */
@RunWith(AndroidJUnit4::class)
class G8SearchUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-25T12:00:00Z")
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
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        database.close()
    }

    private fun seedTask(
        title: String,
        position: GtdPosition = GtdPosition.INBOX,
        projectId: ProjectId? = null,
        detail: String? = null,
    ): TaskId = runBlocking {
        taskRepository.insert(
            Task(
                id = TaskId(0L),
                title = title,
                projectId = projectId,
                position = position,
                createdAt = now,
                updatedAt = now,
                detail = detail,
            ),
        )
    }

    private fun seedProject(name: String): ProjectId = runBlocking {
        projectRepository.insert(
            Project(
                id = ProjectId(0L),
                name = name,
                createdAt = now,
                updatedAt = now,
            ),
        )
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

    private fun setScreen() {
        composeRule.setContent {
            NautileTheme {
                val state by vm.uiState.collectAsState()
                TodoScreen(
                    state = state,
                    onOpenSearch = vm::openSearch,
                    onSearchQueryChanged = vm::onSearchQueryChanged,
                    onClearSearch = vm::clearSearch,
                    onCloseSearch = vm::closeSearch,
                )
            }
        }
    }

    private fun awaitTasks(count: Int) {
        composeRule.waitUntil(10_000) {
            vm.uiState.value.visibleTasks.size == count
        }
    }

    private fun openSearch() {
        composeRule.onNodeWithContentDescription("搜索待办").assertIsEnabled().performClick()
        composeRule.waitUntil(5_000) { vm.uiState.value.searchActive }
        composeRule.onNodeWithContentDescription("退出搜索").assertIsDisplayed()
    }

    private fun typeQuery(text: String) {
        composeRule.onNode(hasSetTextAction()).performTextInput(text)
        composeRule.waitUntil(5_000) { vm.uiState.value.searchQuery == text }
    }

    private fun replaceQuery(text: String) {
        composeRule.onNode(hasSetTextAction()).performTextReplacement(text)
        composeRule.waitUntil(5_000) { vm.uiState.value.searchQuery == text }
    }

    private fun topOf(text: String): Float =
        composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun G8_CMP38_entry_opens_and_filters_by_title_and_project_in_realtime() {
        val projectId = seedProject("Deep Work")
        seedTask("概率论刷题")
        seedTask("英语单词", projectId = projectId)
        seedTask("买牛奶")
        createViewModel()
        setScreen()
        awaitTasks(3)

        // 生产 ViewModel 输出搜索可用，入口可点（与默认 TodoUiState 的禁用占位区分）。
        assertTrue("生产状态应启用搜索", vm.uiState.value.searchAvailable)
        openSearch()

        typeQuery("概率")
        composeRule.onNodeWithText("概率论刷题").assertIsDisplayed()
        composeRule.onNodeWithText("英语单词").assertDoesNotExist()
        composeRule.onNodeWithText("买牛奶").assertDoesNotExist()
        // 无命中的项目组不显示（0 结果组）。
        assertEquals(0, composeRule.onAllNodesWithText("Deep Work", substring = true)
            .fetchSemanticsNodes().size)

        // 清空后重新输入项目名，实时切换为项目命中。
        replaceQuery("deep")
        composeRule.onNodeWithText("英语单词").assertIsDisplayed()
        composeRule.onNodeWithText("概率论刷题").assertDoesNotExist()
        composeRule.onNodeWithText("买牛奶").assertDoesNotExist()
        composeRule.onAllNodesWithText("Deep Work", substring = true).fetchSemanticsNodes()
            .let { assertTrue("项目组头应显示", it.isNotEmpty()) }
    }

    @Test
    fun G8_CMP38_no_hit_shows_search_empty_state_not_list_empty_state() {
        seedTask("概率论刷题")
        seedTask("买牛奶")
        createViewModel()
        setScreen()
        awaitTasks(2)
        openSearch()

        typeQuery("不存在的关键词")

        composeRule.onNodeWithText("无搜索结果").assertIsDisplayed()
        composeRule.onNodeWithText("该清单暂无任务").assertDoesNotExist()
        composeRule.onNodeWithText("概率论刷题").assertDoesNotExist()
        composeRule.onNodeWithText("买牛奶").assertDoesNotExist()
    }

    @Test
    fun G8_CMP38_clear_restores_list_and_keeps_search_open() {
        seedTask("概率论刷题")
        seedTask("买牛奶")
        createViewModel()
        setScreen()
        awaitTasks(2)
        openSearch()
        typeQuery("概率")
        composeRule.onNodeWithText("买牛奶").assertDoesNotExist()

        val clear = composeRule.onNodeWithContentDescription("清除搜索")
        clear.assertIsEnabled().performClick()
        composeRule.waitUntil(5_000) { vm.uiState.value.searchQuery.isEmpty() }

        composeRule.onNodeWithText("概率论刷题").assertIsDisplayed()
        composeRule.onNodeWithText("买牛奶").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("退出搜索").assertIsDisplayed()
        assertTrue("清除后仍在搜索态", vm.uiState.value.searchActive)
        composeRule.onNodeWithContentDescription("清除搜索").assertIsNotEnabled()
    }

    @Test
    fun G8_CMP38_close_restores_full_list_and_preserves_order_without_writing_data() {
        seedTask("Alpha 一")
        seedTask("Alpha 二")
        seedTask("Alpha 三")
        createViewModel()
        setScreen()
        awaitTasks(3)
        val before = vm.uiState.value.visibleTasks.map { it.id }

        openSearch()
        typeQuery("Alpha")
        // 实时过滤后仍保持原展示顺序。
        assertTrue("搜索后任务顺序应保持", topOf("Alpha 一") < topOf("Alpha 二"))
        assertTrue("搜索后任务顺序应保持", topOf("Alpha 二") < topOf("Alpha 三"))

        // 搜索不改变数据：仓库内容与可见事实不变。
        assertEquals(before, vm.uiState.value.visibleTasks.map { it.id })
        val storedDuringSearch = runBlocking { taskRepository.observeAll().first() }
        assertEquals(
            listOf("Alpha 一", "Alpha 二", "Alpha 三"),
            storedDuringSearch.map { it.title },
        )

        composeRule.onNodeWithContentDescription("退出搜索").performClick()
        composeRule.waitUntil(5_000) { !vm.uiState.value.searchActive }

        composeRule.onNodeWithContentDescription("退出搜索").assertDoesNotExist()
        composeRule.onNodeWithText("Alpha 一").assertIsDisplayed()
        composeRule.onNodeWithText("Alpha 二").assertIsDisplayed()
        composeRule.onNodeWithText("Alpha 三").assertIsDisplayed()
        assertTrue("退出后任务顺序应保持", topOf("Alpha 一") < topOf("Alpha 二"))
        assertTrue("退出后任务顺序应保持", topOf("Alpha 二") < topOf("Alpha 三"))
        assertTrue("搜索串不应保留", vm.uiState.value.searchQuery.isEmpty())
        assertFalse(vm.uiState.value.searchActive)
        // 计数事实不随搜索过滤变化。
        assertEquals(before, vm.uiState.value.visibleTasks.map { it.id })
    }

    @Test
    fun G8_CMP38_search_limits_to_current_gtd_list_and_hides_empty_project_group() {
        val emptyProject = seedProject("空项目")
        seedTask("概率论刷题")
        seedTask("概率论听课", position = GtdPosition.WAITING)
        createViewModel()
        setScreen()
        awaitTasks(1)

        // 搜索前保留 0 任务项目组（清单视图既有语义）。
        composeRule.onAllNodesWithText("空项目", substring = true).fetchSemanticsNodes()
            .let { assertTrue("搜索前应显示空项目组", it.isNotEmpty()) }
        assertTrue(emptyProject.value > 0L)

        openSearch()
        typeQuery("概率")

        composeRule.onNodeWithText("概率论刷题").assertIsDisplayed()
        composeRule.onNodeWithText("概率论听课").assertDoesNotExist()
        composeRule.onAllNodesWithText("空项目", substring = true).fetchSemanticsNodes()
            .let { assertTrue("搜索态不显示 0 结果项目组", it.isEmpty()) }
    }

    @Test
    fun G8_CMP38_search_does_not_match_detail_and_keeps_stored_fields() {
        val id = seedTask("普通任务", detail = "概率论复习笔记")
        createViewModel()
        setScreen()
        awaitTasks(1)
        openSearch()

        typeQuery("概率论")
        composeRule.onNodeWithText("无搜索结果").assertIsDisplayed()
        composeRule.onNodeWithText("普通任务").assertDoesNotExist()

        replaceQuery("")
        composeRule.onNodeWithText("普通任务").assertIsDisplayed()
        assertEquals("概率论复习笔记", runBlocking { taskRepository.get(id) }?.detail)
    }

    /** 仅测试使用：设置仓库替身，避免连接真实 DataStore。 */
    private class MemorySettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(
            UserSettings(lastGtdPosition = GtdPosition.INBOX),
        )
        override val settings: Flow<UserSettings> = state

        override suspend fun setDefaultPage(page: AppPage) = Unit

        override suspend fun setLastPage(page: AppPage) = Unit

        override suspend fun setLastGtdPosition(position: GtdPosition) {
            state.value = state.value.copy(lastGtdPosition = position)
        }

        override suspend fun setPredictiveBackEnabled(enabled: Boolean) = Unit

        override suspend fun setViewOptions(position: GtdPosition, options: String) = Unit
    }
}
