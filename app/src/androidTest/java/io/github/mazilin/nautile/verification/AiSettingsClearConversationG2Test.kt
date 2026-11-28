package io.github.mazilin.nautile.verification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.ui.screen.settings.AiSettingsFeedback
import io.github.mazilin.nautile.ui.screen.settings.AiSettingsUiState
import io.github.mazilin.nautile.ui.screen.settings.SettingsScreen
import io.github.mazilin.nautile.ui.theme.NautileTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 根设置“清除 AI 会话”入口、确认弹层与一次性反馈（06 §6.1/§6.3；AC-A-14、AC-AN-03）。
 *
 * 数据层“清除不影响 tasks/projects”由 AiConversationRepositoryTest 断言；
 * 本文件只验证入口可定位、确认/取消/执行与成功/失败反馈，不声称数据不变已在此覆盖。
 */
@RunWith(AndroidJUnit4::class)
class AiSettingsClearConversationG2Test {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setSettings(
        initial: AiSettingsUiState = AiSettingsUiState(),
        onClear: () -> Unit = {},
    ) {
        composeRule.setContent {
            NautileTheme {
                SettingsScreen(
                    settings = UserSettings(),
                    onBack = {},
                    onOpenDefaultPage = {},
                    onPredictiveBackChanged = {},
                    aiSettings = initial,
                    onOpenDeepSeekKey = {},
                    onClearAiConversation = onClear,
                )
            }
        }
    }

    @Test
    fun clearConversationRowIsLocatableWithSupportingText() {
        setSettings()

        composeRule.onNodeWithTag("settings_clear_ai_conversation").assertIsDisplayed()
        composeRule.onNodeWithText("清除 AI 会话").assertIsDisplayed()
        composeRule.onNodeWithText("删除本机 AI 消息，不影响待办、日程、日志").assertIsDisplayed()
    }

    @Test
    fun tappingRowOpensConfirmDialogAndCancelDoesNotClear() {
        var cleared = 0
        setSettings(onClear = { cleared++ })

        composeRule.onNodeWithTag("settings_clear_ai_conversation").performClick()
        composeRule.onNodeWithText("清除 AI 会话？").assertIsDisplayed()
        composeRule.onNodeWithText(
            "将删除本机保存的全部 AI 消息，且无法恢复；待办、日程、日志不受影响。",
        ).assertIsDisplayed()

        composeRule.onNodeWithTag("settings_clear_ai_cancel").performClick()
        composeRule.onNodeWithText("清除 AI 会话？").assertDoesNotExist()
        assertEquals("取消不得触发清除", 0, cleared)
    }

    @Test
    fun confirmInvokesClearAndClosesDialog() {
        var cleared = 0
        setSettings(onClear = { cleared++ })

        composeRule.onNodeWithTag("settings_clear_ai_conversation").performClick()
        composeRule.onNodeWithTag("settings_clear_ai_confirm").performClick()

        assertEquals("确认应触发一次清除", 1, cleared)
        composeRule.onNodeWithText("清除 AI 会话？").assertDoesNotExist()
    }

    @Test
    fun successFeedbackIsShownAfterClearing() {
        composeRule.setContent {
            var state by remember { mutableStateOf(AiSettingsUiState()) }
            NautileTheme {
                SettingsScreen(
                    settings = UserSettings(),
                    onBack = {},
                    onOpenDefaultPage = {},
                    onPredictiveBackChanged = {},
                    aiSettings = state,
                    onOpenDeepSeekKey = {},
                    onClearAiConversation = {
                        state = state.copy(
                            conversationFeedback = AiSettingsFeedback.ConversationCleared,
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag("settings_clear_ai_conversation").performClick()
        composeRule.onNodeWithTag("settings_clear_ai_confirm").performClick()

        composeRule.onNodeWithText("AI 会话已清除").assertIsDisplayed()
        // 从设置清除后停留设置页：设置行仍在。
        composeRule.onNodeWithTag("settings_clear_ai_conversation").assertIsDisplayed()
    }

    @Test
    fun failureFeedbackIsShownAndRowRemainsUsable() {
        setSettings(
            initial = AiSettingsUiState(
                conversationFeedback = AiSettingsFeedback.ConversationClearFailed,
            ),
        )

        composeRule.onNodeWithText("清除失败，请重试").assertIsDisplayed()
        // 失败后仍可再次打开确认弹层（不假成功、可重试）。
        composeRule.onNodeWithTag("settings_clear_ai_conversation").performClick()
        composeRule.onNodeWithText("清除 AI 会话？").assertIsDisplayed()
    }
}
