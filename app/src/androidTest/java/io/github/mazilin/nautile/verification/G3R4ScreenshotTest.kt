package io.github.mazilin.nautile.verification

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.rule.TaskGroup
import io.github.mazilin.nautile.ui.screen.todo.GtdSelector
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoUiState
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R4 G3 闸门截图（不提交）：GTD 左抽屉打开态、主页面首尾提示与切换中间态。
 *
 * 由 adb pull 到 `docs/验证记录/门禁/`；截图只作人工视觉核对，不替代 connected 断言。
 * 夹具为内存态，不代表真实数据库状态。
 */
@RunWith(AndroidJUnit4::class)
class G3R4ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-11-26T12:00:00Z")

    private fun task(id: Long, title: String) = Task(
        id = TaskId(id),
        title = title,
        position = GtdPosition.NEXT_ACTION,
        createdAt = now,
        updatedAt = now,
    )

    private val groups = listOf(
        TaskGroup(null, listOf(task(1L, "概率论刷题"), task(2L, "买牛奶"))),
    )

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "g3r4_$name.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/nautile_evidence",
            )
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        requireNotNull(uri) { "无法创建截图输出：$name" }
        resolver.openOutputStream(uri)!!.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("截图应写入：$uri", resolver.openInputStream(uri) != null)
    }

    /** 抽屉打开：当前项选中、右侧 32% 遮罩覆盖在主待办页上。 */
    @Test
    fun g3r4_drawer_open() {
        composeRule.setContent {
            NautileTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    TodoScreen(
                        state = TodoUiState(
                            position = GtdPosition.NEXT_ACTION,
                            groups = groups,
                            visibleTasks = groups.flatMap { it.tasks },
                            searchAvailable = true,
                            viewOptionsAvailable = true,
                        ),
                    )
                    GtdSelector(
                        currentPosition = GtdPosition.NEXT_ACTION,
                        tasks = emptyList(),
                        onSelect = {},
                        onClose = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("清单").assertIsDisplayed()
        // 头部副标题与抽屉选中项都叫“下一步行动”，因此按多节点取第一个。
        composeRule.onAllNodesWithText("下一步行动").onFirst().assertIsDisplayed()
        capture("drawer_open")
    }

    /** 主页面首位继续下拉：中间态显示“已是第一个清单”。 */
    @Test
    fun g3r4_first_position_hint() {
        composeRule.setContent {
            NautileTheme {
                TodoScreen(state = TodoUiState(position = GtdPosition.INBOX))
            }
        }
        composeRule.onRoot().performTouchInput {
            down(center)
            repeat(30) { moveBy(Offset(0f, 30f)) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("已是第一个清单").assertIsDisplayed()
        capture("first_hint")
        composeRule.onRoot().performTouchInput { up() }
    }

    /** 主页面末位继续上拉：中间态显示“已是最后一个清单”。 */
    @Test
    fun g3r4_last_position_hint() {
        composeRule.setContent {
            NautileTheme {
                TodoScreen(state = TodoUiState(position = GtdPosition.ENDED))
            }
        }
        composeRule.onRoot().performTouchInput {
            down(center)
            repeat(30) { moveBy(Offset(0f, -30f)) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("已是最后一个清单").assertIsDisplayed()
        capture("last_hint")
        composeRule.onRoot().performTouchInput { up() }
    }

    /** 主页面中间清单拉动：中间态显示“松手切换到 下一步行动”。 */
    @Test
    fun g3r4_switch_preview() {
        composeRule.setContent {
            NautileTheme {
                TodoScreen(state = TodoUiState(position = GtdPosition.INBOX))
            }
        }
        composeRule.onRoot().performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, -30f)) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("松手切换到 下一步行动").assertIsDisplayed()
        capture("switch_preview")
        composeRule.onRoot().performTouchInput { up() }
    }
}
