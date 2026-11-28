package io.github.mazilin.nautile.verification

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.MainActivity
import io.github.mazilin.nautile.NautileApp
import io.github.mazilin.nautile.domain.model.AiMessageStatus
import io.github.mazilin.nautile.domain.model.AiRole
import io.github.mazilin.nautile.domain.model.AppPage
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AI 页恢复、清除后不恢复与 tasks/projects 隔离（AC-N-19、AC-A-12/14、AC-AN-02/03）。
 *
 * 在真实 [MainActivity] 上执行“Activity 重建”（`scenario.recreate()`）：
 * - 上次停在 AI 页时重建后仍显示 AI 页，输入框不自动获得焦点（不主动弹键盘）；
 * - 清除 AI 会话后重建，消息不再出现；
 * - 待办/项目数量与内容不变。
 *
 * 限制（报告中同样声明）：这是 Activity 重建，不是杀进程冷启动；进程级持久化由
 * Room 文件重开测试（AiConversationRepositoryTest）覆盖。日程/日志不变分支待 R6 合并后补验，本轮未执行。
 * 使用模拟器真实应用数据库；测试开始/结束会清除 AI 会话，但不改动待办/项目。
 */
@RunWith(AndroidJUnit4::class)
class AiG3RestartTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app: NautileApp
        get() = composeRule.activity.application as NautileApp

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun recreateAndWaitForAi() {
        composeRule.activityRule.scenario.recreate()
        waitForTag("ai_screen")
    }

    @Test
    fun aiPageRestoresWithoutKeyboardAndClearedConversationStaysCleared() {
        waitForTag("tab_AI")
        composeRule.onNodeWithTag("tab_AI").performClick()
        waitForTag("ai_screen")

        // 基线：清空本机 AI 会话，避免历史数据影响断言。
        runBlocking { app.container.aiConversationRepository.clearConversation() }
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithTag("ai_empty_state").fetchSemanticsNodes().isNotEmpty()
        }

        val tasksBefore = runBlocking { app.container.taskRepository.observeAll().first() }
        val projectsBefore = runBlocking { app.container.projectRepository.observeAll().first() }

        // 写入一轮完整对话（时间固定，便于重建后断言）。
        val at = Instant.ofEpochMilli(1_700_000_100_000)
        runBlocking {
            val ids = app.container.aiConversationRepository.beginTurn("重启恢复问题", at)
            app.container.aiConversationRepository.finishAssistant(
                messageId = ids.assistantMessageId,
                content = "重启恢复回答",
                status = AiMessageStatus.COMPLETE,
                errorCode = null,
                at = at.plusMillis(1_000),
            )
        }
        waitForText("重启恢复回答")

        // 上次页写入 AI（供启动恢复使用）。
        composeRule.waitUntil(10_000) {
            runBlocking { app.container.settingsRepository.settings.first().lastPage == AppPage.AI }
        }

        composeRule.onNodeWithTag("ai_input").assertIsNotFocused()
        assertFalse(
            "AI 页恢复不得自动弹键盘",
            app.container.keyboardCoordinator.imeVisible.value,
        )

        // Activity 重建：AI 页恢复、消息保留、输入框不自动聚焦。
        recreateAndWaitForAi()
        waitForText("重启恢复回答")
        composeRule.onNodeWithTag("tab_AI").assertIsSelected()
        composeRule.onNodeWithTag("ai_input").assertIsNotFocused()

        val restored = runBlocking { app.container.aiConversationRepository.observeMessages().first() }
        assertEquals(2, restored.size)
        assertEquals(AiRole.USER, restored[0].role)
        assertEquals("重启恢复问题", restored[0].content)
        assertEquals(AiMessageStatus.COMPLETE, restored[0].status)
        assertEquals(at, restored[0].createdAt)
        assertEquals(AiRole.ASSISTANT, restored[1].role)
        assertEquals("重启恢复回答", restored[1].content)
        assertEquals(AiMessageStatus.COMPLETE, restored[1].status)
        assertEquals(at, restored[1].createdAt)

        // 设置页清除会话：停留设置页提示成功，返回 AI 页为空态。
        composeRule.onNodeWithTag("ai_settings_entry").performClick()
        waitForTag("settings_clear_ai_conversation")
        composeRule.onNodeWithTag("settings_clear_ai_conversation").performClick()
        composeRule.onNodeWithTag("settings_clear_ai_confirm").performClick()
        waitForText("AI 会话已清除")
        composeRule.onNodeWithText("返回").performClick()
        waitForTag("ai_screen")
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithTag("ai_empty_state").fetchSemanticsNodes().isNotEmpty()
        }

        // 清除后重建：消息不再恢复。
        recreateAndWaitForAi()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithTag("ai_empty_state").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("重启恢复回答").assertCountEquals(0)
        assertTrue(
            "清除会话后重建不得恢复消息",
            runBlocking { app.container.aiConversationRepository.observeMessages().first() }.isEmpty(),
        )

        // tasks/projects 不变。
        assertEquals(
            "清除 AI 会话不得改动待办数量/内容",
            tasksBefore.map { it.id to it.title },
            runBlocking { app.container.taskRepository.observeAll().first() }
                .map { it.id to it.title },
        )
        assertEquals(
            "清除 AI 会话不得改动项目数量/内容",
            projectsBefore.map { it.id to it.name },
            runBlocking { app.container.projectRepository.observeAll().first() }
                .map { it.id to it.name },
        )
    }

    @Test
    fun aiPageHasNoAutoFocusOnFreshEntry() {
        waitForTag("tab_AI")
        composeRule.onNodeWithTag("tab_AI").performClick()
        waitForTag("ai_screen")

        composeRule.onNodeWithTag("ai_input").assertIsNotFocused()
        assertFalse(app.container.keyboardCoordinator.imeVisible.value)
        composeRule.onNodeWithTag("ai_title").assertIsDisplayed()
    }
}
