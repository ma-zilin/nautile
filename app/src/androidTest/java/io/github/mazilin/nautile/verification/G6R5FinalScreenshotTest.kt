package io.github.mazilin.nautile.verification

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import io.github.mazilin.nautile.domain.rule.TaskGroup
import io.github.mazilin.nautile.interaction.AppTab
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.OverlayBackStack
import io.github.mazilin.nautile.ui.AppRoot
import io.github.mazilin.nautile.ui.screen.settings.SettingsScreen
import io.github.mazilin.nautile.ui.screen.todo.GtdSelector
import io.github.mazilin.nautile.ui.screen.todo.NewTaskSheet
import io.github.mazilin.nautile.ui.screen.todo.NewTaskUiState
import io.github.mazilin.nautile.ui.screen.todo.ProjectDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.ProjectDetailUiState
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoUiState
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R5-G6 最终浅/深全套终审截图测试（TickTick 四图对齐闭环 + 全场景补齐）。
 *
 * 覆盖 13 张终审截图（补齐 G2–G5 已有 19 张缺失的深色帧、完整底导帧、优先级锚定菜单与搜索/设置帧）：
 * - `r5g6_todo_full_light` / `r5g6_todo_full_dark`
 * - `r5g6_drawer_dark`
 * - `r5g6_new_task_dark`
 * - `r5g6_priority_menu_light` / `r5g6_priority_menu_dark`
 * - `r5g6_project_detail_expanded_dark` / `r5g6_project_detail_collapsed_dark`
 * - `r5g6_ended_list_dark`
 * - `r5g6_search_light` / `r5g6_search_dark`
 * - `r5g6_settings_light` / `r5g6_settings_dark`
 *
 * 通过 `NautileTheme(darkTheme = ...)` 独立控制深浅色，不修改系统深色设置，无测试间污染。
 * 运行环境：emulator-5554（API 36，模拟器，非真机）。
 */
@RunWith(AndroidJUnit4::class)
class G6R5FinalScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now: Instant = Instant.parse("2026-11-28T00:00:00Z")
    private val today: LocalDate = LocalDate.of(2026, 11, 28)

    private val activeProject = Project(
        id = ProjectId(10L),
        name = "机器人课程项目",
        status = ProjectStatus.ACTIVE,
        createdAt = now,
        updatedAt = now,
    )

    private val emptyProject = Project(
        id = ProjectId(11L),
        name = "寒假科研准备",
        status = ProjectStatus.ACTIVE,
        createdAt = now,
        updatedAt = now,
    )

    private val overdueTask = Task(
        id = TaskId(1L),
        title = "提交卡尔曼滤波实验报告",
        detail = "补齐协方差收敛曲线与误差对比表，检查 PDF 公式排版",
        projectId = activeProject.id,
        position = GtdPosition.NEXT_ACTION,
        priority = Priority.HIGH,
        dueDate = today.minusDays(1),
        dueTime = LocalTime.of(18, 0),
        createdAt = now,
        updatedAt = now,
    )

    private val normalProjectTask = Task(
        id = TaskId(2L),
        title = "整理第三章概率论错题",
        detail = "重点复盘条件概率与全概率公式推导",
        projectId = activeProject.id,
        position = GtdPosition.NEXT_ACTION,
        priority = Priority.MEDIUM,
        dueDate = today,
        dueTime = LocalTime.of(20, 30),
        createdAt = now.plusSeconds(1),
        updatedAt = now.plusSeconds(1),
    )

    private val standaloneTask = Task(
        id = TaskId(3L),
        title = "预约校医院体测复检",
        projectId = null,
        position = GtdPosition.NEXT_ACTION,
        priority = Priority.LOW,
        dueDate = today.plusDays(1),
        createdAt = now.plusSeconds(2),
        updatedAt = now.plusSeconds(2),
    )

    private val fullTodoState = TodoUiState(
        position = GtdPosition.NEXT_ACTION,
        groups = listOf(
            TaskGroup(project = activeProject, tasks = listOf(overdueTask, normalProjectTask)),
            TaskGroup(project = emptyProject, tasks = emptyList()),
            TaskGroup(project = null, tasks = listOf(standaloneTask)),
        ),
        visibleTasks = listOf(overdueTask, normalProjectTask, standaloneTask),
        searchAvailable = true,
        viewOptionsAvailable = true,
        overdueTaskIds = setOf(overdueTask.id),
        referenceDate = today,
    )

    private val completedTask = Task(
        id = TaskId(21L),
        title = "完成 ROS2 话题通信演示",
        detail = "已验证节点发布/订阅与 QoS 匹配",
        projectId = activeProject.id,
        position = GtdPosition.ENDED,
        positionBeforeClose = GtdPosition.NEXT_ACTION,
        priority = Priority.HIGH,
        dueDate = today.minusDays(2),
        dueTime = LocalTime.of(14, 0),
        closedOutcome = ClosedOutcome.COMPLETED,
        closedAt = now,
        closedOn = today.minusDays(1),
        createdAt = now,
        updatedAt = now,
    )

    private val abandonedTask = Task(
        id = TaskId(22L),
        title = "旧版脚本兼容迁移",
        projectId = null,
        position = GtdPosition.ENDED,
        positionBeforeClose = GtdPosition.SOMEDAY,
        closedOutcome = ClosedOutcome.ABANDONED,
        closedAt = now,
        closedOn = today.minusDays(1),
        createdAt = now.plusSeconds(1),
        updatedAt = now.plusSeconds(1),
    )

    private val endedTodoState = TodoUiState(
        position = GtdPosition.ENDED,
        groups = listOf(
            TaskGroup(project = activeProject, tasks = listOf(completedTask)),
            TaskGroup(project = null, tasks = listOf(abandonedTask)),
        ),
        visibleTasks = listOf(completedTask, abandonedTask),
        searchAvailable = true,
        viewOptionsAvailable = true,
        referenceDate = today,
    )

    private val settings = UserSettings(
        defaultPage = AppPage.TODO,
        predictiveBackEnabled = true,
    )

    /** 写入公共 Download/nautile_evidence/r5g6_*.png，写前清理同名旧记录避免重名后缀。 */
    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap: Bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "r5g6_$name.png"
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
        println("R5G6_SCREENSHOT name=$name file=$fileName uri=$uri")
    }

    private fun setFullAppTodo(state: TodoUiState, darkTheme: Boolean) {
        val navigation = NavigationCoordinator().apply { applyInitialTab(AppTab.TODO) }
        val overlays = OverlayBackStack()
        composeRule.setContent {
            NautileTheme(darkTheme = darkTheme) {
                AppRoot(
                    navigation = navigation,
                    overlays = overlays,
                    tabContent = { TodoScreen(state = state) },
                    fullScreenContent = {},
                )
            }
        }
    }

    // ---- 1. 主待办完整帧（浅/深：分组卡片 + 空项目组 + Checkbox/详情/逾期 + FAB + 底导） ----

    @Test
    fun r5g6_todo_full_light() {
        setFullAppTodo(fullTodoState, darkTheme = false)
        composeRule.onNodeWithText("机器人课程项目（2）").assertIsDisplayed()
        composeRule.onNodeWithText("寒假科研准备（0）").assertIsDisplayed()
        composeRule.onNodeWithTag("task_checkbox_1").assertIsDisplayed()
        composeRule.onNodeWithTag("add_task").assertIsDisplayed()
        capture("todo_full_light")
    }

    @Test
    fun r5g6_todo_full_dark() {
        setFullAppTodo(fullTodoState, darkTheme = true)
        composeRule.onNodeWithText("机器人课程项目（2）").assertIsDisplayed()
        composeRule.onNodeWithText("寒假科研准备（0）").assertIsDisplayed()
        composeRule.onNodeWithTag("task_checkbox_1").assertIsDisplayed()
        composeRule.onNodeWithTag("add_task").assertIsDisplayed()
        capture("todo_full_dark")
    }

    // ---- 2. GTD 左抽屉 240–320dp 深色 ----

    @Test
    fun r5g6_drawer_dark() {
        composeRule.setContent {
            NautileTheme(darkTheme = true) {
                Box(modifier = Modifier.fillMaxSize()) {
                    TodoScreen(state = fullTodoState)
                    GtdSelector(
                        currentPosition = GtdPosition.NEXT_ACTION,
                        tasks = fullTodoState.visibleTasks,
                        onSelect = {},
                        onClose = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("gtd_drawer").assertIsDisplayed()
        composeRule.onNodeWithText("清单").assertIsDisplayed()
        capture("drawer_dark")
    }

    // ---- 3. 新建待办下半屏深色 ----

    @Test
    fun r5g6_new_task_dark() {
        composeRule.setContent {
            NautileTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NewTaskSheet(
                        state = NewTaskUiState(
                            position = GtdPosition.NEXT_ACTION,
                            targetPosition = GtdPosition.NEXT_ACTION,
                            title = "概率论第三章复习",
                            detail = "重点整理条件概率、全概率公式与贝叶斯习题",
                            priority = Priority.HIGH,
                            dueDate = today,
                            dueTime = LocalTime.of(14, 30),
                            projectId = activeProject.id,
                        ),
                        projects = listOf(activeProject),
                    )
                }
            }
        }
        composeRule.onNodeWithTag("new_task_title").assertIsDisplayed()
        composeRule.onNodeWithTag("new_task_detail").assertIsDisplayed()
        capture("new_task_dark")
    }

    // ---- 4. 无遮罩优先级四级旗标锚定菜单（浅/深，闭环 TickTick 图 4） ----

    private fun setPriorityMenu(darkTheme: Boolean) {
        composeRule.setContent {
            NautileTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NewTaskSheet(
                        state = NewTaskUiState(
                            position = GtdPosition.NEXT_ACTION,
                            targetPosition = GtdPosition.NEXT_ACTION,
                            title = "整理第三章概率论错题",
                            detail = "重点复盘条件概率与全概率公式推导",
                            priority = Priority.HIGH,
                            dueDate = today,
                            dueTime = LocalTime.of(20, 30),
                            projectId = activeProject.id,
                        ),
                        projects = listOf(activeProject),
                    )
                }
            }
        }
        composeRule.onNodeWithTag("attr_priority").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("优先级：高").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("优先级：中").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("优先级：低").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("优先级：无").assertIsDisplayed()
    }

    @Test
    fun r5g6_priority_menu_light() {
        setPriorityMenu(darkTheme = false)
        capture("priority_menu_light")
    }

    @Test
    fun r5g6_priority_menu_dark() {
        setPriorityMenu(darkTheme = true)
        capture("priority_menu_dark")
    }

    // ---- 5. 项目详情深色展开/收起态 ----

    private fun projectDetailState(collapsed: Boolean) = ProjectDetailUiState(
        project = activeProject,
        children = listOf(normalProjectTask, completedTask),
        nameDraft = null,
        nameInvalid = false,
        saving = false,
        error = null,
        leaveBlocked = false,
        collapsed = collapsed,
    )

    @Test
    fun r5g6_project_detail_expanded_dark() {
        composeRule.setContent {
            NautileTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ProjectDetailSheet(
                        state = projectDetailState(collapsed = false),
                        onNameChanged = {},
                        onCommitName = {},
                        onToggleCollapsed = {},
                        onCloseRequested = {},
                    )
                }
            }
        }
        composeRule.onAllNodes(hasContentDescription("编辑", substring = true)).assertCountEquals(0)
        composeRule.onAllNodesWithText("收起子任务").assertCountEquals(0)
        composeRule.onNodeWithTag("project_children_toggle")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("收起子任务")
        composeRule.onNodeWithTag("project_child_2").assertIsDisplayed()
        capture("project_detail_expanded_dark")
    }

    @Test
    fun r5g6_project_detail_collapsed_dark() {
        composeRule.setContent {
            NautileTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ProjectDetailSheet(
                        state = projectDetailState(collapsed = true),
                        onNameChanged = {},
                        onCommitName = {},
                        onToggleCollapsed = {},
                        onCloseRequested = {},
                    )
                }
            }
        }
        composeRule.onAllNodesWithText("展开子任务").assertCountEquals(0)
        composeRule.onNodeWithTag("project_children_toggle")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("展开子任务")
        composeRule.onNodeWithTag("project_child_2").assertDoesNotExist()
        capture("project_detail_collapsed_dark")
    }

    // ---- 6. 已结束清单深色（含 4dp 网格胶囊 + 删除线 + 禁用 Checkbox + 底导） ----

    @Test
    fun r5g6_ended_list_dark() {
        setFullAppTodo(endedTodoState, darkTheme = true)
        composeRule.onNodeWithText("完成 ROS2 话题通信演示").assertIsDisplayed()
        composeRule.onNodeWithText("旧版脚本兼容迁移").assertIsDisplayed()
        composeRule.onNodeWithText("已完成").assertIsDisplayed()
        composeRule.onNodeWithText("已放弃").assertIsDisplayed()
        capture("ended_list_dark")
    }

    // ---- 7. 搜索栏展开态（浅/深：ic_close 清除图标 + 标题行保留 + 命中过滤） ----

    private fun setSearchScreen(darkTheme: Boolean) {
        val searchState = fullTodoState.copy(
            searchActive = true,
            searchQuery = "概率",
            groups = listOf(TaskGroup(project = activeProject, tasks = listOf(normalProjectTask))),
            visibleTasks = fullTodoState.visibleTasks,
        )
        setFullAppTodo(searchState, darkTheme = darkTheme)
        composeRule.onNodeWithContentDescription("退出搜索").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("清除搜索").assertIsDisplayed()
        composeRule.onNodeWithText("整理第三章概率论错题").assertIsDisplayed()
    }

    @Test
    fun r5g6_search_light() {
        setSearchScreen(darkTheme = false)
        capture("search_light")
    }

    @Test
    fun r5g6_search_dark() {
        setSearchScreen(darkTheme = true)
        capture("search_dark")
    }

    // ---- 8. 设置页（浅/深） ----

    private fun setSettingsScreen(darkTheme: Boolean) {
        composeRule.setContent {
            NautileTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        settings = settings,
                        onBack = {},
                        onOpenDefaultPage = {},
                        onPredictiveBackChanged = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("设置").assertIsDisplayed()
    }

    @Test
    fun r5g6_settings_light() {
        setSettingsScreen(darkTheme = false)
        capture("settings_light")
    }

    @Test
    fun r5g6_settings_dark() {
        setSettingsScreen(darkTheme = true)
        capture("settings_dark")
    }
}
