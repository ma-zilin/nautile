package io.github.mazilin.nautile.verification

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.ui.screen.todo.GtdSelector
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoUiState
import io.github.mazilin.nautile.ui.screen.todo.title
import io.github.mazilin.nautile.ui.theme.NautileTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R4 G3 闸门：GTD 左侧全高抽屉（AC-G-04、FR-G-15/16、CMP-10—13、MOT-07）。
 *
 * 预期从需求基线 v2.16（4.26）与设计 `.plans/设计/03_交互与动效规格.md` 派生，不照抄实现：
 * - `W_D = min(max(五项清单名标题最长文本宽 + 2×24dp, 240dp), 320dp)`，测量口径为 `labelLarge`、`fontScale = 1.0`；
 * - 抽屉全高、贴左、右侧遮罩；五项完整显示；切换前后宽度不变；切换后不自动关闭；
 * - 抽屉内只有点按切换，上下拉不切换、不出现“松手切换到 …”；
 * - 关闭路径：关闭图标、点按遮罩、抽屉上左滑（系统返回由 JVM 返回栈测试覆盖）。
 *
 * 隔离性：全部为内存态 Compose 组合，不读写数据库/DataStore；需要设备（模拟器或真机）。
 */
@RunWith(AndroidJUnit4::class)
class AcGtdHeaderT090Test {

    @get:Rule
    val composeRule = createComposeRule()

    private fun dp(px: Float, density: Density): Float = px / density.density

    /** 未合并语义树中遍历全部节点。 */
    private fun walk(node: SemanticsNode): List<SemanticsNode> =
        listOf(node) + node.children.flatMap { walk(it) }

    /**
     * 找到抽屉 Surface：左边缘贴屏幕、宽度等于公式值、高度等于根高（全高）。
     *
     * Material3 非点击 `Surface` 会生成一个无附加语义的节点，可用于取抽屉几何；
     * 用未合并树避免被合并语义掩盖。
     */
    private fun findDrawer(
        density: Density,
        expectedWidthDp: Float,
        rootHeightDp: Float,
    ): SemanticsNode {
        val root = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        return walk(root).firstOrNull { node ->
            val bounds = node.boundsInRoot
            val widthDp = dp(bounds.width, density)
            val heightDp = dp(bounds.height, density)
            val leftDp = dp(bounds.left, density)
            abs(leftDp) <= 1f &&
                abs(widthDp - expectedWidthDp) <= 1f &&
                abs(heightDp - rootHeightDp) <= 1f
        } ?: error(
            "找不到全高贴左抽屉：期望宽 ${expectedWidthDp}dp、高 ${rootHeightDp}dp；" +
                "实际节点\n" + walk(root).joinToString("\n") {
                    "w=${dp(it.boundsInRoot.width, density)} h=${dp(it.boundsInRoot.height, density)} " +
                        "l=${dp(it.boundsInRoot.left, density)} text=${it.config.getOrNull(SemanticsProperties.Text)}"
                },
        )
    }

    /**
     * AC-G-04 / CMP-10：抽屉宽度严格按公式（±1dp），全高、贴左；五项不截断；切换后宽度不变。
     */
    @Test
    fun acG04_drawer_width_formula_full_height_and_left_aligned() {
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
                    tasks = emptyList(),
                    onSelect = { current = it },
                    onClose = {},
                )
            }
        }
        composeRule.waitForIdle()

        // CMP-10 测量口径：labelLarge、fontScale = 1.0。
        assertEquals("测量口径要求 fontScale = 1.0", 1.0f, density.fontScale, 0.001f)

        val naturalWidthPx = GtdPosition.entries.associateWith { position ->
            measurer.measure(
                text = AnnotatedString(position.title),
                style = typography.labelLarge,
                constraints = Constraints(),
            ).size.width.toFloat()
        }
        val expectedWidthDp =
            (naturalWidthPx.values.max() / density.density + 48f).coerceIn(240f, 320f)
        assertTrue(
            "公式结果应落在 240—320dp，实际 $expectedWidthDp",
            expectedWidthDp in 240f..320f,
        )

        val rootHeightDp = dp(
            composeRule.onRoot().fetchSemanticsNode().boundsInRoot.height,
            density,
        )
        val drawer = findDrawer(density, expectedWidthDp, rootHeightDp)
        assertEquals(
            "抽屉宽度应等于 min(max(最长标题 + 48dp, 240dp), 320dp)",
            expectedWidthDp,
            dp(drawer.boundsInRoot.width, density),
            1.0f,
        )
        assertEquals(
            "抽屉左边缘应贴屏幕左边缘",
            0f,
            dp(drawer.boundsInRoot.left, density),
            1.0f,
        )
        assertEquals(
            "抽屉应全高",
            rootHeightDp,
            dp(drawer.boundsInRoot.height, density),
            1.0f,
        )
        assertTrue(
            "抽屉右侧应留出遮罩区域（抽屉窄于屏幕）",
            dp(drawer.boundsInRoot.width, density) <
                dp(composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width, density),
        )

        fun rowWidthsDp(): List<Float> = GtdPosition.entries.map { position ->
            val node = composeRule.onAllNodes(hasText(position.title) and hasClickAction())
                .fetchSemanticsNodes()
                .single()
            dp(node.boundsInRoot.width, density)
        }
        val widthBefore = rowWidthsDp()
        widthBefore.forEach { width ->
            assertEquals("五项行宽应一致", widthBefore.first(), width, 0.5f)
        }
        assertEquals("行宽应等于抽屉宽", expectedWidthDp, widthBefore.first(), 1.0f)

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

        // 切换清单后宽度不变（选中色不改内边距）。
        composeRule.onAllNodes(hasText("等待中") and hasClickAction()).onFirst().performClick()
        composeRule.runOnIdle { assertEquals(GtdPosition.WAITING, current) }
        val widthAfter = rowWidthsDp()
        widthAfter.forEach { width ->
            assertEquals("切换清单后抽屉宽度应不变", widthBefore.first(), width, 0.5f)
        }

        // 无横向滚动：抽屉内不出现横向滚动节点。
        val horizontalScrollable = composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertTrue("抽屉不应出现横向滚动节点", horizontalScrollable.isEmpty())

        // 五项恰为五个清单，无“日历”。
        composeRule.onNodeWithText("日历").assertDoesNotExist()
    }

    /** AC-G-04 / CMP-11/12：五项、只有点按切换；抽屉内上下拉不切换且不出现“松手切换到 …”。 */
    @Test
    fun acG04_click_only_switch_and_drawer_vertical_pull_does_not_switch() {
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

        // 恰五项，每项一个可点击行；无“日历”。
        GtdPosition.entries.forEach { position ->
            assertEquals(
                "清单项 ${position.title} 应恰有一个可点击行",
                1,
                composeRule.onAllNodes(hasText(position.title) and hasClickAction())
                    .fetchSemanticsNodes().size,
            )
        }
        composeRule.onNodeWithText("清单").assertIsDisplayed()
        composeRule.onNodeWithText("日历").assertDoesNotExist()

        // 抽屉内上下拉：不切换、不出现“松手切换到 …”。
        val waitingRow = {
            composeRule.onAllNodes(hasText("等待中") and hasClickAction()).onFirst()
        }
        waitingRow().performTouchInput { swipeUp() }
        waitingRow().performTouchInput { swipeDown() }
        composeRule.runOnIdle {
            assertNull("抽屉内上下拉不应切换清单", selected)
            assertEquals("抽屉内上下拉不应关闭抽屉", 0, closed)
        }
        assertTrue(
            "抽屉打开时不应出现“松手切换到 …”",
            composeRule.onAllNodesWithText("松手切换到", substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )

        // 右滑不切换清单，也不关闭抽屉（只有左滑关闭）。
        waitingRow().performTouchInput { swipeRight() }
        composeRule.runOnIdle {
            assertNull("抽屉内右滑不应切换清单", selected)
            assertEquals("右滑不应关闭抽屉", 0, closed)
        }

        // 点按切换：保持打开且不关闭。
        composeRule.onAllNodes(hasText("将来／也许") and hasClickAction()).onFirst().performClick()
        composeRule.runOnIdle {
            assertEquals(GtdPosition.SOMEDAY, selected)
            assertEquals("点按切换不应关闭抽屉", 0, closed)
        }
        composeRule.onNodeWithText("清单").assertIsDisplayed()
    }

    /** AC-G-04 / CMP-13：关闭图标、点按遮罩、抽屉上左滑三条 UI 关闭路径各自生效。 */
    @Test
    fun acG04_close_icon_scrim_tap_and_drawer_left_swipe_close() {
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

        // 1) 关闭图标。
        composeRule.onNodeWithContentDescription("关闭").performClick()
        composeRule.runOnIdle { assertEquals(1, closed) }

        // 2) 点按右侧遮罩（抽屉之外）。
        composeRule.onRoot().performTouchInput {
            click(Offset(width - 8f, height / 2f))
        }
        composeRule.runOnIdle { assertEquals("点按遮罩应关闭抽屉", 2, closed) }

        // 3) 抽屉上左滑（复用 48dp 滑动阈值）。
        composeRule.onAllNodes(hasText("等待中") and hasClickAction()).onFirst()
            .performTouchInput { swipeLeft() }
        composeRule.runOnIdle {
            assertEquals("抽屉上左滑应关闭抽屉", 3, closed)
            assertNull("关闭不应切换清单", selected)
        }
    }

    /** AC-N-11 / A11Y-03/07：头部顺序、搜索与视图可用、设置与 GTD 入口语义。 */
    @Test
    fun acN11_header_order_and_search_and_view_enabled() {
        lateinit var density: Density
        composeRule.setContent {
            NautileTheme {
                density = LocalDensity.current
                TodoScreen(
                    state = TodoUiState(
                        position = GtdPosition.INBOX,
                        searchAvailable = true,
                        viewOptionsAvailable = true,
                    ),
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

        val search = composeRule.onNodeWithContentDescription("搜索待办")
        val view = composeRule.onNodeWithContentDescription("视图选项")
        search.assertIsDisplayed().assertIsEnabled()
        view.assertIsDisplayed().assertIsEnabled()

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

        assertTouchTarget(composeRule.onNodeWithTag("gtd_entry"), density, "GTD 入口")
        assertTouchTarget(search, density, "搜索入口")
        assertTouchTarget(view, density, "视图入口")
        assertTouchTarget(composeRule.onNodeWithTag("settings_entry"), density, "设置入口")
    }

    /** CMP-19 / MOT-07：抽屉打开后五项可见，关闭后不再存在（只判终态，不依赖动画时长）。 */
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
        composeRule.onNodeWithText("清单").assertIsDisplayed()
        GtdPosition.entries.forEach { position ->
            composeRule.onAllNodes(hasText(position.title) and hasClickAction())
                .onFirst().assertIsDisplayed()
        }

        // 设计 §6.2 #11：关闭为 ic_close 图标按钮，按 contentDescription“关闭”定位。
        composeRule.onNodeWithContentDescription("关闭").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("清单").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("清单").assertDoesNotExist()
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
