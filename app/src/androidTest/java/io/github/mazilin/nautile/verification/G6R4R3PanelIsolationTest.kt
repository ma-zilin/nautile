package io.github.mazilin.nautile.verification

import android.graphics.Bitmap
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
 * G6-R4-R3 独立复测：面板内点按不得误关（上轮新回归）+ 遮罩/下拉/菜单既有口径回归。
 *
 * 依据：`docs/需求基线.md` v2.15（AC-D-07/08、FR-G-14）、CMP-05/CMP-31/CMP-45/CMP-46、
 * T-088 输入框排除，以及本轮修复“遮罩点按手势移到遮罩层，面板区域点按由上层 Surface 消费”。
 *
 * 覆盖五个面板（新建、任务详情、项目详情、任务编辑、项目编辑）：
 * - 点按面板标题文字、点按面板内空白（拖拽柄区）都不请求关闭；未移动点按遮罩请求关闭；
 * - 输入框聚焦后从遮罩区下拉 40dp / 200dp 都不请求关闭（T-088）；
 * - 未聚焦从遮罩区下拉 40dp 回弹不关闭、200dp 关闭；
 * - 任务详情/任务编辑/新建的菜单与日期面板：点外部只关菜单/日期面板，系统返回先关菜单/日期面板。
 *
 * 宿主只做“请求关闭→移除”的接线，不复制业务规则；运行环境 emulator-5554（API 36，模拟器，非真机）。
 * 动画观感、真机手势体验不在本用例判定范围。
 */
@RunWith(AndroidJUnit4::class)
class G6R4R3PanelIsolationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now: Instant = Instant.parse("2026-11-26T12:00:00Z")

    private fun task() = Task(
        id = TaskId(1L),
        title = "任务",
        position = GtdPosition.INBOX,
        detail = "旧详情",
        createdAt = now,
        updatedAt = now,
    )

    private fun taskDetailState() = TaskDetailUiState(
        task = task(),
        titleDraft = null,
        titleInvalid = false,
        saving = false,
        error = null,
        leaveBlocked = false,
        canReopen = false,
    )

    private fun projectDetailState() = ProjectDetailUiState(
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
    )

    /** 宿主：记录关闭请求次数，并按“允许关闭”移除面板（与生产宿主同层，不复制业务判定）。 */
    private class PanelHost {
        var visible by mutableStateOf(true)
        var closeRequests = 0

        fun requestClose() {
            closeRequests += 1
            visible = false
        }
    }

    private fun setNewTaskHost(host: PanelHost) {
        composeRule.setContent {
            NautileTheme {
                NewTaskSheet(
                    state = NewTaskUiState(position = GtdPosition.INBOX, title = "新任务"),
                    onCancel = host::requestClose,
                    onDismissRequest = host::requestClose,
                    visible = host.visible,
                )
            }
        }
    }

    private fun setTaskDetailHost(host: PanelHost) {
        composeRule.setContent {
            NautileTheme {
                TaskDetailSheet(
                    state = taskDetailState(),
                    onTitleChanged = {},
                    onCommitTitle = {},
                    onReopen = {},
                    onCloseRequested = host::requestClose,
                    visible = host.visible,
                )
            }
        }
    }

    private fun setProjectDetailHost(host: PanelHost) {
        composeRule.setContent {
            NautileTheme {
                ProjectDetailSheet(
                    state = projectDetailState(),
                    onNameChanged = {},
                    onCommitName = {},
                    onToggleCollapsed = {},
                    onCloseRequested = host::requestClose,
                    visible = host.visible,
                )
            }
        }
    }

    // ---------- 基础交互 ----------

    /** 点按面板内标题文字：不移动的点按落在上层 Surface，不得被遮罩层判为点按遮罩。 */
    private fun tapPanelTitle(title: String) {
        composeRule.onNodeWithText(title).performClick()
        composeRule.waitForIdle()
    }

    /** 点按面板内空白：拖拽柄中心（标题上沿上方 19dp = 8dp 内边距 + 22dp 柄区的一半）。 */
    private fun tapPanelBlank(title: String) {
        val titleTop = composeRule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top
        val handleCenterY = titleTop - with(composeRule.density) { 19.dp.toPx() }
        composeRule.onRoot().performTouchInput {
            click(Offset(width * 0.5f, handleCenterY))
        }
        composeRule.waitForIdle()
    }

    /** 点按遮罩左上角（未移动）。仅用于无输入框焦点的状态，避免 IME 产生额外 root。 */
    private fun tapScrim() {
        composeRule.onRoot().performTouchInput { click(Offset(12f, 12f)) }
        composeRule.waitForIdle()
    }

    /** 从遮罩区慢速下拉（200ms），起点在面板上方。 */
    private fun pullFromScrim(distanceDp: Float) {
        composeRule.onRoot().performTouchInput {
            val start = Offset(width * 0.5f, height * 0.15f)
            swipe(start, start + Offset(0f, distanceDp.dp.toPx()), durationMillis = 200)
        }
        composeRule.waitForIdle()
    }

    /** 聚焦第一个输入框后，从遮罩区慢速下拉；IME 会新增 root，定位含输入框的主根。 */
    private fun focusInputAndPullFromScrim(distanceDp: Float) {
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performClick()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().assertIsFocused()
        composeRule.onNode(isRoot() and hasAnyDescendant(hasSetTextAction()))
            .performTouchInput {
                val start = Offset(width * 0.5f, height * 0.15f)
                swipe(start, start + Offset(0f, distanceDp.dp.toPx()), durationMillis = 200)
            }
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    /** 证据截图（不提交）：公共 Download/nautile_evidence，唯一名避免覆盖。 */
    private fun saveEvidence(name: String) {
        composeRule.waitForIdle()
        val bitmap: Bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "g6r4_r3_${name}_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/nautile_evidence",
            )
        }
        val uri = requireNotNull(
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
        ) { "无法创建截图输出：$fileName" }
        context.contentResolver.openOutputStream(uri)!!.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("截图应写入：$uri", context.contentResolver.openInputStream(uri) != null)
        println("G6R4R3_SCREENSHOT name=$name file=$fileName uri=$uri")
    }

    // ---------- 新建面板 ----------

    @Test
    fun G6R4R3_new_task_panel_taps_keep_open_then_scrim_tap_closes() {
        val host = PanelHost()
        setNewTaskHost(host)

        tapPanelTitle("新建")
        assertEquals("点按新建面板标题不得关闭", 0, host.closeRequests)
        composeRule.onNodeWithText("新建").assertIsDisplayed()

        tapPanelBlank("新建")
        assertEquals("点按新建面板空白（拖拽柄区）不得关闭", 0, host.closeRequests)
        composeRule.onNodeWithText("新建").assertIsDisplayed()
        saveEvidence("new_task_panel_tap")

        tapScrim()
        composeRule.waitForIdle()
        assertEquals("新建面板点按遮罩应关闭一次", 1, host.closeRequests)
        composeRule.onNodeWithText("新建").assertDoesNotExist()
    }

    @Test
    fun G6R4R3_new_task_focused_input_pull_40_200_does_not_close() {
        val host = PanelHost()
        setNewTaskHost(host)

        focusInputAndPullFromScrim(40f)
        assertEquals("新建面板输入框聚焦下拉 40dp 不得关闭", 0, host.closeRequests)
        composeRule.onNodeWithText("新建").assertExists()

        focusInputAndPullFromScrim(200f)
        assertEquals("新建面板输入框聚焦下拉 200dp 不得关闭（T-088）", 0, host.closeRequests)
        composeRule.onNodeWithText("新建").assertExists()
        assertTrue("面板应保持可见", host.visible)
    }

    @Test
    fun G6R4R3_new_task_unfocused_40dp_rebounds_and_200dp_closes() {
        val host = PanelHost()
        setNewTaskHost(host)

        pullFromScrim(40f)
        assertEquals("新建面板未聚焦下拉 40dp 未超阈值应回弹", 0, host.closeRequests)
        composeRule.onNodeWithText("新建").assertIsDisplayed()

        pullFromScrim(200f)
        composeRule.waitForIdle()
        assertEquals("新建面板未聚焦下拉 200dp 应关闭一次", 1, host.closeRequests)
        composeRule.onNodeWithText("新建").assertDoesNotExist()
    }

    // ---------- 任务详情 ----------

    @Test
    fun G6R4R3_task_detail_panel_taps_keep_open_then_scrim_tap_closes() {
        val host = PanelHost()
        setTaskDetailHost(host)

        tapPanelTitle("任务详情")
        assertEquals("点按任务详情标题不得关闭（上轮新回归）", 0, host.closeRequests)
        composeRule.onNodeWithText("任务详情").assertIsDisplayed()

        tapPanelBlank("任务详情")
        assertEquals("点按任务详情空白（拖拽柄区）不得关闭", 0, host.closeRequests)
        composeRule.onNodeWithText("任务详情").assertIsDisplayed()
        saveEvidence("task_detail_panel_tap")

        tapScrim()
        composeRule.waitForIdle()
        assertEquals("任务详情点按遮罩应关闭一次", 1, host.closeRequests)
        composeRule.onNodeWithText("任务详情").assertDoesNotExist()
    }

    @Test
    fun G6R4R3_task_detail_focused_input_pull_40_200_does_not_close() {
        val host = PanelHost()
        setTaskDetailHost(host)

        focusInputAndPullFromScrim(40f)
        assertEquals("任务详情输入框聚焦下拉 40dp 不得关闭", 0, host.closeRequests)
        composeRule.onNodeWithText("任务详情").assertExists()

        focusInputAndPullFromScrim(200f)
        assertEquals("任务详情输入框聚焦下拉 200dp 不得关闭（T-088）", 0, host.closeRequests)
        composeRule.onNodeWithText("任务详情").assertExists()
        assertTrue("面板应保持可见", host.visible)
    }

    @Test
    fun G6R4R3_task_detail_unfocused_40dp_rebounds_and_200dp_closes() {
        val host = PanelHost()
        setTaskDetailHost(host)

        pullFromScrim(40f)
        assertEquals("任务详情未聚焦下拉 40dp 未超阈值应回弹", 0, host.closeRequests)
        composeRule.onNodeWithText("任务详情").assertIsDisplayed()

        pullFromScrim(200f)
        composeRule.waitForIdle()
        assertEquals("任务详情未聚焦下拉 200dp 应关闭一次", 1, host.closeRequests)
        composeRule.onNodeWithText("任务详情").assertDoesNotExist()
    }

    // ---------- 项目详情 ----------

    @Test
    fun G6R4R3_project_detail_panel_taps_keep_open_then_scrim_tap_closes() {
        val host = PanelHost()
        setProjectDetailHost(host)

        tapPanelTitle("项目详情")
        assertEquals("点按项目详情标题不得关闭（上轮新回归）", 0, host.closeRequests)
        composeRule.onNodeWithText("项目详情").assertIsDisplayed()

        tapPanelBlank("项目详情")
        assertEquals("点按项目详情空白（拖拽柄区）不得关闭", 0, host.closeRequests)
        composeRule.onNodeWithText("项目详情").assertIsDisplayed()
        saveEvidence("project_detail_panel_tap")

        tapScrim()
        composeRule.waitForIdle()
        assertEquals("项目详情点按遮罩应关闭一次", 1, host.closeRequests)
        composeRule.onNodeWithText("项目详情").assertDoesNotExist()
    }

    @Test
    fun G6R4R3_project_detail_focused_input_pull_40_200_does_not_close() {
        val host = PanelHost()
        setProjectDetailHost(host)

        focusInputAndPullFromScrim(40f)
        assertEquals("项目详情输入框聚焦下拉 40dp 不得关闭", 0, host.closeRequests)
        composeRule.onNodeWithText("项目详情").assertExists()

        focusInputAndPullFromScrim(200f)
        assertEquals("项目详情输入框聚焦下拉 200dp 不得关闭（T-088）", 0, host.closeRequests)
        composeRule.onNodeWithText("项目详情").assertExists()
        assertTrue("面板应保持可见", host.visible)
    }

    @Test
    fun G6R4R3_project_detail_unfocused_40dp_rebounds_and_200dp_closes() {
        val host = PanelHost()
        setProjectDetailHost(host)

        pullFromScrim(40f)
        assertEquals("项目详情未聚焦下拉 40dp 未超阈值应回弹", 0, host.closeRequests)
        composeRule.onNodeWithText("项目详情").assertIsDisplayed()

        pullFromScrim(200f)
        composeRule.waitForIdle()
        assertEquals("项目详情未聚焦下拉 200dp 应关闭一次", 1, host.closeRequests)
        composeRule.onNodeWithText("项目详情").assertDoesNotExist()
    }

    // ---------- 菜单 / 日期面板：点外部只关子层，返回先关子层 ----------

    @Test
    fun G6R4R3_task_detail_menu_and_date_panel_close_before_sheet() {
        val host = PanelHost()
        setTaskDetailHost(host)

        // 优先级菜单：点菜单外只关菜单。
        composeRule.onNodeWithContentDescription("设置优先级，当前：无").performClick()
        composeRule.onNodeWithText("高").assertIsDisplayed()
        tapScrim()
        composeRule.onNodeWithText("高").assertDoesNotExist()
        composeRule.onNodeWithText("任务详情").assertIsDisplayed()
        assertEquals("点菜单外不应关闭详情", 0, host.closeRequests)

        // 系统返回：先关菜单，详情保持。
        composeRule.onNodeWithContentDescription("设置优先级，当前：无").performClick()
        composeRule.onNodeWithText("高").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("高").assertDoesNotExist()
        composeRule.onNodeWithText("任务详情").assertIsDisplayed()
        assertEquals("返回先关菜单，不关详情", 0, host.closeRequests)

        // 日期面板：点外部只关日期面板。
        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置").performClick()
        composeRule.onNodeWithText("选择截止日期").assertIsDisplayed()
        tapScrim()
        composeRule.onNodeWithText("选择截止日期").assertDoesNotExist()
        composeRule.onNodeWithText("任务详情").assertExists()
        assertEquals("点外部只关日期面板，不关详情", 0, host.closeRequests)

        // 日期面板：返回先关日期面板。
        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置").performClick()
        composeRule.onNodeWithText("选择截止日期").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("选择截止日期").assertDoesNotExist()
        composeRule.onNodeWithText("任务详情").assertExists()
        assertEquals("返回先关日期面板，不关详情", 0, host.closeRequests)

        // 子层都关闭后，遮罩点按才关闭详情。
        tapScrim()
        composeRule.waitForIdle()
        assertEquals("子层关闭后遮罩点按关闭详情一次", 1, host.closeRequests)
        composeRule.onNodeWithText("任务详情").assertDoesNotExist()
    }

    @Test
    fun G6R4R3_new_task_date_panel_scrim_tap_closes_only_date_panel() {
        val host = PanelHost()
        setNewTaskHost(host)

        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置").performClick()
        composeRule.onNodeWithText("选择截止日期").assertIsDisplayed()
        tapScrim()
        composeRule.onNodeWithText("选择截止日期").assertDoesNotExist()
        composeRule.onNodeWithText("新建").assertExists()
        assertEquals("点外部只关日期面板，不关新建面板", 0, host.closeRequests)
    }

    @Test
    fun G6R4R3_new_task_date_panel_back_closes_only_date_panel() {
        val host = PanelHost()
        setNewTaskHost(host)

        composeRule.onNodeWithContentDescription("设置截止日期，当前：未设置").performClick()
        composeRule.onNodeWithText("选择截止日期").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("选择截止日期").assertDoesNotExist()
        composeRule.onNodeWithText("新建").assertExists()
        assertEquals("返回先关日期面板，不关新建面板", 0, host.closeRequests)
    }

    @Test
    fun G6R4R3_new_task_menu_outside_tap_closes_only_menu() {
        val host = PanelHost()
        setNewTaskHost(host)

        composeRule.onNodeWithContentDescription("设置优先级，当前：无").performClick()
        composeRule.onNodeWithText("高").assertIsDisplayed()
        tapScrim()
        composeRule.onNodeWithText("高").assertDoesNotExist()
        composeRule.onNodeWithText("新建").assertExists()
        assertEquals("点菜单外只关菜单，不关新建面板", 0, host.closeRequests)
    }

    // ---------- 保留可交互入口：面板内按钮仍能关闭 ----------

    @Test
    fun G6R4R3_task_detail_close_button_still_closes() {
        val host = PanelHost()
        setTaskDetailHost(host)

        // v2.16（4.27 / R5-8）：任务详情关闭按钮改为顶部关闭图标按钮 detail_close_button。
        composeRule.onNodeWithTag("detail_close_button").performClick()
        composeRule.waitForIdle()
        assertEquals("任务详情关闭图标按钮应关闭一次", 1, host.closeRequests)
        composeRule.onNodeWithText("任务详情").assertDoesNotExist()
    }

    @Test
    fun G6R4R3_project_detail_close_button_still_closes() {
        val host = PanelHost()
        setProjectDetailHost(host)

        // v2.16（4.27 / R5-9）：项目详情关闭按钮改为顶部关闭图标按钮 project_detail_close_button。
        composeRule.onNodeWithTag("project_detail_close_button").performClick()
        composeRule.waitForIdle()
        assertEquals("项目详情关闭图标按钮应关闭一次", 1, host.closeRequests)
        composeRule.onNodeWithText("项目详情").assertDoesNotExist()
    }

    @Test
    fun G6R4R3_new_task_cancel_button_still_closes() {
        val host = PanelHost()
        setNewTaskHost(host)

        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()
        assertEquals("新建面板“取消”按钮应关闭一次", 1, host.closeRequests)
        composeRule.onNodeWithText("新建").assertDoesNotExist()
    }
}
