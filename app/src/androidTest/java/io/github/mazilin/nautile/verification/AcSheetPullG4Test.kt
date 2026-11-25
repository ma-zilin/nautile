package io.github.mazilin.nautile.verification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
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
 * G4 闸门：面板下拉关闭阈值、输入框排除、无效字段阻止（MOT-08、MOT-09、T-088、SPC-08）。
 *
 * 预期依据（用户 2026-11-25 确认的设计与本次 G4 任务指令）：
 * - 松手位移 ≥ max(25% 面板高度, 96dp) 或速度 > 800dp/s 才提交关闭，否则 BoundaryRebound 回弹；
 * - 拖动位移 1:1 跟随手指；
 * - 输入框聚焦或手势起点在输入框内时整段手势不参与下拉关闭（T-088）；
 * - 无效字段由父层 canLeave 阻止移除并提示（AC-D-07、AC-D-08）。
 *
 * 宿主接线与 MainActivity 一致：onDismissRequest 先经 canLeave 再决定是否移除面板；
 * canLeave 是生产代码里的判定属性，不在测试中复制业务规则。
 * 需要设备/模拟器；本轮在 emulator-5554（API 36，非真机）执行。
 * 真实键盘空隙与拖拽位移视觉 1:1 无法由语义树断言，列入人工/像素核对项。
 */
@RunWith(AndroidJUnit4::class)
class AcSheetPullG4Test {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-25T09:00:00Z")

    private fun task() = Task(
        id = TaskId(1L),
        title = "任务",
        position = GtdPosition.INBOX,
        detail = "旧详情",
        createdAt = now,
        updatedAt = now,
    )

    private fun taskState(
        titleDraft: String? = null,
        titleInvalid: Boolean = false,
        error: String? = null,
    ) = TaskDetailUiState(
        task = task(),
        titleDraft = titleDraft,
        titleInvalid = titleInvalid,
        saving = false,
        error = error,
        leaveBlocked = false,
        canReopen = false,
    )

    private fun projectState(
        nameDraft: String? = null,
        nameInvalid: Boolean = false,
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
        nameDraft = nameDraft,
        nameInvalid = nameInvalid,
        saving = false,
        error = error,
        leaveBlocked = false,
        collapsed = false,
    )

    /** 宿主式状态：visible 由 onDismissRequest 按 canLeave 决定，closeRequests 记录请求次数。 */
    private class SheetHost<T>(initial: T) {
        var visible by mutableStateOf(true)
        var state by mutableStateOf(initial)
        var closeRequests = 0
    }

    /** 在根节点上从 [startYFraction] 处竖直下拉 [distanceDp]；默认 200ms（低速）。 */
    private fun pullDown(
        distanceDp: Float,
        startYFraction: Float = 0.15f,
        durationMillis: Long = 200,
    ) {
        composeRule.onRoot().performTouchInput {
            val start = Offset(width * 0.5f, height * startYFraction)
            swipe(start, start + Offset(0f, distanceDp.dp.toPx()), durationMillis = durationMillis)
        }
    }

    private fun setNewTaskHost(initial: NewTaskUiState): SheetHost<NewTaskUiState> {
        val host = SheetHost(initial)
        composeRule.setContent {
            NautileTheme {
                if (host.visible) {
                    NewTaskSheet(
                        state = host.state,
                        onTitleChanged = { host.state = host.state.copy(title = it) },
                        onDetailChanged = { host.state = host.state.copy(detail = it) },
                        onDismissRequest = {
                            host.closeRequests += 1
                            if (host.state.canLeave) host.visible = false
                        },
                        visible = host.visible,
                    )
                }
            }
        }
        return host
    }

    private fun setTaskDetailHost(initial: TaskDetailUiState): SheetHost<TaskDetailUiState> {
        val host = SheetHost(initial)
        composeRule.setContent {
            NautileTheme {
                if (host.visible) {
                    TaskDetailSheet(
                        state = host.state,
                        onTitleChanged = { host.state = host.state.copy(titleDraft = it) },
                        onCommitTitle = {},
                        onReopen = {},
                        onCloseRequested = {
                            host.closeRequests += 1
                            if (host.state.canLeave) host.visible = false
                        },
                        visible = host.visible,
                    )
                }
            }
        }
        return host
    }

    private fun setProjectDetailHost(initial: ProjectDetailUiState): SheetHost<ProjectDetailUiState> {
        val host = SheetHost(initial)
        composeRule.setContent {
            NautileTheme {
                if (host.visible) {
                    ProjectDetailSheet(
                        state = host.state,
                        onNameChanged = { host.state = host.state.copy(nameDraft = it) },
                        onCommitName = {},
                        onToggleCollapsed = {},
                        onCloseRequested = {
                            host.closeRequests += 1
                            if (host.state.canLeave) host.visible = false
                        },
                        visible = host.visible,
                    )
                }
            }
        }
        return host
    }

    // ---------- 新建面板：阈值 ----------

    @Test
    fun G4_AC_D07_new_task_pull_above_threshold_closes_once() {
        val host = setNewTaskHost(NewTaskUiState(position = GtdPosition.INBOX, title = "新任务"))

        pullDown(200f)

        composeRule.runOnIdle {
            assertEquals("位移 200dp 超过 max(25% 高度, 96dp) 应提交关闭一次", 1, host.closeRequests)
            assertTrue("有效标题应允许移除面板", !host.visible)
        }
        composeRule.onNodeWithText("新建").assertDoesNotExist()
    }

    /**
     * 回归保护：遮罩点按仍应关闭面板。
     *
     * 本轮把根级 detectTapGestures 改为带 touch slop 判定后，未移动的点按不能被判成拖动。
     * 该行为在本轮修改前已存在，属于改造后的回归断言，不适用“先红后绿”。
     */
    @Test
    fun G4_T088_new_task_scrim_tap_still_closes() {
        val host = setNewTaskHost(NewTaskUiState(title = "新任务"))

        composeRule.onRoot().performTouchInput {
            click(Offset(width * 0.5f, height * 0.05f))
        }

        composeRule.runOnIdle {
            assertEquals("点按遮罩应请求关闭一次", 1, host.closeRequests)
            assertTrue("有效标题应允许移除面板", !host.visible)
        }
        composeRule.onNodeWithText("新建").assertDoesNotExist()
    }

    @Test
    fun G4_MOT08_new_task_40dp_pull_rebounds_and_stays() {
        val host = setNewTaskHost(NewTaskUiState(title = "新任务"))

        pullDown(40f)

        composeRule.runOnIdle {
            assertEquals("位移 40dp 未超 96dp 阈值应回弹、不请求关闭", 0, host.closeRequests)
            assertTrue(host.visible)
        }
        composeRule.onNodeWithText("新建").assertIsDisplayed()
    }

    /**
     * 70dp 位于旧 64dp 与现行 96dp 之间：现行设计应回弹。
     * 该用例用于区分“固定 64dp”与“max(25%, 96dp)”两种实现（先红后绿判据）。
     */
    @Test
    fun G4_MOT08_new_task_70dp_between_old_and_new_threshold_rebounds() {
        val host = setNewTaskHost(NewTaskUiState(title = "新任务"))

        pullDown(70f)

        composeRule.runOnIdle {
            assertEquals("70dp 在旧 64dp 与现行 96dp 之间，应回弹而不请求关闭", 0, host.closeRequests)
            assertTrue(host.visible)
        }
        composeRule.onNodeWithText("新建").assertIsDisplayed()
    }

    @Test
    fun G4_MOT08_new_task_handle_80dp_pull_rebounds() {
        val host = setNewTaskHost(NewTaskUiState(title = "新任务"))

        // 拖拽柄中心：内容 Column 顶部内边距 8dp，其上为 22dp 柄区，柄中心在标题上沿上方 19dp。
        val titleTop = composeRule.onNodeWithText("新建")
            .fetchSemanticsNode().boundsInRoot.top
        val handleCenterY = titleTop - with(composeRule.density) { 19.dp.toPx() }

        composeRule.onRoot().performTouchInput {
            val start = Offset(width * 0.5f, handleCenterY)
            swipe(start, start + Offset(0f, 80.dp.toPx()), durationMillis = 200)
        }

        composeRule.runOnIdle {
            assertEquals(
                "拖拽柄 80dp 未超 96dp 阈值应回弹（位移须 1:1，不得被重复累加）",
                0,
                host.closeRequests,
            )
            assertTrue(host.visible)
        }
        composeRule.onNodeWithText("新建").assertIsDisplayed()
    }

    /** 80dp 位移低于 96dp，但 40ms 完成约 2000dp/s > 800dp/s，应提交关闭（MOT-08.3 速度判定）。 */
    @Test
    fun G4_MOT08_new_task_fast_fling_below_distance_threshold_closes() {
        val host = setNewTaskHost(NewTaskUiState(title = "新任务"))

        pullDown(80f, durationMillis = 40)

        composeRule.runOnIdle {
            assertEquals("速度 > 800dp/s 应提交关闭一次", 1, host.closeRequests)
            assertTrue(!host.visible)
        }
    }

    // ---------- 新建面板：T-088 输入框排除 ----------

    @Test
    fun G4_T088_new_task_start_inside_input_drag_does_not_close() {
        val host = setNewTaskHost(NewTaskUiState(title = "新任务"))

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTouchInput {
            swipe(Offset(center.x, center.y - 10f), Offset(center.x, center.y + 500f), 200)
        }

        composeRule.runOnIdle {
            assertEquals("手势起点在输入框内不应请求关闭", 0, host.closeRequests)
            assertTrue(host.visible)
        }
    }

    /**
     * 输入框聚焦后从遮罩区下拉：T-088 要求整段手势不参与下拉关闭。
     * 该断言在 2026-11-25 轮次实际失败（见验证记录），保留以等待开发修复。
     */
    @Test
    fun G4_T088_new_task_input_focused_scrim_drag_does_not_close() {
        val host = setNewTaskHost(NewTaskUiState(title = "新任务"))

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performClick()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().assertIsFocused()

        val roots = composeRule.onAllNodes(isRoot()).fetchSemanticsNodes()
        // 聚焦后 IME 可能新增 isRoot；定位包含输入框的主根再下拉。
        composeRule.onNode(isRoot() and hasAnyDescendant(hasSetTextAction()))
            .performTouchInput {
                val start = Offset(width * 0.5f, height * 0.15f)
                swipe(start, start + Offset(0f, 200.dp.toPx()), durationMillis = 200)
            }

        composeRule.runOnIdle {
            assertEquals(
                "输入框聚焦时从遮罩区下拉不应请求关闭（roots=${roots.size}）",
                0,
                host.closeRequests,
            )
            assertTrue(host.visible)
        }
        composeRule.onAllNodes(hasSetTextAction()).onFirst().assertIsFocused()
    }

    /**
     * 诊断用：输入框聚焦后从遮罩区上滑（非下拉方向）。
     * 下拉处理器不会启动；若仍请求关闭，说明遮罩把拖动误判为点按（对照用）。
     */
    @Test
    fun G4_T088_new_task_input_focused_scrim_upward_swipe_does_not_close() {
        val host = setNewTaskHost(NewTaskUiState(title = "新任务"))

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performClick()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().assertIsFocused()

        composeRule.onNode(isRoot() and hasAnyDescendant(hasSetTextAction()))
            .performTouchInput {
                val start = Offset(width * 0.5f, height * 0.35f)
                swipe(start, start + Offset(0f, -200.dp.toPx()), durationMillis = 200)
            }

        composeRule.runOnIdle {
            assertEquals("向上滑动不构成下拉，不应请求关闭", 0, host.closeRequests)
            assertTrue(host.visible)
        }
    }

    /**
     * 诊断用：聚焦后从遮罩区下拉 40dp（低于 96dp 阈值）。
     * 无论是否排除聚焦，位移不足都不应提交；若关闭则说明遮罩把拖动误判为点按。
     */
    @Test
    fun G4_T088_new_task_input_focused_scrim_40dp_drag_does_not_close() {
        val host = setNewTaskHost(NewTaskUiState(title = "新任务"))

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performClick()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().assertIsFocused()

        composeRule.onNode(isRoot() and hasAnyDescendant(hasSetTextAction()))
            .performTouchInput {
                val start = Offset(width * 0.5f, height * 0.15f)
                swipe(start, start + Offset(0f, 40.dp.toPx()), durationMillis = 200)
            }

        composeRule.runOnIdle {
            assertEquals("40dp 低于阈值，聚焦时也不应请求关闭", 0, host.closeRequests)
            assertTrue(host.visible)
        }
    }

    @Test
    fun G4_AC_D07_new_task_keyboard_focus_typing_keeps_panel_usable() {
        val host = setNewTaskHost(NewTaskUiState(title = "新任务"))

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performClick()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("改")
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasSetTextAction()).onFirst().assertIsFocused()
        composeRule.onNodeWithText("改", substring = true).assertExists()
        composeRule.onNodeWithText("新建").assertIsDisplayed()
        composeRule.onNodeWithText("创建").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(host.visible) }
    }

    // ---------- 新建面板：无效字段 ----------

    @Test
    fun G4_AC_D07_new_task_invalid_field_pull_shows_prompt_and_stays() {
        val host = setNewTaskHost(
            NewTaskUiState(
                position = GtdPosition.INBOX,
                title = "   ",
                titleInvalid = true,
                error = "请先填写有效标题",
                attempted = true,
            ),
        )

        pullDown(200f)

        composeRule.runOnIdle {
            assertEquals("超阈值下拉应发出关闭请求", 1, host.closeRequests)
            assertTrue("无效标题时面板不得被移除", host.visible)
        }
        composeRule.onNodeWithText("新建").assertIsDisplayed()
        composeRule.onNodeWithText("标题不能为空").assertIsDisplayed()
        composeRule.onNodeWithText("请先填写有效标题").assertIsDisplayed()
    }

    // ---------- 任务详情：阈值与排除 ----------

    @Test
    fun G4_MOT08_task_detail_pull_above_threshold_closes_once() {
        val host = setTaskDetailHost(taskState())

        pullDown(200f, startYFraction = 0.2f)

        composeRule.runOnIdle {
            assertEquals("位移 200dp 超过 max(25% 高度, 96dp) 应提交关闭一次", 1, host.closeRequests)
            assertTrue(!host.visible)
        }
    }

    /** 100dp 低于 60% 面板高度的 25%（约 120dp），应回弹；旧 64dp 实现会关闭。 */
    @Test
    fun G4_MOT08_task_detail_100dp_pull_rebounds_and_stays() {
        val host = setTaskDetailHost(taskState())

        pullDown(100f, startYFraction = 0.2f)

        composeRule.runOnIdle {
            assertEquals("100dp 未超面板高度 25% 阈值应回弹、不请求关闭", 0, host.closeRequests)
            assertTrue(host.visible)
        }
        composeRule.onNodeWithText("任务详情").assertIsDisplayed()
    }

    @Test
    fun G4_T088_task_detail_start_inside_input_drag_does_not_close() {
        val host = setTaskDetailHost(taskState())

        composeRule.onNodeWithTag("task_detail_detail").performTouchInput {
            swipe(Offset(center.x, center.y - 10f), Offset(center.x, center.y + 500f), 200)
        }

        composeRule.runOnIdle {
            assertEquals("手势起点在输入框内不应请求关闭", 0, host.closeRequests)
            assertTrue(host.visible)
        }
    }

    @Test
    fun G4_T088_task_detail_input_focused_scrim_drag_does_not_close() {
        val host = setTaskDetailHost(taskState())

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performClick()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().assertIsFocused()

        composeRule.onNode(isRoot() and hasAnyDescendant(hasSetTextAction()))
            .performTouchInput {
                val start = Offset(width * 0.5f, height * 0.15f)
                swipe(start, start + Offset(0f, 200.dp.toPx()), durationMillis = 200)
            }

        composeRule.runOnIdle {
            assertEquals("输入框聚焦时从遮罩区下拉不应请求关闭", 0, host.closeRequests)
            assertTrue(host.visible)
        }
    }

    @Test
    fun G4_AC_D08_task_detail_invalid_field_pull_blocked_by_host() {
        val host = setTaskDetailHost(
            taskState(
                titleDraft = "   ",
                titleInvalid = true,
                error = "请先修正无效标题",
            ),
        )

        pullDown(200f, startYFraction = 0.2f)

        composeRule.runOnIdle {
            assertEquals("超阈值下拉仍应发出一次关闭请求", 1, host.closeRequests)
            assertTrue("无效标题时父层阻止移除", host.visible)
        }
        composeRule.onNodeWithText("任务详情").assertIsDisplayed()
        composeRule.onNodeWithText("标题不能为空").assertIsDisplayed()
        composeRule.onNodeWithText("请先修正无效标题").assertIsDisplayed()
    }

    // ---------- 项目详情：阈值与排除 ----------

    @Test
    fun G4_MOT08_project_detail_pull_above_threshold_closes_once() {
        val host = setProjectDetailHost(projectState())

        pullDown(200f, startYFraction = 0.2f)

        composeRule.runOnIdle {
            assertEquals("位移 200dp 超过 max(25% 高度, 96dp) 应提交关闭一次", 1, host.closeRequests)
            assertTrue(!host.visible)
        }
    }

    @Test
    fun G4_MOT08_project_detail_100dp_pull_rebounds_and_stays() {
        val host = setProjectDetailHost(projectState())

        pullDown(100f, startYFraction = 0.2f)

        composeRule.runOnIdle {
            assertEquals("100dp 未超面板高度 25% 阈值应回弹、不请求关闭", 0, host.closeRequests)
            assertTrue(host.visible)
        }
        composeRule.onNodeWithText("项目详情").assertIsDisplayed()
    }

    @Test
    fun G4_T088_project_detail_start_inside_input_drag_does_not_close() {
        val host = setProjectDetailHost(projectState())

        composeRule.onNodeWithText("项目").performTouchInput {
            swipe(Offset(center.x, center.y - 10f), Offset(center.x, center.y + 500f), 200)
        }

        composeRule.runOnIdle {
            assertEquals("手势起点在项目名称输入框内不应请求关闭", 0, host.closeRequests)
            assertTrue(host.visible)
        }
    }

    @Test
    fun G4_AC_D08_project_detail_invalid_field_pull_blocked_by_host() {
        val host = setProjectDetailHost(
            projectState(
                nameDraft = "   ",
                nameInvalid = true,
                error = "请先修正项目名称",
            ),
        )

        pullDown(200f, startYFraction = 0.2f)

        composeRule.runOnIdle {
            assertEquals("超阈值下拉仍应发出一次关闭请求", 1, host.closeRequests)
            assertTrue("无效名称时父层阻止移除", host.visible)
        }
        composeRule.onNodeWithText("项目详情").assertIsDisplayed()
        composeRule.onNodeWithText("项目名称不能为空").assertIsDisplayed()
        composeRule.onNodeWithText("请先修正项目名称").assertIsDisplayed()
    }
}
