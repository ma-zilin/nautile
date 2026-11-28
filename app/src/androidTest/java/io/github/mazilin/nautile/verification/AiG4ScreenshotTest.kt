package io.github.mazilin.nautile.verification

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.domain.ai.AiPhase
import io.github.mazilin.nautile.domain.model.AiErrorCode
import io.github.mazilin.nautile.domain.model.AiMessage
import io.github.mazilin.nautile.domain.model.AiMessageId
import io.github.mazilin.nautile.domain.model.AiMessageStatus
import io.github.mazilin.nautile.domain.model.AiRole
import io.github.mazilin.nautile.domain.model.AiSessionId
import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.ui.screen.ai.AiScreen
import io.github.mazilin.nautile.ui.screen.ai.AiUiState
import io.github.mazilin.nautile.ui.screen.settings.AiSettingsUiState
import io.github.mazilin.nautile.ui.screen.settings.DeepSeekKeyScreen
import io.github.mazilin.nautile.ui.screen.settings.SettingsScreen
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G4 真机证据截图（06 §2—§6、§10；AC-N-23、AC-AN-04/07）。
 *
 * 以测试渲染状态驱动 [AiScreen] / [DeepSeekKeyScreen] / [SettingsScreen]，浅色与深色各一轮，
 * 截图写入 MediaStore Downloads/nautile_evidence（应用卸载后仍可 adb pull），
 * 只作视觉核对证据，不替代语义/行为断言；截图不含真实 Key（Key 页输入为探针串且始终掩码）。
 */
@RunWith(AndroidJUnit4::class)
class AiG4ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var dark by mutableStateOf(false)
    private var fontScale by mutableStateOf(1f)
    private var aiState by mutableStateOf(AiUiState())
    private var keyState by mutableStateOf(AiSettingsUiState())

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

    private fun capture(name: String) {
        composeRule.waitForIdle()
        // 用 UiAutomation 截取整屏：可同时覆盖 M3 对话框等独立窗口，
        // 避免 onRoot().captureToImage() 只截主 Compose 根而漏掉“清除确认”弹层。
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "无法截屏：$name"
        }
        val context = instrumentation.targetContext
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "g4_$name.png")
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

    @Test
    fun g4ScreenshotAiStatesLightAndDark() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                NautileTheme(darkTheme = dark) {
                    AiScreen(
                        state = aiState,
                        onDraftChange = {},
                        onSend = {},
                        onStop = {},
                        onOpenSettings = {},
                    )
                }
            }
        }

        val user = message(1, AiRole.USER, "帮我梳理今天的三件重要事")
        val assistant = message(
            2,
            AiRole.ASSISTANT,
            "可以先按截止时间和影响面排序，再留出一段不被打断的时间处理最难的一项。",
        )
        val streaming = message(2, AiRole.ASSISTANT, "", AiMessageStatus.STREAMING)

        val scenes = listOf(
            "ai_empty_key_missing" to AiUiState(keyMissing = true),
            "ai_empty_ready" to AiUiState(),
            "ai_conversation" to AiUiState(messages = listOf(user, assistant)),
            "ai_streaming" to AiUiState(
                messages = listOf(user, streaming),
                phase = AiPhase.Streaming,
                streamingMessageId = AiMessageId(2),
                streamingText = "可以先按截止时间",
            ),
            "ai_stopping" to AiUiState(
                messages = listOf(user, streaming),
                phase = AiPhase.Streaming,
                streamingMessageId = AiMessageId(2),
                streamingText = "可以先按截止时间",
                stopping = true,
            ),
            "ai_failed" to AiUiState(
                messages = listOf(
                    user,
                    message(
                        2,
                        AiRole.ASSISTANT,
                        "可以先按截止时间",
                        AiMessageStatus.FAILED,
                        AiErrorCode.NETWORK,
                    ),
                ),
                phase = AiPhase.Failed(AiErrorCode.NETWORK),
                draft = "失败后保留的草稿",
            ),
        )

        listOf(false to "light", true to "dark").forEach { (isDark, suffix) ->
            scenes.forEach { (name, state) ->
                composeRule.runOnIdle {
                    dark = isDark
                    aiState = state
                }
                capture("${name}_$suffix")
            }
        }
    }

    @Test
    fun g4ScreenshotKeyPageMasked() {
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                DeepSeekKeyScreen(
                    state = keyState,
                    onBack = {},
                    onSaveKey = {},
                    onClearKey = {},
                )
            }
        }
        // 探针串只为验证掩码；不是真实 Key，截图只应出现圆点。
        composeRule.onNodeWithTag("ai_key_input").performTextInput("sk-g4-mask-probe")

        listOf(false to "light", true to "dark").forEach { (isDark, suffix) ->
            composeRule.runOnIdle { dark = isDark }
            capture("ai_key_page_masked_$suffix")
        }

        composeRule.runOnIdle { keyState = AiSettingsUiState(keyConfigured = true) }
        capture("ai_key_page_saved_light")
    }

    @Test
    fun g4ScreenshotClearConversationConfirm() {
        composeRule.setContent {
            NautileTheme(darkTheme = dark) {
                SettingsScreen(
                    settings = UserSettings(defaultPage = AppPage.TODO),
                    onBack = {},
                    onOpenDefaultPage = {},
                    onPredictiveBackChanged = {},
                    aiSettings = AiSettingsUiState(keyConfigured = true),
                )
            }
        }
        composeRule.onNodeWithTag("settings_clear_ai_conversation").performClick()

        listOf(false to "light", true to "dark").forEach { (isDark, suffix) ->
            composeRule.runOnIdle { dark = isDark }
            capture("ai_clear_confirm_$suffix")
        }
    }

    @Test
    fun g4ScreenshotFontScale200() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                NautileTheme(darkTheme = false) {
                    AiScreen(
                        state = aiState,
                        onDraftChange = {},
                        onSend = {},
                        onStop = {},
                        onOpenSettings = {},
                    )
                }
            }
        }

        val user = message(1, AiRole.USER, "帮我梳理今天的三件重要事")
        val assistant = message(
            2,
            AiRole.ASSISTANT,
            "可以先按截止时间和影响面排序，再留出一段不被打断的时间处理最难的一项。",
        )
        composeRule.runOnIdle {
            fontScale = 2f
            aiState = AiUiState(messages = listOf(user, assistant), draft = "200% 字体下的草稿")
        }
        capture("ai_font_scale_200_light")

        composeRule.runOnIdle { aiState = AiUiState(keyMissing = true) }
        capture("ai_font_scale_200_key_missing_light")
    }
}
