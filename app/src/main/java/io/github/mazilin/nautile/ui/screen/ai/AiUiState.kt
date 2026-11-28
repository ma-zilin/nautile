package io.github.mazilin.nautile.ui.screen.ai

import io.github.mazilin.nautile.domain.ai.AiPhase
import io.github.mazilin.nautile.domain.model.AiErrorCode
import io.github.mazilin.nautile.domain.model.AiMessage
import io.github.mazilin.nautile.domain.model.AiMessageId

/**
 * AI 页 UI 状态（架构 §7.2）。
 *
 * 业务字段来自进程级 [io.github.mazilin.nautile.domain.usecase.AiChatCoordinator]；
 * [draft]/[stopping]/[clearing]/[sendError] 是页面本地状态，不进入 domain。
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
    /** UI 本地：请求开始前的发送失败（如落库失败），用提示条展示；不创建失败消息。 */
    val sendError: AiErrorCode? = null,
) {

    /** 是否正在流式生成（不含停止收尾）。 */
    val streaming: Boolean get() = phase is AiPhase.Streaming

    /** 发送启用条件：草稿非空、有 Key、非流式/停止收尾/清除中（06 §4.3）。 */
    val sendEnabled: Boolean
        get() = draft.isNotBlank() && !keyMissing && !streaming && !stopping && !clearing

    /**
     * 停止图标是否显示：流式或停止收尾显示停止；清除中不显示停止。
     *
     * 停止收尾期间保持停止图标并置灰，避免按钮在“停止/发送”之间跳变（06 §2.3/§4.3）。
     */
    val stopVisible: Boolean get() = (streaming || stopping) && !clearing

    /** 停止可点：仅流式中且尚未点停止、未清除时（06 §4.3）。 */
    val stopEnabled: Boolean get() = streaming && !stopping && !clearing
}
