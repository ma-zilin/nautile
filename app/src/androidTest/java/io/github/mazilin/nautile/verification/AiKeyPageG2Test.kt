package io.github.mazilin.nautile.verification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.interaction.FullScreenPage
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.OverlayBackStack
import io.github.mazilin.nautile.ui.AppRoot
import io.github.mazilin.nautile.ui.screen.common.PlaceholderScreen
import io.github.mazilin.nautile.ui.screen.settings.AiSettingsFeedback
import io.github.mazilin.nautile.ui.screen.settings.AiSettingsUiState
import io.github.mazilin.nautile.ui.screen.settings.DefaultPageScreen
import io.github.mazilin.nautile.ui.screen.settings.DeepSeekKeyScreen
import io.github.mazilin.nautile.ui.screen.settings.SettingsScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.theme.NautileTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DeepSeek Key 全屏子页（06 §6.1/§6.2/§10；AC-AN-04、AC-AN-05、AC-N-21 普通返回部分）。
 *
 * 只用 Compose 语义层：掩码、保存/清除回调、未配置/已保存状态、失败反馈、不展示模型名，
 * 以及设置行进入 Key 页与普通返回。真机预测性返回与掩码截图仍单列人工。
 */
@RunWith(AndroidJUnit4::class)
class AiKeyPageG2Test {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setKeyScreen(
        initial: AiSettingsUiState = AiSettingsUiState(),
        onSave: (String) -> Unit = {},
        onClear: () -> Unit = {},
    ) {
        composeRule.setContent {
            NautileTheme {
                DeepSeekKeyScreen(
                    state = initial,
                    onBack = {},
                    onSaveKey = onSave,
                    onClearKey = onClear,
                )
            }
        }
    }

    @Test
    fun unconfiguredPageShowsNotConfiguredMaskedInputAndDisabledActions() {
        setKeyScreen()

        composeRule.onNodeWithTag("ai_key_status").assertTextContains("当前状态：未配置")
        composeRule.onNodeWithTag("ai_key_input")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        composeRule.onNodeWithTag("ai_key_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("ai_key_clear").assertIsNotEnabled()
    }

    @Test
    fun typingKeepsPlaintextHiddenAndSaveReceivesTrimmedInput() {
        var saved: String? = null
        setKeyScreen(onSave = { saved = it })

        val secret = "sk-very-secret-123"
        composeRule.onNodeWithTag("ai_key_input").performTextInput(secret)

        // 始终掩码：Password 语义存在，且可见 Text 不包含明文（明文只在模型值/EditableText 中）。
        val inputNode = composeRule.onNodeWithTag("ai_key_input").fetchSemanticsNode()
        assertTrue(
            "Key 输入应带 Password 语义",
            inputNode.config.contains(SemanticsProperties.Password),
        )
        val visibleText = inputNode.config.getOrNull(SemanticsProperties.Text)
            ?.joinToString("") { it.text }
            .orEmpty()
        assertTrue("掩码输入不得在界面上显示明文", !visibleText.contains(secret))

        composeRule.onNodeWithTag("ai_key_save").assertIsEnabled().performClick()
        assertEquals(secret, saved)
    }

    @Test
    fun successfulSaveShowsSavedStateAndClearsMaskedInput() {
        composeRule.setContent {
            var state by remember { mutableStateOf(AiSettingsUiState()) }
            NautileTheme {
                DeepSeekKeyScreen(
                    state = state,
                    onBack = {},
                    onSaveKey = {
                        state = state.copy(
                            keyConfigured = true,
                            keyFeedback = AiSettingsFeedback.KeySaved,
                        )
                    },
                    onClearKey = {},
                )
            }
        }

        composeRule.onNodeWithTag("ai_key_input").performTextInput("fake-key")
        composeRule.onNodeWithTag("ai_key_save").performClick()

        composeRule.onNodeWithTag("ai_key_status").assertTextContains("当前状态：已保存")
        composeRule.onNodeWithText("已保存").assertIsDisplayed()
        // 保存成功后清空掩码输入 → 保存按钮回到禁用。
        composeRule.onNodeWithTag("ai_key_save").assertIsNotEnabled()
    }

    @Test
    fun clearKeyReturnsToNotConfiguredWithTransientFeedback() {
        composeRule.setContent {
            var state by remember {
                mutableStateOf(AiSettingsUiState(keyConfigured = true))
            }
            NautileTheme {
                DeepSeekKeyScreen(
                    state = state,
                    onBack = {},
                    onSaveKey = {},
                    onClearKey = {
                        state = state.copy(
                            keyConfigured = false,
                            keyFeedback = AiSettingsFeedback.KeyCleared,
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag("ai_key_status").assertTextContains("当前状态：已保存")
        composeRule.onNodeWithTag("ai_key_clear").assertIsEnabled().performClick()

        composeRule.onNodeWithTag("ai_key_status").assertTextContains("当前状态：未配置")
        composeRule.onNodeWithText("已清除").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_key_clear").assertIsNotEnabled()
    }

    @Test
    fun saveAndClearFailuresAreShownAsTransientErrors() {
        setKeyScreen(AiSettingsUiState(keyFeedback = AiSettingsFeedback.KeySaveFailed))
        composeRule.onNodeWithText("保存失败，请重试").assertIsDisplayed()
    }

    @Test
    fun clearFailureIsShownAsTransientError() {
        setKeyScreen(
            AiSettingsUiState(
                keyConfigured = true,
                keyFeedback = AiSettingsFeedback.KeyClearFailed,
            ),
        )
        composeRule.onNodeWithText("清除失败，请重试").assertIsDisplayed()
    }

    @Test
    fun pageDoesNotShowModelName() {
        setKeyScreen()

        // 标题与输入框 label 都会显示“DeepSeek Key”；断言至少一个可见即可。
        composeRule.onAllNodesWithText("DeepSeek Key").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("deepseek-chat", substring = true).assertCountEquals(0)
    }

    /**
     * 设置根“DeepSeek Key”行进入全屏子页，普通返回逐级 pop 回设置根（AC-N-21 普通返回部分）。
     */
    @Test
    fun settingsRowOpensKeyPageAndBackReturnsToSettings() {
        val navigation = NavigationCoordinator()
        val overlays = OverlayBackStack()

        composeRule.setContent {
            NautileTheme {
                AppRoot(
                    navigation = navigation,
                    overlays = overlays,
                    tabContent = { tab ->
                        when (tab) {
                            io.github.mazilin.nautile.interaction.AppTab.TODO -> TodoScreen(
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
                                onOpenDeepSeekKey = navigation::navigateToDeepSeekKey,
                                onClearAiConversation = {},
                            )

                            FullScreenPage.DefaultPage -> DefaultPageScreen(
                                currentPage = UserSettings().defaultPage,
                                onSelect = {},
                                onBack = { navigation.back() },
                            )

                            FullScreenPage.DeepSeekKey -> DeepSeekKeyScreen(
                                state = AiSettingsUiState(),
                                onBack = { navigation.back() },
                                onSaveKey = {},
                                onClearKey = {},
                            )
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("settings_entry").performClick()
        composeRule.onNodeWithTag("settings_deepseek_key").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(FullScreenPage.DeepSeekKey, navigation.currentFullScreenPage)
        }
        composeRule.onNodeWithTag("ai_key_input").assertIsDisplayed()

        composeRule.onNodeWithTag("ai_key_back").performClick()
        composeRule.runOnIdle {
            assertEquals(FullScreenPage.Settings, navigation.currentFullScreenPage)
        }
        assertTrue(navigation.fullScreenStack.value.contains(FullScreenPage.Settings))
    }
}
