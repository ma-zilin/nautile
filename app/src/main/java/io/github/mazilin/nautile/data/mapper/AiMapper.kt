package io.github.mazilin.nautile.data.mapper

import io.github.mazilin.nautile.data.db.entity.AiMessageEntity
import io.github.mazilin.nautile.data.db.entity.AiSessionEntity
import io.github.mazilin.nautile.domain.model.AiErrorCode
import io.github.mazilin.nautile.domain.model.AiMessage
import io.github.mazilin.nautile.domain.model.AiMessageId
import io.github.mazilin.nautile.domain.model.AiMessageStatus
import io.github.mazilin.nautile.domain.model.AiRole
import io.github.mazilin.nautile.domain.model.AiSession
import io.github.mazilin.nautile.domain.model.AiSessionId
import java.time.Instant

/**
 * AI entity ↔ domain 纯函数（架构 §3.2）。
 *
 * 枚举按文本名解析；未知值回落而不是抛异常，防止旧数据或未来枚举改名导致启动崩溃。
 */
object AiMapper {

    fun toDomain(entity: AiSessionEntity): AiSession = AiSession(
        id = AiSessionId(entity.id),
        createdAt = Instant.ofEpochMilli(entity.createdAt),
        updatedAt = Instant.ofEpochMilli(entity.updatedAt),
    )

    fun toDomain(entity: AiMessageEntity): AiMessage = AiMessage(
        id = AiMessageId(entity.id),
        sessionId = AiSessionId(entity.sessionId),
        role = parseRole(entity.role),
        content = entity.content,
        status = parseStatus(entity.status),
        errorCode = entity.errorCode?.let(::parseErrorCode),
        createdAt = Instant.ofEpochMilli(entity.createdAt),
    )

    private fun parseRole(raw: String): AiRole =
        AiRole.entries.firstOrNull { it.name == raw } ?: AiRole.USER

    private fun parseStatus(raw: String): AiMessageStatus =
        AiMessageStatus.entries.firstOrNull { it.name == raw } ?: AiMessageStatus.FAILED

    private fun parseErrorCode(raw: String): AiErrorCode =
        AiErrorCode.entries.firstOrNull { it.name == raw } ?: AiErrorCode.UNKNOWN
}
