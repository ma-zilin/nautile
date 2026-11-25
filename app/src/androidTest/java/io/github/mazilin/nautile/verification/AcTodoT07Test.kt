package io.github.mazilin.nautile.verification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.rule.TaskGroup
import io.github.mazilin.nautile.ui.screen.todo.GtdSelector
import io.github.mazilin.nautile.ui.screen.todo.ProjectConfirmation
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoUiState
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S1-T07 Compose UI AC 验证（T-054）。
 *
 * 覆盖 GTD 标题按钮/空白右滑打开、条目优先、选择器五项与空态、边界拉动、导航滚动隐藏。
 * 需要设备/模拟器；当前 adb 设备无权限，本轮只编译，未执行。
 */
@RunWith(AndroidJUnit4::class)
class AcTodoT07Test {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-21T00:00:00Z")

    private fun task(id: Long, title: String) = Task(
        id = TaskId(id),
        title = title,
        position = GtdPosition.INBOX,
        createdAt = now,
        updatedAt = now,
    )

    private fun stateWith(tasks: List<Task>) = TodoUiState(
        position = GtdPosition.INBOX,
        groups = if (tasks.isEmpty()) emptyList() else listOf(TaskGroup(null, tasks)),
        visibleTasks = tasks,
    )

    private fun projectState(projectName: String, tasks: List<Task>): TodoUiState {
        val project = Project(
            id = ProjectId(7L),
            name = projectName,
            createdAt = now,
            updatedAt = now,
        )
        return TodoUiState(
            position = GtdPosition.INBOX,
            groups = listOf(TaskGroup(project, tasks)),
            visibleTasks = tasks,
        )
    }

    @Test
    fun AC_G_04_independent_gtd_entry_opens_gtd_selector() {
        var opened = false
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = stateWith(emptyList()),
                    onOpenSelector = { opened = true },
                )
            }
        }

        composeRule.onNodeWithTag("gtd_entry").performClick()

        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun AC_G_04_blank_area_right_swipe_opens_gtd_selector() {
        var opened = false
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = stateWith(emptyList()),
                    onOpenSelector = { opened = true },
                )
            }
        }

        composeRule.onRoot().performTouchInput { swipeRight() }

        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun AC_G_04_sub_threshold_right_swipe_does_not_open_gtd_selector() {
        var opened = false
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = stateWith(emptyList()),
                    onOpenSelector = { opened = true },
                )
            }
        }

        composeRule.onRoot().performTouchInput {
            swipeRight(startX = width * 0.5f, endX = width * 0.5f + 10f)
        }

        composeRule.runOnIdle { assertFalse("低于阈值的右滑不应打开选择器", opened) }
    }

    @Test
    fun AC_G_04_item_swipe_abandons_item_and_does_not_open_selector() {
        var opened = false
        var abandoned: TaskId? = null
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = stateWith(listOf(task(1L, "任务A"))),
                    onOpenSelector = { opened = true },
                    onAbandonTask = { abandoned = it },
                )
            }
        }

        composeRule.onNodeWithText("任务A").performTouchInput { swipeRight() }

        composeRule.runOnIdle {
            assertFalse("条目滑动不应打开 GTD 选择器", opened)
            assertEquals(TaskId(1L), abandoned)
        }
    }

    @Test
    fun AC_T_17_task_swipe_left_completes_item() {
        var completed: TaskId? = null
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = stateWith(listOf(task(1L, "任务A"))),
                    onCompleteTask = { completed = it },
                )
            }
        }

        composeRule.onNodeWithText("任务A").performTouchInput { swipeLeft() }

        composeRule.runOnIdle { assertEquals(TaskId(1L), completed) }
    }

    @Test
    fun AC_T_17_project_swipe_right_requests_abandon_confirmation() {
        var requested: Pair<ProjectId, ClosedOutcome>? = null
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = projectState("课程项目", listOf(task(1L, "子任务"))),
                    onRequestProjectClose = { id, outcome -> requested = id to outcome },
                )
            }
        }

        composeRule.onNodeWithText("课程项目（1）").performTouchInput { swipeRight() }

        composeRule.runOnIdle {
            assertEquals(ProjectId(7L) to ClosedOutcome.ABANDONED, requested)
        }
    }

    @Test
    fun AC_G_04_selector_close_button_and_blank_left_swipe_close() {
        var closed = 0
        composeRule.setContent {
            NautileTheme {
                GtdSelector(
                    currentPosition = GtdPosition.INBOX,
                    tasks = emptyList(),
                    onSelect = {},
                    onClose = { closed += 1 },
                )
            }
        }

        // 设计 §6.2 #11：关闭为 ic_close 图标按钮，按 contentDescription“关闭”定位。
        composeRule.onNodeWithContentDescription("关闭").performClick()
        composeRule.onRoot().performTouchInput { swipeLeft() }

        composeRule.runOnIdle { assertEquals(2, closed) }
    }

    @Test
    fun AC_G_04_selector_lists_five_positions_and_click_switches() {
        var selected: GtdPosition? = null
        composeRule.setContent {
            NautileTheme {
                GtdSelector(
                    currentPosition = GtdPosition.INBOX,
                    tasks = emptyList(),
                    onSelect = { selected = it },
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithText("清单").assertIsDisplayed()
        composeRule.onNodeWithText("等待中").performClick()

        composeRule.runOnIdle { assertEquals(GtdPosition.WAITING, selected) }
    }

    @Test
    fun AC_G_05_click_switch_keeps_selector_open() {
        var current by mutableStateOf(GtdPosition.INBOX)
        composeRule.setContent {
            NautileTheme {
                GtdSelector(
                    currentPosition = current,
                    tasks = emptyList(),
                    onSelect = { current = it },
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithText("等待中").performClick()

        composeRule.runOnIdle { assertEquals(GtdPosition.WAITING, current) }
        composeRule.onNodeWithText("清单").assertIsDisplayed()
    }

    @Test
    fun AC_G_05_selector_shows_empty_state() {
        composeRule.setContent {
            NautileTheme {
                GtdSelector(
                    currentPosition = GtdPosition.INBOX,
                    tasks = emptyList(),
                    onSelect = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithText("该清单暂无任务").assertIsDisplayed()
    }

    @Test
    fun AC_G_05_boundary_pull_on_empty_list_commits_to_adjacent() {
        var selected: GtdPosition? = null
        composeRule.setContent {
            NautileTheme {
                GtdSelector(
                    currentPosition = GtdPosition.INBOX,
                    tasks = emptyList(),
                    onSelect = { selected = it },
                    onClose = {},
                )
            }
        }

        composeRule.onRoot().performTouchInput { swipeUp() }

        composeRule.runOnIdle { assertEquals(GtdPosition.NEXT_ACTION, selected) }
    }

    @Test
    fun AC_G_05_first_position_pull_down_does_not_wrap() {
        var selected: GtdPosition? = null
        composeRule.setContent {
            NautileTheme {
                GtdSelector(
                    currentPosition = GtdPosition.INBOX,
                    tasks = emptyList(),
                    onSelect = { selected = it },
                    onClose = {},
                )
            }
        }

        composeRule.onRoot().performTouchInput { swipeDown() }

        composeRule.runOnIdle { assertNull(selected) }
    }

    @Test
    fun AC_G_05_half_screen_below_threshold_rebounds_and_keeps_position() {
        var selected: GtdPosition? = null
        composeRule.setContent {
            NautileTheme {
                GtdSelector(
                    currentPosition = GtdPosition.INBOX,
                    tasks = emptyList(),
                    onSelect = { selected = it },
                    onClose = {},
                )
            }
        }

        composeRule.onRoot().performTouchInput {
            swipeDown(startY = center.y, endY = center.y + 30f)
        }

        composeRule.runOnIdle { assertNull("半屏未超阈值不应切换清单", selected) }
    }

    @Test
    fun AC_G_05_short_list_boundary_pull_commits_to_adjacent() {
        var selected: GtdPosition? = null
        composeRule.setContent {
            NautileTheme {
                GtdSelector(
                    currentPosition = GtdPosition.INBOX,
                    tasks = listOf(task(1L, "唯一任务")),
                    onSelect = { selected = it },
                    onClose = {},
                )
            }
        }

        composeRule.onRoot().performTouchInput { swipeUp() }

        composeRule.runOnIdle { assertEquals(GtdPosition.NEXT_ACTION, selected) }
    }

    @Test
    fun AC_N_15_todo_scroll_hides_and_shows_navigation() {
        val visibility = mutableListOf<Boolean>()
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = stateWith((1L..30L).map { task(it, "任务$it") }),
                    onNavigationVisibilityChanged = { visibility += it },
                )
            }
        }

        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.runOnIdle { assertTrue("上滑应触发隐藏", visibility.contains(false)) }

        composeRule.onRoot().performTouchInput { swipeDown() }
        composeRule.runOnIdle { assertTrue("下滑应触发显示", visibility.contains(true)) }
    }

    @Test
    fun AC_G_05_full_screen_empty_list_pull_switches_and_keeps_full_screen() {
        var switched: GtdPosition? = null
        var opened = false
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = stateWith(emptyList()),
                    onOpenSelector = { opened = true },
                    onSwitchPosition = { switched = it },
                )
            }
        }

        composeRule.onRoot().performTouchInput { swipeUp() }

        composeRule.runOnIdle {
            assertEquals(GtdPosition.NEXT_ACTION, switched)
            assertFalse("全屏切换不应打开 GTD 选择器", opened)
        }
    }

    @Test
    fun AC_G_05_full_screen_short_list_pull_switches() {
        var switched: GtdPosition? = null
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = stateWith(listOf(task(1L, "唯一任务"))),
                    onSwitchPosition = { switched = it },
                )
            }
        }

        composeRule.onRoot().performTouchInput { swipeUp() }

        composeRule.runOnIdle { assertEquals(GtdPosition.NEXT_ACTION, switched) }
    }

    @Test
    fun AC_G_05_full_screen_below_threshold_rebounds_and_keeps_position() {
        var switched: GtdPosition? = null
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = stateWith(emptyList()),
                    onSwitchPosition = { switched = it },
                )
            }
        }

        composeRule.onRoot().performTouchInput {
            swipeDown(startY = center.y, endY = center.y + 30f)
        }

        composeRule.runOnIdle { assertNull("未超阈值不应切换清单", switched) }
    }

    @Test
    fun AC_G_05_full_screen_above_threshold_switches_previous() {
        var switched: GtdPosition? = null
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = stateWith(emptyList()).copy(position = GtdPosition.WAITING),
                    onSwitchPosition = { switched = it },
                )
            }
        }

        composeRule.onRoot().performTouchInput {
            swipeDown(startY = height * 0.3f, endY = height * 0.8f)
        }

        composeRule.runOnIdle { assertEquals(GtdPosition.NEXT_ACTION, switched) }
    }

    @Test
    fun AC_G_05_full_screen_first_position_pull_down_does_not_wrap() {
        var switched: GtdPosition? = null
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = stateWith(emptyList()),
                    onSwitchPosition = { switched = it },
                )
            }
        }

        composeRule.onRoot().performTouchInput {
            swipeDown(startY = height * 0.3f, endY = height * 0.8f)
        }

        composeRule.runOnIdle { assertNull(switched) }
    }

    @Test
    fun AC_G_05_full_screen_last_position_pull_up_does_not_wrap() {
        var switched: GtdPosition? = null
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = stateWith(emptyList()).copy(position = GtdPosition.ENDED),
                    onSwitchPosition = { switched = it },
                )
            }
        }

        composeRule.onRoot().performTouchInput {
            swipeUp(startY = height * 0.8f, endY = height * 0.3f)
        }

        composeRule.runOnIdle { assertNull(switched) }
    }

    @Test
    fun AC_G_05_full_screen_long_list_ordinary_scroll_does_not_switch() {
        var switched: GtdPosition? = null
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = stateWith((1L..30L).map { task(it, "任务$it") }),
                    onSwitchPosition = { switched = it },
                )
            }
        }

        composeRule.onRoot().performTouchInput { swipeUp() }

        composeRule.runOnIdle { assertNull("普通滚动不应触发边界切换", switched) }
    }

    @Test
    fun AC_T_17_project_confirmation_text_has_required_space() {
        val project = Project(
            id = ProjectId(7L),
            name = "课程项目",
            createdAt = now,
            updatedAt = now,
        )
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = TodoUiState(
                        position = GtdPosition.INBOX,
                        groups = listOf(TaskGroup(project, emptyList())),
                        visibleTasks = emptyList(),
                        projectConfirmation = ProjectConfirmation(
                            projectId = ProjectId(7L),
                            projectName = "课程项目",
                            outcome = ClosedOutcome.COMPLETED,
                            taskCount = 3,
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("将完成 3 个子待办").assertIsDisplayed()
    }
}
