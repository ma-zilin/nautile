package io.github.mazilin.nautile.verification

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.rule.TaskGroup
import io.github.mazilin.nautile.ui.screen.todo.NewTaskKind
import io.github.mazilin.nautile.ui.screen.todo.NewTaskSheet
import io.github.mazilin.nautile.ui.screen.todo.NewTaskUiState
import io.github.mazilin.nautile.ui.screen.todo.ProjectDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.ProjectDetailUiState
import io.github.mazilin.nautile.ui.screen.todo.TaskDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.TaskDetailUiState
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoUiState
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T-071 九条 UI 对齐的 Compose 验证（T-072）。
 *
 * 测试名含 T-xx/AC 编号；正常/拒绝分支分别覆盖。需要设备。
 */
@RunWith(AndroidJUnit4::class)
class AcUiT071Test {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-22T12:00:00Z")

    private fun task(
        id: Long = 1L,
        title: String = "任务A",
        closed: Boolean = false,
    ) = Task(
        id = TaskId(id),
        title = title,
        position = if (closed) GtdPosition.ENDED else GtdPosition.INBOX,
        positionBeforeClose = if (closed) GtdPosition.INBOX else null,
        closedOutcome = if (closed) ClosedOutcome.COMPLETED else null,
        closedAt = if (closed) now else null,
        closedOn = if (closed) LocalDate.of(2026, 11, 22) else null,
        createdAt = now,
        updatedAt = now,
    )

    private fun todoState(
        tasks: List<Task> = listOf(task()),
        position: GtdPosition = GtdPosition.INBOX,
    ) = TodoUiState(
        position = position,
        groups = if (tasks.isEmpty()) emptyList() else listOf(TaskGroup(null, tasks)),
        visibleTasks = tasks,
    )

    @Test
    fun T_01_todo_header_has_title_and_settings_entry() {
        var settings = 0
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = todoState(emptyList()),
                    onOpenSettings = { settings += 1 },
                )
            }
        }

        composeRule.onNodeWithText("待办").assertIsDisplayed()
        composeRule.onNodeWithText("设置").performClick()

        composeRule.runOnIdle { assertEquals(1, settings) }
    }

    @Test
    fun T_02_gtd_entry_is_independent_from_title_text() {
        var opened = 0
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = todoState(emptyList()),
                    onOpenSelector = { opened += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("gtd_entry").performClick()
        composeRule.onNodeWithText("收件箱").assertHasNoClickAction()

        composeRule.runOnIdle { assertEquals("只有独立入口应打开选择器", 1, opened) }
    }

    @Test
    fun T_09_ended_add_shows_four_target_positions_without_ended() {
        var target = GtdPosition.ENDED
        composeRule.setContent {
            NautileTheme {
                NewTaskSheet(
                    state = NewTaskUiState(
                        kind = NewTaskKind.TODO,
                        targetPosition = GtdPosition.INBOX,
                        showTargetPicker = true,
                    ),
                    onTargetChanged = { target = it },
                )
            }
        }

        composeRule.onNodeWithText("目标清单（已结束不可选）").assertIsDisplayed()
        composeRule.onNodeWithText("下一步行动").performClick()
        composeRule.runOnIdle { assertEquals(GtdPosition.NEXT_ACTION, target) }
        composeRule.onNodeWithText("已结束").assertDoesNotExist()
    }

    @Test
    fun T_10_closed_row_does_not_swipe_and_opens_detail() {
        var abandoned: TaskId? = null
        var opened: TaskId? = null
        val closed = task(id = 9L, title = "已结项任务", closed = true)
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = todoState(listOf(closed), GtdPosition.ENDED),
                    onAbandonTask = { abandoned = it },
                    onOpenTaskDetail = { opened = it },
                )
            }
        }

        composeRule.onNodeWithText("已结项").assertIsDisplayed()
        composeRule.onNodeWithText("已结项任务").performTouchInput { swipeRight() }
        composeRule.runOnIdle { assertNull("已结项行不应响应放弃滑动", abandoned) }

        composeRule.onNodeWithText("已结项任务").performClick()
        composeRule.runOnIdle { assertEquals(TaskId(9L), opened) }
    }

    @Test
    fun T_11_navigation_hides_and_shows_at_thresholds() {
        val visibility = mutableListOf<Boolean>()
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = todoState((1L..30L).map { task(it, "任务$it") }),
                    onNavigationVisibilityChanged = { visibility += it },
                )
            }
        }

        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.runOnIdle { assertTrue("上滑超过隐藏阈值", visibility.contains(false)) }

        composeRule.onRoot().performTouchInput { swipeDown() }
        composeRule.runOnIdle { assertTrue("下滑超过显示阈值", visibility.contains(true)) }
    }

    @Test
    fun T_07_project_form_creates_and_blank_is_blocked() {
        var created = 0
        composeRule.setContent {
            NautileTheme {
                NewTaskSheet(
                    state = NewTaskUiState(
                        kind = NewTaskKind.PROJECT,
                        projectName = "新项目",
                    ),
                    onCreateProject = { created += 1 },
                )
            }
        }

        composeRule.onNodeWithText("项目名称").assertIsDisplayed()
        composeRule.onNodeWithText("创建项目").performClick()
        composeRule.runOnIdle { assertEquals(1, created) }

        composeRule.onNodeWithText("新建").assertIsDisplayed()
    }

    @Test
    fun T_07_blank_project_stays_with_error() {
        composeRule.setContent {
            NautileTheme {
                NewTaskSheet(
                    state = NewTaskUiState(
                        kind = NewTaskKind.PROJECT,
                        projectName = "   ",
                        projectNameInvalid = true,
                        error = "项目名称不能为空",
                        attempted = true,
                    ),
                )
            }
        }

        composeRule.onAllNodesWithText("项目名称不能为空").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("新建").assertIsDisplayed()
    }

    @Test
    fun D_02_project_child_click_reports_task_id() {
        var childId: TaskId? = null
        composeRule.setContent {
            NautileTheme {
                ProjectDetailSheet(
                    state = ProjectDetailUiState(
                        project = Project(
                            id = ProjectId(7L),
                            name = "课程项目",
                            status = ProjectStatus.ACTIVE,
                            createdAt = now,
                            updatedAt = now,
                        ),
                        children = listOf(task(3L, "子任务")),
                        nameDraft = null,
                        nameInvalid = false,
                        saving = false,
                        error = null,
                        leaveBlocked = false,
                        collapsed = false,
                    ),
                    onOpenChild = { childId = it },
                    onNameChanged = {},
                    onCommitName = {},
                    onToggleCollapsed = {},
                    onCloseRequested = {},
                )
            }
        }

        composeRule.onNodeWithTag("project_child_3").performClick()

        composeRule.runOnIdle { assertEquals(TaskId(3L), childId) }
    }

    @Test
    fun D_03_task_detail_content_pull_requests_close() {
        var closes = 0
        composeRule.setContent {
            NautileTheme {
                TaskDetailSheet(
                    state = TaskDetailUiState(
                        task = task(),
                        titleDraft = null,
                        titleInvalid = false,
                        saving = false,
                        error = null,
                        leaveBlocked = false,
                        canReopen = false,
                    ),
                    onCloseRequested = { closes += 1 },
                    onTitleChanged = {},
                    onCommitTitle = {},
                    onReopen = {},
                )
            }
        }

        // T-085 测试口径修正（验证职责）：旧手势是在“任务详情”文字节点上
        // swipeDown(startY = center.y, endY = height * 0.85f)，位移约 10dp，
        // 小于 64dp 关闭阈值，几何上不可能触发关闭（T-083 失败原因之一）。
        // 改为从全屏遮罩起手、纵向拖动约 70% 屏高（远超 64dp），覆盖 T-084 祖先观察路径；
        // 64dp 阈值与 closes == 1 断言保持不变。
        composeRule.onRoot().performTouchInput {
            swipeDown(startY = height * 0.2f, endY = height * 0.9f)
        }

        composeRule.runOnIdle { assertEquals("内容到顶继续下拉应请求关闭", 1, closes) }
    }
}
