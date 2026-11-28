package io.github.mazilin.nautile.platform.network

import io.github.mazilin.nautile.domain.ai.AiSystemPrompt
import io.github.mazilin.nautile.domain.model.AiRequest
import io.github.mazilin.nautile.domain.model.AiRole
import io.github.mazilin.nautile.domain.model.AiTurn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DeepSeek 请求体构造（架构 §2.5、§5.2；FR-A-15/FR-A-20、AC-A-12/AC-A-17 请求体断言）。
 *
 * 断言请求体只含固定系统提示与本次对话消息，不含页面/日期/筛选/可见内容/Key/工具定义。
 */
class DeepSeekRequestJsonTest {

    private val request = AiRequest(
        systemPrompt = AiSystemPrompt.TEXT,
        turns = listOf(
            AiTurn(AiRole.USER, "问题一"),
            AiTurn(AiRole.ASSISTANT, "回答一"),
            AiTurn(AiRole.USER, "问题二"),
        ),
    )

    @Test
    fun requestContainsFixedSystemPromptAndTurnsInOrder() {
        val body = DeepSeekRequestJson.build(request)

        assertTrue("缺少最小字段 model", body.contains("\"model\":\"deepseek-chat\""))
        assertTrue("缺少 stream=true", body.contains("\"stream\":true"))
        assertTrue("缺少 system 角色", body.contains("{\"role\":\"system\",\"content\":"))

        val first = body.indexOf("问题一")
        val second = body.indexOf("回答一")
        val third = body.indexOf("问题二")
        assertTrue("消息顺序被打乱", first in 1 until second && second < third)
        assertTrue("assistant 角色应映射为 assistant", body.contains("\"role\":\"assistant\""))
        assertTrue("user 角色应映射为 user", body.contains("\"role\":\"user\""))
    }

    @Test
    fun requestHasNoPageDateFilterOrCredentialFields() {
        val body = DeepSeekRequestJson.build(request)

        // 不自动附带页面上下文、日期、筛选或可见内容。
        listOf("context", "page", "filter", "visible", "screen", "date", "Authorization", "sk-")
            .forEach { forbidden ->
                assertFalse("请求体不应包含 $forbidden", body.contains(forbidden))
            }
        assertTrue("请求体不应包含 Key 字段", !body.contains("\"api_key\""))
    }

    @Test
    fun quotesBackslashesAndControlCharactersAreEscaped() {
        val tricky = AiRequest(
            systemPrompt = "sys",
            turns = listOf(
                AiTurn(AiRole.USER, "引号\"反斜杠\\换行\n制表\t控制\u0001"),
            ),
        )
        val body = DeepSeekRequestJson.build(tricky)

        assertTrue("双引号未转义", body.contains("\\\"反斜杠"))
        assertTrue("反斜杠未转义", body.contains("\\\\换行"))
        assertTrue("换行未转义", body.contains("\\n制表"))
        assertTrue("制表未转义", body.contains("\\t控制"))
        assertTrue("控制字符未转义为 \\u0001", body.contains("\\u0001"))
        assertFalse("未转义的控制字符不应原样出现", body.contains("\u0001"))
    }

    @Test
    fun emptyTurnsStillProducesValidMinimalShape() {
        val body = DeepSeekRequestJson.build(
            AiRequest(systemPrompt = "sys", turns = emptyList()),
        )

        assertTrue(body.startsWith("{\"model\":"))
        assertTrue(body.endsWith("]}"))
        assertTrue("无 turns 时仍应有 system 消息", body.contains("\"role\":\"system\""))
    }
}
