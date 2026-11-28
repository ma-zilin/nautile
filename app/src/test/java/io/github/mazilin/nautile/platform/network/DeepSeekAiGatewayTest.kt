package io.github.mazilin.nautile.platform.network

import io.github.mazilin.nautile.domain.model.AiError
import io.github.mazilin.nautile.domain.model.AiGatewayException
import io.github.mazilin.nautile.domain.model.AiRequest
import io.github.mazilin.nautile.domain.model.AiRole
import io.github.mazilin.nautile.domain.model.AiTurn
import io.github.mazilin.nautile.domain.port.SecretStore
import io.github.mazilin.nautile.testing.InMemorySecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DeepSeek 网关在“未配置 Key / 取消”两个无需真实网络的分支上的确定性行为（架构 §2.3、§5.4）。
 *
 * 真实 HTTPS/SSE 读取、EOF 缺 `[DONE]` 与读循环取消需要可控 endpoint；本分支未提供注入点，
 * 因此这些路径不在自动化范围，报告中单列。
 */
class DeepSeekAiGatewayTest {

    private val request = AiRequest(
        systemPrompt = "sys",
        turns = listOf(AiTurn(AiRole.USER, "你好")),
    )

    @Test
    fun missingKeyFailsWithKeyMissingBeforeAnyNetwork() {
        val gateway = DeepSeekAiGateway(InMemorySecretStore(), Dispatchers.Unconfined)

        val thrown = runCatching {
            runBlocking { gateway.stream(request).collect { } }
        }.exceptionOrNull()

        assertTrue("期望 AiGatewayException，实际=$thrown", thrown is AiGatewayException)
        assertEquals(AiError.KeyMissing, (thrown as AiGatewayException).error)
    }

    @Test
    fun cancellationFromKeyStoreIsNotWrappedAsBusinessError() {
        val gateway = DeepSeekAiGateway(CancellingSecretStore(), Dispatchers.Unconfined)

        val thrown = runCatching {
            runBlocking { gateway.stream(request).collect { } }
        }.exceptionOrNull()

        assertTrue("取消必须原样抛出 CancellationException，实际=$thrown", thrown is CancellationException)
        assertTrue("取消不得被包装成 AiGatewayException", thrown !is AiGatewayException)
    }

    private class CancellingSecretStore : SecretStore {
        override fun put(key: String, value: String) = Unit

        override fun get(key: String): String? = throw CancellationException("用户停止")

        override fun delete(key: String) = Unit
    }
}
