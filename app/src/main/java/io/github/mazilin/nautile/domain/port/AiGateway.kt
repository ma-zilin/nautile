package io.github.mazilin.nautile.domain.port

import io.github.mazilin.nautile.domain.model.AiRequest
import kotlinx.coroutines.flow.Flow

/**
 * AI 网关端口；实现放 platform/network，domain 不感知 HTTP/Android。
 *
 * 约定：
 * - 收集取消即取消网络请求；
 * - 正常结束必须发射 [AiStreamEvent.Completed]；
 * - 失败抛 [io.github.mazilin.nautile.domain.model.AiGatewayException]；
 * - 取消抛 `CancellationException`，不包装成业务错误。
 */
interface AiGateway {

    fun stream(request: AiRequest): Flow<AiStreamEvent>
}

/** 流式增量事件；错误不以事件发射，避免“流正常关闭但携带失败”的歧义。 */
sealed interface AiStreamEvent {

    /** 一段增量文本；可能为空串，由实现过滤。 */
    data class Delta(val text: String) : AiStreamEvent

    /** 收到结束标记，回复正常完成。 */
    data object Completed : AiStreamEvent
}
