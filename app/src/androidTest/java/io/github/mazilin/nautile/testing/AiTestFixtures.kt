package io.github.mazilin.nautile.testing

import io.github.mazilin.nautile.domain.model.AiError
import io.github.mazilin.nautile.domain.model.AiErrorCode
import io.github.mazilin.nautile.domain.model.AiGatewayException
import io.github.mazilin.nautile.domain.model.AiMessage
import io.github.mazilin.nautile.domain.model.AiMessageId
import io.github.mazilin.nautile.domain.model.AiMessageStatus
import io.github.mazilin.nautile.domain.model.AiRequest
import io.github.mazilin.nautile.domain.port.AiGateway
import io.github.mazilin.nautile.domain.port.AiStreamEvent
import io.github.mazilin.nautile.domain.port.SecretStore
import io.github.mazilin.nautile.domain.repository.AiConversationRepository
import io.github.mazilin.nautile.domain.repository.AiTurnIds
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * G3 Compose/instrumentation 夹具；只用于测试，不进入生产代码。
 *
 * 全部为进程内替身：不访问真实 Keystore、不发起网络请求、不写真实用户数据库。
 */

/** 内存 SecretStore；不接触 Android Keystore，不写真实 Key。 */
class FakeSecretStore : SecretStore {

    private val values = mutableMapOf<String, String>()

    override fun put(key: String, value: String) {
        values[key] = value
    }

    override fun get(key: String): String? = values[key]

    override fun delete(key: String) {
        values.remove(key)
    }
}

/**
 * 脚本化 AI 网关：测试线程用 [delta]/[complete]/[fail]/[endWithoutCompletion] 控制流。
 *
 * 不构造 HTTP 状态码；[requests] 记录请求体供断言（断言后不自动重试）。
 */
class ScriptedAiGateway : AiGateway {

    private sealed interface Script {
        data class Delta(val text: String) : Script
        data object Completed : Script
        data class Failed(val error: AiError) : Script
        data object EndWithoutCompletion : Script
    }

    private val scripts = Channel<Script>(Channel.UNLIMITED)

    val requests = CopyOnWriteArrayList<AiRequest>()

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

/**
 * 可注入 beginTurn 失败的仓库包装；其余操作原样委托真实 Room 仓库。
 *
 * 用于验证“请求开始前落库失败不创建失败消息、保留草稿、显示提示条”。
 */
class FailableAiConversationRepository(
    private val delegate: AiConversationRepository,
) : AiConversationRepository {

    @Volatile
    var failBeginTurn: Boolean = false

    override fun observeMessages(): Flow<List<AiMessage>> = delegate.observeMessages()

    override suspend fun beginTurn(userText: String, at: Instant): AiTurnIds {
        if (failBeginTurn) error("beginTurn failed")
        return delegate.beginTurn(userText, at)
    }

    override suspend fun finishAssistant(
        messageId: AiMessageId,
        content: String,
        status: AiMessageStatus,
        errorCode: AiErrorCode?,
        at: Instant,
    ) = delegate.finishAssistant(messageId, content, status, errorCode, at)

    override suspend fun clearConversation() = delegate.clearConversation()

    override suspend fun markInterruptedAsStopped() = delegate.markInterruptedAsStopped()
}
