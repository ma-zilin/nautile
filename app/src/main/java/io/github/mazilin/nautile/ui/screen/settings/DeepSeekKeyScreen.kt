package io.github.mazilin.nautile.ui.screen.settings

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.ui.designsystem.NautileButton
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.theme.NautileMotion

/**
 * DeepSeek Key 全屏子页（06 §6.2、架构 §7.4）。
 *
 * 硬规则：
 * - 输入始终掩码（[PasswordVisualTransformation] + password 语义），不读取/回显已保存 Key；
 * - 不展示模型名，不做联网探测；保存只做 trim 非空；
 * - 不加 FLAG_SECURE（用户确认），证据只用掩码截图；
 * - 输入不写入 savedInstanceState：本页用普通 remember，转屏即丢弃未保存输入。
 * 宿主 FullScreenPageContainer 唯一消费 insets，本页不自建 Surface/safeDrawing。
 */
@Composable
fun DeepSeekKeyScreen(
    state: AiSettingsUiState,
    onBack: () -> Unit,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 输入只在内存中保留，不经 rememberSaveable，避免写入 savedInstanceState。
    var input by remember { mutableStateOf("") }
    // 保存成功后清空掩码输入；失败保留输入以便重试（06 §6.2）。
    LaunchedEffect(state.keyFeedback) {
        if (state.keyFeedback == AiSettingsFeedback.KeySaved) input = ""
    }
    val canSave = input.trim().isNotEmpty() && !state.savingKey

    Column(modifier = modifier.fillMaxSize().testTag("ai_key_screen")) {
        // 头部按 06 §6.2：48dp 图标按钮返回 + headlineSmall 标题，不新建 NautilePageTopBar。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NautileIconButton(
                iconRes = R.drawable.ic_arrow_back,
                contentDescription = "返回",
                onClick = onBack,
                testTag = "ai_key_back",
            )
            Text(
                text = "DeepSeek Key",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // 持久状态：只区分“未配置/已保存”，变化由 liveRegion 播报。
            Text(
                text = "当前状态：${if (state.keyConfigured) "已保存" else "未配置"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .testTag("ai_key_status")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "仅表示本机是否已保存，不进行联网验证；真实有效性由首次请求失败提示。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("DeepSeek Key") },
                placeholder = { Text("输入新的 Key 以替换/保存") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (canSave) onSaveKey(input) },
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_key_input")
                    // 显式密码语义：TalkBack 只读“DeepSeek Key，密码”，不朗读明文。
                    .semantics { password() },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NautileButton(
                    text = "保存",
                    onClick = { onSaveKey(input) },
                    enabled = canSave,
                    modifier = Modifier.testTag("ai_key_save"),
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onClearKey,
                    enabled = state.keyConfigured && !state.clearingKey,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.testTag("ai_key_clear"),
                ) {
                    Text("清除 Key")
                }
            }
            // 一次性操作反馈切换用 Crossfade（AI-ANIM-09）；不影响输入与按钮可测性。
            Crossfade(
                targetState = keyFeedbackText(state.keyFeedback),
                animationSpec = NautileMotion.StandardEffects,
                label = "ai_key_feedback_crossfade",
            ) { message ->
                if (message != null) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (keyFeedbackIsError(state.keyFeedback)) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier
                                .testTag("ai_key_feedback")
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "仅保存在本机安全存储；不显示已保存的 Key；报告只使用掩码截图。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Key 页一次性反馈文案；不包含 Key 值。 */
internal fun keyFeedbackText(feedback: AiSettingsFeedback?): String? = when (feedback) {
    AiSettingsFeedback.KeySaved -> "已保存"
    AiSettingsFeedback.KeyCleared -> "已清除"
    AiSettingsFeedback.KeySaveFailed -> "保存失败，请重试"
    AiSettingsFeedback.KeyClearFailed -> "清除失败，请重试"
    AiSettingsFeedback.ConversationCleared,
    AiSettingsFeedback.ConversationClearFailed,
    null -> null
}

private fun keyFeedbackIsError(feedback: AiSettingsFeedback?): Boolean = when (feedback) {
    AiSettingsFeedback.KeySaveFailed, AiSettingsFeedback.KeyClearFailed -> true
    else -> false
}
