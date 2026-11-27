package io.github.mazilin.nautile.verification

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.ui.screen.todo.NewTaskSheet
import io.github.mazilin.nautile.ui.screen.todo.NewTaskUiState
import io.github.mazilin.nautile.ui.screen.todo.PendingTaskCommand
import io.github.mazilin.nautile.ui.screen.todo.ProjectDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.ProjectDetailUiState
import io.github.mazilin.nautile.ui.screen.todo.ProjectReopenPrompt
import io.github.mazilin.nautile.ui.screen.todo.TaskDetailSheet
import io.github.mazilin.nautile.ui.screen.todo.TaskDetailUiState
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoUiState
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S1-T08/S1-T09 Compose 组件验证（T-064）。
 *
 * 覆盖详情字段 IME 提交、无效提示、重新打开入口、项目改名/子任务、新建面板创建/取消/无效，
 * 以及项目重开确认/取消 UI。需要设备；未执行时只作编译证据。
 */
@RunWith(AndroidJUnit4::class)
class AcDetailNewTaskUiT08T09Test {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-21T04:00:00Z")

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
        closedOn = if (closed) LocalDate.of(2026, 11, 21) else null,
        createdAt = now,
        updatedAt = now,
    )

    private fun project() = Project(
        id = ProjectId(7L),
        name = "课程项目",
        status = ProjectStatus.ACTIVE,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun AC_D_06_task_title_done_commits() {
        var committed = 0
        composeRule.setContent {
            NautileTheme {
                TaskDetailSheet(
                    state = TaskDetailUiState(
                        task = task(),
                        titleDraft = "新标题",
                        titleInvalid = false,
                        saving = false,
                        error = null,
                        leaveBlocked = false,
                        canReopen = false,
                    ),
                    onTitleChanged = {},
                    onCommitTitle = { committed += 1 },
                    onReopen = {},
                    onCloseRequested = {},
                )
            }
        }

        composeRule.onNodeWithText("新标题").performImeAction()

        composeRule.runOnIdle { assertTrue("IME Done 应触发提交", committed >= 1) }
    }

    @Test
    fun AC_D_08_invalid_task_title_shows_error() {
        composeRule.setContent {
            NautileTheme {
                TaskDetailSheet(
                    state = TaskDetailUiState(
                        task = task(),
                        titleDraft = "   ",
                        titleInvalid = true,
                        saving = false,
                        error = "请先修正无效标题",
                        leaveBlocked = true,
                        canReopen = false,
                    ),
                    onTitleChanged = {},
                    onCommitTitle = {},
                    onReopen = {},
                    onCloseRequested = {},
                )
            }
        }

        composeRule.onNodeWithText("标题不能为空").assertIsDisplayed()
        composeRule.onNodeWithText("请先修正无效标题").assertIsDisplayed()
        composeRule.onNodeWithText("任务详情").assertIsDisplayed()
    }

    @Test
    fun AC_T_29_closed_task_shows_reopen_entry() {
        var reopened = 0
        composeRule.setContent {
            NautileTheme {
                TaskDetailSheet(
                    state = TaskDetailUiState(
                        task = task(closed = true),
                        titleDraft = null,
                        titleInvalid = false,
                        saving = false,
                        error = null,
                        leaveBlocked = false,
                        canReopen = true,
                    ),
                    onTitleChanged = {},
                    onCommitTitle = {},
                    onReopen = { reopened += 1 },
                    onCloseRequested = {},
                )
            }
        }

        composeRule.onNodeWithText("重新打开").performClick()

        composeRule.runOnIdle { assertEquals(1, reopened) }
    }

    @Test
    fun AC_D_06_project_name_done_commits() {
        var committed = 0
        composeRule.setContent {
            NautileTheme {
                ProjectDetailSheet(
                    state = ProjectDetailUiState(
                        project = project(),
                        children = emptyList(),
                        nameDraft = "新项目名",
                        nameInvalid = false,
                        saving = false,
                        error = null,
                        leaveBlocked = false,
                        collapsed = false,
                    ),
                    onNameChanged = {},
                    onCommitName = { committed += 1 },
                    onToggleCollapsed = {},
                    onCloseRequested = {},
                )
            }
        }

        composeRule.onNodeWithText("新项目名").performImeAction()

        composeRule.runOnIdle { assertTrue("IME Done 应触发项目名提交", committed >= 1) }
    }

    @Test
    fun AC_D_06_project_detail_shows_children_and_collapse() {
        var toggles = 0
        composeRule.setContent {
            NautileTheme {
                ProjectDetailSheet(
                    state = ProjectDetailUiState(
                        project = project(),
                        children = listOf(task(1L, "子任务一"), task(2L, "子任务二", closed = true)),
                        nameDraft = null,
                        nameInvalid = false,
                        saving = false,
                        error = null,
                        leaveBlocked = false,
                        collapsed = false,
                    ),
                    onNameChanged = {},
                    onCommitName = {},
                    onToggleCollapsed = { toggles += 1 },
                    onCloseRequested = {},
                )
            }
        }

        composeRule.onNodeWithText("子任务一").assertIsDisplayed()
        composeRule.onNodeWithText("子任务二").assertIsDisplayed()
        // v2.16（4.27 / R5-9）：子任务折叠改为纯 chevron 图标按钮，无可见文字，带 contentDescription。
        composeRule.onAllNodesWithText("收起子任务").assertCountEquals(0)
        composeRule.onAllNodesWithText("展开子任务").assertCountEquals(0)
        composeRule.onNodeWithTag("project_children_toggle")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("收起子任务")
            .performClick()

        composeRule.runOnIdle { assertEquals(1, toggles) }
    }

    @Test
    fun AC_D_07_new_task_sheet_create_and_cancel() {
        var creates = 0
        var cancels = 0
        composeRule.setContent {
            NautileTheme {
                NewTaskSheet(
                    state = NewTaskUiState(position = GtdPosition.INBOX, title = "新任务"),
                    onTitleChanged = {},
                    onCreate = { creates += 1 },
                    onCancel = { cancels += 1 },
                    onDismissRequest = {},
                )
            }
        }

        composeRule.onNodeWithText("新建").assertIsDisplayed()
        // R4 G4：不再有独立“清单：X”块，当前清单由工具栏“清单”项显示（AC-D-07、4.25）。
        composeRule.onNodeWithContentDescription("移动到 GTD 清单，当前：收件箱")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("清单：", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("创建").performClick()
        composeRule.onNodeWithText("取消").performClick()

        composeRule.runOnIdle {
            assertEquals(1, creates)
            assertEquals(1, cancels)
        }
    }

    @Test
    fun AC_D_07_invalid_new_task_stays_visible_with_error() {
        composeRule.setContent {
            NautileTheme {
                NewTaskSheet(
                    state = NewTaskUiState(
                        position = GtdPosition.INBOX,
                        title = "   ",
                        titleInvalid = true,
                        error = "标题不能为空",
                        attempted = true,
                    ),
                    onTitleChanged = {},
                    onCreate = {},
                    onCancel = {},
                    onDismissRequest = {},
                )
            }
        }

        composeRule.onNodeWithText("新建").assertIsDisplayed()
        composeRule.onAllNodesWithText("标题不能为空").onFirst().assertIsDisplayed()
    }

    @Test
    fun AC_T_28_project_reopen_prompt_confirm_and_cancel() {
        var confirms = 0
        var cancels = 0
        composeRule.setContent {
            NautileTheme {
                TodoScreen(
                    state = TodoUiState(
                        projectReopenPrompt = ProjectReopenPrompt(
                            projectId = ProjectId(7L),
                            projectName = "课程项目",
                            status = ProjectStatus.COMPLETED,
                            pending = PendingTaskCommand.Reopen(TaskId(1L)),
                        ),
                    ),
                    onConfirmProjectReopen = { confirms += 1 },
                    onCancelProjectReopen = { cancels += 1 },
                )
            }
        }

        composeRule.onNodeWithText("项目「课程项目」已结项，是否重新打开项目？")
            .assertIsDisplayed()
        composeRule.onNodeWithText("重新打开").performClick()
        composeRule.onNodeWithText("取消").performClick()

        composeRule.runOnIdle {
            assertEquals(1, confirms)
            assertEquals(1, cancels)
        }
    }
}
