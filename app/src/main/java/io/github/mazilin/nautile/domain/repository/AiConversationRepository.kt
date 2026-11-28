package io.github.mazilin.nautile.domain.repository

import io.github.mazilin.nautile.domain.model.AiErrorCode
import io.github.mazilin.nautile.domain.model.AiMessage
import io.github.mazilin.nautile.domain.model.AiMessageId
import io.github.mazilin.nautile.domain.model.AiMessageStatus
import io.github.mazilin.nautile.domain.model.AiSessionId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * AI 会话数据端口（架构 §2.4、§3.2）。
 *
 * 单会话；事务边界由 data 层实现（beginTurn 与 clearConversation 均在单个 Room 事务内）。
 */
interface AiConversationRepository {

    /** 按消息 id 升序观察全部消息。 */
    fun observeMessages(): Flow<List<AiMessage>>

    /**
     * 开始一轮对话：确保单会话存在 → 插入用户消息（COMPLETE）→ 插入助手占位（STREAMING）→ 更新会话时间。
     *
     * 事务化保证重启时至少能恢复“问过但没答完”的顺序与状态。
     */
    suspend fun beginTurn(userText: String, at: Instant): AiTurnIds

    /**
     * 写入助手终态。
     *
     * 行已被“清除会话”删除时更新 0 行，属安全无操作，不复活数据。
     */
    suspend fun finishAssistant(
        messageId: AiMessageId,
        content: String,
        status: AiMessageStatus,
        errorCode: AiErrorCode?,
        at: Instant,
    )

    /** 清除会话：先删消息再删会话（外键 RESTRICT 下顺序不能反），任一失败整体回滚。 */
    suspend fun clearConversation()

    /** 启动恢复：把仍为 STREAMING 的助手行改为 STOPPED。 */
    suspend fun markInterruptedAsStopped()
}

/** 一轮对话产生的三个 id。 */
data class AiTurnIds(
    val sessionId: AiSessionId,
    val userMessageId: AiMessageId,
    val assistantMessageId: AiMessageId,
)
