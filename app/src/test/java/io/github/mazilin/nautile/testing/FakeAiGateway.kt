package io.github.mazilin.nautile.testing

import io.github.mazilin.nautile.domain.port.AiGateway

/** 固定返回值的 AI 网关替身；不发起任何网络请求。 */
class FakeAiGateway(private val response: String = "fake-response") : AiGateway {

    val prompts = mutableListOf<String>()

    override suspend fun request(prompt: String): String {
        prompts += prompt
        return response
    }
}
