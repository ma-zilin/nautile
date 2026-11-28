package io.github.mazilin.nautile.platform.network

import io.github.mazilin.nautile.domain.model.AiError
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DeepSeek SSE payload 解析与断流映射（架构 §5.3、§5.5；AC-AN-01/AC-AN-06）。
 *
 * 使用设备上的真实 `org.json`（JVM 单测里是 Android stub），验证：
 * 增量文本提取、角色/usage/finish 块返回 null、不可解析 JSON 映射为 STREAM_INTERRUPTED。
 */
@RunWith(AndroidJUnit4::class)
class DeepSeekSseParserTest {

    @Test
    fun deltaContentIsExtracted() {
        val payload = "{\"choices\":[{\"delta\":{\"content\":\"你好\"},\"index\":0}]}"
        assertEquals("你好", DeepSeekSseParser.contentOf(payload))
    }

    @Test
    fun roleOnlyDeltaReturnsNull() {
        val payload = "{\"choices\":[{\"delta\":{\"role\":\"assistant\"},\"index\":0}]}"
        assertNull(DeepSeekSseParser.contentOf(payload))
    }

    @Test
    fun finishAndUsageBlocksReturnNull() {
        val finish = "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\",\"index\":0}]}"
        val usage = "{\"choices\":[],\"usage\":{\"total_tokens\":12}}"
        assertNull(DeepSeekSseParser.contentOf(finish))
        assertNull(DeepSeekSseParser.contentOf(usage))
    }

    @Test
    fun malformedJsonMapsToStreamInterrupted() {
        val thrown = runCatching {
            DeepSeekSseParser.contentOf("not-json")
        }.exceptionOrNull()

        assertEquals(AiError.StreamInterrupted, mapError(thrown as Throwable))
        assertTrue("不可解析 JSON 应抛 JSONException", thrown is JSONException)
    }
}
