package io.github.mazilin.nautile.platform.network

/**
 * SSE 帧解析（纯 Kotlin，无 Android/org.json，可 JVM 单测）。
 *
 * 规则（架构 §5.3）：
 * - 行尾 `\r` 去掉；空行结束事件，把累积的 `data:` 行用 `\n` 连接后输出；
 * - 忽略 `:` 注释行与 `event:`/`id:`/`retry:` 等非 data 字段；
 * - `data:` 后一个前导空格按规范去掉；
 * - `data: [DONE]` 立即输出终止哨兵，不要求后续空行；
 * - 支持多行 data；payload 为空不派发；
 * - EOF 时未派发的 data 行不当作完整事件，由网关按流中断处理。
 */
class SseStreamParser {

    private val pendingData = mutableListOf<String>()

    /** 输入一行（不含行尾换行），返回本行可立即派发的 payload 列表。 */
    fun feed(line: String): List<String> {
        val normalized = line.removeSuffix("\r")
        if (normalized.isEmpty()) {
            if (pendingData.isEmpty()) return emptyList()
            val payload = pendingData.joinToString("\n")
            pendingData.clear()
            return if (payload.isEmpty()) emptyList() else listOf(payload)
        }
        if (normalized.startsWith(":")) return emptyList()
        if (!normalized.startsWith(DATA_FIELD)) return emptyList()

        var value = normalized.substring(DATA_FIELD.length)
        if (value.startsWith(" ")) value = value.substring(1)
        if (value == DONE_SENTINEL) {
            pendingData.clear()
            return listOf(DONE_SENTINEL)
        }
        pendingData += value
        return emptyList()
    }

    companion object {
        const val DONE_SENTINEL = "[DONE]"
        private const val DATA_FIELD = "data:"
    }
}
