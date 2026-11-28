package io.github.mazilin.nautile.ui.screen.ai

import io.github.mazilin.nautile.domain.ai.AiPhase
import io.github.mazilin.nautile.domain.model.AiError
import io.github.mazilin.nautile.domain.model.AiErrorCode
import io.github.mazilin.nautile.domain.usecase.AiChatCoordinator
import io.github.mazilin.nautile.domain.usecase.AiKeyCommands
import io.github.mazilin.nautile.testing.ControllableAiGateway
import io.github.mazilin.nautile.testing.FakeClock
import io.github.mazilin.nautile.testing.InMemoryAiConversationRepository
import io.github.mazilin.nautile.testing.InMemorySecretStore
import io.github.mazilin.nautile.testing.MainDispatcherRule
import io.github.mazilin.nautile.ui.screen.settings.AiSettingsViewModel
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * AI 页 ViewModel 本地草稿与门闩联动（G3；AC-A-18/19、AC-AN-01/06）。
 *
 * 只验证本地状态：Key 缺失/Busy/失败保留草稿、Started 才清草稿、停止收尾、
 * 清除会话清空草稿；业务请求与落库由 [io.github.mazilin.nautile.domain.usecase.AiChatCoordinatorTest] 覆盖。
 */
class AiViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: InMemoryAiConversationRepository
    private lateinit var gateway: ControllableAiGateway
    private lateinit var store: InMemorySecretStore
    private lateinit var keys: AiKeyCommands
    private lateinit var scope: CoroutineScope
    private lateinit var coordinator: AiChatCoordinator
    private lateinit var viewModel: AiViewModel

    /** 在 Main 调度器替换为 Unconfined 之后再创建 ViewModel，保证 viewModelScope 同步执行。 */
    @Before
    fun setUp() {
        repository = InMemoryAiConversationRepository()
        gateway = ControllableAiGateway()
        store = InMemorySecretStore()
        keys = AiKeyCommands(store, Dispatchers.Unconfined)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        coordinator = AiChatCoordinator(
            repository = repository,
            gateway = gateway,
            keyCommands = keys,
            clock = FakeClock(Instant.ofEpochMilli(1_700_000_000_000)),
            scope = scope,
        )
        viewModel = AiViewModel(coordinator)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** 保存 Key 后同步刷新 ViewModel 的 keyMissing（模拟 AI 页重新可见）。 */
    private fun withKey() {
        runBlocking { keys.save("fake-key") }
        viewModel.refreshKeyPresence()
    }

    private fun awaitUi(predicate: (AiUiState) -> Boolean): AiUiState = runBlocking {
        withTimeout(3_000) { viewModel.uiState.first(predicate) }
    }

    @Test
    fun onlyStartedClearsDraftAndCreatesMessages() {
        withKey()
        viewModel.onDraftChange("你好")
        viewModel.send()

        assertEquals("Started 后清空草稿", "", viewModel.uiState.value.draft)
        assertEquals(2, viewModel.uiState.value.messages.size)
        assertEquals("你好", viewModel.uiState.value.messages[0].content)
        assertTrue(viewModel.uiState.value.phase is AiPhase.Streaming)
    }

    @Test
    fun keyMissingKeepsDraftAndCreatesNoMessage() {
        viewModel.onDraftChange("你好")
        viewModel.send()

        assertEquals("Key 缺失必须保留草稿", "你好", viewModel.uiState.value.draft)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertTrue(viewModel.uiState.value.keyMissing)
        assertEquals(0, repository.beginTurnCalls)
        assertTrue(gateway.requests.isEmpty())
    }

    @Test
    fun busyKeepsNewDraftWithoutQueueing() {
        withKey()
        viewModel.onDraftChange("第一问")
        viewModel.send()

        viewModel.onDraftChange("第二问")
        viewModel.send()

        assertEquals("Busy 必须保留当前草稿", "第二问", viewModel.uiState.value.draft)
        assertEquals("不得排队第二条请求", 1, gateway.requests.size)
        assertEquals(2, viewModel.uiState.value.messages.size)
    }

    @Test
    fun failureKeepsNewDraftTypedDuringStreamingAndDoesNotResend() {
        withKey()
        viewModel.onDraftChange("第一问")
        viewModel.send()
        awaitUi { it.phase is AiPhase.Streaming }

        // 流式中输入新草稿，再让请求失败：新草稿不得被清空或回填覆盖。
        viewModel.onDraftChange("新草稿X")
        gateway.delta("部分")
        gateway.fail(AiError.Network)

        val failed = awaitUi { it.phase is AiPhase.Failed }
        assertEquals("新草稿X", failed.draft)
        assertEquals(1, gateway.requests.size)
        assertEquals(AiErrorCode.NETWORK, (failed.phase as AiPhase.Failed).code)
        assertEquals("部分", failed.messages[1].content)
    }

    @Test
    fun stoppingDisablesSendUntilTerminalWrittenThenKeepsDraft() {
        withKey()
        viewModel.onDraftChange("问题")
        viewModel.send()
        gateway.delta("半截")

        viewModel.onDraftChange("下一个草稿")
        val gate = CompletableDeferred<Unit>()
        repository.finishGate = gate
        viewModel.stop()

        val stopping = viewModel.uiState.value
        assertTrue("停止收尾期间本地 stopping 应为 true", stopping.stopping)
        assertFalse("停止收尾期间不得发送", stopping.sendEnabled)
        assertTrue(stopping.stopVisible)
        assertFalse("停止收尾期间停止按钮不可重复点击", stopping.stopEnabled)

        gate.complete(Unit)
        val stopped = awaitUi { !it.stopping && it.phase is AiPhase.Stopped }
        assertTrue("停止完成后有草稿应恢复发送（06 §2.3 Stopped）", stopped.sendEnabled)
        assertEquals("停止不得清空草稿", "下一个草稿", stopped.draft)
        assertEquals("半截", stopped.messages[1].content)
    }

    @Test
    fun clearConversationClearsMessagesAndDraft() {
        withKey()
        viewModel.onDraftChange("问题")
        viewModel.send()
        gateway.delta("回答")
        gateway.complete()
        awaitUi { it.phase is AiPhase.Idle && it.messages.size == 2 }

        viewModel.onDraftChange("待清除草稿")
        viewModel.clearConversation()

        val cleared = awaitUi { it.messages.isEmpty() }
        assertEquals("清除后草稿清空", "", cleared.draft)
        assertEquals(1L, coordinator.state.value.clearGeneration)
    }

    @Test
    fun settingsClearAlsoClearsAiPageDraft() {
        withKey()
        viewModel.onDraftChange("问题")
        viewModel.send()
        gateway.complete()
        awaitUi { it.phase is AiPhase.Idle && it.messages.size == 2 }
        viewModel.onDraftChange("设置页清除时的草稿")

        val settingsViewModel = AiSettingsViewModel(keys, coordinator)
        settingsViewModel.clearConversation()

        val cleared = awaitUi { it.messages.isEmpty() }
        assertEquals("设置页清除也应清空 AI 页草稿", "", cleared.draft)
    }

    @Test
    fun refreshKeyPresenceUpdatesUiState() {
        viewModel.refreshKeyPresence()
        assertTrue(viewModel.uiState.value.keyMissing)

        withKey()
        viewModel.refreshKeyPresence()
        assertFalse(viewModel.uiState.value.keyMissing)

        runBlocking { keys.clear() }
        viewModel.refreshKeyPresence()
        assertTrue(viewModel.uiState.value.keyMissing)
    }
}
