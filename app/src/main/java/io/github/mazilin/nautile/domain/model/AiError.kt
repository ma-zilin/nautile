package io.github.mazilin.nautile.domain.model

/**
 * AI 错误的持久化短码（架构 §2.2）。
 *
 * 只存稳定短码，不存 HTTP 状态、错误原文或对话内容；旧数据遇到未知码回落 [UNKNOWN]，
 * 不让枚举改名或旧库数据导致启动崩溃。
 */
enum class AiErrorCode {
    KEY_MISSING,
    INVALID_KEY,
    INSUFFICIENT_BALANCE,
    RATE_LIMITED,
    REQUEST_REJECTED,
    SERVER_ERROR,
    NETWORK,
    TIMEOUT,
    STREAM_INTERRUPTED,
    HTTP_ERROR,
    UNKNOWN,
}

/**
 * AI 运行时错误（可含 HTTP 状态码）。
 *
 * UI 文案映射放 ui 层；domain 不出现中文界面文案。
 */
sealed interface AiError {
    data object KeyMissing : AiError
    data object InvalidKey : AiError
    data object InsufficientBalance : AiError
    data object RateLimited : AiError
    data object RequestRejected : AiError
    data class Server(val status: Int) : AiError
    data object Network : AiError
    data object Timeout : AiError
    data object StreamInterrupted : AiError
    data class HttpError(val status: Int) : AiError
    data object Unknown : AiError
}

/**
 * 运行时错误 → 持久化短码。
 *
 * [AiError.KeyMissing] 通常不落库：未开始请求就不创建消息。
 */
fun AiError.toErrorCode(): AiErrorCode = when (this) {
    AiError.KeyMissing -> AiErrorCode.KEY_MISSING
    AiError.InvalidKey -> AiErrorCode.INVALID_KEY
    AiError.InsufficientBalance -> AiErrorCode.INSUFFICIENT_BALANCE
    AiError.RateLimited -> AiErrorCode.RATE_LIMITED
    AiError.RequestRejected -> AiErrorCode.REQUEST_REJECTED
    is AiError.Server -> AiErrorCode.SERVER_ERROR
    AiError.Network -> AiErrorCode.NETWORK
    AiError.Timeout -> AiErrorCode.TIMEOUT
    AiError.StreamInterrupted -> AiErrorCode.STREAM_INTERRUPTED
    is AiError.HttpError -> AiErrorCode.HTTP_ERROR
    AiError.Unknown -> AiErrorCode.UNKNOWN
}

/**
 * 网关业务异常；携带可持久化的 [error]。
 *
 * 取消（`CancellationException`）不包装成该异常，由调用方按停止处理。
 */
class AiGatewayException(
    val error: AiError,
    cause: Throwable? = null,
) : Exception(cause)
