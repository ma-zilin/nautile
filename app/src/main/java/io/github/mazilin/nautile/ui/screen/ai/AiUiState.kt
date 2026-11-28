package io.github.mazilin.nautile.ui.screen.ai

import io.github.mazilin.nautile.domain.ai.AiPhase
import io.github.mazilin.nautile.domain.model.AiMessage
import io.github.mazilin.nautile.domain.model.AiMessageId

/**
 * AI 页 UI 状态（架构 §7.2）。
 *
 * 业务字段来自进程级 [io.github.mazilin.nautile.domain.usecase.AiChatCoordinator]；
 * [draft]/[stopping]/[clearing] 是页面本地状态，不进入 domain。
 */
data class AiUiState(
    val messages: List<AiMessage> = emptyList(),
    val phase: AiPhase = AiPhase.Idle,
    val streamingMessageId: AiMessageId? = null,
    val streamingText: String = "",
    val keyMissing: Boolean = false,
    val draft: String = "",
    /** UI 本地：已点停止、等待终态写入；domain 阶段仍为 Streaming。 */
    val stopping: Boolean = false,
    /** UI 本地：清除会话进行中。 */
    val clearing: Boolean = false,
) {

    /** 是否正在流式生成（不含停止收尾）。 */
    val streaming: Boolean get() = phase is AiPhase.Streaming

    /** 发送启用条件：草稿非空、非流式/停止收尾/清除中（06 §4.3）。 */
    val sendEnabled: Boolean
        get() = draft.isNotBlank() && !streaming && !stopping && !clearing

    /** 停止按钮只在流式中且尚未点停止时显示。 */
    val stopVisible: Boolean get() = streaming && !stopping && !clearing
}
