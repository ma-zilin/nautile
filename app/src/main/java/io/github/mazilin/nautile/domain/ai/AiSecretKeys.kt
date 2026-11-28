package io.github.mazilin.nautile.domain.ai

/**
 * 密钥存储的逻辑键名。
 *
 * 这是业务键名，不是 Keystore 别名；别名由 platform/keystore 按固定前缀生成。
 */
object AiSecretKeys {

    /** DeepSeek API Key 的逻辑键名；Phase A 唯一使用的 Key。 */
    const val DEEPSEEK_API_KEY = "deepseek_api_key"
}
