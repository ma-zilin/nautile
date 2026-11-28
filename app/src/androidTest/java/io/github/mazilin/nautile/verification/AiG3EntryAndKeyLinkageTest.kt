package io.github.mazilin.nautile.verification

import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.repository.RoomAiConversationRepository
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.domain.usecase.AiChatCoordinator
import io.github.mazilin.nautile.domain.usecase.AiKeyCommands
import io.github.mazilin.nautile.interaction.AppTab
import io.github.mazilin.nautile.interaction.FullScreenPage
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.OverlayBackStack
import io.github.mazilin.nautile.platform.clock.SystemClock
import io.github.mazilin.nautile.testing.FakeSecretStore
import io.github.mazilin.nautile.testing.ScriptedAiGateway
import io.github.mazilin.nautile.ui.AppRoot
import io.github.mazilin.nautile.ui.screen.ai.AiScreen
import io.github.mazilin.nautile.ui.screen.ai.AiViewModel
import io.github.mazilin.nautile.ui.screen.settings.AiSettingsViewModel
import io.github.mazilin.nautile.ui.screen.settings.DeepSeekKeyScreen
import io.github.mazilin.nautile.ui.screen.settings.SettingsScreen
import io.github.mazilin.nautile.ui.theme.NautileTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AI 入口、设置入口与 Key 联动（AC-N-23、AC-A-15、AC-AN-04）。
 *
 * 用 AppRoot + 真实 AiScreen/AiViewModel/SettingsScreen/DeepSeekKeyScreen 接线：
 * 点 AI 入口进入全屏对话页；右上设置进入根设置页；
 * 设置中保存 Key 后返回 AI 页不再提示缺失且可发送；清除 Key 后恢复缺失与禁用。
 */
@RunWith(AndroidJUnit4::class)
class AiG3EntryAndKeyLinkageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: NautileDatabase
    private lateinit var navigation: NavigationCoordinator
    private lateinit var overlays: OverlayBackStack
    private lateinit var store: FakeSecretStore
    private lateinit var keys: AiKeyCommands
    private lateinit var scope: CoroutineScope
    private lateinit var aiViewModel: AiViewModel
    private lateinit var aiSettingsViewModel: AiSettingsViewModel

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, NautileDatabase::class.java).build()
        val repository = RoomAiConversationRepository(database)
        store = FakeSecretStore()
        keys = AiKeyCommands(store, Dispatchers.Unconfined)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = AiChatCoordinator(
            repository = repository,
            gateway = ScriptedAiGateway(),
            keyCommands = keys,
            clock = SystemClock(),
            scope = scope,
        )
        aiViewModel = AiViewModel(coordinator)
        aiSettingsViewModel = AiSettingsViewModel(keys, coordinator)
        navigation = NavigationCoordinator()
        overlays = OverlayBackStack()
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    private fun setContent() {
        composeRule.setContent {
            NautileTheme {
                val aiState by aiViewModel.uiState.collectAsState()
                val aiSettings by aiSettingsViewModel.uiState.collectAsState()
                AppRoot(
                    navigation = navigation,
                    overlays = overlays,
                    tabContent = { tab ->
                        when (tab) {
                            AppTab.AI -> AiScreen(
                                state = aiState,
                                onDraftChange = aiViewModel::onDraftChange,
                                onSend = aiViewModel::send,
                                onStop = aiViewModel::stop,
                                onOpenSettings = navigation::navigateToSettings,
                                onScreenVisible = aiViewModel::refreshKeyPresence,
                            )

                            else -> Text("页面-${tab.title}")
                        }
                    },
                    fullScreenContent = { page ->
                        when (page) {
                            FullScreenPage.Settings -> SettingsScreen(
                                settings = UserSettings(),
                                onBack = navigation::back,
                                onOpenDefaultPage = {},
                                onPredictiveBackChanged = {},
                                aiSettings = aiSettings,
                                onOpenDeepSeekKey = navigation::navigateToDeepSeekKey,
                                onClearAiConversation = aiSettingsViewModel::clearConversation,
                            )

                            FullScreenPage.DeepSeekKey -> DeepSeekKeyScreen(
                                state = aiSettings,
                                onBack = navigation::back,
                                onSaveKey = aiSettingsViewModel::saveKey,
                                onClearKey = aiSettingsViewModel::clearKey,
                            )

                            FullScreenPage.DefaultPage -> Text("默认页")
                        }
                    },
                )
            }
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTagGone(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun openKeyPageFromAi() {
        waitForTag("ai_screen")
        composeRule.onNodeWithTag("ai_settings_entry").performClick()
        waitForTag("settings_deepseek_key")
        composeRule.onNodeWithTag("settings_deepseek_key").performClick()
        waitForTag("ai_key_screen")
    }

    private fun backToAiPageFromKey() {
        composeRule.onNodeWithTag("ai_key_back").performClick()
        waitForTagGone("ai_key_screen")
        waitForTag("settings_deepseek_key")
        composeRule.onNodeWithText("返回").performClick()
        waitForTagGone("settings_deepseek_key")
        waitForTag("ai_screen")
    }

    @Test
    fun aiEntryAndSettingsEntryAreReachable() {
        setContent()

        // AC-A-15：点底部 AI 进入对话页（无 Key 时显示缺失空态）。
        composeRule.onNodeWithTag("tab_AI").performClick()
        waitForTag("ai_screen")
        composeRule.onNodeWithTag("ai_title").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_empty_state").assertIsDisplayed()

        // AC-A-09：未配置 Key 不阻塞其他一级页面。
        composeRule.onNodeWithTag("tab_TODO").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("页面-待办").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("页面-待办").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_AI").performClick()
        waitForTag("ai_screen")

        // AC-N-23：右上设置进入根设置页。
        composeRule.onNodeWithTag("ai_settings_entry").performClick()
        waitForTag("settings_clear_ai_conversation")
        assertEquals(FullScreenPage.Settings, navigation.currentFullScreenPage)
    }

    @Test
    fun savingKeyInSettingsUnlocksAiPageAndClearingLocksAgain() {
        setContent()
        composeRule.onNodeWithTag("tab_AI").performClick()
        waitForTag("ai_screen")

        // 初始未配置：空态说明 + 发送禁用。
        composeRule.onNodeWithText("尚未配置 DeepSeek Key").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_send_button").assertIsNotEnabled()

        openKeyPageFromAi()
        composeRule.onNodeWithTag("ai_key_input").performTextInput("sk-fake-g3-key")
        composeRule.onNodeWithTag("ai_key_save").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("当前状态：已保存").fetchSemanticsNodes().isNotEmpty()
        }

        backToAiPageFromKey()
        // 保存后返回 AI 页：keyMissing=false，可发送。
        composeRule.onNodeWithText("开始和 AI 对话").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_key_missing_banner").assertDoesNotExist()
        composeRule.onNodeWithTag("ai_input").performTextInput("你好")
        composeRule.onNodeWithTag("ai_send_button").assertIsEnabled()

        // 清除 Key 后返回 AI 页：keyMissing=true，发送再次禁用。
        openKeyPageFromAi()
        composeRule.onNodeWithTag("ai_key_clear").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("当前状态：未配置").fetchSemanticsNodes().isNotEmpty()
        }
        backToAiPageFromKey()
        composeRule.onNodeWithText("尚未配置 DeepSeek Key").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_send_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("ai_key_missing_banner").assertDoesNotExist()
    }
}
