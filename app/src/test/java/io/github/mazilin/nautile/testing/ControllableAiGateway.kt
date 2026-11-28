package io.github.mazilin.nautile.testing

import io.github.mazilin.nautile.domain.model.AiError
import io.github.mazilin.nautile.domain.model.AiGatewayException
import io.github.mazilin.nautile.domain.model.AiRequest
import io.github.mazilin.nautile.domain.port.AiGateway
import io.github.mazilin.nautile.domain.port.AiStreamEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 可控 AI 网关替身（G3 协调器/ViewModel 测试用）。
 *
 * 测试线程通过 [delta]/[complete]/[fail]/[endWithoutCompletion] 精确控制流；
 * 不发起网络请求、不构造 HTTP 状态码；[requests] 保留每次请求体用于断言。
 */
class ControllableAiGateway : AiGateway {

    private sealed interface Script {
        data class Delta(val text: String) : Script
        data object Completed : Script
        data class Failed(val error: AiError) : Script
        data object EndWithoutCompletion : Script
    }

    private val scripts = Channel<Script>(Channel.UNLIMITED)

    /** 每次开始收集时记录的请求体，按调用顺序保存。 */
    val requests = mutableListOf<AiRequest>()

    override fun stream(request: AiRequest): Flow<AiStreamEvent> = flow {
        requests += request
        for (script in scripts) {
            when (script) {
                is Script.Delta -> emit(AiStreamEvent.Delta(script.text))
                Script.Completed -> {
                    emit(AiStreamEvent.Completed)
                    return@flow
                }

                is Script.Failed -> throw AiGatewayException(script.error)
                Script.EndWithoutCompletion -> return@flow
            }
        }
    }

    fun delta(text: String) {
        scripts.trySend(Script.Delta(text))
    }

    fun complete() {
        scripts.trySend(Script.Completed)
    }

    fun fail(error: AiError) {
        scripts.trySend(Script.Failed(error))
    }

    fun endWithoutCompletion() {
        scripts.trySend(Script.EndWithoutCompletion)
    }
}
