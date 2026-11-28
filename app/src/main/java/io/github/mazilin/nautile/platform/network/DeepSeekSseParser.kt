package io.github.mazilin.nautile.platform.network

import org.json.JSONObject

/**
 * 从 SSE data payload 提取 DeepSeek 增量文本（使用 Android 自带 org.json）。
 *
 * `choices[0].delta.content` 缺失（角色块、usage 块、finish 块）时返回 null；
 * JSON 解析异常向上抛，由网关映射为 `STREAM_INTERRUPTED`，不打印完整 payload（可能含对话内容）。
 */
object DeepSeekSseParser {

    fun contentOf(payload: String): String? {
        val root = JSONObject(payload)
        val choices = root.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null
        val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return null
        val content = delta.optString("content")
        return content.ifEmpty { null }
    }
}
