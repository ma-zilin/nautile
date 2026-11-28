package io.github.mazilin.nautile.platform.network

import io.github.mazilin.nautile.domain.model.AiRequest
import io.github.mazilin.nautile.domain.model.AiRole

/**
 * DeepSeek 请求 JSON 构造（纯 Kotlin、无 Android/org.json，可 JVM 单测）。
 *
 * 只接受 [AiRequest]：固定系统提示 + 本次对话消息；不拼接页面、筛选、日期、可见内容或设备信息。
 * 手写最小转义：`"`、`\`、换行、回车、制表、退格、换页与控制字符。
 */
object DeepSeekRequestJson {

    fun build(request: AiRequest, model: String = DeepSeekConfig.MODEL): String = buildString {
        append("{\"model\":")
        appendQuoted(model)
        append(",\"stream\":true,\"messages\":[{\"role\":\"system\",\"content\":")
        appendQuoted(request.systemPrompt)
        append('}')
        request.turns.forEach { turn ->
            append(",{\"role\":")
            appendQuoted(turn.role.toApiRole())
            append(",\"content\":")
            appendQuoted(turn.content)
            append('}')
        }
        append("]}")
    }

    private fun AiRole.toApiRole(): String = when (this) {
        AiRole.USER -> "user"
        AiRole.ASSISTANT -> "assistant"
    }

    private fun StringBuilder.appendQuoted(value: String) {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (ch < ' ') {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
        append('"')
    }
}
