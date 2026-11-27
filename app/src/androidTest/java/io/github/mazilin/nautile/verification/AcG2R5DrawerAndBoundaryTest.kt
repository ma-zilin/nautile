package io.github.mazilin.nautile.verification

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.rule.TaskGroup
import io.github.mazilin.nautile.ui.screen.todo.GtdSelector
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
 * R5-G2 独立验收（模拟器 UI + 截图）：GTD 左抽屉 240–320dp 与主页面边界拉动方向锁定修复。
 *
 * 依据：《需求基线》v2.16（FR-G-15、FR-G-16、FR-G-18、BR-61、AC-G-04、AC-G-05、S-14）
 * 与 `.plans/设计/03_交互与动效规格.md`（§2.1、§2.3、§3、§4 G3）。
 *
 * 运行环境说明：在模拟器（非真机）执行；截图写入 Downloads/nautile_evidence/r5g2_*.png，
 * 由外部 adb pull 至 docs/验证记录/门禁/ 供人工逐张目视核对。
 */
@RunWith(AndroidJUnit4::class)
class AcG2R5DrawerAndBoundaryTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now: Instant = Instant.parse("2026-11-27T12:00:00Z")

    private fun dp(px: Float, density: Density): Float = px / density.density

    private fun task(id: Long, title: String, position: GtdPosition = GtdPosition.WAITING) = Task(
        id = TaskId(id),
        title = title,
        position = position,
        createdAt = now,
        updatedAt = now,
    )

    private fun shortGroups(position: GtdPosition): List<TaskGroup> = listOf(
        TaskGroup(
            project = null,
            tasks = listOf(
                task(1L, "整理实验室数据", position),
                task(2L, "复核需求基线", position),
            ),
        ),
    )

    private fun pressBack() {
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    /** 将当前组合根截图保存到媒体库 Downloads/nautile_evidence/r5g2_<name>.png。 */
    private fun captureEvidence(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val fileName = "r5g2_$name.png"
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/nautile_evidence"

        // 清理同名旧记录，避免 MediaStore 自动追加 (1) 后缀。
        resolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(fileName),
        )

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        requireNotNull(uri) { "无法创建截图输出：$fileName" }
        resolver.openOutputStream(uri)!!.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("截图应可读：$uri", resolver.openInputStream(uri) != null)
    }

    /**
     * AC-G-04 / FR-G-15：验证 `gtd_drawer` 宽度严格符合 min(max(maxTextWidth + 48dp, 240dp), 320dp)、
     * 全高贴左、遮罩 `gtd_scrim` 存在、五项完整显示且切换前后宽度不变、无“日历”。
     */
    @Test
    fun acG04_r5_drawer_width_240_to_320_full_height_left_aligned_and_scrim() {
        var current by mutableStateOf(GtdPosition.NEXT_ACTION)
        lateinit var density: Density
        lateinit var measurer: TextMeasurer
        lateinit var typography: Typography

        composeRule.setContent {
            NautileTheme(darkTheme = false) {
                density = LocalDensity.current
                measurer = rememberTextMeasurer()
                typography = MaterialTheme.typography
                Box(modifier = Modifier.fillMaxSize()) {
                    TodoScreen(
                        state = TodoUiState(
                            position = current,
                            groups = shortGroups(current),
                            visibleTasks = shortGroups(current).flatMap { it.tasks },
                            searchAvailable = true,
                            viewOptionsAvailable = true,
                        ),
                    )
                    GtdSelector(
                        currentPosition = current,
                        tasks = emptyList(),
                        onSelect = { current = it },
                        onClose = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val maxTextPx = GtdPosition.entries.maxOf { pos ->
            measurer.measure(
                text = AnnotatedString(pos.title),
                style = typography.labelLarge,
                constraints = Constraints(),
            ).size.width.toFloat()
        }
        val expectedWidthDp = (maxTextPx / density.density + 48f).coerceIn(240f, 320f)
        assertTrue("期望宽度应落在 240..320dp，实际 $expectedWidthDp", expectedWidthDp in 240f..320f)

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val drawerBounds = composeRule.onNodeWithTag("gtd_drawer").fetchSemanticsNode().boundsInRoot
        val scrimBounds = composeRule.onNodeWithTag("gtd_scrim").fetchSemanticsNode().boundsInRoot

        val drawerWidthDp = dp(drawerBounds.width, density)
        assertEquals(
            "gtd_drawer 宽度必须符合 min(max(maxTextWidth + 48dp, 240dp), 320dp)",
            expectedWidthDp,
            drawerWidthDp,
            1.0f,
        )
        assertEquals("gtd_drawer 左边必须贴屏幕左缘", 0f, dp(drawerBounds.left, density), 1.0f)
        assertEquals(
            "gtd_drawer 必须全高",
            dp(rootBounds.height, density),
            dp(drawerBounds.height, density),
            1.0f,
        )
        assertEquals(
            "遮罩宽度应覆盖全屏",
            dp(rootBounds.width, density),
            dp(scrimBounds.width, density),
            1.0f,
        )
        assertTrue(
            "抽屉右侧应露出遮罩区",
            drawerBounds.width < rootBounds.width,
        )

        // 截取浅色模式下抽屉打开态证据图。
        captureEvidence("drawer_open")

        // 逐项点按五项清单，验证宽度始终不变且无“日历”。
        composeRule.onNodeWithText("日历").assertDoesNotExist()
        GtdPosition.entries.forEach { pos ->
            composeRule.onNodeWithTag("gtd_item_${pos.name}").assertIsDisplayed().performClick()
            composeRule.waitForIdle()
            assertEquals("点按应切换到 ${pos.title}", pos, current)
            val widthNow = dp(
                composeRule.onNodeWithTag("gtd_drawer").fetchSemanticsNode().boundsInRoot.width,
                density,
            )
            assertEquals("切换清单后抽屉宽度不得抖动", drawerWidthDp, widthNow, 0.5f)
        }
    }

    /**
     * AC-G-04 / FR-G-16：抽屉打开时仅点按切换、禁用上下拉（不触发底层切换与提示）；
     * 关闭图标、点按遮罩、系统返回、抽屉左滑四条关闭路径全部生效且不改当前清单。
     */
    @Test
    fun acG04_r5_drawer_blocks_vertical_pull_and_supports_four_close_paths() {
        var current by mutableStateOf(GtdPosition.WAITING)
        var drawerOpen by mutableStateOf(true)
        var closeCount = 0
        var bgSwitches = 0

        composeRule.setContent {
            NautileTheme(darkTheme = false) {
                BackHandler(enabled = drawerOpen) {
                    closeCount += 1
                    drawerOpen = false
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    TodoScreen(
                        state = TodoUiState(
                            position = current,
                            groups = shortGroups(current),
                            visibleTasks = shortGroups(current).flatMap { it.tasks },
                        ),
                        onSwitchPosition = {
                            bgSwitches += 1
                            current = it
                        },
                    )
                    if (drawerOpen) {
                        GtdSelector(
                            currentPosition = current,
                            tasks = emptyList(),
                            onSelect = { current = it },
                            onClose = {
                                closeCount += 1
                                drawerOpen = false
                            },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        // 抽屉内与遮罩区上下拉：绝不切换清单、绝不出现边界切换提示。
        composeRule.onNodeWithTag("gtd_drawer").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("gtd_drawer").performTouchInput { swipeDown() }
        composeRule.onNodeWithTag("gtd_scrim").performTouchInput {
            swipe(
                start = Offset(width - 16f, height * 0.8f),
                end = Offset(width - 16f, height * 0.2f),
            )
        }
        composeRule.waitForIdle()
        assertEquals("抽屉打开时上下拉不得切换清单", GtdPosition.WAITING, current)
        assertEquals("抽屉打开时不得触发底层边界切换", 0, bgSwitches)
        assertTrue(
            "抽屉打开时不得出现边界拉动提示",
            composeRule.onAllNodesWithTag("boundary_pull_hint").fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "抽屉打开时不得出现“松手切换到”文案",
            composeRule.onAllNodesWithText("松手切换到", substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )

        // 路径 1：关闭图标。
        composeRule.onNodeWithContentDescription("关闭").performClick()
        composeRule.waitForIdle()
        assertFalse("关闭图标应关闭抽屉", drawerOpen)
        assertEquals(1, closeCount)

        // 路径 2：点按右侧遮罩。
        composeRule.runOnIdle { drawerOpen = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gtd_scrim").performTouchInput {
            click(Offset(width - 12f, height / 2f))
        }
        composeRule.waitForIdle()
        assertFalse("点按遮罩应关闭抽屉", drawerOpen)
        assertEquals(2, closeCount)

        // 路径 3：系统返回。
        composeRule.runOnIdle { drawerOpen = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gtd_drawer").assertIsDisplayed()
        pressBack()
        assertFalse("系统返回应关闭抽屉", drawerOpen)
        assertEquals(3, closeCount)

        // 路径 4：抽屉上左滑。
        composeRule.runOnIdle { drawerOpen = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gtd_drawer").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertFalse("抽屉上左滑应关闭抽屉", drawerOpen)
        assertEquals(4, closeCount)

        // 四次关闭均不得误切清单。
        assertEquals(GtdPosition.WAITING, current)
    }

    /**
     * AC-G-05 / FR-G-18 / BR-61（R5-2 核心 UI 回归）：
     * 短列表顶部下拉超阈值预览“松手切换到 下一步行动” → 不松手快速反向大幅上推越过 0 →
     * 提示立即消失、不转向“将来／也许”、松手不切换；再验证镜像方向（底部上拉后快速反向越过 0 下拉）。
     */
    @Test
    fun acG05_r5_short_list_reverse_past_zero_hides_hint_and_cancels_switch() {
        var current by mutableStateOf(GtdPosition.WAITING)
        var switched: GtdPosition? = null

        composeRule.setContent {
            NautileTheme(darkTheme = false) {
                TodoScreen(
                    state = TodoUiState(
                        position = current,
                        groups = shortGroups(current),
                        visibleTasks = shortGroups(current).flatMap { it.tasks },
                        searchAvailable = true,
                        viewOptionsAvailable = true,
                    ),
                    onSwitchPosition = {
                        switched = it
                        current = it
                    },
                )
            }
        }
        composeRule.waitForIdle()

        // 场景 A：短列表从顶部起手下拉 → 显示“松手切换到 下一步行动” → 截预览图 → 快速反向大幅上推越过 0。
        composeRule.onRoot().performTouchInput {
            down(Offset(centerX, height * 0.35f))
            repeat(18) { moveBy(Offset(0f, 30f)) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("boundary_pull_hint").assertIsDisplayed()
        composeRule.onNodeWithText("松手切换到 下一步行动").assertIsDisplayed()
        captureEvidence("boundary_preview")

        // 不松手反向大幅上推（远超 0，试图拉到“将来／也许”）：提示必须立即隐藏且松手不切换。
        composeRule.onRoot().performTouchInput {
            repeat(32) { moveBy(Offset(0f, -35f)) }
        }
        composeRule.waitForIdle()
        assertTrue(
            "反向越过 0 后 boundary_pull_hint 必须立即消失",
            composeRule.onAllNodesWithTag("boundary_pull_hint").fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "反向越过 0 后绝不可切换提示为“松手切换到 将来／也许”",
            composeRule.onAllNodesWithText("松手切换到", substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertNull("短列表下拉后反向越过 0 松手绝不应切换清单", switched)
            assertEquals(GtdPosition.WAITING, current)
        }

        // 场景 B（镜像）：短列表从底部起手上拉 → 显示“松手切换到 将来／也许” → 快速反向大幅下拉越过 0 → 松手不切换。
        composeRule.onRoot().performTouchInput {
            down(Offset(centerX, height * 0.65f))
            repeat(18) { moveBy(Offset(0f, -30f)) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("松手切换到 将来／也许").assertIsDisplayed()

        composeRule.onRoot().performTouchInput {
            repeat(32) { moveBy(Offset(0f, 35f)) }
        }
        composeRule.waitForIdle()
        assertTrue(
            "镜像反向越过 0 后提示必须消失",
            composeRule.onAllNodesWithTag("boundary_pull_hint").fetchSemanticsNodes().isEmpty(),
        )
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertNull("镜像反向越过 0 松手绝不应切换清单", switched)
            assertEquals(GtdPosition.WAITING, current)
        }
    }

    /**
     * AC-G-05 / FR-G-18 / BR-61：主页面五位置连续拉拽稳定切换（INBOX → ENDED 再回到 INBOX），
     * 并在首尾边界验证“已是第一个清单”与“已是最后一个清单”提示不循环，留存首尾截图证据。
     */
    @Test
    fun acG05_r5_five_positions_consecutive_pulls_and_edge_hints() {
        var current by mutableStateOf(GtdPosition.INBOX)
        val history = mutableListOf(current)

        composeRule.setContent {
            NautileTheme(darkTheme = false) {
                TodoScreen(
                    state = TodoUiState(
                        position = current,
                        groups = shortGroups(current),
                        visibleTasks = shortGroups(current).flatMap { it.tasks },
                        searchAvailable = true,
                        viewOptionsAvailable = true,
                    ),
                    onSwitchPosition = {
                        current = it
                        history += it
                    },
                )
            }
        }
        composeRule.waitForIdle()

        // 1) 首位（收件箱）下拉：提示“已是第一个清单”并截图，松手不切换。
        composeRule.onRoot().performTouchInput {
            down(Offset(centerX, height * 0.35f))
            repeat(30) { moveBy(Offset(0f, 30f)) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("boundary_pull_hint").assertIsDisplayed()
        composeRule.onNodeWithText("已是第一个清单").assertIsDisplayed()
        captureEvidence("first_boundary_hint")
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals("首位下拉不得切换", GtdPosition.INBOX, current)

        // 2) 连续 4 次上拉走完五位置：INBOX -> NEXT_ACTION -> WAITING -> SOMEDAY -> ENDED。
        repeat(4) {
            composeRule.onRoot().performTouchInput {
                swipeUp(startY = height * 0.8f, endY = height * 0.2f)
            }
            composeRule.waitForIdle()
        }
        assertEquals("连续 4 次上拉应到达已结束", GtdPosition.ENDED, current)

        // 3) 末位（已结束）继续上拉：提示“已是最后一个清单”并截图，松手不切换。
        composeRule.onRoot().performTouchInput {
            down(Offset(centerX, height * 0.7f))
            repeat(30) { moveBy(Offset(0f, -30f)) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("boundary_pull_hint").assertIsDisplayed()
        composeRule.onNodeWithText("已是最后一个清单").assertIsDisplayed()
        captureEvidence("last_boundary_hint")
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals("末位上拉不得切换", GtdPosition.ENDED, current)

        // 4) 连续 4 次下拉回到收件箱：ENDED -> SOMEDAY -> WAITING -> NEXT_ACTION -> INBOX。
        repeat(4) {
            composeRule.onRoot().performTouchInput {
                swipeDown(startY = height * 0.25f, endY = height * 0.85f)
            }
            composeRule.waitForIdle()
        }
        assertEquals(
            "五位置连续上下拉轨迹应精确按序往返，无跳过无循环",
            listOf(
                GtdPosition.INBOX,
                GtdPosition.NEXT_ACTION,
                GtdPosition.WAITING,
                GtdPosition.SOMEDAY,
                GtdPosition.ENDED,
                GtdPosition.SOMEDAY,
                GtdPosition.WAITING,
                GtdPosition.NEXT_ACTION,
                GtdPosition.INBOX,
            ),
            history,
        )
    }
}
