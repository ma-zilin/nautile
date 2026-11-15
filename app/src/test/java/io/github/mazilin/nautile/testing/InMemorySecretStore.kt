package io.github.mazilin.nautile.testing

import io.github.mazilin.nautile.domain.port.SecretStore

/** 内存版密钥存储替身；不接触 Android Keystore。 */
class InMemorySecretStore : SecretStore {

    private val values = mutableMapOf<String, String>()

    override fun put(key: String, value: String) {
        values[key] = value
    }

    override fun get(key: String): String? = values[key]

    override fun delete(key: String) {
        values.remove(key)
    }
}
