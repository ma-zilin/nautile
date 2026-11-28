package io.github.mazilin.nautile.domain.usecase

import io.github.mazilin.nautile.domain.ai.AiSecretKeys
import io.github.mazilin.nautile.domain.port.SecretStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Key 保存结果；“未配置/已保存”是持久状态，失败只作瞬时反馈。 */
sealed interface KeySaveResult {
    data object Saved : KeySaveResult
    data object Failed : KeySaveResult
}

/** Key 清除结果。 */
sealed interface KeyClearResult {
    data object Cleared : KeyClearResult
    data object Failed : KeyClearResult
}

/**
 * DeepSeek Key 命令（架构 §6）。
 *
 * - 不缓存 Key：hasKey 每次读取真实存储，设置页清除后 AI 页不会误认为仍有 Key；
 * - 保存只做 trim 非空，不校验服务商格式；
 * - 不打印 Key；所有 Keystore/文件 IO 都在注入的 IO 调度器执行。
 */
class AiKeyCommands(
    private val secretStore: SecretStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun hasKey(): Boolean = withContext(ioDispatcher) {
        try {
            !secretStore.get(AiSecretKeys.DEEPSEEK_API_KEY).isNullOrBlank()
        } catch (_: Exception) {
            // 读取失败按“未配置”处理，不把内部异常文本暴露给界面。
            false
        }
    }

    suspend fun save(raw: String): KeySaveResult = withContext(ioDispatcher) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return@withContext KeySaveResult.Failed
        try {
            secretStore.put(AiSecretKeys.DEEPSEEK_API_KEY, trimmed)
            KeySaveResult.Saved
        } catch (_: Exception) {
            KeySaveResult.Failed
        }
    }

    suspend fun clear(): KeyClearResult = withContext(ioDispatcher) {
        try {
            secretStore.delete(AiSecretKeys.DEEPSEEK_API_KEY)
            KeyClearResult.Cleared
        } catch (_: Exception) {
            KeyClearResult.Failed
        }
    }
}
