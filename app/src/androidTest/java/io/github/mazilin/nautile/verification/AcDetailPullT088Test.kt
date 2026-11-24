package io.github.mazilin.nautile.verification

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.ui.screen.todo.ProjectDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.ProjectDetailUiState
import io.github.mazilin.nautile.ui.screen.todo.TaskDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.TaskDetailUiState
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T-088 回归：输入框聚焦或手势起点在输入框内时，详情下拉不关闭；
 * 非输入区短内容下拉仍关闭；无效字段下拉后提示保留。
 *
 * 64dp 阈值与既有 T_077/T_081/D_03 断言未修改。需要设备。
 */
@RunWith(AndroidJUnit4::class)
class AcDetailPullT088Test {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-24T12:00:00Z")

    private fun task() = Task(
        id = TaskId(1L),
        title = "任务",
        position = GtdPosition.INBOX,
        detail = "旧详情",
        createdAt = now,
        updatedAt = now,
    )

    private fun taskState(
        titleInvalid: Boolean = false,
        leaveBlocked: Boolean = false,
        error: String? = null,
    ) = TaskDetailUiState(
        task = task(),
        titleDraft = if (titleInvalid) "   " else null,
        titleInvalid = titleInvalid,
        saving = false,
        error = error,
        leaveBlocked = leaveBlocked,
        canReopen = false,
    )

    private fun projectState(
        nameInvalid: Boolean = false,
        leaveBlocked: Boolean = false,
        error: String? = null,
    ) = ProjectDetailUiState(
        project = Project(
            id = ProjectId(1L),
            name = "项目",
            status = ProjectStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        ),
        children = emptyList(),
        nameDraft = if (nameInvalid) "   " else null,
        nameInvalid = nameInvalid,
        saving = false,
        error = error,
        leaveBlocked = leaveBlocked,
        collapsed = false,
    )

    @Test
    fun T_088_task_input_focused_pull_does_not_close() {
        var closeRequests = 0
        composeRule.setContent {
            NautileTheme {
                TaskDetailSheet(
                    state = taskState(),
                    onTitleChanged = {},
                    onCommitTitle = {},
                    onReopen = {},
                    onCloseRequested = { closeRequests += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("task_detail_detail").performClick()
        // 聚焦后 IME 会产生额外 isRoot 节点；定位包含输入框的主根。
        composeRule.onNode(isRoot() and hasAnyDescendant(hasSetTextAction()))
            .performTouchInput {
                swipeDown(startY = height * 0.2f, endY = height * 0.9f)
            }

        composeRule.runOnIdle {
            assertEquals("输入框聚焦时非输入区下拉不应请求关闭", 0, closeRequests)
        }
    }

    @Test
    fun T_088_task_pull_starting_inside_input_does_not_close() {
        var closeRequests = 0
        composeRule.setContent {
            NautileTheme {
                TaskDetailSheet(
                    state = taskState(),
                    onTitleChanged = {},
                    onCommitTitle = {},
                    onReopen = {},
                    onCloseRequested = { closeRequests += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("task_detail_detail").performTouchInput {
            swipeDown(startY = center.y - 10f, endY = center.y + 500f)
        }

        composeRule.runOnIdle {
            assertEquals("手势起点在输入框内不应请求关闭", 0, closeRequests)
        }
    }

    @Test
    fun T_088_task_non_input_pull_still_closes() {
        var closeRequests = 0
        composeRule.setContent {
            NautileTheme {
                TaskDetailSheet(
                    state = taskState(),
                    onTitleChanged = {},
                    onCommitTitle = {},
                    onReopen = {},
                    onCloseRequested = { closeRequests += 1 },
                )
            }
        }

        composeRule.onRoot().performTouchInput {
            swipeDown(startY = height * 0.2f, endY = height * 0.9f)
        }

        composeRule.runOnIdle {
            assertEquals("非输入区短内容下拉超过 64dp 应请求关闭", 1, closeRequests)
        }
    }

    @Test
    fun T_088_project_input_focused_pull_does_not_close() {
        var closeRequests = 0
        composeRule.setContent {
            NautileTheme {
                ProjectDetailSheet(
                    state = projectState(),
                    onNameChanged = {},
                    onCommitName = {},
                    onToggleCollapsed = {},
                    onCloseRequested = { closeRequests += 1 },
                )
            }
        }

        composeRule.onNodeWithText("项目").performClick()
        // 聚焦后 IME 会产生额外 isRoot 节点；定位包含输入框的主根。
        composeRule.onNode(isRoot() and hasAnyDescendant(hasSetTextAction()))
            .performTouchInput {
                swipeDown(startY = height * 0.2f, endY = height * 0.9f)
            }

        composeRule.runOnIdle {
            assertEquals("项目名称聚焦时非输入区下拉不应请求关闭", 0, closeRequests)
        }
    }

    @Test
    fun T_088_project_pull_starting_inside_input_does_not_close() {
        var closeRequests = 0
        composeRule.setContent {
            NautileTheme {
                ProjectDetailSheet(
                    state = projectState(),
                    onNameChanged = {},
                    onCommitName = {},
                    onToggleCollapsed = {},
                    onCloseRequested = { closeRequests += 1 },
                )
            }
        }

        composeRule.onNode(hasSetTextAction() and hasText("项目", substring = true))
            .performTouchInput {
                swipeDown(startY = center.y - 10f, endY = center.y + 500f)
            }

        composeRule.runOnIdle {
            assertEquals("手势起点在项目名称输入框内不应请求关闭", 0, closeRequests)
        }
    }

    @Test
    fun T_088_project_non_input_pull_still_closes() {
        var closeRequests = 0
        composeRule.setContent {
            NautileTheme {
                ProjectDetailSheet(
                    state = projectState(),
                    onNameChanged = {},
                    onCommitName = {},
                    onToggleCollapsed = {},
                    onCloseRequested = { closeRequests += 1 },
                )
            }
        }

        composeRule.onRoot().performTouchInput {
            swipeDown(startY = height * 0.2f, endY = height * 0.9f)
        }

        composeRule.runOnIdle {
            assertEquals("项目非输入区短内容下拉超过 64dp 应请求关闭", 1, closeRequests)
        }
    }

    @Test
    fun T_088_invalid_field_pull_outside_input_shows_prompt() {
        composeRule.setContent {
            NautileTheme {
                TaskDetailSheet(
                    state = taskState(
                        titleInvalid = true,
                        leaveBlocked = true,
                        error = "请先修正无效标题",
                    ),
                    onTitleChanged = {},
                    onCommitTitle = {},
                    onReopen = {},
                    onCloseRequested = {},
                )
            }
        }

        composeRule.onRoot().performTouchInput {
            swipeDown(startY = height * 0.2f, endY = height * 0.9f)
        }

        // 组件只负责发出关闭请求；无效字段的阻止由父层 requestDetailLeave 完成，
        // 对应 JVM 用例 T_088_invalid_field_blocks_leave_and_prompts。
        composeRule.onNodeWithText("标题不能为空").assertIsDisplayed()
        composeRule.onNodeWithText("请先修正无效标题").assertIsDisplayed()
        composeRule.onNodeWithText("任务详情").assertIsDisplayed()
    }
}
