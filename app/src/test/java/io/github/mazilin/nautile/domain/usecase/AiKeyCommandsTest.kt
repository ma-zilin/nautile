package io.github.mazilin.nautile.domain.usecase

import io.github.mazilin.nautile.domain.ai.AiSecretKeys
import io.github.mazilin.nautile.domain.port.SecretStore
import io.github.mazilin.nautile.testing.InMemorySecretStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DeepSeek Key 命令（架构 §6；AC-AN-04 保存/清除状态、AC-AN-05 不落明文）。
 *
 * 只用内存替身，不接触 Android Keystore，不写真实 Key。
 */
class AiKeyCommandsTest {

    private val store = InMemorySecretStore()
    private val commands = AiKeyCommands(store, Dispatchers.Unconfined)

    @Test
    fun hasKeyFalseWhenStoreEmpty() = runBlocking {
        assertFalse(commands.hasKey())
    }

    @Test
    fun saveTrimsAndEnablesHasKey() = runBlocking {
        val result = commands.save("  test-fake-key  ")

        assertEquals(KeySaveResult.Saved, result)
        assertTrue(commands.hasKey())
        assertEquals(
            "保存应去掉首尾空白",
            "test-fake-key",
            store.get(AiSecretKeys.DEEPSEEK_API_KEY),
        )
    }

    @Test
    fun blankSaveFailsWithoutWriting() = runBlocking {
        assertEquals(KeySaveResult.Failed, commands.save("   "))
        assertFalse(commands.hasKey())
        assertNull(store.get(AiSecretKeys.DEEPSEEK_API_KEY))
    }

    @Test
    fun clearRemovesStoredKeyAndHasKeyReflectsStoreOnEachCall() = runBlocking {
        assertEquals(KeySaveResult.Saved, commands.save("fake-key"))
        assertTrue(commands.hasKey())

        assertEquals(KeyClearResult.Cleared, commands.clear())
        assertFalse("清除后 hasKey 必须重新读取真实存储", commands.hasKey())
        assertNull(store.get(AiSecretKeys.DEEPSEEK_API_KEY))
    }

    @Test
    fun storeFailureBecomesFailedResultWithoutThrowing() = runBlocking {
        val failing = AiKeyCommands(FailingSecretStore(), Dispatchers.Unconfined)

        assertEquals(KeySaveResult.Failed, failing.save("fake-key"))
        assertEquals(KeyClearResult.Failed, failing.clear())
        assertFalse("读取失败按未配置处理", failing.hasKey())
    }

    /** 全部操作抛 IO 的替身：验证命令层不把内部异常暴露给界面。 */
    private class FailingSecretStore : SecretStore {
        override fun put(key: String, value: String) = throw IOException("put failed")

        override fun get(key: String): String? = throw IOException("get failed")

        override fun delete(key: String) = throw IOException("delete failed")
    }
}
