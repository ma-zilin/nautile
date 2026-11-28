package io.github.mazilin.nautile.verification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import io.github.mazilin.nautile.ui.screen.settings.CategoryManagementScreen
import io.github.mazilin.nautile.ui.screen.settings.CategoryManagementUiState
import io.github.mazilin.nautile.ui.screen.settings.DefaultPageScreen
import io.github.mazilin.nautile.ui.screen.settings.SettingsScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.theme.NautileTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R4 G2 闸门：设置页全屏容器（SPC-09、CMP-50）与状态栏明暗（CMP-51）的语义层验证。
 *
 * 宿主沿用 AppRoot + 真 NavigationCoordinator + 真 SettingsScreen/DefaultPageScreen，
 * 与 MainActivity 相同接线，但不启动 Activity（避免 T-040 记录的真机冷启动挂起）。
 * 预测性返回开关用内存态 UserSettings 回写，只用内存态，不读写 DataStore/数据库。
 *
 * 本文件只验证语义层（节点存在、可见、可点击、状态保持）；
 * 系统栏图标明暗、safeDrawing 顶部安全区与深色像素对比度需要在设备上用
 * adb/uiautomator 实测，见门禁证据；Compose 测试宿主不能替代真机/模拟器系统栏判定。
 */
@RunWith(AndroidJUnit4::class)
class AcSettingsFullScreenG2R4Test {

    @get:Rule
    val composeRule = createComposeRule()

    private val navigation = NavigationCoordinator()
    private val overlays = OverlayBackStack()

    private fun setHost(darkTheme: Boolean = false) {
        composeRule.setContent {
            var settings by remember { mutableStateOf(UserSettings()) }
            NautileTheme(darkTheme = darkTheme) {
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
                                settings = settings,
                                onBack = { navigation.back() },
                                onOpenDefaultPage = navigation::navigateToDefaultPage,
                                onPredictiveBackChanged = { enabled ->
                                    settings = settings.copy(predictiveBackEnabled = enabled)
                                },
                                // R6-G4：设置根 → 分类管理（P-09/FR-N-30 设置子页全屏）。
                                onOpenCategoryManagement = navigation::navigateToCategoryManagement,
                            )

                            FullScreenPage.DefaultPage -> DefaultPageScreen(
                                currentPage = settings.defaultPage,
                                onSelect = { page2 -> settings = settings.copy(defaultPage = page2) },
                                onBack = { navigation.back() },
                            )

                            // R6-G4 新增枚举分支：仅补齐穷举并渲染真实分类管理页（默认空态）。
                            FullScreenPage.CategoryManagement -> CategoryManagementScreen(
                                state = CategoryManagementUiState(),
                                onBack = { navigation.back() },
                                onCreateTopLevel = {},
                                onCreateChild = {},
                                onEdit = {},
                                onRequestDelete = {},
                                onNameChanged = {},
                                onSave = {},
                                onCloseEdit = {},
                                onConfirmDelete = {},
                                onDismissDeleteConfirm = {},
                                onDismissGuard = {},
                                onDismissMessage = {},
                            )
                        }
                    },
                    predictiveBackEnabled = settings.predictiveBackEnabled,
                )
            }
        }
    }

    /**
     * AC（CMP-50 语义层）：待办头部设置入口进入根设置页，标题/返回/默认页/预测性返回
     * 四个语义锚点均存在且可见；返回回待办，设置页卸载。
     */
    @Test
    fun G2R4_settings_root_texts_visible_and_back_returns_todo() {
        setHost()

        composeRule.onNodeWithTag("settings_entry").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(FullScreenPage.Settings, navigation.currentFullScreenPage)
        }

        // 设置根页既有锚点 + 骨架语义：标题与四个可见文本/控件。
        composeRule.onNodeWithTag("settings_default_page").assertIsDisplayed()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
        composeRule.onNodeWithText("返回").assertIsDisplayed()
        composeRule.onNodeWithText("默认页").assertIsDisplayed()
        composeRule.onNodeWithText("预测性返回").assertIsDisplayed()
        composeRule.onNodeWithTag("predictive_back_switch").assertIsDisplayed()

        composeRule.onNodeWithText("返回").performClick()
        composeRule.runOnIdle { assertNull(navigation.currentFullScreenPage) }
        composeRule.onAllNodesWithTag("settings_default_page").assertCountEquals(0)
        composeRule.onNodeWithTag("settings_entry").assertIsDisplayed()
    }

    /**
     * 预测性返回开关可切换且状态保持：默认开启 → 关闭 → 退出再进入仍为关闭 → 再开启。
     * 开关写回宿主内存态 UserSettings，不落 DataStore（持久化由既有 DataStore 测试覆盖）。
     */
    @Test
    fun G2R4_predictive_back_switch_toggles_and_keeps_state_across_reentry() {
        setHost()

        composeRule.onNodeWithTag("settings_entry").performClick()
        composeRule.onNodeWithTag("predictive_back_switch").assertIsOn()

        composeRule.onNodeWithTag("predictive_back_switch").performClick()
        composeRule.onNodeWithTag("predictive_back_switch").assertIsOff()

        // 退出设置页再进入：内存态保持关闭。
        composeRule.onNodeWithText("返回").performClick()
        composeRule.onAllNodesWithTag("settings_default_page").assertCountEquals(0)
        composeRule.onNodeWithTag("settings_entry").performClick()
        composeRule.onNodeWithTag("predictive_back_switch").assertIsOff()

        composeRule.onNodeWithTag("predictive_back_switch").performClick()
        composeRule.onNodeWithTag("predictive_back_switch").assertIsOn()
    }

    /** 默认页入口存在并可进入默认页，再返回回到设置根（CMP-50 两页都适用本条）。 */
    @Test
    fun G2R4_default_page_entry_opens_and_back_returns_settings_root() {
        setHost()

        composeRule.onNodeWithTag("settings_entry").performClick()
        composeRule.onNodeWithTag("settings_default_page").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(FullScreenPage.DefaultPage, navigation.currentFullScreenPage)
        }

        composeRule.onNodeWithTag("default_page_TODO").assertIsDisplayed()
        composeRule.onNodeWithText("默认页").assertIsDisplayed()
        composeRule.onNodeWithText("返回").performClick()

        composeRule.onNodeWithTag("settings_default_page").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(FullScreenPage.Settings, navigation.currentFullScreenPage)
        }
    }

    /**
     * 深色主题下设置页语义文本仍存在且可见（contentColor 取 onBackground 的语义层证据）。
     * 像素级非黑底黑字与状态栏图标明暗由设备截图/系统栏检查判定，本用例不冒充像素验证。
     */
    @Test
    fun G2R4_dark_theme_settings_texts_visible_semantics() {
        setHost(darkTheme = true)

        composeRule.onNodeWithTag("settings_entry").performClick()
        composeRule.onNodeWithTag("settings_default_page").assertIsDisplayed()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
        composeRule.onNodeWithText("返回").assertIsDisplayed()
        composeRule.onNodeWithText("默认页").assertIsDisplayed()
        composeRule.onNodeWithText("预测性返回").assertIsDisplayed()
        composeRule.onNodeWithTag("predictive_back_switch").assertIsDisplayed()
    }

    /**
     * R6-G4 冻结用例适配：设置根 → 分类管理（P-09/FR-N-30 设置子页全屏），返回逐级回到设置根。
     * 只补新增枚举分支与对应导航断言，不放宽原有断言。
     */
    @Test
    fun G4_category_management_entry_opens_full_screen_and_back_returns_settings_root() {
        setHost()

        composeRule.onNodeWithTag("settings_entry").performClick()
        composeRule.onNodeWithTag("settings_category_management").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(FullScreenPage.CategoryManagement, navigation.currentFullScreenPage)
        }
        composeRule.onNodeWithTag("category_add_top").assertIsDisplayed()
        composeRule.onNodeWithText("分类管理").assertIsDisplayed()

        composeRule.onNodeWithTag("category_management_back").performClick()
        composeRule.runOnIdle {
            assertEquals(FullScreenPage.Settings, navigation.currentFullScreenPage)
        }
        composeRule.onNodeWithTag("settings_default_page").assertIsDisplayed()
    }
}
