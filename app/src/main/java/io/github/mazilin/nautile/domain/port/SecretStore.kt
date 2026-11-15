package io.github.mazilin.nautile.domain.port

/** 密钥存储端口；实现放 platform/keystore。 */
interface SecretStore {
    fun put(key: String, value: String)

    fun get(key: String): String?

    fun delete(key: String)
}
