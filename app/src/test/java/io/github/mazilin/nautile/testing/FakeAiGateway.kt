package io.github.mazilin.nautile.testing

import io.github.mazilin.nautile.domain.model.AiRequest
import io.github.mazilin.nautile.domain.port.AiGateway
import io.github.mazilin.nautile.domain.port.AiStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 固定返回值的 AI 网关替身；不发起任何网络请求。
 *
 * 在 [AiGateway] 层发射增量与完成事件，不产生 HTTP 状态码；
 * 可用 [requests] 断言请求体（只含对话消息与固定系统提示）。
 */
class FakeAiGateway(private val response: String = "fake-response") : AiGateway {

    val requests = mutableListOf<AiRequest>()

    override fun stream(request: AiRequest): Flow<AiStreamEvent> = flow {
        requests += request
        if (response.isNotEmpty()) emit(AiStreamEvent.Delta(response))
        emit(AiStreamEvent.Completed)
    }
}
