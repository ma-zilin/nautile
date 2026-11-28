package io.github.mazilin.nautile.platform.network

import io.github.mazilin.nautile.domain.ai.AiSecretKeys
import io.github.mazilin.nautile.domain.model.AiError
import io.github.mazilin.nautile.domain.model.AiGatewayException
import io.github.mazilin.nautile.domain.model.AiRequest
import io.github.mazilin.nautile.domain.port.AiGateway
import io.github.mazilin.nautile.domain.port.AiStreamEvent
import io.github.mazilin.nautile.domain.port.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * DeepSeek 流式网关（架构 §5）：HttpURLConnection + SSE。
 *
 * 约定：
 * - 自行从 [SecretStore] 读取 Key；[AiRequest] 不含 Key；
 * - 单请求互斥由上层协调器保证；本类不排队、不自动重试、不跟随重定向（避免把 Authorization 带到其他主机）；
 * - 收集取消即 `disconnect()`，阻塞读尽快结束；取消抛 `CancellationException`，不包装成业务错误；
 * - 不记录 Key、请求体或响应原文；错误只映射为 [AiError]。
 */
class DeepSeekAiGateway(
    private val secretStore: SecretStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AiGateway {

    override fun stream(request: AiRequest): Flow<AiStreamEvent> = channelFlow {
        val key = withContext(ioDispatcher) { secretStore.get(AiSecretKeys.DEEPSEEK_API_KEY) }
        if (key.isNullOrBlank()) throw AiGatewayException(AiError.KeyMissing)

        val body = DeepSeekRequestJson.build(request)
        val connectionRef = AtomicReference<HttpURLConnection?>(null)
        val reading = launch(ioDispatcher) {
            try {
                val connection = openConnection(key, body)
                connectionRef.set(connection)
                ensureActive()

                val status = connection.responseCode
                if (status !in 200..299) throw AiGatewayException(mapHttpStatus(status))

                val parser = SseStreamParser()
                val deadlineNanos = System.nanoTime() +
                    TimeUnit.MILLISECONDS.toNanos(DeepSeekConfig.OVERALL_TIMEOUT_MS.toLong())
                var done = false
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (!done) {
                        if (System.nanoTime() > deadlineNanos) {
                            throw AiGatewayException(AiError.Timeout)
                        }
                        val line = reader.readLine() ?: break
                        for (payload in parser.feed(line)) {
                            if (payload == SseStreamParser.DONE_SENTINEL) {
                                done = true
                                break
                            }
                            val content = DeepSeekSseParser.contentOf(payload)
                            if (!content.isNullOrEmpty()) send(AiStreamEvent.Delta(content))
                        }
                    }
                }
                // EOF 缺少 [DONE] 视为流提前中断，不伪造完成。
                if (!done) throw AiGatewayException(AiError.StreamInterrupted)
                send(AiStreamEvent.Completed)
                close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!isActive) throw CancellationException()
                close(AiGatewayException(mapError(e), e))
            } finally {
                connectionRef.getAndSet(null)?.disconnect()
            }
        }
        awaitClose {
            reading.cancel()
            connectionRef.getAndSet(null)?.disconnect()
        }
    }

    private fun openConnection(key: String, body: String): HttpURLConnection {
        val connection = URL(DeepSeekConfig.ENDPOINT).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = DeepSeekConfig.CONNECT_TIMEOUT_MS
        connection.readTimeout = DeepSeekConfig.READ_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Authorization", "Bearer $key")
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "text/event-stream")
        connection.outputStream.use { output ->
            output.write(body.toByteArray(Charsets.UTF_8))
        }
        return connection
    }
}
