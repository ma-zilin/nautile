package io.github.mazilin.nautile.platform.network

import io.github.mazilin.nautile.domain.model.AiError
import io.github.mazilin.nautile.domain.model.AiGatewayException
import org.json.JSONException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * HTTP 状态码 → [AiError]（纯函数，可 JVM 单测；架构 §5.5）。
 *
 * 只映射为业务错误，不读取/打印响应体；3xx 归入 [AiError.HttpError]。
 */
fun mapHttpStatus(status: Int): AiError = when {
    status == 401 || status == 403 -> AiError.InvalidKey
    status == 402 -> AiError.InsufficientBalance
    status == 429 -> AiError.RateLimited
    status == 400 || status == 422 -> AiError.RequestRejected
    status in 500..599 -> AiError.Server(status)
    else -> AiError.HttpError(status)
}

/**
 * 异常 → [AiError]（纯函数，可 JVM 单测）。
 *
 * 取消（`CancellationException`）不进入此函数；调用方必须原样抛出，停止不显示为失败。
 */
fun mapError(error: Throwable): AiError = when (error) {
    is AiGatewayException -> error.error
    is SocketTimeoutException -> AiError.Timeout
    is InterruptedIOException -> AiError.Timeout
    is JSONException -> AiError.StreamInterrupted
    is IOException -> AiError.Network
    else -> AiError.Unknown
}
