package io.github.mazilin.nautile.verification

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mazilin.nautile.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G8 闸门：搜索返回与跨模块（CMP-38、FR-N-14/15、BR-76）。
 *
 * 在真实 [MainActivity] 宿主上验证：
 * - 系统返回先退出搜索，待办页保留（不退出页面/应用）；
 * - 切到日程模块后搜索栏消失；切回待办也不恢复上次查询（不跨模块保留）。
 *
 * 使用真实应用容器与 DataStore；测试只操作搜索栏与底部导航，不写任务数据。
 * 运行环境：emulator-5554（API 36，模拟器，非真机）。
 */
@RunWith(AndroidJUnit4::class)
class G8SearchActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun goToTodoTab() {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithTag("tab_TODO").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("tab_TODO").performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodes(
                hasContentDescription("搜索待办", substring = true),
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun enterSearch() {
        goToTodoTab()
        composeRule.onNode(
            hasContentDescription("搜索待办", substring = true),
        ).performClick()
        composeRule.onNodeWithContentDescription("退出搜索").assertIsDisplayed()
    }

    private fun awaitSearchClosed() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("退出搜索")
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun G8_CMP38_back_exits_search_and_keeps_todo_page() {
        enterSearch()
        composeRule.onNode(hasSetTextAction()).performTextInput("x")

        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        awaitSearchClosed()
        // 只退出搜索：待办页与新建入口仍在，页面没有被返回键带走。
        composeRule.onNodeWithContentDescription("搜索待办").assertIsDisplayed()
        composeRule.onNodeWithTag("add_task").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("退出搜索").assertCountEquals(0)
    }

    @Test
    fun G8_CMP38_search_does_not_cross_modules_or_restore_query() {
        enterSearch()
        composeRule.onNode(hasSetTextAction()).performTextInput("遗留查询")

        composeRule.onNodeWithTag("tab_SCHEDULE").performClick()
        awaitSearchClosed()

        composeRule.onNodeWithTag("tab_TODO").performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty()
        }
        // 切回待办：搜索入口恢复可见，但不会自动打开或恢复上次查询。
        composeRule.onNode(
            hasContentDescription("搜索待办", substring = true),
        ).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("退出搜索").assertCountEquals(0)
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
}
