package io.github.mazilin.nautile.verification

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
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
import io.github.mazilin.nautile.ui.screen.settings.AiSettingsFeedback
import io.github.mazilin.nautile.ui.screen.settings.AiSettingsUiState
import io.github.mazilin.nautile.ui.screen.settings.DeepSeekKeyScreen
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G4 无障碍语义与几何核对（06 §3.1、§4.1/§4.3、§6.2、§10；AC-AN-04）。
 *
 * 直接以 [AiUiState] 渲染 [AiScreen]，以真实 Compose 语义树断言：
 * - 消息角色“你/AI”与内容合并为单一可读节点；
 * - 流式消息暴露 `stateDescription="正在生成"`；
 * - 停止/发送按钮 48dp、可点、有可读 description；
 * - 气泡最大宽度不超过列表可用宽的 0.82；
 * - Key 输入具有密码语义且显示层为掩码，状态文字含 liveRegion，页面无模型名；
 * - 200% 字体下输入框与发送按钮不重叠。
 *
 * 不覆盖 TalkBack 真机朗读观感（未自动化，报告单列）。
 */
@RunWith(AndroidJUnit4::class)
class AiG4A11ySemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

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

    private fun setScreen(state: AiUiState) {
        composeRule.setContent {
            NautileTheme(darkTheme = false) {
                AiScreen(
                    state = state,
                    onDraftChange = {},
                    onSend = {},
                    onStop = {},
                    onOpenSettings = {},
                )
            }
        }
    }

    @Test
    fun messageRoleAndContentMergeIntoReadableNode() {
        setScreen(
            AiUiState(
                messages = listOf(
                    message(1, AiRole.USER, "你好"),
                    message(2, AiRole.ASSISTANT, "收到"),
                ),
            ),
        )

        // 合并语义：同一节点同时包含角色标签与内容；若未合并则不会存在这样的节点。
        composeRule.onNode(hasText("你") and hasText("你好")).assertExists()
        composeRule.onNode(hasText("AI") and hasText("收到")).assertExists()
    }

    @Test
    fun streamingMessageExposesStateDescription() {
        setScreen(
            AiUiState(
                messages = listOf(
                    message(1, AiRole.USER, "你好"),
                    message(2, AiRole.ASSISTANT, "", AiMessageStatus.STREAMING),
                ),
                phase = AiPhase.Streaming,
                streamingMessageId = AiMessageId(2),
                streamingText = "第一段",
            ),
        )

        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "正在生成"),
        ).assertExists()
    }

    @Test
    fun stopButtonIs48dpClickableWithDescription() {
        var stopClicked = false
        composeRule.setContent {
            NautileTheme(darkTheme = false) {
                AiScreen(
                    state = AiUiState(
                        messages = listOf(
                            message(1, AiRole.USER, "你好"),
                            message(2, AiRole.ASSISTANT, "", AiMessageStatus.STREAMING),
                        ),
                        phase = AiPhase.Streaming,
                        streamingMessageId = AiMessageId(2),
                        streamingText = "第一段",
                    ),
                    onDraftChange = {},
                    onSend = {},
                    onStop = { stopClicked = true },
                    onOpenSettings = {},
                )
            }
        }

        val stop = composeRule.onNodeWithTag("ai_stop_button")
        stop.assertIsDisplayed().assertIsEnabled()
            .assert(hasContentDescription("停止"))
            .assert(hasClickAction())
        val bounds = stop.getUnclippedBoundsInRoot()
        assertTrue("停止按钮应达 48dp 触控（宽 ${bounds.width}）", bounds.width.value >= 47.5f)
        assertTrue("停止按钮应达 48dp 触控（高 ${bounds.height}）", bounds.height.value >= 47.5f)
        stop.performClick()
        assertTrue("停止按钮点击应回调 onStop", stopClicked)
    }

    @Test
    fun disabledSendButtonReadsUnavailable() {
        setScreen(AiUiState(draft = ""))
        composeRule.onNodeWithTag("ai_send_button")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .assert(hasContentDescription("发送，当前不可用"))
    }

    @Test
    fun bubbleWidthStaysWithin82PercentOfListWidth() {
        val longText = "这是一条很长的用户消息".repeat(20)
        setScreen(AiUiState(messages = listOf(message(1, AiRole.USER, longText))))

        val rootWidth = composeRule.onRoot().getUnclippedBoundsInRoot().width.value
        val bubble = composeRule.onNode(hasText("你") and hasText(longText))
        val bubbleWidth = bubble.getUnclippedBoundsInRoot().width.value
        assertTrue(
            "气泡宽度 ${bubbleWidth}dp 不应超过列表可用宽 ${rootWidth}dp 的 0.82",
            bubbleWidth <= rootWidth * 0.82f + 1f,
        )
    }

    @Test
    fun keyInputHasPasswordSemanticsAndStatusIsLiveRegion() {
        composeRule.setContent {
            NautileTheme(darkTheme = false) {
                DeepSeekKeyScreen(
                    state = AiSettingsUiState(
                        keyConfigured = true,
                        keyFeedback = AiSettingsFeedback.KeySaved,
                    ),
                    onBack = {},
                    onSaveKey = {},
                    onClearKey = {},
                )
            }
        }

        composeRule.onNodeWithTag("ai_key_input")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))

        composeRule.onNodeWithTag("ai_key_status")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
        composeRule.onNodeWithText("当前状态：已保存").assertIsDisplayed()

        // 不展示模型名/联网探测入口。
        composeRule.onNode(hasText("deepseek-chat", substring = true)).assertDoesNotExist()
        composeRule.onNode(hasText("测试连接", substring = true)).assertDoesNotExist()
    }

    @Test
    fun keyInputDisplayIsMaskedAndSavePassesInput() {
        var saved = ""
        composeRule.setContent {
            NautileTheme(darkTheme = false) {
                DeepSeekKeyScreen(
                    state = AiSettingsUiState(keyConfigured = false),
                    onBack = {},
                    onSaveKey = { saved = it },
                    onClearKey = {},
                )
            }
        }

        val plain = "sk-g4-mask-probe"
        composeRule.onNodeWithTag("ai_key_input").performTextInput(plain)
        val node = composeRule.onNodeWithTag("ai_key_input").fetchSemanticsNode()
        assertNotNull(
            "Key 输入应保留密码语义",
            node.config.getOrNull(SemanticsProperties.Password),
        )
        // 显示层必须是掩码：所有文本布局结果都不应包含输入明文。
        val getLayouts = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)
        if (getLayouts != null) {
            val layouts = mutableListOf<TextLayoutResult>()
            getLayouts.action?.invoke(layouts)
            layouts.forEach { layout ->
                assertFalse(
                    "Key 输入显示层不应出现明文：${layout.layoutInput.text.text}",
                    layout.layoutInput.text.text.contains(plain),
                )
            }
        }
        composeRule.onNodeWithTag("ai_key_save").assertIsEnabled().performClick()
        assertTrue("保存应回调输入值", saved == plain)
    }

    @Test
    fun textScalesWithoutInputSendOverlap() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                NautileTheme(darkTheme = false) {
                    AiScreen(
                        state = AiUiState(draft = "200% 字体下的草稿"),
                        onDraftChange = {},
                        onSend = {},
                        onStop = {},
                        onOpenSettings = {},
                    )
                }
            }
        }

        val input = composeRule.onNodeWithTag("ai_input").assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val send = composeRule.onNodeWithTag("ai_send_button").assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue(
            "200% 字体下输入框右边 ${input.right}dp 不应与发送按钮左边 ${send.left}dp 重叠",
            input.right.value <= send.left.value + 0.5f,
        )
        composeRule.onNodeWithTag("ai_title").assertIsDisplayed()
    }
}
