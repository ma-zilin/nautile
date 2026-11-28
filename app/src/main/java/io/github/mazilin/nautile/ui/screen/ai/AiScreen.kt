package io.github.mazilin.nautile.ui.screen.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.AiErrorCode
import io.github.mazilin.nautile.domain.model.AiMessage
import io.github.mazilin.nautile.domain.model.AiMessageStatus
import io.github.mazilin.nautile.domain.model.AiRole
import io.github.mazilin.nautile.ui.designsystem.NautileButton
import io.github.mazilin.nautile.ui.designsystem.NautileEmptyState
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.theme.NautileMotion

/**
 * AI 一级页面（06 §2—§5、§7—§10；AC-N-23、AC-A-15/18、AC-AN-01/07）。
 *
 * 结构固定为大标题“AI”＋右上设置、消息区、输入栏；页面本身不是全屏子页，
 * 不套 Scaffold、不再加 safeDrawing/imePadding（insets 由 AppRoot 宿主提供）。
 * 不显示搜索、附件、模型名或上下文标签；不展示每条消息时间。
 *
 * @param onScreenVisible 页面（重新）进入组合时回调，用于刷新 Key 存在性；不取消处理中请求。
 */
@Composable
fun AiScreen(
    state: AiUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onScreenVisible: () -> Unit = {},
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val nearBottomThreshold = with(LocalDensity.current) { 8.dp.roundToPx() }
    var initialScrollDone by remember { mutableStateOf(false) }
    var forceFollowOnce by remember { mutableStateOf(false) }
    var previousStreaming by remember { mutableStateOf(false) }

    // 进入页面时刷新 Key 存在性；只读状态，不触发网络、不取消处理中请求。
    LaunchedEffect(Unit) { onScreenVisible() }

    // 键盘打开时隐藏底部导航由宿主按 activeTab==AI && imeVisible 处理；
    // 离开 AI 页（切 tab、打开设置/Key 全屏页）时收键盘，返回不主动弹键盘（06 §9.1/§9.2）。
    DisposableEffect(Unit) {
        onDispose { keyboard?.hide() }
    }

    // 发送被接受（进入流式）后收键盘，并让下一条消息无条件贴底（06 §4.4）。
    LaunchedEffect(state.streaming) {
        if (state.streaming && !previousStreaming) {
            keyboard?.hide()
            forceFollowOnce = true
        }
        previousStreaming = state.streaming
    }

    // 初始/恢复会话直接贴底（不播动画）；发送后强制跟随；其余仅在接近底部时跟随。
    LaunchedEffect(state.messages.size) {
        val count = state.messages.size
        if (count == 0) return@LaunchedEffect
        val lastIndex = count - 1
        when {
            forceFollowOnce -> {
                listState.animateScrollToItem(lastIndex)
                forceFollowOnce = false
            }

            !initialScrollDone -> {
                listState.scrollToItem(lastIndex)
                initialScrollDone = true
            }

            listState.isNearBottom(nearBottomThreshold) -> {
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    // 流式增量：接近底部时立即贴底，不逐 token 播滚动动画（06 §4.4、MOT-13）。
    LaunchedEffect(state.streamingText, state.streamingMessageId) {
        if (state.streamingText.isNotEmpty() &&
            state.messages.isNotEmpty() &&
            listState.isNearBottom(nearBottomThreshold)
        ) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("ai_screen"),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AiHeader(onOpenSettings = onOpenSettings)
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Box(modifier = Modifier.weight(1f)) {
                // 空态 ↔ 消息列表交叉淡入（AI-ANIM-07）；首次进入不播放。
                Crossfade(
                    targetState = state.messages.isEmpty(),
                    animationSpec = NautileMotion.StandardEffects,
                    label = "ai_empty_message_switch",
                ) { empty ->
                    if (empty) {
                        AiEmptyState(
                            keyMissing = state.keyMissing,
                            onOpenSettings = onOpenSettings,
                        )
                    } else {
                        MessageList(
                            state = state,
                            listState = listState,
                        )
                    }
                }
            }

            // 发送前失败（请求未开始、不建失败消息）提示；错误用文字与 error 色，不只靠颜色。
            AnimatedVisibility(
                visible = state.sendError != null,
                enter = fadeIn(animationSpec = NautileMotion.StandardEffects),
                exit = fadeOut(animationSpec = NautileMotion.StandardEffects),
            ) {
                val code = state.sendError
                if (code != null) {
                    Text(
                        text = aiErrorMessage(code),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("ai_error_banner")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }

            // 有历史消息但无 Key：输入栏上方提示条；空消息时由空态承担（06 §5.1）。
            AnimatedVisibility(
                visible = state.keyMissing && state.messages.isNotEmpty(),
                enter = fadeIn(animationSpec = NautileMotion.StandardEffects),
                exit = fadeOut(animationSpec = NautileMotion.StandardEffects),
            ) {
                AiKeyMissingBanner(onOpenSettings = onOpenSettings)
            }

            AiInputBar(
                state = state,
                onDraftChange = onDraftChange,
                onSend = onSend,
                onStop = onStop,
            )
        }
    }
}

/** 头部：大标题“AI”＋右上设置；无搜索/附件/模型名（06 §2.1、AC-N-23）。 */
@Composable
private fun AiHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "AI",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
                .testTag("ai_title")
                .semantics { heading() },
        )
        NautileIconButton(
            iconRes = R.drawable.ic_settings,
            contentDescription = "设置",
            onClick = onOpenSettings,
            testTag = "ai_settings_entry",
        )
    }
}

/** 消息列表：稳定 key = message.id；内容内边距 16dp/8dp；不显示时间（06 §2.1、§3）。 */
@Composable
private fun MessageList(
    state: AiUiState,
    listState: LazyListState,
) {
    val messages = state.messages
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_message_list"),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 8.dp,
        ),
    ) {
        itemsIndexed(
            items = messages,
            key = { _, message -> message.id.value },
        ) { index, message ->
            val topPadding = when {
                index == 0 -> 0.dp
                messages[index - 1].role != message.role -> 12.dp
                else -> 8.dp
            }
            val streamingText = if (message.id == state.streamingMessageId) {
                state.streamingText
            } else {
                null
            }
            AiMessageItem(
                message = message,
                streamingText = streamingText,
                stopping = state.stopping,
                topPadding = topPadding,
                modifier = Modifier.animateItem(
                    fadeInSpec = NautileMotion.StandardEffects,
                    placementSpec = NautileMotion.ListItemPlacement,
                    fadeOutSpec = NautileMotion.StandardEffects,
                ),
            )
        }
    }
}

/**
 * 单条消息（06 §3）：用户右对齐 primaryContainer，AI 左对齐 surfaceContainerHigh；
 * 角色标签“你/AI”与内容合并朗读，状态行单独成节点便于定位与播报。
 */
@Composable
private fun AiMessageItem(
    message: AiMessage,
    streamingText: String?,
    stopping: Boolean,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == AiRole.USER
    val isStreaming = streamingText != null
    val content = streamingText ?: message.content
    val horizontalAlignment = if (isUser) Alignment.End else Alignment.Start

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .testTag("ai_message_${message.id.value}"),
    ) {
        val bubbleMaxWidth = maxWidth * 0.82f
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = horizontalAlignment,
        ) {
            // 角色标签 + 气泡合并为一个语义节点，朗读为“你/AI：{内容}”。
            Column(
                modifier = Modifier
                    .widthIn(max = bubbleMaxWidth)
                    .semantics(mergeDescendants = true) {
                        if (isStreaming) stateDescription = "正在生成"
                    },
                horizontalAlignment = horizontalAlignment,
            ) {
                Text(
                    text = if (isUser) "你" else "AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ) {
                    if (content.isEmpty()) {
                        // 空助手占位气泡最小高 48dp（24dp 内容 + 上下 12dp 内边距）。
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .defaultMinSize(minWidth = 16.dp, minHeight = 24.dp),
                        )
                    } else {
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }

            val statusText = when {
                isStreaming -> if (stopping) "正在停止…" else "正在生成…"
                message.status == AiMessageStatus.STREAMING -> "正在生成…"
                message.status == AiMessageStatus.STOPPED -> "已停止"
                message.status == AiMessageStatus.FAILED -> aiErrorMessage(message.errorCode)
                else -> null
            }
            // 状态行只在消息边界变化：首次出现淡入（AI-ANIM-03）；
            // “正在停止…”→“已停止”等文字切换用 Crossfade（AI-ANIM-13）；流式增量不触发。
            AnimatedVisibility(
                visible = statusText != null,
                enter = fadeIn(animationSpec = NautileMotion.StandardEffects),
                exit = fadeOut(animationSpec = NautileMotion.StandardEffects),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))
                    Crossfade(
                        targetState = statusText,
                        animationSpec = NautileMotion.StandardEffects,
                        label = "ai_message_status_crossfade",
                    ) { text ->
                        if (text != null) {
                            val isError =
                                !isStreaming && message.status == AiMessageStatus.FAILED
                            // 终态/错误用 liveRegion 播报一次；流式过程不逐 token 播报（§10.1）。
                            val announce = !isStreaming &&
                                (message.status == AiMessageStatus.STOPPED ||
                                    message.status == AiMessageStatus.FAILED)
                            Text(
                                text = text,
                                style = if (isError) {
                                    MaterialTheme.typography.bodySmall
                                } else {
                                    MaterialTheme.typography.labelSmall
                                },
                                color = if (isError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .testTag("ai_message_status_${message.id.value}")
                                    .then(
                                        if (announce) {
                                            Modifier.semantics {
                                                liveRegion = LiveRegionMode.Polite
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 空态（06 §5.1）：必须显示“仅发送本次对话内容，不读取页面或本地数据”。 */
@Composable
private fun AiEmptyState(
    keyMissing: Boolean,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_empty_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NautileEmptyState(
            title = if (keyMissing) "尚未配置 DeepSeek Key" else "开始和 AI 对话",
            description = if (keyMissing) {
                "在设置中配置后即可开始对话。\n仅发送本次对话内容，不读取页面或本地数据。"
            } else {
                "输入消息开始对话。\n仅发送本次对话内容，不读取页面或本地数据。"
            },
            iconRes = if (keyMissing) R.drawable.ic_key else R.drawable.ic_auto_awesome,
        )
        if (keyMissing) {
            NautileButton(
                text = "去设置",
                onClick = onOpenSettings,
            )
        }
    }
}

/** 无 Key 且有历史消息时的输入栏上方提示条（06 §5.1）。 */
@Composable
private fun AiKeyMissingBanner(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp)
            .testTag("ai_key_missing_banner")
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "尚未配置 DeepSeek Key，无法发送",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenSettings) {
            Text("去设置")
        }
    }
}

/**
 * 输入栏（06 §4）：OutlinedTextField 1—5 行 + 48dp 发送/停止切换；
 * 发送条件见 [AiUiState.sendEnabled]；流式中仍可编辑草稿但不能发送。
 */
@Composable
private fun AiInputBar(
    state: AiUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = onDraftChange,
                    placeholder = {
                        Text(
                            text = "给 AI 发消息",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    // 多行可换行，不把 IME 回车当作发送（06 §4.1/§4.2）。
                    singleLine = false,
                    minLines = 1,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default,
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    enabled = !state.clearing,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_input"),
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 发送/停止同一位置切换，尺寸不变（AI-ANIM-04）；减少动态效果时直接切换。
                Crossfade(
                    targetState = state.stopVisible,
                    animationSpec = NautileMotion.StandardEffects,
                    label = "ai_send_stop_switch",
                ) { showStop ->
                    if (showStop) {
                        NautileIconButton(
                            iconRes = R.drawable.ic_stop,
                            contentDescription = if (state.stopEnabled) {
                                "停止"
                            } else {
                                "停止，正在停止"
                            },
                            onClick = onStop,
                            enabled = state.stopEnabled,
                            testTag = "ai_stop_button",
                        )
                    } else {
                        NautileIconButton(
                            iconRes = R.drawable.ic_send,
                            contentDescription = if (state.sendEnabled) {
                                "发送"
                            } else {
                                "发送，当前不可用"
                            },
                            onClick = onSend,
                            enabled = state.sendEnabled,
                            testTag = "ai_send_button",
                        )
                    }
                }
            }
        }
    }
}

/** 判定“接近底部”：最后一个可见项是列表末项，且底部距视口 ≤ 8dp（06 §4.4）。 */
private fun LazyListState.isNearBottom(thresholdPx: Int): Boolean {
    val info = layoutInfo
    val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return true
    if (info.totalItemsCount == 0) return true
    return lastVisible.index == info.totalItemsCount - 1 &&
        lastVisible.offset + lastVisible.size <= info.viewportEndOffset + thresholdPx
}

/**
 * 持久化错误码 → UI 文案（06 §5.3）；不显示 HTTP 数字、Key、模型名或服务端原文。
 *
 * 重启后只按落库短码映射；未知或缺失时回落通用“请求失败”。
 */
internal fun aiErrorMessage(code: AiErrorCode?): String = when (code) {
    AiErrorCode.KEY_MISSING -> "尚未配置 DeepSeek Key，无法发送"
    AiErrorCode.INVALID_KEY -> "发送失败：DeepSeek Key 无效，请在设置中更新"
    AiErrorCode.INSUFFICIENT_BALANCE -> "发送失败：账户余额不足"
    AiErrorCode.RATE_LIMITED -> "发送失败：请求过于频繁，请稍后重试"
    AiErrorCode.REQUEST_REJECTED -> "发送失败：请求未被接受"
    AiErrorCode.SERVER_ERROR -> "发送失败：服务暂时不可用"
    AiErrorCode.NETWORK -> "发送失败：网络不可用，请检查网络后重试"
    AiErrorCode.TIMEOUT -> "发送失败：请求超时，请稍后重试"
    AiErrorCode.STREAM_INTERRUPTED -> "发送失败：回复中断，已保留已收到的内容"
    AiErrorCode.HTTP_ERROR -> "发送失败：请求失败"
    AiErrorCode.UNKNOWN -> "发送失败：未知错误"
    null -> "发送失败：请求失败"
}
