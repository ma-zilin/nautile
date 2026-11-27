package io.github.mazilin.nautile.verification

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * G6-R2 独立验证探针：CMP-46 的关闭触发只有“点遮罩/系统返回/下拉”，
 * 点按面板本体（非可交互空白或标题文字）不得关闭详情。
 *
 * 该探针用于核对根级遮罩点按手势是否越界处理了面板内部的未消费点按。
 */
@RunWith(AndroidJUnit4::class)
class G6R4R2PanelTapProbeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-26T12:00:00Z")

    @Test
    fun G6R4R2_probe_task_detail_body_tap_does_not_close() {
        var closeRequests = 0
        composeRule.setContent {
            NautileTheme {
                TaskDetailSheet(
                    state = TaskDetailUiState(
                        task = Task(
                            id = TaskId(1L),
                            title = "任务",
                            position = GtdPosition.INBOX,
                            detail = "旧详情",
                            createdAt = now,
                            updatedAt = now,
                        ),
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
                    onCloseRequested = { closeRequests += 1 },
                )
            }
        }

        composeRule.onNodeWithText("任务详情").performClick()
        composeRule.waitForIdle()
        assertEquals("点按面板内标题不应请求关闭详情", 0, closeRequests)
        composeRule.onNodeWithText("任务详情").assertIsDisplayed()
    }

    @Test
    fun G6R4R2_probe_project_detail_body_tap_does_not_close() {
        var closeRequests = 0
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
                    onNameChanged = {},
                    onCommitName = {},
                    onToggleCollapsed = {},
                    onCloseRequested = { closeRequests += 1 },
                )
            }
        }

        composeRule.onNodeWithText("项目详情").performClick()
        composeRule.waitForIdle()
        assertEquals("点按面板内标题不应请求关闭面板", 0, closeRequests)
        composeRule.onNodeWithText("项目详情").assertIsDisplayed()
    }
}
