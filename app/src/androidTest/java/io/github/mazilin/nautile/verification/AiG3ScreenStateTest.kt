package io.github.mazilin.nautile.verification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.domain.ai.AiPhase
import io.github.mazilin.nautile.domain.model.AiErrorCode
import io.github.mazilin.nautile.domain.model.AiMessage
import io.github.mazilin.nautile.domain.model.AiMessageId
import io.github.mazilin.nautile.domain.model.AiMessageStatus
import io.github.mazilin.nautile.domain.model.AiRole
import io.github.mazilin.nautile.domain.model.AiSessionId
import io.github.mazilin.nautile.ui.screen.ai.AiScreen
import io.github.mazilin.nautile.ui.screen.ai.AiUiState
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AI 页静态状态与文案（06 §2—§5/§10—§11；AC-N-23、AC-A-17/18、AC-AN-06/07）。
 *
 * 直接以 [AiUiState] 驱动 [AiScreen]，不经过网络/协调器；验证：
 * - 头部大标题/设置入口、无搜索/附件/模型名/上下文标签；
 * - 空态与 Key 缺失说明必须包含“仅发送本次对话内容，不读取页面或本地数据”；
 * - 流式/停止/失败状态及 `ai_stop_button`/`ai_send_button` 互斥；
 * - 错误映射文案与 `ai_error_banner`/`ai_message_status_{id}` 的 liveRegion。
 */
@RunWith(AndroidJUnit4::class)
class AiG3ScreenStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(
        state: AiUiState,
        onOpenSettings: () -> Unit = {},
        onSend: () -> Unit = {},
        onStop: () -> Unit = {},
        onDraftChange: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            NautileTheme {
                AiScreen(
                    state = state,
                    onDraftChange = onDraftChange,
                    onSend = onSend,
                    onStop = onStop,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }

    private fun message(
        id: Long,
        role: AiRole,
        content: String,
        status: AiMessageStatus = AiMessageStatus.COMPLETE,
        errorCode: AiErrorCode? = null,
    ) = AiMessage(
        id = AiMessageId(id),
        sessionId = AiSessionId(1),
        role = role,
        content = content,
        status = status,
        errorCode = errorCode,
        createdAt = Instant.ofEpochMilli(1_000),
    )

    private val user = message(1, AiRole.USER, "你好")
    private val assistantStreaming = message(2, AiRole.ASSISTANT, "", AiMessageStatus.STREAMING)

    @Test
    fun headerShowsTitleAndSettingsWithoutSearchAttachmentOrModelName() {
        var opened = false
        setScreen(AiUiState(), onOpenSettings = { opened = true })

        composeRule.onNodeWithTag("ai_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_title").assertIsDisplayed().assertTextEquals("AI")
        composeRule.onNodeWithTag("ai_settings_entry").assertIsDisplayed().performClick()
        assertTrue("设置入口应回调进入根设置页", opened)

        // 无搜索、附件、模型名、上下文标签、通知角标。
        composeRule.onAllNodesWithText("搜索", substring = true).assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription("搜索", substring = true)).assertCountEquals(0)
        composeRule.onAllNodesWithText("附件", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("deepseek-chat", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("上下文", substring = true).assertCountEquals(0)
    }

    @Test
    fun emptyStateShowsRequiredScopeSentenceAndDisabledSend() {
        setScreen(AiUiState())

        composeRule.onNodeWithTag("ai_empty_state").assertIsDisplayed()
        composeRule.onNodeWithText("开始和 AI 对话").assertIsDisplayed()
        composeRule.onNodeWithText(
            "仅发送本次对话内容，不读取页面或本地数据",
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("ai_send_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("ai_stop_button").assertDoesNotExist()
    }

    @Test
    fun keyMissingStateExplainsScopeAndOffersSettings() {
        var opened = false
        setScreen(AiUiState(keyMissing = true), onOpenSettings = { opened = true })

        composeRule.onNodeWithText("尚未配置 DeepSeek Key").assertIsDisplayed()
        composeRule.onNodeWithText(
            "仅发送本次对话内容，不读取页面或本地数据",
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("去设置").assertIsDisplayed().performClick()
        assertTrue("Key 缺失应提供去设置入口", opened)

        composeRule.onNodeWithTag("ai_send_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("ai_stop_button").assertDoesNotExist()
    }

    @Test
    fun keyMissingWithHistoryShowsBannerAboveInput() {
        setScreen(
            AiUiState(
                keyMissing = true,
                messages = listOf(user),
            ),
        )

        composeRule.onNodeWithTag("ai_key_missing_banner").assertIsDisplayed()
        composeRule.onNodeWithText("尚未配置 DeepSeek Key，无法发送").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_empty_state").assertDoesNotExist()
        composeRule.onNodeWithTag("ai_send_button").assertIsNotEnabled()
    }

    @Test
    fun streamingShowsStopButtonAndNotSendButton() {
        setScreen(
            AiUiState(
                messages = listOf(user, assistantStreaming),
                phase = AiPhase.Streaming,
                streamingMessageId = AiMessageId(2),
                streamingText = "第一段",
                draft = "流式中可编辑的草稿",
            ),
        )

        composeRule.onNodeWithTag("ai_stop_button").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("ai_send_button").assertDoesNotExist()
        composeRule.onNodeWithTag("ai_message_status_2").assertTextEquals("正在生成…")
        composeRule.onNodeWithText("第一段").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_input").assertTextContains("流式中可编辑的草稿")
    }

    @Test
    fun stoppingDisablesStopButtonAndKeepsStopIcon() {
        setScreen(
            AiUiState(
                messages = listOf(user, assistantStreaming),
                phase = AiPhase.Streaming,
                streamingMessageId = AiMessageId(2),
                streamingText = "第一段",
                draft = "草稿",
                stopping = true,
            ),
        )

        composeRule.onNodeWithTag("ai_stop_button").assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithTag("ai_send_button").assertDoesNotExist()
        composeRule.onNodeWithTag("ai_message_status_2").assertTextEquals("正在停止…")
    }

    @Test
    fun stoppedStatePreservesPartialTextAndRestoresSendButton() {
        setScreen(
            AiUiState(
                messages = listOf(
                    user,
                    message(2, AiRole.ASSISTANT, "半截回答", AiMessageStatus.STOPPED),
                ),
                phase = AiPhase.Stopped,
            ),
        )

        composeRule.onNodeWithText("半截回答").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_message_status_2").assertTextEquals("已停止")
        composeRule.onNodeWithTag("ai_send_button").assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithTag("ai_stop_button").assertDoesNotExist()
    }

    @Test
    fun failedStatesShowDistinctMappedErrorsWithLiveRegion() {
        var state by mutableStateOf<AiUiState>(AiUiState())
        composeRule.setContent {
            NautileTheme {
                AiScreen(
                    state = state,
                    onDraftChange = {},
                    onSend = {},
                    onStop = {},
                    onOpenSettings = {},
                )
            }
        }

        val expected = listOf(
            AiErrorCode.INVALID_KEY to "发送失败：DeepSeek Key 无效，请在设置中更新",
            AiErrorCode.INSUFFICIENT_BALANCE to "发送失败：账户余额不足",
            AiErrorCode.RATE_LIMITED to "发送失败：请求过于频繁，请稍后重试",
            AiErrorCode.SERVER_ERROR to "发送失败：服务暂时不可用",
            AiErrorCode.NETWORK to "发送失败：网络不可用，请检查网络后重试",
            AiErrorCode.TIMEOUT to "发送失败：请求超时，请稍后重试",
            AiErrorCode.STREAM_INTERRUPTED to "发送失败：回复中断，已保留已收到的内容",
        )

        expected.forEach { (code, text) ->
            composeRule.runOnIdle {
                state = AiUiState(
                    messages = listOf(
                        user,
                        message(2, AiRole.ASSISTANT, "部分文本", AiMessageStatus.FAILED, code),
                    ),
                    phase = AiPhase.Failed(code),
                )
            }
            composeRule.onNodeWithTag("ai_message_status_2")
                .assertTextEquals(text)
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.LiveRegion,
                        LiveRegionMode.Polite,
                    ),
                )
        }
    }

    @Test
    fun sendErrorBannerIsShownWithLiveRegion() {
        setScreen(AiUiState(sendError = AiErrorCode.UNKNOWN, draft = "保留的草稿"))

        composeRule.onNodeWithTag("ai_error_banner")
            .assertIsDisplayed()
            .assertTextEquals("发送失败：未知错误")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
        composeRule.onNodeWithTag("ai_input").assertTextContains("保留的草稿")
    }

    @Test
    fun sendButtonEnablementFollowsDraftAndKey() {
        var state by mutableStateOf(AiUiState(draft = ""))
        composeRule.setContent {
            NautileTheme {
                AiScreen(
                    state = state,
                    onDraftChange = {},
                    onSend = {},
                    onStop = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("ai_send_button").assertIsNotEnabled()

        composeRule.runOnIdle { state = state.copy(draft = "有内容") }
        composeRule.onNodeWithTag("ai_send_button").assertIsEnabled()

        composeRule.runOnIdle { state = state.copy(keyMissing = true) }
        composeRule.onNodeWithTag("ai_send_button").assertIsNotEnabled()

        composeRule.runOnIdle { state = state.copy(keyMissing = false, clearing = true) }
        composeRule.onNodeWithTag("ai_send_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("ai_input").assertIsNotEnabled()
    }

    @Test
    fun inputPlaceholderIsDocumentedWording() {
        setScreen(AiUiState())
        composeRule.onNodeWithText("给 AI 发消息").assertIsDisplayed()
    }
}
