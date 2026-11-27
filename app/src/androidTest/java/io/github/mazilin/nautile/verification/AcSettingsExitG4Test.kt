package io.github.mazilin.nautile.verification

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.interaction.AppTab
import io.github.mazilin.nautile.interaction.FullScreenPage
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.OverlayBackStack
import io.github.mazilin.nautile.ui.AppRoot
import io.github.mazilin.nautile.ui.theme.NautileTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G4 闸门：设置页 pop 约 200ms，页面保留到动画结束再移除（MOT-09.2、CMP-29、AC-N-21）。
 *
 * 预期依据（用户 2026-11-25 确认）：pop 位移用 NautileMotion.StandardSpatial（200ms），
 * 不能先移除再动画；返回栈语义不变；预测性返回开关不影响直接返回结果。
 * 测试用主时钟 autoAdvance=false 精确停在动画中段。
 *
 * 系统预测返回手势（松手前预览）与“不叠加自绘 pop”的真机行为无法用公开 API 断言，
 * 列人工/未自动化项。需要设备/模拟器；本轮在 emulator-5554（API 36，非真机）执行。
 */
@RunWith(AndroidJUnit4::class)
class AcSettingsExitG4Test {

    @get:Rule
    val composeRule = createComposeRule()

    private val navigation = NavigationCoordinator()
    private val overlays = OverlayBackStack()

    private fun setHost(predictiveBackEnabled: Boolean = false) {
        composeRule.setContent {
            NautileTheme {
                AppRoot(
                    navigation = navigation,
                    overlays = overlays,
                    tabContent = { tab ->
                        Text(
                            text = "一级页:${tab.title}",
                            modifier = Modifier.testTag("tab_content"),
                        )
                    },
                    fullScreenContent = { page ->
                        Text(
                            text = "全屏:${page.title}",
                            modifier = Modifier.testTag("settings_page"),
                        )
                    },
                    predictiveBackEnabled = predictiveBackEnabled,
                )
            }
        }
    }

    @Test
    fun G4_MOT09_2_settings_pop_keeps_page_until_animation_ends() {
        composeRule.mainClock.autoAdvance = false
        setHost()

        composeRule.runOnIdle { navigation.navigateToSettings() }
        // 覆盖 push 的 Expressive spring（约 400—500ms），停在设置页静止态。
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithTag("settings_page").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(FullScreenPage.Settings, navigation.currentFullScreenPage)
        }

        composeRule.runOnIdle { navigation.back() }
        composeRule.runOnIdle {
            assertNull("返回栈应立即回到一级页（动效只影响呈现）", navigation.currentFullScreenPage)
        }

        // 约 200ms 的 pop：100ms 时页面仍须保留在组合中。
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithTag("settings_page").assertExists()
        val midBounds = composeRule.onNodeWithTag("settings_page").fetchSemanticsNode().boundsInRoot
        println("G4 settings pop mid-frame bounds=$midBounds")

        // 动画结束后才移除节点，一级页恢复可见。
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_page").assertDoesNotExist()
        composeRule.onNodeWithTag("tab_content").assertIsDisplayed()
    }

    @Test
    fun G4_AC_N21_predictive_back_enabled_back_still_pops_with_same_stack() {
        composeRule.mainClock.autoAdvance = false
        setHost(predictiveBackEnabled = true)

        composeRule.runOnIdle { navigation.navigateToSettings() }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag("settings_page").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf(FullScreenPage.Settings), navigation.fullScreenStack.value)
        }

        // 预测返回开关开启时，普通返回结果与返回栈语义不变：pop 一格，不残留重复项。
        composeRule.runOnIdle { navigation.back() }
        composeRule.runOnIdle {
            assertEquals(emptyList<FullScreenPage>(), navigation.fullScreenStack.value)
        }

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithTag("settings_page").assertExists()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_page").assertDoesNotExist()
        composeRule.onNodeWithTag("tab_content").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(AppTab.TODO, navigation.activeTab.value) }
    }

    /**
     * 在 pop 中段（100ms）截取设备画面，作为“页面保留到动画结束”的视觉证据。
     * 通过 MediaStore 写入公共 Pictures（应用自建媒体条目，无需额外权限），
     * 取证时 adb pull 到 docs/验证记录/门禁（不提交）。
     */
    @Test
    fun G4_MOT09_2_settings_pop_mid_frame_screenshot() {
        composeRule.mainClock.autoAdvance = false
        setHost()

        composeRule.runOnIdle { navigation.navigateToSettings() }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag("settings_page").assertIsDisplayed()

        composeRule.runOnIdle { navigation.back() }
        composeRule.mainClock.advanceTimeBy(100)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot()
        // G6R4 修复：固定 DISPLAY_NAME 重复插入时 MediaStore 会自动改名，adb pull 旧名会取到过期图。
        // 改为唯一名（时间戳），保证重复执行幂等、取证时按本次输出名拉取。
        val fileName = "g4_settings_pop_mid_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/nautile_g4",
            )
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        )
        assertTrue("中段截图应插入 MediaStore", uri != null)
        context.contentResolver.openOutputStream(uri!!)!!.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        println("G4 settings mid frame name=$fileName uri=$uri")

        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_page").assertDoesNotExist()
    }
}
