package io.github.mazilin.nautile.verification

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
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
import io.github.mazilin.nautile.data.db.entity.ProjectEntity
import io.github.mazilin.nautile.data.db.entity.TaskEntity
import io.github.mazilin.nautile.data.repository.RoomAiConversationRepository
import io.github.mazilin.nautile.domain.ai.AiPhase
import io.github.mazilin.nautile.domain.model.AiError
import io.github.mazilin.nautile.domain.usecase.AiChatCoordinator
import io.github.mazilin.nautile.domain.usecase.AiKeyCommands
import io.github.mazilin.nautile.platform.clock.SystemClock
import io.github.mazilin.nautile.testing.FailableAiConversationRepository
import io.github.mazilin.nautile.testing.FakeSecretStore
import io.github.mazilin.nautile.testing.ScriptedAiGateway
import io.github.mazilin.nautile.ui.screen.ai.AiScreen
import io.github.mazilin.nautile.ui.screen.ai.AiViewModel
import io.github.mazilin.nautile.ui.theme.NautileTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AI 页对话主链路（FakeGateway + 真实 ViewModel/协调器 + in-memory Room）。
 *
 * 覆盖 AC-A-09/18/19、AC-AN-01/06/07：
 * 发送 → 流式增量 → 停止（按钮互斥、停止后不再追加）→ 失败保留新草稿且不自动重发 →
 * 空态/清除后空态 → 发送后列表滚动到底 → 清除会话不影响 tasks/projects。
 *
 * 不调用真实 DeepSeek；Room 为 in-memory，不写真实用户数据库。
 */
@RunWith(AndroidJUnit4::class)
class AiG3ChatFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: NautileDatabase
    private lateinit var repository: FailableAiConversationRepository
    private lateinit var gateway: ScriptedAiGateway
    private lateinit var store: FakeSecretStore
    private lateinit var keys: AiKeyCommands
    private lateinit var scope: CoroutineScope
    private lateinit var coordinator: AiChatCoordinator
    private lateinit var viewModel: AiViewModel

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, NautileDatabase::class.java).build()
        repository = FailableAiConversationRepository(RoomAiConversationRepository(database))
        gateway = ScriptedAiGateway()
        store = FakeSecretStore()
        keys = AiKeyCommands(store, Dispatchers.Unconfined)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        coordinator = AiChatCoordinator(
            repository = repository,
            gateway = gateway,
            keyCommands = keys,
            clock = SystemClock(),
            scope = scope,
        )
        viewModel = AiViewModel(coordinator)
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    private fun withKey() {
        runBlocking { keys.save("fake-key") }
        viewModel.refreshKeyPresence()
    }

    private fun setScreen() {
        composeRule.setContent {
            NautileTheme {
                val state by viewModel.uiState.collectAsState()
                AiScreen(
                    state = state,
                    onDraftChange = viewModel::onDraftChange,
                    onSend = viewModel::send,
                    onStop = viewModel::stop,
                    onOpenSettings = {},
                    onScreenVisible = viewModel::refreshKeyPresence,
                )
            }
        }
    }

    private fun awaitUi(
        timeoutMillis: Long = 5_000,
        predicate: (io.github.mazilin.nautile.ui.screen.ai.AiUiState) -> Boolean,
    ) {
        composeRule.waitUntil(timeoutMillis) { predicate(viewModel.uiState.value) }
    }

    private fun seedTurn(question: String, answer: String) = runBlocking {
        val ids = repository.beginTurn(question, SystemClock().now())
        repository.finishAssistant(
            messageId = ids.assistantMessageId,
            content = answer,
            status = io.github.mazilin.nautile.domain.model.AiMessageStatus.COMPLETE,
            errorCode = null,
            at = SystemClock().now(),
        )
        ids
    }

    @Test
    fun sendStreamsThenStopsWithoutFurtherContent() {
        withKey()
        setScreen()

        composeRule.onNodeWithTag("ai_input").performTextInput("你好")
        composeRule.onNodeWithTag("ai_send_button").assertIsEnabled().performClick()
        awaitUi { it.messages.size == 2 && it.phase is AiPhase.Streaming }

        assertEquals("Started 后清空草稿", "", viewModel.uiState.value.draft)
        composeRule.onNodeWithTag("ai_stop_button").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("ai_send_button").assertDoesNotExist()

        gateway.delta("第一段")
        awaitUi { it.streamingText == "第一段" }
        composeRule.onNodeWithText("第一段").assertIsDisplayed()

        gateway.delta("第二段")
        awaitUi { it.streamingText == "第一段第二段" }

        val assistantId = viewModel.uiState.value.messages[1].id.value
        composeRule.onNodeWithTag("ai_stop_button").performClick()
        awaitUi { it.phase is AiPhase.Stopped }

        composeRule.onNodeWithTag("ai_message_status_$assistantId").assertTextEquals("已停止")
        composeRule.onNodeWithText("第一段第二段").assertIsDisplayed()
        assertEquals("停止不得自动发起新请求", 1, gateway.requests.size)

        // 停止后网关再产出也不得追加到界面。
        gateway.delta("停止后的内容")
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("停止后的内容").assertCountEquals(0)
        assertEquals("停止后不得再发起请求", 1, gateway.requests.size)
    }

    @Test
    fun failureKeepsNewDraftAndDoesNotAutoResend() {
        withKey()
        setScreen()

        composeRule.onNodeWithTag("ai_input").performTextInput("第一问")
        composeRule.onNodeWithTag("ai_send_button").performClick()
        awaitUi { it.phase is AiPhase.Streaming }

        // 流式中输入新草稿，再让请求失败；新草稿不得被清空或回填覆盖。
        composeRule.onNodeWithTag("ai_input").performTextInput("新草稿X")
        gateway.delta("部分")
        gateway.fail(AiError.Timeout)
        awaitUi { it.phase is AiPhase.Failed }

        val assistantId = viewModel.uiState.value.messages[1].id.value
        composeRule.onNodeWithTag("ai_input").assertTextContains("新草稿X")
        composeRule.onNodeWithTag("ai_message_status_$assistantId")
            .assertTextEquals("发送失败：请求超时，请稍后重试")
        composeRule.onNodeWithText("部分").assertIsDisplayed()

        Thread.sleep(400)
        composeRule.waitForIdle()
        assertEquals("失败不得自动重发", 1, gateway.requests.size)
        assertEquals("失败保留当前草稿", "新草稿X", viewModel.uiState.value.draft)
    }

    @Test
    fun emptyStateClearAndScrollToBottom() {
        withKey()
        // 历史 12 轮 = 24 条消息；先等 ViewModel 收到完整快照，再组合页面，
        // 避免与测试夹具的连续写入竞态影响“初始进入贴底”的判定。
        repeat(12) { index -> seedTurn("历史问题$index", "历史回答$index") }
        composeRule.waitUntil(5_000) { viewModel.uiState.value.messages.size == 24 }
        setScreen()
        awaitUi { it.messages.size == 24 }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("历史回答11").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("历史回答11").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_empty_state").assertDoesNotExist()

        composeRule.onNodeWithTag("ai_input").performTextInput("最后一条")
        composeRule.onNodeWithTag("ai_send_button").performClick()
        awaitUi { it.messages.size == 26 && it.phase is AiPhase.Streaming }
        gateway.delta("最后回复")
        gateway.complete()
        awaitUi { it.messages.size == 26 && it.phase is AiPhase.Idle }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("最后回复").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("最后回复").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_message_26").assertIsDisplayed()

        viewModel.onDraftChange("待清除草稿")
        composeRule.runOnIdle { viewModel.clearConversation() }
        awaitUi { it.messages.isEmpty() }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("ai_empty_state").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("ai_empty_state").assertIsDisplayed()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("ai_message_list").fetchSemanticsNodes().isEmpty()
        }
        assertEquals("清除后草稿清空", "", viewModel.uiState.value.draft)
    }

    @Test
    fun beginningTurnFailureShowsErrorBannerAndKeepsDraft() {
        withKey()
        repository.failBeginTurn = true
        setScreen()

        composeRule.onNodeWithTag("ai_input").performTextInput("会失败的问题")
        composeRule.onNodeWithTag("ai_send_button").performClick()
        awaitUi { it.sendError != null }

        composeRule.onNodeWithTag("ai_error_banner")
            .assertIsDisplayed()
            .assertTextEquals("发送失败：未知错误")
        composeRule.onNodeWithTag("ai_input").assertTextContains("会失败的问题")
        assertEquals("请求开始前失败不得创建消息", 0, viewModel.uiState.value.messages.size)
        assertEquals(0, gateway.requests.size)
    }

    @Test
    fun requestFailureAndClearDoNotTouchTasksOrProjects() {
        val projectId = runBlocking {
            database.projectDao().insert(
                ProjectEntity(name = "项目甲", status = "ACTIVE", createdAt = 10, updatedAt = 20),
            )
        }
        runBlocking {
            database.taskDao().insert(
                TaskEntity(
                    title = "待办甲",
                    detail = "详情",
                    projectId = projectId,
                    position = "INBOX",
                    priority = "NONE",
                    createdAt = 10,
                    updatedAt = 20,
                ),
            )
        }
        val tasksBefore = runBlocking { database.taskDao().observeAll().first() }
        val projectsBefore = runBlocking { database.projectDao().observeAll().first() }

        withKey()
        setScreen()
        composeRule.onNodeWithTag("ai_input").performTextInput("失败请求")
        composeRule.onNodeWithTag("ai_send_button").performClick()
        awaitUi { it.phase is AiPhase.Streaming }
        gateway.fail(AiError.Network)
        awaitUi { it.phase is AiPhase.Failed }

        assertEquals(
            "AI 请求失败不得改动待办",
            tasksBefore.map { it.id to it.title },
            runBlocking { database.taskDao().observeAll().first() }.map { it.id to it.title },
        )
        assertEquals(
            "AI 请求失败不得改动项目",
            projectsBefore.map { it.id to it.name },
            runBlocking { database.projectDao().observeAll().first() }.map { it.id to it.name },
        )

        composeRule.runOnIdle { viewModel.clearConversation() }
        awaitUi { it.messages.isEmpty() }

        assertEquals(
            "清除 AI 会话不得改动待办",
            tasksBefore.map { it.id to it.title to it.detail },
            runBlocking { database.taskDao().observeAll().first() }
                .map { it.id to it.title to it.detail },
        )
        assertEquals(
            "清除 AI 会话不得改动项目",
            projectsBefore.map { it.id to it.name },
            runBlocking { database.projectDao().observeAll().first() }.map { it.id to it.name },
        )
    }

    @Test
    fun secondTurnRequestContainsOnlyPriorConversationAndFixedPrompt() {
        withKey()
        setScreen()

        composeRule.onNodeWithTag("ai_input").performTextInput("第一问")
        composeRule.onNodeWithTag("ai_send_button").performClick()
        awaitUi { it.phase is AiPhase.Streaming }
        gateway.delta("第一答")
        gateway.complete()
        awaitUi {
            it.phase is AiPhase.Idle &&
                it.messages.size == 2 &&
                it.messages[1].status == io.github.mazilin.nautile.domain.model.AiMessageStatus.COMPLETE
        }

        composeRule.onNodeWithTag("ai_input").performTextInput("第二问")
        composeRule.onNodeWithTag("ai_send_button").performClick()
        awaitUi { gateway.requests.size == 2 }

        val request = gateway.requests[1]
        assertEquals(
            io.github.mazilin.nautile.domain.ai.AiSystemPrompt.TEXT,
            request.systemPrompt,
        )
        assertEquals(
            listOf(
                io.github.mazilin.nautile.domain.model.AiRole.USER to "第一问",
                io.github.mazilin.nautile.domain.model.AiRole.ASSISTANT to "第一答",
                io.github.mazilin.nautile.domain.model.AiRole.USER to "第二问",
            ),
            request.turns.map { it.role to it.content },
        )

        gateway.complete()
        awaitUi { it.phase is AiPhase.Idle && it.messages.size == 4 }
    }

    @Test
    fun keyMissingBlocksSendWithoutRequest() {
        setScreen()

        composeRule.onNodeWithTag("ai_input").performTextInput("没有 Key 的消息")
        // 发送按钮禁用，不响应点击。
        composeRule.onNodeWithTag("ai_send_button").assertIsNotEnabled()
        composeRule.waitForIdle()
        Thread.sleep(200)
        composeRule.waitForIdle()

        assertEquals(0, gateway.requests.size)
        assertEquals(0, viewModel.uiState.value.messages.size)
        composeRule.onNodeWithTag("ai_key_missing_banner").assertDoesNotExist()
        composeRule.onNodeWithTag("ai_empty_state").assertIsDisplayed()
    }
}
