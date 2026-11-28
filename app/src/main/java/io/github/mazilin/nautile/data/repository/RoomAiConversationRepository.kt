package io.github.mazilin.nautile.data.repository

import androidx.room.withTransaction
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.db.entity.AiMessageEntity
import io.github.mazilin.nautile.data.db.entity.AiSessionEntity
import io.github.mazilin.nautile.data.mapper.AiMapper
import io.github.mazilin.nautile.domain.model.AiErrorCode
import io.github.mazilin.nautile.domain.model.AiMessage
import io.github.mazilin.nautile.domain.model.AiMessageId
import io.github.mazilin.nautile.domain.model.AiMessageStatus
import io.github.mazilin.nautile.domain.model.AiRole
import io.github.mazilin.nautile.domain.model.AiSessionId
import io.github.mazilin.nautile.domain.repository.AiConversationRepository
import io.github.mazilin.nautile.domain.repository.AiTurnIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Room AI 会话仓库（架构 §3.2）。
 *
 * 只依赖 Room 与 domain；事务边界在仓库层统一处理，UI/协调器不直接操作 DAO。
 */
class RoomAiConversationRepository(
    private val database: NautileDatabase,
) : AiConversationRepository {

    private val dao = database.aiConversationDao()

    override fun observeMessages(): Flow<List<AiMessage>> =
        dao.observeAll().map { entities -> entities.map(AiMapper::toDomain) }

    override suspend fun beginTurn(userText: String, at: Instant): AiTurnIds =
        database.withTransaction {
            val timestamp = at.toEpochMilli()
            val sessionId = dao.firstSession()?.id
                ?: dao.insertSession(
                    AiSessionEntity(createdAt = timestamp, updatedAt = timestamp),
                )
            val userMessageId = dao.insertMessage(
                AiMessageEntity(
                    sessionId = sessionId,
                    role = AiRole.USER.name,
                    content = userText,
                    status = AiMessageStatus.COMPLETE.name,
                    errorCode = null,
                    createdAt = timestamp,
                ),
            )
            val assistantMessageId = dao.insertMessage(
                AiMessageEntity(
                    sessionId = sessionId,
                    role = AiRole.ASSISTANT.name,
                    content = "",
                    status = AiMessageStatus.STREAMING.name,
                    errorCode = null,
                    createdAt = timestamp,
                ),
            )
            dao.touchSession(sessionId, timestamp)
            AiTurnIds(
                sessionId = AiSessionId(sessionId),
                userMessageId = AiMessageId(userMessageId),
                assistantMessageId = AiMessageId(assistantMessageId),
            )
        }

    override suspend fun finishAssistant(
        messageId: AiMessageId,
        content: String,
        status: AiMessageStatus,
        errorCode: AiErrorCode?,
        at: Instant,
    ) {
        database.withTransaction {
            dao.finishMessage(
                id = messageId.value,
                content = content,
                status = status.name,
                errorCode = errorCode?.name,
            )
            // 消息已被清除时该更新不命中任何行，不会复活数据。
            dao.touchSessionOfMessage(messageId.value, at.toEpochMilli())
        }
    }

    override suspend fun clearConversation() {
        database.withTransaction {
            // 外键为 RESTRICT：必须先删消息，再删会话。
            dao.deleteAllMessages()
            dao.deleteAllSessions()
        }
    }

    override suspend fun markInterruptedAsStopped() {
        dao.markStreamingAsStopped()
    }
}
