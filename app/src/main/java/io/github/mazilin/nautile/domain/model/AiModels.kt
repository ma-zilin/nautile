package io.github.mazilin.nautile.domain.model

import java.time.Instant

/** AI 会话 ID 值类；以 Long 承载。 */
@JvmInline
value class AiSessionId(val value: Long)

/** AI 消息 ID 值类；以 Long 承载。 */
@JvmInline
value class AiMessageId(val value: Long)

/**
 * AI 会话（Phase A 只有一段对话，仍保留会话实体便于未来多会话与消息归属）。
 *
 * 时间统一用 [Instant]；持久化为 UTC epoch 毫秒。
 */
data class AiSession(
    val id: AiSessionId,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** 消息角色；持久化为枚举名文本。 */
enum class AiRole { USER, ASSISTANT }

/**
 * 消息状态。
 *
 * [STREAMING] 只用于助手占位行；进程死亡后由启动恢复改为 [STOPPED]。
 */
enum class AiMessageStatus {
    STREAMING,
    COMPLETE,
    STOPPED,
    FAILED,
}

/**
 * 一条已持久化的 AI 消息。
 *
 * @property content 占位行为空串，不存 null；用户消息只写 [AiMessageStatus.COMPLETE]
 * @property errorCode 仅助手 [AiMessageStatus.FAILED] 时有值；只存稳定短码，不存敏感信息
 * @property createdAt 消息行创建时刻；用户消息与助手占位行使用同一发送时刻
 */
data class AiMessage(
    val id: AiMessageId,
    val sessionId: AiSessionId,
    val role: AiRole,
    val content: String,
    val status: AiMessageStatus,
    val errorCode: AiErrorCode?,
    val createdAt: Instant,
)

/** 发送给模型的单轮消息；只包含已持久化的文案，不包含页面/筛选/可见内容。 */
data class AiTurn(
    val role: AiRole,
    val content: String,
)

/** 固定系统提示 + 本次对话消息；不包含 Key，也不包含任何页面数据。 */
data class AiRequest(
    val systemPrompt: String,
    val turns: List<AiTurn>,
)
