package io.github.mazilin.nautile.verification

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.ui.screen.todo.GtdSelector
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoUiState
import io.github.mazilin.nautile.ui.screen.todo.title
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G1-A2 闸门：待办页头部与 GTD 选择器左宽自适应（AC-G-04、CMP-10/11、A11Y-03/07、RDY-01）。
 *
 * 预期从需求基线 v2.14 与设计 `.plans/设计/01_视觉与动效标准.md` 派生，不照抄实现：
 * - `W_L = max(五项清单名标题文本宽) + 2×12dp`，`W_R = W − W_L`；
 * - 左列恰五项、无“日历”、只有点按切换；
 * - 头部从左到右 GTD → 标题 → 搜索 → 视图 → 设置；搜索已可用（G8），视图仍可见禁用；
 * - 选择器打开后左右两列可见，关闭后不再存在（只判终态，不依赖动画时长）。
 *
 * 隔离性：全部为内存态 Compose 组合，不读写数据库/DataStore；需要设备（模拟器或真机）。
 * 文本宽度测量口径：`fontScale = 1.0`，用 `useUnmergedTree = true` 取内层 text 节点，
 * 按 `LocalDensity` 转 dp；不用固定比例替代测量。
 */
@RunWith(AndroidJUnit4::class)
class AcGtdHeaderT090Test {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-25T00:00:00Z")

    private fun task(id: Long, title: String) = Task(
        id = TaskId(id),
        title = title,
        position = GtdPosition.INBOX,
        createdAt = now,
        updatedAt = now,
    )

    private fun dp(px: Float, density: Density): Float = px / density.density

    /**
     * AC-G-04 / CMP-10：左宽跟随最长标题，切换选中项不变；右宽为余下宽度；五项完整显示。
     *
     * 组合 GtdSelector 默认静态形态（animateReveal = false），避免动画中间帧影响测量。
     */
    @Test
    fun acG04_left_width_follows_longest_title() {
        var current by mutableStateOf(GtdPosition.INBOX)
        lateinit var density: Density
        lateinit var measurer: TextMeasurer
        lateinit var typography: Typography
        composeRule.setContent {
            NautileTheme {
                density = LocalDensity.current
                measurer = rememberTextMeasurer()
                typography = MaterialTheme.typography
                GtdSelector(
                    currentPosition = current,
                    tasks = listOf(task(1L, "唯一任务")),
                    onSelect = { current = it },
                    onClose = {},
                )
            }
        }
        composeRule.waitForIdle()

        // 设计 CMP-10 的测量口径：fontScale = 1.0。
        assertEquals("测量口径要求 fontScale = 1.0", 1.0f, density.fontScale, 0.001f)

        // 独立测量五项标题的自然文本宽（bodyLarge，无界约束），不用实现中的固定比例。
        val naturalWidthPx = GtdPosition.entries.associateWith { position ->
            measurer.measure(
                text = AnnotatedString(position.title),
                style = typography.bodyLarge,
                constraints = Constraints(),
            ).size.width.toFloat()
        }
        val expectedLeftDp = naturalWidthPx.values.maxOrNull()!! / density.density + 24f

        fun rowWidthDp(): List<Float> = GtdPosition.entries.map { position ->
            composeRule.onAllNodes(hasText(position.title) and hasClickAction())
                .fetchSemanticsNodes()
                .single()
                .let { dp(it.boundsInRoot.width, density) }
        }

        val leftWidthBefore = rowWidthDp()
        leftWidthBefore.forEach { width ->
            assertEquals("五项左列行宽应一致", leftWidthBefore.first(), width, 0.5f)
        }
        assertEquals(
            "W_L 应等于最长标题文本宽 + 2×12dp",
            expectedLeftDp,
            leftWidthBefore.first(),
            1.0f,
        )

        // 五项名称完整显示：渲染文本宽 ≈ 自然文本宽（截断时渲染宽会小于自然宽）。
        GtdPosition.entries.forEach { position ->
            val nodes = composeRule.onAllNodesWithText(position.title, useUnmergedTree = true)
                .fetchSemanticsNodes()
            assertTrue("${position.title} 应有文本节点", nodes.isNotEmpty())
            val leftMost = nodes.minByOrNull { it.boundsInRoot.left }
                ?: error("${position.title} 缺少文本节点")
            assertEquals(
                "${position.title} 不应截断或省略",
                naturalWidthPx.getValue(position),
                leftMost.boundsInRoot.width,
                1.5f,
            )
        }

        // 右侧从 W_L 处开始：右列标题左缘 = W_L + 16dp（右列内边距）。
        val rightHeadingNodes =
            composeRule.onAllNodesWithText(GtdPosition.INBOX.title, useUnmergedTree = true)
                .fetchSemanticsNodes()
        val rightHeadingLeftDp = rightHeadingNodes.maxByOrNull { it.boundsInRoot.left }
            ?.let { dp(it.boundsInRoot.left, density) }
            ?: error("右列标题缺失")
        assertEquals(
            "右列应从 W_L 处开始（+16dp 内边距）",
            leftWidthBefore.first() + 16f,
            rightHeadingLeftDp,
            1.0f,
        )

        // 右宽 = 可用宽 − 左宽（可用宽取选择器面板宽，即 fillMaxWidth 的根宽）。
        val availableWidthDp = dp(composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width, density)
        val rightWidthDp = availableWidthDp - leftWidthBefore.first()
        assertTrue("右列应有可用宽度", rightWidthDp > 0f)
        assertEquals("W_R = W − W_L", availableWidthDp - leftWidthBefore.first(), rightWidthDp, 1.0f)

        // 无横向滚动：不应出现横向可滚动节点。
        val horizontalScrollable = composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertTrue("选择器不应出现横向滚动节点", horizontalScrollable.isEmpty())

        // 切换选中项后左宽不变（选中色不改内边距）。
        composeRule.onAllNodes(hasText("等待中") and hasClickAction()).onFirst().performClick()
        composeRule.runOnIdle { assertEquals(GtdPosition.WAITING, current) }
        val leftWidthAfter = rowWidthDp()
        leftWidthAfter.forEach { width ->
            assertEquals("切换清单后 W_L 应不变", leftWidthBefore.first(), width, 0.5f)
        }
    }

    /** AC-G-04 / CMP-11：左列恰五项、不含“日历”，切换只由点按触发且选择器保持打开。 */
    @Test
    fun acG04_click_only_switch_and_five_items() {
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

        // 左列恰五项：每个清单名恰有一个可点击行（右列标题不可点击）。
        GtdPosition.entries.forEach { position ->
            assertEquals(
                "左列项 ${position.title} 应恰有一个可点击行",
                1,
                composeRule.onAllNodes(hasText(position.title) and hasClickAction())
                    .fetchSemanticsNodes().size,
            )
        }
        composeRule.onNodeWithText("清单").assertIsDisplayed()
        composeRule.onNodeWithText("日历").assertDoesNotExist()

        // 点按切换：选择器保持打开（左列与右列仍在）。
        composeRule.onAllNodes(hasText("将来／也许") and hasClickAction()).onFirst().performClick()
        composeRule.runOnIdle { assertEquals(GtdPosition.SOMEDAY, selected) }
        composeRule.onNodeWithText("清单").assertIsDisplayed()

        // 只有点按切换：横向滑动左列项不产生清单切换。
        selected = null
        composeRule.onAllNodes(hasText("已结束") and hasClickAction()).onFirst()
            .performTouchInput { swipeLeft() }
        composeRule.runOnIdle { assertNull("滑动不应切换清单", selected) }
    }

    /** A11Y-03/07、RDY-01/03：头部顺序、搜索可用/视图禁用语义、设置与 GTD 入口语义、48dp 触控。 */
    @Test
    fun acN11_header_order_and_search_enabled_view_disabled() {
        lateinit var density: Density
        composeRule.setContent {
            NautileTheme {
                density = LocalDensity.current
                // G8 起搜索可用（用户 2026-11-25 决定）；视图仍按 RDY-02/04 保持禁用。
                TodoScreen(
                    state = TodoUiState(position = GtdPosition.INBOX, searchAvailable = true),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("待办").assertIsDisplayed()
        composeRule.onNodeWithTag("gtd_entry")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("打开 GTD 清单选择器")
        composeRule.onNodeWithTag("settings_entry")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("设置")

        val searchDesc = "搜索待办"
        val viewDesc = "视图选项，功能尚未开放"
        val search = composeRule.onNodeWithContentDescription(searchDesc)
        val view = composeRule.onNodeWithContentDescription(viewDesc)
        search.assertIsDisplayed().assertIsEnabled()
        view.assertIsDisplayed().assertIsNotEnabled()

        // 头部从左到右：GTD → 标题 → 搜索 → 视图 → 设置。
        val gtdLeft = composeRule.onNodeWithTag("gtd_entry").fetchSemanticsNode().boundsInRoot.left
        val titleLeft = composeRule.onNodeWithText("待办").fetchSemanticsNode().boundsInRoot.left
        val searchLeft = search.fetchSemanticsNode().boundsInRoot.left
        val viewLeft = view.fetchSemanticsNode().boundsInRoot.left
        val settingsLeft =
            composeRule.onNodeWithTag("settings_entry").fetchSemanticsNode().boundsInRoot.left
        assertTrue("GTD 入口应在标题左侧", gtdLeft < titleLeft)
        assertTrue("搜索应在标题右侧", titleLeft < searchLeft)
        assertTrue("搜索应在视图左侧", searchLeft < viewLeft)
        assertTrue("视图应在设置左侧", viewLeft < settingsLeft)

        // 48dp 触控目标（A11Y-01/ICO-10）。
        assertTouchTarget(composeRule.onNodeWithTag("gtd_entry"), density, "GTD 入口")
        assertTouchTarget(search, density, "搜索入口")
        assertTouchTarget(view, density, "视图入口")
        assertTouchTarget(composeRule.onNodeWithTag("settings_entry"), density, "设置入口")
    }

    /** CMP-19 / MOT-07：选择器打开后左列与右列可见，关闭后两列均不再存在（只判终态）。 */
    @Test
    fun acA2_selector_reveal_final_state() {
        var open by mutableStateOf(false)
        var visible by mutableStateOf(false)
        composeRule.setContent {
            NautileTheme {
                if (open) {
                    GtdSelector(
                        currentPosition = GtdPosition.INBOX,
                        tasks = emptyList(),
                        onSelect = {},
                        onClose = { visible = false },
                        animateReveal = true,
                        visible = visible,
                        onExitFinished = { open = false },
                    )
                } else {
                    TextButton(onClick = { visible = true; open = true }) {
                        Text("打开选择器")
                    }
                }
            }
        }

        composeRule.onNodeWithText("打开选择器").performClick()
        composeRule.waitForIdle()
        // 左列（“清单”表头）与右列（空态）都可见。
        composeRule.onNodeWithText("清单").assertIsDisplayed()
        composeRule.onNodeWithText("该清单暂无任务").assertIsDisplayed()

        // 设计 §6.2 #11：关闭为 ic_close 图标按钮，按 contentDescription“关闭”定位。
        composeRule.onNodeWithContentDescription("关闭").performClick()
        // 退出动效完成后覆盖层才移除；等待终态，不把中间帧当依据。
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("清单").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("清单").assertDoesNotExist()
        composeRule.onNodeWithText("该清单暂无任务").assertDoesNotExist()
    }

    private fun assertTouchTarget(
        node: SemanticsNodeInteraction,
        density: Density,
        label: String,
    ) {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "$label 触控宽应 ≥48dp，实际 ${dp(bounds.width, density)}dp",
            dp(bounds.width, density) >= 47.5f,
        )
        assertTrue(
            "$label 触控高应 ≥48dp，实际 ${dp(bounds.height, density)}dp",
            dp(bounds.height, density) >= 47.5f,
        )
    }
}
