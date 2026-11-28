package io.github.mazilin.nautile.testing

import io.github.mazilin.nautile.domain.model.AiErrorCode
import io.github.mazilin.nautile.domain.model.AiMessage
import io.github.mazilin.nautile.domain.model.AiMessageId
import io.github.mazilin.nautile.domain.model.AiMessageStatus
import io.github.mazilin.nautile.domain.model.AiRole
import io.github.mazilin.nautile.domain.model.AiSessionId
import io.github.mazilin.nautile.domain.repository.AiConversationRepository
import io.github.mazilin.nautile.domain.repository.AiTurnIds
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 内存版 AI 会话仓库（G3 JVM 测试用）；不接触 Android/Room。
 *
 * 语义与 [io.github.mazilin.nautile.data.repository.RoomAiConversationRepository] 对齐：
 * beginTurn 插入 USER(COMPLETE) + ASSISTANT(STREAMING)；finishAssistant 原地更新；
 * clearConversation 清空；markInterruptedAsStopped 只把 STREAMING 改为 STOPPED。
 *
 * 额外提供测试观测点：[events] 记录调用顺序，[finishGate] 可阻塞终态写入以验证
 * “停止完成前不可发送”，[failBeginTurn] 模拟请求开始前落库失败。
 */
class InMemoryAiConversationRepository(
    initialMessages: List<AiMessage> = emptyList(),
) : AiConversationRepository {

    private val messages = MutableStateFlow(initialMessages.sortedBy { it.id.value })
    private var nextId = (initialMessages.maxOfOrNull { it.id.value } ?: 0L) + 1L
    private var sessionId: Long = initialMessages.firstOrNull()?.sessionId?.value ?: 0L

    /** 有序调用记录，例如 beginTurn / finish:STOPPED / clear。 */
    val events = mutableListOf<String>()

    var beginTurnCalls = 0
        private set

    /** 终态写入记录（状态与内容），按调用顺序。 */
    val finishCalls = mutableListOf<Pair<AiMessageStatus, String>>()

    var clearCalls = 0
        private set

    var markInterruptedCalls = 0
        private set

    /** 非空时 finishAssistant 先等待该门闩，用于验证停止收尾期间的门闩语义。 */
    var finishGate: CompletableDeferred<Unit>? = null

    /** 为 true 时 beginTurn 抛错，模拟请求开始前落库失败。 */
    var failBeginTurn: Boolean = false

    override fun observeMessages(): Flow<List<AiMessage>> = messages.asStateFlow()

    override suspend fun beginTurn(userText: String, at: Instant): AiTurnIds {
        beginTurnCalls++
        events += "beginTurn"
        if (failBeginTurn) error("beginTurn failed")
        if (sessionId == 0L) sessionId = at.toEpochMilli().coerceAtLeast(1L)
        val session = AiSessionId(sessionId)
        val userId = AiMessageId(nextId++)
        val assistantId = AiMessageId(nextId++)
        messages.value = messages.value.sortedBy { it.id.value } + listOf(
            AiMessage(
                id = userId,
                sessionId = session,
                role = AiRole.USER,
                content = userText,
                status = AiMessageStatus.COMPLETE,
                errorCode = null,
                createdAt = at,
            ),
            AiMessage(
                id = assistantId,
                sessionId = session,
                role = AiRole.ASSISTANT,
                content = "",
                status = AiMessageStatus.STREAMING,
                errorCode = null,
                createdAt = at,
            ),
        )
        return AiTurnIds(
            sessionId = session,
            userMessageId = userId,
            assistantMessageId = assistantId,
        )
    }

    override suspend fun finishAssistant(
        messageId: AiMessageId,
        content: String,
        status: AiMessageStatus,
        errorCode: AiErrorCode?,
        at: Instant,
    ) {
        finishGate?.await()
        events += "finish:${status.name}"
        finishCalls += status to content
        messages.value = messages.value.map { message ->
            if (message.id == messageId) {
                message.copy(content = content, status = status, errorCode = errorCode)
            } else {
                message
            }
        }
    }

    override suspend fun clearConversation() {
        events += "clear"
        clearCalls++
        messages.value = emptyList()
        sessionId = 0L
    }

    override suspend fun markInterruptedAsStopped() {
        markInterruptedCalls++
        events += "markInterrupted"
        messages.value = messages.value.map { message ->
            if (message.role == AiRole.ASSISTANT && message.status == AiMessageStatus.STREAMING) {
                message.copy(status = AiMessageStatus.STOPPED)
            } else {
                message
            }
        }
    }

    /** 当前落库后的消息快照（断言终态用）。 */
    fun storedMessages(): List<AiMessage> = messages.value
}
