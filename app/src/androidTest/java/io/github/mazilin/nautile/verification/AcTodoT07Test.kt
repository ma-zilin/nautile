package io.github.mazilin.nautile.verification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
import io.github.mazilin.nautile.ui.screen.todo.title
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

    /** AC-G-04：主待办页只显示当前一个 GTD 清单，不同时并排显示两个清单。 */
    @Test
    fun AC_G_04_main_page_shows_only_current_gtd_list() {
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = TodoUiState(
                        position = GtdPosition.NEXT_ACTION,
                        searchAvailable = true,
                        viewOptionsAvailable = true,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("下一步行动").assertIsDisplayed()
        GtdPosition.entries
            .filter { it != GtdPosition.NEXT_ACTION }
            .forEach { composeRule.onNodeWithText(it.title).assertDoesNotExist() }
    }

    @Test
    fun AC_G_04_item_swipe_completes_item_and_does_not_open_selector() {
        // v2.16（4.26 / R5-7）：右滑任务行触发完成且由条目消费，绝不触发 GTD 左抽屉。
        var opened = false
        var completed: TaskId? = null
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = stateWith(listOf(task(1L, "任务A"))),
                    onOpenSelector = { opened = true },
                    onCompleteTask = { completed = it },
                )
            }
        }

        composeRule.onNodeWithText("任务A").performTouchInput { swipeRight() }

        composeRule.runOnIdle {
            assertFalse("条目滑动不应打开 GTD 选择器", opened)
            assertEquals(TaskId(1L), completed)
        }
    }

    @Test
    fun AC_T_17_task_swipe_left_abandons_item() {
        // v2.16（4.26 / R5-7）：左滑任务行触发放弃。
        var abandoned: TaskId? = null
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = stateWith(listOf(task(1L, "任务A"))),
                    onAbandonTask = { abandoned = it },
                )
            }
        }

        composeRule.onNodeWithText("任务A").performTouchInput { swipeLeft() }

        composeRule.runOnIdle { assertEquals(TaskId(1L), abandoned) }
    }

    @Test
    fun AC_T_17_project_swipe_requests_complete_and_abandon_confirmation() {
        // v2.16（4.26 / R5-7）：项目行右滑完成、左滑放弃，均先请求批量子待办数量确认。
        val requested = mutableListOf<Pair<ProjectId, ClosedOutcome>>()
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = projectState("课程项目", listOf(task(1L, "子任务"))),
                    onRequestProjectClose = { id, outcome -> requested += id to outcome },
                )
            }
        }

        composeRule.onNodeWithText("课程项目（1）").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("课程项目（1）").performTouchInput { swipeLeft() }

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    ProjectId(7L) to ClosedOutcome.COMPLETED,
                    ProjectId(7L) to ClosedOutcome.ABANDONED,
                ),
                requested,
            )
        }
    }

    @Test
    fun AC_G_04_drawer_close_icon_scrim_tap_and_left_swipe_close() {
        var selected: GtdPosition? = null
        var closed = 0
        composeRule.setContent {
            NautileTheme {
                GtdSelector(
                    currentPosition = GtdPosition.INBOX,
                    tasks = emptyList(),
                    onSelect = { selected = it },
                    onClose = { closed += 1 },
                )
            }
        }

        // 设计 §6.2 #11：关闭为 ic_close 图标按钮，按 contentDescription“关闭”定位。
        composeRule.onNodeWithContentDescription("关闭").performClick()
        composeRule.runOnIdle { assertEquals(1, closed) }

        // 点按抽屉右侧遮罩关闭。
        composeRule.onRoot().performTouchInput {
            click(Offset(width - 8f, height / 2f))
        }
        composeRule.runOnIdle { assertEquals("点按遮罩应关闭抽屉", 2, closed) }

        // 抽屉上左滑关闭（不是空白区右滑）。
        composeRule.onAllNodes(hasText("等待中") and hasClickAction()).onFirst()
            .performTouchInput { swipeLeft() }
        composeRule.runOnIdle {
            assertEquals("抽屉上左滑应关闭抽屉", 3, closed)
            assertNull("关闭不应切换清单", selected)
        }
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

    /**
     * AC-G-04 / CMP-12：抽屉内上下拉只浏览，不切换清单，也不显示“松手切换到 …”。
     *
     * v2.15（2026-11-26 决策）把 GTD 选择器改为左侧全高抽屉，抽屉内不再有边界拉动；
     * 旧“全屏选择器边界拉动切换”的断言由本条按新口径替换，不削弱 AC-G-04/05 覆盖。
     */
    @Test
    fun AC_G_04_drawer_vertical_pull_does_not_switch() {
        var selected: GtdPosition? = null
        var closed = 0
        composeRule.setContent {
            NautileTheme {
                GtdSelector(
                    currentPosition = GtdPosition.INBOX,
                    tasks = emptyList(),
                    onSelect = { selected = it },
                    onClose = { closed += 1 },
                )
            }
        }

        val row = { composeRule.onAllNodes(hasText("等待中") and hasClickAction()).onFirst() }
        row().performTouchInput { swipeUp() }
        row().performTouchInput { swipeDown() }

        composeRule.runOnIdle {
            assertNull("抽屉内上下拉不应切换清单", selected)
            assertEquals("抽屉内上下拉不应关闭抽屉", 0, closed)
        }
        assertTrue(
            "抽屉打开时不应出现“松手切换到 …”",
            composeRule.onAllNodesWithText("松手切换到", substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    /** AC-G-04：抽屉不再渲染右列任务列表（右列已移除），只剩标题与五项。 */
    @Test
    fun AC_G_04_drawer_has_no_right_column_task_list() {
        composeRule.setContent {
            NautileTheme {
                GtdSelector(
                    currentPosition = GtdPosition.INBOX,
                    tasks = listOf(task(1L, "唯一任务")),
                    onSelect = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithText("清单").assertIsDisplayed()
        GtdPosition.entries.forEach { position ->
            composeRule.onAllNodes(hasText(position.title) and hasClickAction())
                .onFirst().assertIsDisplayed()
        }
        composeRule.onNodeWithText("唯一任务").assertDoesNotExist()
        composeRule.onNodeWithText("该清单暂无任务").assertDoesNotExist()
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

    /**
     * CMP-14 / BR-61：主待办页连续两次快速上拉，方向正确（依次到相邻两个清单），不跳错。
     *
     * 用状态回写模拟宿主：提交后 position 变化，第二次手势必须基于新位置继续向后一个清单。
     */
    @Test
    fun AC_G_05_main_page_rapid_two_pulls_move_two_positions() {
        var position by mutableStateOf(GtdPosition.INBOX)
        val switches = mutableListOf<GtdPosition>()
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = stateWith(emptyList()).copy(position = position),
                    onSwitchPosition = {
                        switches += it
                        position = it
                    },
                )
            }
        }

        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(
                "连续两次快速上拉应依次到下一步行动、等待中",
                listOf(GtdPosition.NEXT_ACTION, GtdPosition.WAITING),
                switches,
            )
            assertEquals(GtdPosition.WAITING, position)
        }
    }

    /** CMP-14 / BR-61：首位继续下拉显示“已是第一个清单”，且不切换。 */
    @Test
    fun AC_G_05_main_page_first_position_shows_hint_and_does_not_switch() {
        var switched: GtdPosition? = null
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = stateWith(emptyList()),
                    onSwitchPosition = { switched = it },
                )
            }
        }

        // 手动持住下拉，覆盖预览中间态；空清单阻力 0.15，需拉足够距离才显示提示。
        composeRule.onRoot().performTouchInput {
            down(center)
            repeat(30) { moveBy(Offset(0f, 30f)) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("已是第一个清单").assertIsDisplayed()
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertNull("首位下拉不应切换清单", switched) }
    }

    /** CMP-14 / BR-61：末位继续上拉显示“已是最后一个清单”，且不切换。 */
    @Test
    fun AC_G_05_main_page_last_position_shows_hint_and_does_not_switch() {
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
            down(center)
            repeat(30) { moveBy(Offset(0f, -30f)) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("已是最后一个清单").assertIsDisplayed()
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertNull("末位上拉不应切换清单", switched) }
    }

    /** CMP-14：中间清单拉动预览显示“松手切换到 X”，松手超阈值才切换。 */
    @Test
    fun AC_G_05_main_page_preview_shows_target_before_release() {
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
            down(center)
            repeat(20) { moveBy(Offset(0f, -30f)) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("松手切换到 下一步行动").assertIsDisplayed()
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertEquals(GtdPosition.NEXT_ACTION, switched) }
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
