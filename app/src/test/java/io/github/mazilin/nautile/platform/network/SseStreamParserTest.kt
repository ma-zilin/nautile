package io.github.mazilin.nautile.platform.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SSE 帧解析边界（架构 §5.3；AC-AN-01 流式、AC-AN-06 断流判定）。
 *
 * 只验证纯 Kotlin framing：分块、多行 data、CRLF、注释/非 data 字段、`[DONE]` 哨兵与
 * EOF 缺 `[DONE]`（网关据此抛 STREAM_INTERRUPTED）。不涉及 org.json 与网络。
 */
class SseStreamParserTest {

    private fun feed(vararg lines: String): List<String> {
        val parser = SseStreamParser()
        val output = mutableListOf<String>()
        lines.forEach { output += parser.feed(it) }
        return output
    }

    @Test
    fun singleDataLineDispatchedOnlyAfterBlankLine() {
        val parser = SseStreamParser()

        assertEquals(emptyList<String>(), parser.feed("data: {\"a\":1}"))
        assertEquals(listOf("{\"a\":1}"), parser.feed(""))
    }

    @Test
    fun trailingCarriageReturnIsStripped() {
        assertEquals(listOf("{\"a\":1}"), feed("data: {\"a\":1}\r", "\r"))
    }

    @Test
    fun multiLineDataJoinedWithNewline() {
        assertEquals(
            listOf("{\"a\":1}\nsecond"),
            feed("data: {\"a\":1}", "data: second", ""),
        )
    }

    @Test
    fun commentAndNonDataFieldsIgnored() {
        assertEquals(
            listOf("payload"),
            feed(
                ": keep-alive",
                "event: message",
                "id: 42",
                "retry: 1000",
                "data: payload",
                "",
            ),
        )
    }

    @Test
    fun onlySingleLeadingSpaceIsStripped() {
        // SSE 规范只去掉一个前导空格；其余空格属于 payload。
        assertEquals(listOf(" hello"), feed("data:  hello", ""))
        assertEquals(listOf("hello"), feed("data:hello", ""))
    }

    @Test
    fun doneSentinelEmittedImmediatelyWithoutBlankLine() {
        val parser = SseStreamParser()

        assertEquals(listOf(SseStreamParser.DONE_SENTINEL), parser.feed("data: [DONE]"))
        // 哨兵后的空行不应再派发空事件。
        assertEquals(emptyList<String>(), parser.feed(""))
    }

    @Test
    fun emptyPayloadIsNotDispatched() {
        assertEquals(emptyList<String>(), feed("data:", ""))
        assertEquals(emptyList<String>(), feed("data: ", ""))
    }

    @Test
    fun chunkedFeedsKeepOrderAndDispatchOnlyOnBlankLine() {
        val parser = SseStreamParser()
        val output = mutableListOf<String>()

        output += parser.feed("data: 第一段")
        output += parser.feed("data: 第二段")
        assertTrue("空行前不应派发", output.isEmpty())

        output += parser.feed("data: 第三段")
        output += parser.feed("")
        assertEquals(listOf("第一段\n第二段\n第三段"), output)
    }

    @Test
    fun eofWithoutDoneNeverProducesDoneSentinel() {
        // 读到 EOF 但缺少 data: [DONE]：parser 不产生哨兵，网关据此判 STREAM_INTERRUPTED。
        val output = feed("data: 部分内容", "")

        assertEquals(listOf("部分内容"), output)
        assertTrue(
            "缺少 [DONE] 时不得产生完成哨兵",
            output.none { it == SseStreamParser.DONE_SENTINEL },
        )
    }
}
