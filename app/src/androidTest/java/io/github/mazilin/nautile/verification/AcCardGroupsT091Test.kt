package io.github.mazilin.nautile.verification

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.ProjectRepository
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import io.github.mazilin.nautile.domain.repository.TaskRepository
import io.github.mazilin.nautile.domain.rule.TaskGroup
import io.github.mazilin.nautile.domain.rule.TaskGrouping
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoUiState
import io.github.mazilin.nautile.ui.screen.todo.TodoViewModel
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G2 闸门：主题、组件与待办列表视觉（独立验证，T-091）。
 *
 * 预期从需求基线 v2.2（FR-V-01、FR-G-13、D-07、D-10、D-11、AC-T-14）与
 * 设计 `.plans/设计/01_视觉与动效标准.md`（SHP-06/07、SPC-07、CMP-04/07/27、
 * A11Y-23、COL-08/COL-11）派生，不照抄实现：
 * - 所有项目组都显示（0 任务的项目也保留），组头名称 + 数量，整卡可点入项目详情；
 * - “单独任务”组仅在有未归属项目任务时出现；
 * - 组内任务行保持点按进详情、左滑完成；
 * - FAB 为圆形 primary、contentDescription“新建待办或项目”、tag add_task、可点；
 * - 本轮不折叠：不出现展开/收起/折叠箭头语义，也不出现可切换（ToggleableState）节点；
 * - 浅色/深色只换色板，不改变语义结构（不做像素断言）。
 *
 * 颜色/间距/圆角等纯视觉值由 `scripts/check_ui_tokens.sh` 与源码核对，
 * 语义测试不能证明 16dp 圆角、surfaceContainerLowest 或 8dp 间距，报告单独列出。
 */
@RunWith(AndroidJUnit4::class)
class AcCardGroupsT091Test {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-25T00:00:00Z")

    private fun project(id: Long = 7L, name: String = "空项目") = Project(
        id = ProjectId(id),
        name = name,
        createdAt = now,
        updatedAt = now,
    )

    private fun task(id: Long, title: String, projectId: ProjectId? = null) = Task(
        id = TaskId(id),
        title = title,
        projectId = projectId,
        position = GtdPosition.INBOX,
        createdAt = now,
        updatedAt = now,
    )

    private fun state(groups: List<TaskGroup>) = TodoUiState(
        position = GtdPosition.INBOX,
        groups = groups,
        visibleTasks = groups.flatMap { it.tasks },
    )

    /** 回归防线：新增的“含空项目”分组不改变旧 [TaskGrouping.byProject] 语义。 */
    @Test
    fun t091_grouping_including_empty_keeps_all_projects_and_standalone_rule() {
        val empty = project(1L, "空项目")
        val active = project(2L, "有任务项目")
        val projectTask = task(11L, "项目任务", active.id)
        val looseTask = task(12L, "单独任务A")

        val withLoose = TaskGrouping.byProjectIncludingEmpty(
            listOf(projectTask, looseTask),
            listOf(empty, active),
        )
        assertEquals("两个项目组 + 单独任务组", 3, withLoose.size)
        assertEquals(empty, withLoose[0].project)
        assertTrue("空项目组任务应为空", withLoose[0].tasks.isEmpty())
        assertEquals(active, withLoose[1].project)
        assertEquals(listOf(TaskId(11L)), withLoose[1].tasks.map { it.id })
        assertTrue("末尾应为单独任务组", withLoose[2].isUngrouped)
        assertEquals(listOf(TaskId(12L)), withLoose[2].tasks.map { it.id })

        val withoutLoose = TaskGrouping.byProjectIncludingEmpty(
            listOf(projectTask),
            listOf(empty, active),
        )
        assertEquals("无未归属任务时不得出现单独任务组", 2, withoutLoose.size)
        assertFalse(withoutLoose.any { it.isUngrouped })

        assertTrue(
            "旧 byProject 仍只输出有任务的项目（既有单测与调用依赖该语义）",
            TaskGrouping.byProject(emptyList(), listOf(empty)).isEmpty(),
        )
    }

    /** FR-V-01、SHP-07、SPC-07：0 任务项目仍显示名称与数量 0，整卡可点入项目详情。 */
    @Test
    fun t091_empty_project_group_is_visible_with_count_zero_and_opens_detail() {
        var opened: ProjectId? = null
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = state(listOf(TaskGroup(project(), emptyList()))),
                    onOpenProjectDetail = { opened = it },
                )
            }
        }

        composeRule.onNodeWithText("空项目（0）").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("该清单暂无任务").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(ProjectId(7L), opened) }
    }

    /** SHP-06/07、CMP-04/07：组头名称 + 数量；组内行仍可点入详情、左滑完成。 */
    @Test
    fun t091_group_header_count_and_rows_keep_click_and_swipe() {
        var openedProject: ProjectId? = null
        var openedTask: TaskId? = null
        var completed: TaskId? = null
        val p = project(7L, "课程项目")
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = state(listOf(TaskGroup(p, listOf(task(1L, "子任务A", p.id))))),
                    onOpenProjectDetail = { openedProject = it },
                    onOpenTaskDetail = { openedTask = it },
                    onCompleteTask = { completed = it },
                )
            }
        }

        composeRule.onNodeWithText("课程项目（1）").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(ProjectId(7L), openedProject) }

        composeRule.onNodeWithText("子任务A").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(TaskId(1L), openedTask) }

        composeRule.onNodeWithText("子任务A").performTouchInput { swipeLeft() }
        composeRule.runOnIdle { assertEquals("组内行左滑仍应完成", TaskId(1L), completed) }
    }

    /** FR-V-01、D-07：“单独任务”组仅在有未归属项目任务时出现，且带数量。 */
    @Test
    fun t091_standalone_group_only_when_ungrouped_tasks_exist() {
        var includeLoose by mutableStateOf(false)
        val p = project(7L, "课程项目")
        val projectGroup = TaskGroup(p, listOf(task(1L, "子任务A", p.id)))
        val looseGroup = TaskGroup(null, listOf(task(2L, "单独任务B")))
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = state(
                        if (includeLoose) listOf(projectGroup, looseGroup) else listOf(projectGroup),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("单独任务").assertDoesNotExist()

        composeRule.runOnIdle { includeLoose = true }
        composeRule.onNodeWithText("单独任务").assertIsDisplayed()
        composeRule.onNode(hasText("单独任务") and hasText("1")).assertIsDisplayed()
    }

    /** FR-D-02、A11Y-01：FAB 语义为“新建待办或项目”、tag add_task、可点；56dp 方形边界对应圆形。 */
    @Test
    fun t091_fab_is_round_primary_and_opens_new_task() {
        var opens = 0
        lateinit var density: Density
        composeRule.setContent {
            NautileTheme {
                density = LocalDensity.current
                TodoScreen(
                    state = state(emptyList()),
                    onOpenNewTask = { opens += 1 },
                )
            }
        }

        val fab = composeRule.onNodeWithTag("add_task")
        fab.assertIsDisplayed()
            .assertContentDescriptionEquals("新建待办或项目")
            .performClick()
        composeRule.runOnIdle { assertEquals(1, opens) }

        val bounds = fab.fetchSemanticsNode().boundsInRoot
        val widthDp = bounds.width / density.density
        val heightDp = bounds.height / density.density
        assertEquals("圆形 FAB 的边界应为等宽高", widthDp, heightDp, 0.5f)
        assertEquals("FAB 规格 56dp", 56f, widthDp, 1f)
    }

    /** CMP-07、CMP-42：本轮不折叠，不渲染展开/收起/折叠箭头，也不提供可切换控件。 */
    @Test
    fun t091_no_expand_arrow_or_toggle_semantics() {
        val p = project(7L, "课程项目")
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = state(
                        listOf(
                            TaskGroup(p, listOf(task(1L, "子任务A", p.id))),
                            TaskGroup(null, listOf(task(2L, "单独任务B"))),
                        ),
                    ),
                )
            }
        }

        val toggleNodes = composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertTrue("本轮不折叠，不应出现可切换节点", toggleNodes.isEmpty())

        listOf("展开", "收起", "折叠").forEach { label ->
            val nodes = composeRule.onAllNodes(
                hasContentDescription(label, substring = true),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            assertTrue("不应出现“$label”箭头语义", nodes.isEmpty())
        }

        // 组头与行仍按各自用途可点：组头进项目详情、行进任务详情，不靠箭头暗示。
        composeRule.onNodeWithText("课程项目（1）").assertIsDisplayed()
        composeRule.onNodeWithText("单独任务").assertIsDisplayed()
    }

    /** COL-02/COL-12：浅色与深色只换色板，语义结构与角色均可用；不做像素断言。 */
    @Test
    fun t091_light_and_dark_keep_same_semantics() {
        var dark by mutableStateOf(false)
        var cardColor: Color? = null
        val p = project(7L, "空项目")
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                cardColor = MaterialTheme.colorScheme.surfaceContainerLowest
                TodoScreen(
                    state = state(
                        listOf(
                            TaskGroup(p, emptyList()),
                            TaskGroup(null, listOf(task(2L, "单独任务B"))),
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("空项目（0）").assertIsDisplayed()
        composeRule.onNodeWithText("单独任务").assertIsDisplayed()
        composeRule.onNodeWithTag("add_task")
            .assertContentDescriptionEquals("新建待办或项目")
        val lightCardColor = cardColor

        composeRule.runOnIdle { dark = true }
        composeRule.onNodeWithText("空项目（0）").assertIsDisplayed()
        composeRule.onNodeWithText("单独任务").assertIsDisplayed()
        composeRule.onNodeWithTag("add_task")
            .assertContentDescriptionEquals("新建待办或项目")
        composeRule.runOnIdle {
            assertNotEquals("深浅色应使用不同卡片角色值", lightCardColor, cardColor)
        }
    }

    /**
     * 业务不变量：ViewModel 必须使用“含空项目”的分组，否则 0 任务项目在真实应用中不可见。
     * 用内存仓库与固定时钟构造，不读写 Room/DataStore。
     */
    @Test
    fun t091_viewmodel_keeps_empty_project_group() = runBlocking {
        val emptyProject = project(7L, "空项目")
        val tasks = MemoryTaskRepository(emptyList())
        val projects = MemoryProjectRepository(listOf(emptyProject))
        val settings = MemorySettingsRepository()
        val clock = Clock { now }
        val viewModel = TodoViewModel(
            taskRepository = tasks,
            projectRepository = projects,
            settingsRepository = settings,
            taskCommands = TaskCommands(tasks, projects, clock),
            projectCommands = ProjectCommands(projects, tasks, clock),
        )

        val state = withTimeout(5_000) {
            viewModel.uiState.first { it.groups.isNotEmpty() }
        }
        assertEquals(1, state.groups.size)
        assertEquals("空项目", state.groups.single().project?.name)
        assertTrue("0 任务项目组应保留且任务为空", state.groups.single().tasks.isEmpty())
    }
}

private class MemoryTaskRepository(initial: List<Task>) : TaskRepository {
    private val data = MutableStateFlow(initial)

    override fun observeByPosition(position: GtdPosition): Flow<List<Task>> =
        data.map { tasks -> tasks.filter { it.position == position } }

    override fun observeAll(): Flow<List<Task>> = data

    override suspend fun get(id: TaskId): Task? = data.value.firstOrNull { it.id == id }

    override suspend fun insert(task: Task): TaskId {
        val id = TaskId((data.value.maxOfOrNull { it.id.value } ?: 0L) + 1L)
        data.value = data.value + task.copy(id = id)
        return id
    }

    override suspend fun update(task: Task) {
        data.value = data.value.map { if (it.id == task.id) task else it }
    }

    override suspend fun delete(id: TaskId) {
        data.value = data.value.filterNot { it.id == id }
    }

    override suspend fun deleteAll(ids: List<TaskId>) {
        data.value = data.value.filterNot { it.id in ids }
    }
}

private class MemoryProjectRepository(initial: List<Project>) : ProjectRepository {
    private val data = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Project>> = data

    override suspend fun get(id: ProjectId): Project? = data.value.firstOrNull { it.id == id }

    override suspend fun insert(project: Project): ProjectId {
        val id = ProjectId((data.value.maxOfOrNull { it.id.value } ?: 0L) + 1L)
        data.value = data.value + project.copy(id = id)
        return id
    }

    override suspend fun update(project: Project) {
        data.value = data.value.map { if (it.id == project.id) project else it }
    }

    override suspend fun delete(id: ProjectId) {
        data.value = data.value.filterNot { it.id == id }
    }

    override suspend fun updateWithTasks(project: Project, tasks: List<Task>) {
        update(project)
    }

    override suspend fun deleteWithTasks(id: ProjectId, taskIds: List<TaskId>) {
        delete(id)
    }
}

private class MemorySettingsRepository(
    initial: UserSettings = UserSettings(lastGtdPosition = GtdPosition.INBOX),
) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun setDefaultPage(page: AppPage) = Unit

    override suspend fun setLastPage(page: AppPage) = Unit

    override suspend fun setLastGtdPosition(position: GtdPosition) {
        settings.value = settings.value.copy(lastGtdPosition = position)
    }

    override suspend fun setPredictiveBackEnabled(enabled: Boolean) = Unit

    override suspend fun setViewOptions(position: GtdPosition, options: String) = Unit
}
