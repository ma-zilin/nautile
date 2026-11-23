package io.github.mazilin.nautile.verification

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.ui.screen.todo.NewTaskKind
import io.github.mazilin.nautile.ui.screen.todo.NewTaskSheet
import io.github.mazilin.nautile.ui.screen.todo.NewTaskUiState
import io.github.mazilin.nautile.ui.screen.todo.ProjectDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.ProjectDetailUiState
import io.github.mazilin.nautile.ui.screen.todo.TaskDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.TaskDetailUiState
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T-077/T-081 详情下拉、字段显示与新建两行 Compose 验证。
 * 需要设备；设备未授权时只编译，不写通过。
 */
@RunWith(AndroidJUnit4::class)
class AcUiT077T081Test {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-23T12:00:00Z")

    private fun task(detail: String? = null) = Task(
        id = TaskId(1L),
        title = "任务",
        position = GtdPosition.INBOX,
        detail = detail,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun T_077_task_detail_short_content_pull_closes() {
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
                    onTitleChanged = {},
                    onCommitTitle = {},
                    onReopen = {},
                    onCloseRequested = { closes += 1 },
                    onDetailChanged = {},
                    onCommitDetail = {},
                )
            }
        }

        composeRule.onRoot().performTouchInput { swipeDown() }

        composeRule.runOnIdle { assertEquals(1, closes) }
    }

    @Test
    fun T_077_project_detail_short_content_pull_closes() {
        var closes = 0
        composeRule.setContent {
            NautileTheme {
                ProjectDetailSheet(
                    state = ProjectDetailUiState(
                        project = Project(
                            id = ProjectId(1L),
                            name = "项目",
                            status = ProjectStatus.ACTIVE,
                            createdAt = now,
                            updatedAt = now,
                        ),
                        children = emptyList(),
                        nameDraft = null,
                        nameInvalid = false,
                        saving = false,
                        error = null,
                        leaveBlocked = false,
                        collapsed = false,
                    ),
                    onOpenChild = {},
                    onNameChanged = {},
                    onCommitName = {},
                    onToggleCollapsed = {},
                    onCloseRequested = { closes += 1 },
                )
            }
        }

        composeRule.onRoot().performTouchInput { swipeDown() }

        composeRule.runOnIdle { assertEquals(1, closes) }
    }

    @Test
    fun T_077_invalid_project_name_shows_error() {
        composeRule.setContent {
            NautileTheme {
                ProjectDetailSheet(
                    state = ProjectDetailUiState(
                        project = Project(
                            id = ProjectId(1L),
                            name = "项目",
                            status = ProjectStatus.ACTIVE,
                            createdAt = now,
                            updatedAt = now,
                        ),
                        children = emptyList(),
                        nameDraft = "   ",
                        nameInvalid = true,
                        saving = false,
                        error = "请先修正无效项目名称",
                        leaveBlocked = true,
                        collapsed = false,
                    ),
                    onOpenChild = {},
                    onNameChanged = {},
                    onCommitName = {},
                    onToggleCollapsed = {},
                    onCloseRequested = {},
                )
            }
        }

        composeRule.onNodeWithText("项目名称不能为空").assertIsDisplayed()
        composeRule.onNodeWithText("请先修正无效项目名称").assertIsDisplayed()
    }

    @Test
    fun T_081_task_detail_shows_detail_field_and_commits_ime() {
        var commits = 0
        composeRule.setContent {
            NautileTheme {
                TaskDetailSheet(
                    state = TaskDetailUiState(
                        task = task(detail = "旧详情"),
                        titleDraft = null,
                        titleInvalid = false,
                        saving = false,
                        error = null,
                        leaveBlocked = false,
                        canReopen = false,
                        detailDraft = "新详情",
                    ),
                    onTitleChanged = {},
                    onCommitTitle = {},
                    onReopen = {},
                    onCloseRequested = {},
                    onDetailChanged = {},
                    onCommitDetail = { commits += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("task_detail_detail").assertIsDisplayed()
        composeRule.onNodeWithText("新详情").performImeAction()

        composeRule.runOnIdle { assertTrue("IME Done 应触发详情提交", commits >= 1) }
    }

    @Test
    fun T_081_new_task_todo_mode_has_two_visible_rows() {
        composeRule.setContent {
            NautileTheme {
                NewTaskSheet(
                    state = NewTaskUiState(kind = NewTaskKind.TODO, title = "标题"),
                )
            }
        }

        composeRule.onNodeWithText("详情（可选）").assertIsDisplayed()
        composeRule.onNodeWithTag("new_task_detail").assertIsDisplayed()
    }

    @Test
    fun T_081_new_task_project_mode_hides_detail_row() {
        composeRule.setContent {
            NautileTheme {
                NewTaskSheet(
                    state = NewTaskUiState(kind = NewTaskKind.PROJECT, projectName = "项目"),
                )
            }
        }

        composeRule.onNodeWithText("详情（可选）").assertDoesNotExist()
        composeRule.onNodeWithTag("new_task_detail").assertDoesNotExist()
    }

    @Test
    fun T_081_detail_short_content_pull_with_draft_requests_close() {
        var closes = 0
        composeRule.setContent {
            NautileTheme {
                TaskDetailSheet(
                    state = TaskDetailUiState(
                        task = task(detail = "旧详情"),
                        titleDraft = null,
                        titleInvalid = false,
                        saving = false,
                        error = null,
                        leaveBlocked = false,
                        canReopen = false,
                        detailDraft = "草稿详情",
                    ),
                    onTitleChanged = {},
                    onCommitTitle = {},
                    onReopen = {},
                    onCloseRequested = { closes += 1 },
                    onDetailChanged = {},
                    onCommitDetail = {},
                )
            }
        }

        composeRule.onRoot().performTouchInput { swipeDown() }

        composeRule.runOnIdle { assertEquals(1, closes) }
    }
}
