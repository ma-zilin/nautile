package io.github.mazilin.nautile.verification

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.interaction.AppTab
import io.github.mazilin.nautile.interaction.FullScreenPage
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.OverlayBackStack
import io.github.mazilin.nautile.ui.AppRoot
import io.github.mazilin.nautile.ui.screen.common.PlaceholderScreen
import io.github.mazilin.nautile.ui.screen.settings.SettingsScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.theme.NautileTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 闸门 P1（多智能体架构试跑，非功能交付）的待办页设置入口 AC 验证。
 *
 * 宿主沿用 AppRoot + 真 NavigationCoordinator：待办 tab 接真实 TodoScreen，
 * 全屏页接真实 SettingsScreen（与 MainActivity 相同的接线，但不启动 Activity，
 * 避免 T-040 记录的真机冷启动挂起）。
 *
 * 隔离性：不读写 DataStore/数据库，只用内存态 NavigationCoordinator，无外部依赖。
 * 需要真机/模拟器；无设备时只编译，报告中标注“未执行—无设备”。
 */
@RunWith(AndroidJUnit4::class)
class AcSettingsEntryP1Test {

    @get:Rule
    val composeRule = createComposeRule()

    private val navigation = NavigationCoordinator()
    private val overlays = OverlayBackStack()

    private fun setHost() {
        composeRule.setContent {
            NautileTheme {
                AppRoot(
                    navigation = navigation,
                    overlays = overlays,
                    tabContent = { tab ->
                        when (tab) {
                            AppTab.TODO -> TodoScreen(
                                onOpenSettings = navigation::navigateToSettings,
                            )

                            else -> PlaceholderScreen(
                                title = tab.title,
                                onOpenSettings = navigation::navigateToSettings,
                            )
                        }
                    },
                    fullScreenContent = { page ->
                        when (page) {
                            FullScreenPage.Settings -> SettingsScreen(
                                settings = UserSettings(),
                                onBack = { navigation.back() },
                                onOpenDefaultPage = navigation::navigateToDefaultPage,
                                onPredictiveBackChanged = {},
                            )

                            else -> PlaceholderScreen(
                                title = page.title,
                                onOpenSettings = {},
                            )
                        }
                    },
                )
            }
        }
    }

    /**
     * AC-P1-1：settings_entry 唯一、可见、可点击，读屏名仍为“设置”。
     *
     * G1-A2 授权行为变更（设计 §6 ICO-05/§10）：设置入口由 TextButton("设置") 改为图标按钮，
     * 可见文本消失、contentDescription 保持“设置”，testTag 不变。本断言随设计变更调整，
     * 不属于回归；唯一性/可见/可点击/顺序/负分支断言保持不变。
     */
    @Test
    fun settings_entry_exists_AC_P1_1() {
        setHost()

        // 唯一性：全树恰好 1 个该 tag 节点（其他节点复用 tag 会在此失败）。
        composeRule.onAllNodesWithTag("settings_entry").assertCountEquals(1)

        composeRule.onNodeWithTag("settings_entry")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertContentDescriptionEquals("设置")

        // 头部顺序（结构顺序，不判像素间距）：GTD 入口在左，设置入口在右。
        val gtdBounds = composeRule.onNodeWithTag("gtd_entry").fetchSemanticsNode().boundsInRoot
        val settingsBounds = composeRule
            .onNodeWithTag("settings_entry")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "设置入口应位于 GTD 入口右侧（头部顺序不变）",
            settingsBounds.left >= gtdBounds.left,
        )

        // 负分支：其他一级页的设置入口不复用该 tag（占位页只有文字，无 tag）。
        composeRule.onNodeWithTag("tab_SCHEDULE").performClick()
        composeRule.onAllNodesWithTag("settings_entry").assertCountEquals(0)
        composeRule.onNodeWithText("设置").assertIsDisplayed()
    }

    /** AC-P1-2：点击待办页设置入口进入根设置页；未点击时锚点不得出现。 */
    @Test
    fun opens_root_settings_from_todo_AC_P1_2() {
        setHost()

        // 未点击：根设置页锚点不得出现。
        composeRule.onAllNodesWithTag("settings_default_page").assertCountEquals(0)

        composeRule.onNodeWithTag("settings_entry").performClick()

        // 主判据：根设置页既有锚点。
        composeRule.onNodeWithTag("settings_default_page").assertIsDisplayed()
        // 辅助判据：设置页标题；此时待办页已卸载，故该文字唯一。
        composeRule.onAllNodesWithText("设置").assertCountEquals(1)
        composeRule.runOnIdle {
            assertEquals(FullScreenPage.Settings, navigation.currentFullScreenPage)
        }

        // AC-P1-3 的导航部分：返回仍回待办页，且锚点消失。
        composeRule.onNodeWithText("返回").performClick()
        composeRule.runOnIdle { assertNull(navigation.currentFullScreenPage) }
        composeRule.onAllNodesWithTag("settings_default_page").assertCountEquals(0)
        composeRule.onAllNodesWithTag("settings_entry").assertCountEquals(1)
    }
}
