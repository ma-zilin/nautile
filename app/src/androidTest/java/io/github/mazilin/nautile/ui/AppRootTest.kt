package io.github.mazilin.nautile.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.interaction.AppTab
import io.github.mazilin.nautile.interaction.BackDispatcher
import io.github.mazilin.nautile.interaction.FullScreenPage
import io.github.mazilin.nautile.interaction.KeyboardCoordinator
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.Overlay
import io.github.mazilin.nautile.interaction.OverlayBackStack
import io.github.mazilin.nautile.ui.screen.common.PlaceholderScreen
import io.github.mazilin.nautile.ui.theme.NautileTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S1-T06 AppRoot 测试：四入口切换、无横滑、导航可见性、设置入口与返回优先级。
 * 需要真机/模拟器；无人到场时不执行，报告中标注“未执行”。
 */
@RunWith(AndroidJUnit4::class)
class AppRootTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val navigation = NavigationCoordinator()
    private val overlays = OverlayBackStack()
    private val keyboard = KeyboardCoordinator()
    private val dispatcher = BackDispatcher(keyboard, overlays, navigation)

    private fun setAppRoot() {
        composeRule.setContent {
            NautileTheme {
                AppRoot(
                    navigation = navigation,
                    overlays = overlays,
                    tabContent = { tab ->
                        when (tab) {
                            AppTab.TODO -> Text("待办内容")
                            else -> PlaceholderScreen(
                                title = tab.title,
                                onOpenSettings = navigation::navigateToSettings,
                            )
                        }
                    },
                    fullScreenContent = { page -> Text("全屏:${page.title}") },
                )
            }
        }
    }

    @Test
    fun acN14_four_tabs_switch_by_bottom_navigation_only() {
        setAppRoot()
        composeRule.onNodeWithText("待办内容").assertIsDisplayed()

        listOf(AppTab.SCHEDULE, AppTab.LOG, AppTab.AI).forEach { tab ->
            composeRule.onNodeWithTag("tab_${tab.name}").performClick()
            composeRule.onNodeWithTag("placeholder_title_${tab.title}").assertIsDisplayed()
            composeRule.runOnIdle { assertEquals(tab, navigation.activeTab.value) }
        }

        composeRule.onNodeWithTag("tab_TODO").performClick()
        composeRule.onNodeWithText("待办内容").assertIsDisplayed()
    }

    @Test
    fun acN16_horizontal_swipe_does_not_change_tab() {
        setAppRoot()
        composeRule.onNodeWithTag("tab_SCHEDULE").performClick()

        composeRule.onNodeWithTag("app_root_content").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("app_root_content").performTouchInput { swipeRight() }

        composeRule.runOnIdle { assertEquals(AppTab.SCHEDULE, navigation.activeTab.value) }
    }

    @Test
    fun acN15_bottom_navigation_hidden_for_overlay_and_fullscreen() {
        setAppRoot()
        composeRule.onNodeWithTag("tab_TODO").assertExists()

        composeRule.runOnIdle { overlays.open(Overlay.NewTaskSheet) }
        composeRule.onNodeWithTag("tab_TODO").assertDoesNotExist()

        composeRule.runOnIdle { overlays.close() }
        composeRule.onNodeWithTag("tab_TODO").assertExists()

        composeRule.runOnIdle { navigation.navigateToSettings() }
        composeRule.onNodeWithTag("tab_TODO").assertDoesNotExist()
        composeRule.onNodeWithText("全屏:设置").assertIsDisplayed()
    }

    @Test
    fun frN19_three_placeholder_pages_open_same_settings() {
        setAppRoot()

        listOf(AppTab.SCHEDULE, AppTab.LOG, AppTab.AI).forEach { tab ->
            composeRule.onNodeWithTag("tab_${tab.name}").performClick()
            composeRule.onNodeWithText("设置").performClick()
            composeRule.onNodeWithText("全屏:设置").assertIsDisplayed()
            composeRule.runOnIdle {
                assertEquals(FullScreenPage.Settings, navigation.currentFullScreenPage)
            }

            composeRule.runOnIdle { navigation.back() }
            composeRule.onNodeWithTag("tab_${tab.name}").assertExists()
        }
    }

    @Test
    fun acN14_back_priority_keyboard_overlay_fullscreen_then_system() {
        var hidden = false
        keyboard.updateImeVisible(true)

        assertTrue(dispatcher.dispatch { hidden = true })
        assertTrue(hidden)

        overlays.open(Overlay.GtdSelector)
        assertTrue(dispatcher.dispatch {})
        assertNull(overlays.current)

        navigation.navigateToSettings()
        assertTrue(dispatcher.dispatch {})
        assertNull(navigation.currentFullScreenPage)

        assertFalse(dispatcher.dispatch {})
    }
}
