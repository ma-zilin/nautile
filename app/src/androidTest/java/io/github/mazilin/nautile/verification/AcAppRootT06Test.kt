package io.github.mazilin.nautile.verification

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
import io.github.mazilin.nautile.interaction.FullScreenPage
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.Overlay
import io.github.mazilin.nautile.interaction.OverlayBackStack
import io.github.mazilin.nautile.ui.AppRoot
import io.github.mazilin.nautile.ui.screen.common.PlaceholderScreen
import io.github.mazilin.nautile.ui.theme.NautileTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S1-T06 AppRoot UI 层 AC 验证（T-053）。
 *
 * 覆盖四入口点按、无页面横滑、三页占位设置入口、导航隐藏的结构条件。
 * 需要真机/模拟器；本轮只编译，不执行，报告中标注“未自动化/未执行”。
 */
@RunWith(AndroidJUnit4::class)
class AcAppRootT06Test {

    @get:Rule
    val composeRule = createComposeRule()

    private val navigation = NavigationCoordinator()
    private val overlays = OverlayBackStack()

    private fun setAppRoot(scrollNavigationVisible: Boolean = true) {
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
                    scrollNavigationVisible = scrollNavigationVisible,
                )
            }
        }
    }

    @Test
    fun AC_N_14_four_bottom_entries_switch_pages_by_click() {
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
    fun AC_N_16_horizontal_swipe_does_not_change_first_level_page() {
        setAppRoot()
        composeRule.onNodeWithTag("tab_SCHEDULE").performClick()

        composeRule.onNodeWithTag("app_root_content").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("app_root_content").performTouchInput { swipeRight() }

        composeRule.runOnIdle { assertEquals(AppTab.SCHEDULE, navigation.activeTab.value) }
    }

    @Test
    fun AC_N_14_three_placeholder_settings_entries_open_same_root() {
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
    fun AC_N_15_navigation_hidden_for_overlay_and_full_screen() {
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
    fun AC_N_15_navigation_hidden_when_scroll_state_says_hidden() {
        setAppRoot(scrollNavigationVisible = false)

        composeRule.onNodeWithText("待办内容").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_TODO").assertDoesNotExist()
    }
}
