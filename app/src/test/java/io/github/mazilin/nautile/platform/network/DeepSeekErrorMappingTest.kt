package io.github.mazilin.nautile.platform.network

import io.github.mazilin.nautile.domain.model.AiError
import io.github.mazilin.nautile.domain.model.AiGatewayException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DeepSeek HTTP/异常映射纯函数（架构 §5.5；AC-A-09 错误码、AC-AN-06 超时/服务端/断流）。
 *
 * 注意：这里只证明状态码/异常→[AiError] 的映射规则，不代表真实接口联调；
 * `org.json` 的 `JSONException` 在 JVM 单测是 Android stub，断流映射改由 androidTest 覆盖。
 * 取消（`CancellationException`）不进入本函数，由网关原样抛出，故不在此断言为业务错误。
 */
class DeepSeekErrorMappingTest {

    @Test
    fun http400And422MapToRequestRejected() {
        assertEquals(AiError.RequestRejected, mapHttpStatus(400))
        assertEquals(AiError.RequestRejected, mapHttpStatus(422))
    }

    @Test
    fun http401And403MapToInvalidKey() {
        assertEquals(AiError.InvalidKey, mapHttpStatus(401))
        assertEquals(AiError.InvalidKey, mapHttpStatus(403))
    }

    @Test
    fun http402MapsToInsufficientBalance() {
        assertEquals(AiError.InsufficientBalance, mapHttpStatus(402))
    }

    @Test
    fun http429MapsToRateLimited() {
        assertEquals(AiError.RateLimited, mapHttpStatus(429))
    }

    @Test
    fun http5xxMapsToServerWithStatus() {
        assertEquals(AiError.Server(500), mapHttpStatus(500))
        assertEquals(AiError.Server(503), mapHttpStatus(503))
        assertEquals(AiError.Server(599), mapHttpStatus(599))
    }

    @Test
    fun otherHttpStatusMapsToHttpError() {
        assertEquals(AiError.HttpError(301), mapHttpStatus(301))
        assertEquals(AiError.HttpError(404), mapHttpStatus(404))
    }

    @Test
    fun socketTimeoutAndInterruptedIoMapToTimeout() {
        assertEquals(AiError.Timeout, mapError(SocketTimeoutException("read timed out")))
        assertEquals(AiError.Timeout, mapError(InterruptedIOException("interrupted")))
    }

    @Test
    fun streamInterruptedFromGatewayExceptionIsPreserved() {
        assertEquals(
            AiError.StreamInterrupted,
            mapError(AiGatewayException(AiError.StreamInterrupted)),
        )
    }

    @Test
    fun ioFailureMapsToNetwork() {
        assertEquals(AiError.Network, mapError(IOException("connection reset")))
    }

    @Test
    fun gatewayExceptionKeepsBusinessError() {
        assertEquals(AiError.InvalidKey, mapError(AiGatewayException(AiError.InvalidKey)))
        assertEquals(AiError.Server(500), mapError(AiGatewayException(AiError.Server(500))))
    }

    @Test
    fun unknownThrowableMapsToUnknown() {
        assertEquals(AiError.Unknown, mapError(IllegalStateException("boom")))
    }
}
