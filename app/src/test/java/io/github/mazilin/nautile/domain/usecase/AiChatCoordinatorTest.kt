package io.github.mazilin.nautile.domain.usecase

import io.github.mazilin.nautile.domain.ai.AiChatState
import io.github.mazilin.nautile.domain.ai.AiPhase
import io.github.mazilin.nautile.domain.ai.AiSystemPrompt
import io.github.mazilin.nautile.domain.ai.SendOutcome
import io.github.mazilin.nautile.domain.model.AiError
import io.github.mazilin.nautile.domain.model.AiErrorCode
import io.github.mazilin.nautile.domain.model.AiMessage
import io.github.mazilin.nautile.domain.model.AiMessageId
import io.github.mazilin.nautile.domain.model.AiMessageStatus
import io.github.mazilin.nautile.domain.model.AiRequest
import io.github.mazilin.nautile.domain.model.AiRole
import io.github.mazilin.nautile.domain.model.AiSessionId
import io.github.mazilin.nautile.testing.ControllableAiGateway
import io.github.mazilin.nautile.testing.FakeClock
import io.github.mazilin.nautile.testing.InMemoryAiConversationRepository
import io.github.mazilin.nautile.testing.InMemorySecretStore
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 对话协调器 JVM 验证（G3；AC-A-12/17/19、AC-AN-01/02/06）。
 *
 * 预期取自架构 §2.4/§2.5 与 06 §4—§5，不照抄实现：
 * 单请求不排队、先查 Key、Started 才落库、增量只留内存、停止写 STOPPED 且收尾前不可发送、
 * 清除先 stop→join→删除、错误映射 FAILED、请求体只含 USER+COMPLETE 与固定系统提示、
 * 启动恢复 STREAMING→STOPPED。
 *
 * 全部使用内存替身与可控网关；不发起网络请求，不代表真实 DeepSeek 接口。
 */
class AiChatCoordinatorTest {

    private class Harness(initialMessages: List<AiMessage> = emptyList()) {
        val repository = InMemoryAiConversationRepository(initialMessages)
        val gateway = ControllableAiGateway()
        val store = InMemorySecretStore()
        val keys = AiKeyCommands(store, Dispatchers.Unconfined)
        val clock = FakeClock(Instant.ofEpochMilli(1_700_000_000_000))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = AiChatCoordinator(repository, gateway, keys, clock, scope)

        suspend fun withKey() {
            keys.save("fake-key")
        }

        fun close() {
            scope.cancel()
        }

        suspend fun await(predicate: (AiChatState) -> Boolean): AiChatState =
            withTimeout(3_000) { coordinator.state.first(predicate) }
    }

    private fun harness(
        initialMessages: List<AiMessage> = emptyList(),
        block: suspend Harness.() -> Unit,
    ) = runBlocking {
        val harness = Harness(initialMessages)
        try {
            harness.block()
        } finally {
            harness.close()
        }
    }

    @Test
    fun singleRequestReturnsBusyWithoutQueueing() = harness {
        withKey()
        assertEquals(SendOutcome.Started, coordinator.trySend("第一问"))
        gateway.delta("部分")

        assertEquals(SendOutcome.Busy, coordinator.trySend("第二问"))
        assertEquals("已有请求时不得排队或并发", 1, gateway.requests.size)
        assertEquals("Busy 不得写入第二条用户消息", 2, repository.storedMessages().size)

        coordinator.stop()
        await { it.phase == AiPhase.Stopped }
    }

    @Test
    fun keyMissingDoesNotWriteRoomOrCallGateway() = harness {
        assertEquals(SendOutcome.KeyMissing, coordinator.trySend("你好"))

        assertTrue(coordinator.state.value.keyMissing)
        assertEquals("Key 缺失不得调用 beginTurn", 0, repository.beginTurnCalls)
        assertTrue(repository.storedMessages().isEmpty())
        assertTrue("Key 缺失不得发起网络请求", gateway.requests.isEmpty())
    }

    @Test
    fun incrementalDeltasStayInMemoryUntilTerminal() = harness {
        withKey()
        assertEquals(SendOutcome.Started, coordinator.trySend("问题"))
        gateway.delta("A")
        gateway.delta("B")

        assertEquals("AB", coordinator.state.value.streamingText)
        val streamingRow = repository.storedMessages()[1]
        assertEquals("增量不得逐次写库", "", streamingRow.content)
        assertEquals(AiMessageStatus.STREAMING, streamingRow.status)

        gateway.complete()
        val done = await { it.phase is AiPhase.Idle && it.messages.size == 2 }
        assertEquals("终态一次性落库", "AB", done.messages[1].content)
        assertEquals(AiMessageStatus.COMPLETE, done.messages[1].status)
    }

    @Test
    fun stopWritesStoppedAndKeepsGateUntilTerminalWritten() = harness {
        withKey()
        assertEquals(SendOutcome.Started, coordinator.trySend("问题"))
        gateway.delta("半截")

        val gate = CompletableDeferred<Unit>()
        repository.finishGate = gate
        coordinator.stop()

        // 终态写入被门闩挡住：停止尚未完成，新发送必须 Busy。
        assertEquals(SendOutcome.Busy, coordinator.trySend("停止中的新请求"))
        assertEquals("停止收尾期间不得发起新请求", 1, gateway.requests.size)
        assertEquals(AiPhase.Streaming, coordinator.state.value.phase)

        gate.complete(Unit)
        await { it.phase == AiPhase.Stopped }

        val assistant = repository.storedMessages()[1]
        assertEquals(AiMessageStatus.STOPPED, assistant.status)
        assertEquals("停止必须保留已收到内容", "半截", assistant.content)

        assertEquals(
            "终态写入完成且门闩释放后可再次发送",
            SendOutcome.Started,
            coordinator.trySend("停止完成后的新请求"),
        )
    }

    @Test
    fun clearStopsWaitsThenDeletesInOrder() = harness {
        withKey()
        assertEquals(SendOutcome.Started, coordinator.trySend("会被清除的问题"))
        gateway.delta("清除前的部分内容")

        coordinator.clearConversation()

        val relevant = repository.events.filter { it != "markInterrupted" }
        assertEquals(
            "清除必须先停止并等待终态写入，再删除",
            listOf("beginTurn", "finish:STOPPED", "clear"),
            relevant,
        )
        assertEquals(1, repository.clearCalls)
        assertTrue("清除后不保留任何消息", repository.storedMessages().isEmpty())
        assertEquals(AiPhase.Idle, coordinator.state.value.phase)
        assertEquals("清除成功应通知界面清空草稿", 1L, coordinator.state.value.clearGeneration)
    }

    @Test
    fun gatewayErrorsMapToFailedWithShortCodeAndPartialText() {
        val cases = listOf(
            AiError.InvalidKey to AiErrorCode.INVALID_KEY,
            AiError.InsufficientBalance to AiErrorCode.INSUFFICIENT_BALANCE,
            AiError.RateLimited to AiErrorCode.RATE_LIMITED,
            AiError.RequestRejected to AiErrorCode.REQUEST_REJECTED,
            AiError.Server(status = 500) to AiErrorCode.SERVER_ERROR,
            AiError.Network to AiErrorCode.NETWORK,
            AiError.Timeout to AiErrorCode.TIMEOUT,
            AiError.HttpError(status = 418) to AiErrorCode.HTTP_ERROR,
            AiError.Unknown to AiErrorCode.UNKNOWN,
        )

        cases.forEach { (error, code) ->
            harness {
                withKey()
                assertEquals(SendOutcome.Started, coordinator.trySend("问题"))
                gateway.delta("部分文本")
                gateway.fail(error)

                val failed = await { it.phase is AiPhase.Failed }
                assertEquals(code, (failed.phase as AiPhase.Failed).code)
                val assistant = repository.storedMessages()[1]
                assertEquals(AiMessageStatus.FAILED, assistant.status)
                assertEquals(code, assistant.errorCode)
                assertEquals("失败必须保留已收到的部分文本", "部分文本", assistant.content)
                assertEquals("失败不得自动重试", 1, gateway.requests.size)
            }
        }
    }

    @Test
    fun streamEndingWithoutCompletionBecomesStreamInterrupted() = harness {
        withKey()
        assertEquals(SendOutcome.Started, coordinator.trySend("问题"))
        gateway.delta("半截")
        gateway.endWithoutCompletion()

        val failed = await { it.phase is AiPhase.Failed }
        assertEquals(AiErrorCode.STREAM_INTERRUPTED, (failed.phase as AiPhase.Failed).code)
        assertEquals(AiMessageStatus.FAILED, repository.storedMessages()[1].status)
    }

    @Test
    fun beginTurnFailureReturnsFailedAndCreatesNoMessage() = harness {
        repository.failBeginTurn = true
        withKey()

        assertEquals(
            SendOutcome.Failed(AiErrorCode.UNKNOWN),
            coordinator.trySend("问题"),
        )
        assertEquals(0, gateway.requests.size)
        assertTrue(repository.storedMessages().isEmpty())
        assertTrue(coordinator.state.value.phase is AiPhase.Idle)
    }

    @Test
    fun requestContainsOnlyUserAndCompleteAssistantTurnsWithFixedPrompt() = harness {
        withKey()

        assertEquals(SendOutcome.Started, coordinator.trySend("q1"))
        gateway.delta("a1")
        gateway.complete()
        await { it.phase is AiPhase.Idle && it.messages.size == 2 }

        assertEquals(SendOutcome.Started, coordinator.trySend("q2"))
        gateway.delta("a2")
        gateway.complete()
        await { it.phase is AiPhase.Idle && it.messages.size == 4 }

        // 第三轮失败，留下 FAILED 助手部分文本，不得进入后续请求。
        assertEquals(SendOutcome.Started, coordinator.trySend("q3"))
        gateway.delta("半截回复")
        gateway.fail(AiError.Network)
        await { it.phase is AiPhase.Failed }

        assertEquals(SendOutcome.Started, coordinator.trySend("q4"))

        val request = gateway.requests.last()
        assertEquals("必须携带固定系统提示", AiSystemPrompt.TEXT, request.systemPrompt)
        assertTrue(request.systemPrompt.contains("无法访问"))
        assertTrue(request.systemPrompt.contains("不要假设当前页面"))

        assertEquals(
            listOf(
                AiRole.USER to "q1",
                AiRole.ASSISTANT to "a1",
                AiRole.USER to "q2",
                AiRole.ASSISTANT to "a2",
                AiRole.USER to "q3",
                AiRole.USER to "q4",
            ),
            request.turns.map { it.role to it.content },
        )
        assertFalse(
            "STOPPED/FAILED 部分文本不得作为上下文",
            request.turns.any { it.content == "半截回复" },
        )

        // 结构上只由固定系统提示与对话轮次承载：AiRequest 只有这两个构造字段（静态核对见报告）。
        val payload = request.turns.joinToString("|") { it.content }
        listOf("待办", "筛选", "当前页面", "2026-", "可见内容").forEach { forbidden ->
            assertFalse("请求体不得包含页面数据：$forbidden", payload.contains(forbidden))
        }

        // 序列化后仍只包含 system + 对话消息，不新增页面/日期/筛选/可见内容字段。
        val json = io.github.mazilin.nautile.platform.network.DeepSeekRequestJson.build(request)
        listOf("\"page\"", "\"date\"", "\"filter\"", "\"visibleContent\"", "\"context\"").forEach {
            assertFalse("请求 JSON 不得包含页面上下文字段：$it", json.contains(it))
        }
        assertEquals(
            "序列化消息数应等于 1 条系统提示 + 对话轮次",
            1 + request.turns.size,
            Regex("\"role\"").findAll(json).count(),
        )
    }

    @Test
    fun startupRestoresStreamingAssistantAsStopped() = harness(
        initialMessages = listOf(
            AiMessage(
                id = AiMessageId(1),
                sessionId = AiSessionId(1),
                role = AiRole.USER,
                content = "上次的问题",
                status = AiMessageStatus.COMPLETE,
                errorCode = null,
                createdAt = Instant.ofEpochMilli(1_000),
            ),
            AiMessage(
                id = AiMessageId(2),
                sessionId = AiSessionId(1),
                role = AiRole.ASSISTANT,
                content = "上次的半截回答",
                status = AiMessageStatus.STREAMING,
                errorCode = null,
                createdAt = Instant.ofEpochMilli(1_000),
            ),
        ),
    ) {
        val restored = await {
            it.messages.size == 2 && it.messages[1].status == AiMessageStatus.STOPPED
        }

        assertEquals(1, repository.markInterruptedCalls)
        assertEquals(
            "启动必须先做 STREAMING→STOPPED 恢复再观察消息",
            "markInterrupted",
            repository.events.first(),
        )
        assertEquals(AiMessageStatus.COMPLETE, restored.messages[0].status)
        assertEquals("上次的半截回答", restored.messages[1].content)
    }

    @Test
    fun refreshKeyPresenceReflectsStoreChanges() = harness {
        coordinator.refreshKeyPresence()
        assertTrue("未配置时 keyMissing 应为 true", coordinator.state.value.keyMissing)

        withKey()
        coordinator.refreshKeyPresence()
        assertFalse("保存 Key 后 keyMissing 应为 false", coordinator.state.value.keyMissing)

        keys.clear()
        coordinator.refreshKeyPresence()
        assertTrue("清除 Key 后 keyMissing 应为 true", coordinator.state.value.keyMissing)
    }
}
