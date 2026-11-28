package io.github.mazilin.nautile.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.UserSettings

/**
 * 根设置页 P-13（S1-T10、FR-N-19；AI 行按 06 §6.1/§6.3）。
 *
 * 提供默认页入口、预测性返回开关、DeepSeek Key 入口与清除 AI 会话入口；
 * 分类/标签/个人记忆等未实现入口不展示。设置页为全屏子页，返回由 NavigationCoordinator 逐级处理；
 * 宿主 FullScreenPageContainer 已统一提供 Surface 与 safeDrawing inset，本页不再自建。
 *
 * AI 相关参数带默认值，仅为不破坏既有调用点；正式宿主由 MainActivity 注入真实状态与回调。
 */
@Composable
fun SettingsScreen(
    settings: UserSettings,
    onBack: () -> Unit,
    onOpenDefaultPage: () -> Unit,
    onPredictiveBackChanged: (Boolean) -> Unit,
    aiSettings: AiSettingsUiState = AiSettingsUiState(),
    onOpenDeepSeekKey: () -> Unit = {},
    onClearAiConversation: () -> Unit = {},
) {
    // 清除会话的确认弹层默认入场由 M3 组件承担（MOT-06），不自绘。
    var showClearDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("返回")
            }
            Text("设置", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenDefaultPage)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("settings_default_page"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("默认页", modifier = Modifier.weight(1f))
            Text(appPageLabel(settings.defaultPage), style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("预测性返回", modifier = Modifier.weight(1f))
            Switch(
                checked = settings.predictiveBackEnabled,
                onCheckedChange = onPredictiveBackChanged,
                modifier = Modifier.testTag("predictive_back_switch"),
            )
        }
        HorizontalDivider()
        SettingsActionRow(
            title = "DeepSeek Key",
            supporting = if (aiSettings.keyConfigured) "已保存" else "未配置",
            onClick = onOpenDeepSeekKey,
            leadingIconRes = R.drawable.ic_key,
            testTag = "settings_deepseek_key",
        )
        HorizontalDivider()
        SettingsActionRow(
            title = "清除 AI 会话",
            supporting = if (aiSettings.clearingConversation) {
                "正在清除…"
            } else {
                "删除本机 AI 消息，不影响待办、日程、日志"
            },
            onClick = { showClearDialog = true },
            enabled = !aiSettings.clearingConversation,
            testTag = "settings_clear_ai_conversation",
        )
        conversationFeedbackText(aiSettings.conversationFeedback)?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (conversationFeedbackIsError(aiSettings.conversationFeedback)) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("settings_clear_ai_feedback")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            modifier = Modifier.testTag("settings_clear_ai_dialog"),
            title = {
                Text("清除 AI 会话？", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Text(
                    text = "将删除本机保存的全部 AI 消息，且无法恢复；待办、日程、日志不受影响。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearAiConversation()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.testTag("settings_clear_ai_confirm"),
                ) {
                    Text("清除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false },
                    modifier = Modifier.testTag("settings_clear_ai_cancel"),
                ) {
                    Text("取消")
                }
            },
        )
    }
}

/**
 * 设置动作行（06 §6.1）：整行可点、最小高 56dp、水平 16dp/垂直 12dp；
 * 标题 bodyLarge、支持文字 bodySmall；尾部 chevron 为装饰（contentDescription=null）。
 */
@Composable
private fun SettingsActionRow(
    title: String,
    supporting: String,
    onClick: () -> Unit,
    testTag: String,
    enabled: Boolean = true,
    leadingIconRes: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            // 整行合并为一个可点节点：标题 + 支持文字一次读出，装饰图标不重复朗读。
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIconRes != null) {
            Icon(
                painter = painterResource(leadingIconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** 清除会话一次性反馈文案；不进入持久状态，不含 AI 消息内容。 */
internal fun conversationFeedbackText(feedback: AiSettingsFeedback?): String? = when (feedback) {
    AiSettingsFeedback.ConversationCleared -> "AI 会话已清除"
    AiSettingsFeedback.ConversationClearFailed -> "清除失败，请重试"
    else -> null
}

internal fun conversationFeedbackIsError(feedback: AiSettingsFeedback?): Boolean =
    feedback == AiSettingsFeedback.ConversationClearFailed

/** 默认页显示名；仅四个一级页面（FR-N-20）。 */
fun appPageLabel(page: AppPage): String = when (page) {
    AppPage.TODO -> "待办"
    AppPage.SCHEDULE -> "日程"
    AppPage.LOG -> "日志"
    AppPage.AI -> "AI"
}
