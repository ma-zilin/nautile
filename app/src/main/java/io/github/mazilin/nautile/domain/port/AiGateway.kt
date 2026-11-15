package io.github.mazilin.nautile.domain.port

/** AI 网关端口；实现放 platform/network；增量 1 不接入任何网络。 */
interface AiGateway {
    suspend fun request(prompt: String): String
}
