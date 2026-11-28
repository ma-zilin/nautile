package io.github.mazilin.nautile.domain.ai

import io.github.mazilin.nautile.domain.model.AiErrorCode
import io.github.mazilin.nautile.domain.model.AiMessage
import io.github.mazilin.nautile.domain.model.AiMessageId

/**
 * AI 页业务阶段（架构 §2.4、06 §5.2）。
 *
 * 仅表达请求层面；`Stopping`/`Clearing` 属于 UI 本地展示状态，不进入该密封接口。
 */
sealed interface AiPhase {

    /** 空闲：可输入、可发送。 */
    data object Idle : AiPhase

    /** 流式生成中：最后一条助手占位行仍在接收增量。 */
    data object Streaming : AiPhase

    /** 已停止：用户停止或进程中断恢复；发送恢复可用。 */
    data object Stopped : AiPhase

    /** 失败：助手消息已落库为 FAILED，携带可持久化短码。 */
    data class Failed(val code: AiErrorCode) : AiPhase
}

/**
 * 进程级 AI 对话状态（架构 §2.4）。
 *
 * [messages] 来自仓库观察流（按 id 升序）；[streamingText] 只在内存中累积，
 * 终态才一次性写入 Room。[keyMissing] 是配置状态，可与任意 [phase] 并存。
 */
data class AiChatState(
    val messages: List<AiMessage> = emptyList(),
    val phase: AiPhase = AiPhase.Idle,
    val streamingMessageId: AiMessageId? = null,
    val streamingText: String = "",
    val keyMissing: Boolean = false,
    /**
     * 清除会话成功计数；每次成功清除自增。
     *
     * 只供 UI 观察以清空本地草稿（用户 2026-11-28 确认：清除后 AI 页草稿清空），
     * 不参与请求构造、门闩或落库语义。
     */
    val clearGeneration: Long = 0L,
)

/**
 * 一次发送尝试的结果（架构 §2.4）。
 *
 * 只有 [Started] 表示 `beginTurn` 已成功、请求已启动；其余结果都必须保留当前草稿。
 */
sealed interface SendOutcome {

    data object Started : SendOutcome

    /** 已有请求或清除进行中；不排队。 */
    data object Busy : SendOutcome

    /** 未配置 Key：不写 Room、不清草稿、不发网络。 */
    data object KeyMissing : SendOutcome

    /** 请求开始前失败（如 `beginTurn` 落库失败）；不创建失败消息。 */
    data class Failed(val code: AiErrorCode) : SendOutcome
}
