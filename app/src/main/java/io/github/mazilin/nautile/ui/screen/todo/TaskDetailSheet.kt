package io.github.mazilin.nautile.ui.screen.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.domain.model.Priority
import kotlin.math.abs

/**
 * 任务半屏详情（S1-T08、AC-D-06/08、AC-T-29）。
 *
 * 无整页保存按钮：标题在字段结束/IME 完成时提交；无效标题由父层阻止离开并提示。
 * 下滑关闭与关闭按钮都先经父层 canLeave 检查；本组件不自行修改业务数据。
 */
@Composable
fun TaskDetailSheet(
    state: TaskDetailUiState,
    onTitleChanged: (String) -> Unit,
    onCommitTitle: () -> Unit,
    onReopen: () -> Unit,
    onCloseRequested: () -> Unit,
    onDetailChanged: (String) -> Unit = {},
    onCommitDetail: () -> Unit = {},
) {
    val density = LocalDensity.current
    val closeThreshold = with(density) { 64.dp.toPx() }
    var dragTotal by remember { mutableFloatStateOf(0f) }
    val scrollState = rememberScrollState()

    // T-088：输入框聚焦或手势起点落在输入框内时，不触发遮罩/内容的下拉关闭。
    val titleFocused = remember { mutableStateOf(false) }
    val detailFocused = remember { mutableStateOf(false) }
    val titleBounds = remember { mutableStateOf<Rect?>(null) }
    val detailBounds = remember { mutableStateOf<Rect?>(null) }
    val boxOrigin = remember { mutableStateOf(Offset.Zero) }
    val gestureFromInput = remember { mutableStateOf(false) }
    fun inputActive(): Boolean = titleFocused.value || detailFocused.value
    fun pointInInput(pointInRoot: Offset): Boolean =
        titleBounds.value?.contains(pointInRoot) == true ||
            detailBounds.value?.contains(pointInRoot) == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .onGloballyPositioned { boxOrigin.value = it.positionInRoot() }
            // T-084：在祖先层用 Initial pass 观察下拉，不与内容 verticalScroll 竞争；
            // 仅内容不可滚动且向下超过 64dp 时请求关闭，不消费事件、不遮挡输入框/按钮。
            // T-088：输入框聚焦或起点在输入框内时整段手势不参与下拉关闭。
            .pointerInput(closeThreshold) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    gestureFromInput.value =
                        inputActive() || pointInInput(down.position + boxOrigin.value)
                    var totalDown = 0f
                    var started = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!gestureFromInput.value &&
                            !scrollState.canScrollBackward &&
                            !scrollState.canScrollForward
                        ) {
                            val dy = change.positionChange().y
                            if (!started) {
                                if (abs(dy) < viewConfiguration.touchSlop) {
                                    if (!change.pressed) break
                                    continue
                                }
                                started = true
                            }
                            if (dy > 0f) totalDown += dy
                        }
                        if (!change.pressed) {
                            if (!gestureFromInput.value && totalDown > closeThreshold) {
                                onCloseRequested()
                            }
                            totalDown = 0f
                            break
                        }
                    }
                }
            },
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            var contentPull by remember { mutableFloatStateOf(0f) }
            val contentConnection = remember(closeThreshold) {
                object : NestedScrollConnection {
                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        // 内容已到顶仍继续下拉时累计；无效字段由父层阻止关闭。
                        // T-088：输入框起手/聚焦的手势不累计，避免输入框内拖动误关。
                        if (gestureFromInput.value ||
                            source != NestedScrollSource.UserInput ||
                            available.y <= 0f ||
                            scrollState.canScrollBackward
                        ) {
                            return Offset.Zero
                        }
                        contentPull += available.y
                        return Offset(0f, available.y)
                    }

                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                        if (!gestureFromInput.value && contentPull > closeThreshold) {
                            onCloseRequested()
                        }
                        contentPull = 0f
                        return Velocity.Zero
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState)
                    .nestedScroll(contentConnection),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .pointerInput(closeThreshold) {
                            detectVerticalDragGestures(
                                onDragStart = { dragTotal = 0f },
                                onVerticalDrag = { _, dragAmount -> dragTotal += dragAmount },
                                onDragEnd = {
                                    if (dragTotal > closeThreshold) onCloseRequested()
                                    dragTotal = 0f
                                },
                                onDragCancel = { dragTotal = 0f },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = RoundedCornerShape(2.dp),
                            ),
                    )
                }

                Text("任务详情", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.titleValue,
                    onValueChange = onTitleChanged,
                    label = { Text("标题") },
                    singleLine = true,
                    isError = state.titleInvalid,
                    supportingText = if (state.titleInvalid) {
                        { Text("标题不能为空") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCommitTitle() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { titleBounds.value = it.boundsInRoot() }
                        .onFocusChanged { focus ->
                            titleFocused.value = focus.isFocused
                            if (!focus.isFocused && state.titleDraft != null) onCommitTitle()
                        },
                )
                // 详情紧跟标题：多行自由文本，编辑即保存（FR-D-03、AC-D-09）。
                OutlinedTextField(
                    value = state.detailValue,
                    onValueChange = onDetailChanged,
                    label = { Text("详情（可选）") },
                    minLines = 1,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCommitDetail() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { detailBounds.value = it.boundsInRoot() }
                        .onFocusChanged { focus ->
                            detailFocused.value = focus.isFocused
                            if (!focus.isFocused && state.detailDraft != null) onCommitDetail()
                        }
                        .testTag("task_detail_detail"),
                )
                Text("GTD 位置：${state.task.position.title}")
                Text("优先级：${priorityLabel(state.task.priority)}")
                if (state.task.projectId != null) {
                    Text("所属项目：已关联")
                }
                if (state.canReopen) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onReopen) {
                        Text("重新打开")
                    }
                }
                if (state.saving) {
                    Text("保存中…", style = MaterialTheme.typography.labelSmall)
                }
                state.error?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onCloseRequested,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

private fun priorityLabel(priority: Priority): String = when (priority) {
    Priority.HIGH -> "高"
    Priority.MEDIUM -> "中"
    Priority.LOW -> "低"
    Priority.NONE -> "无"
}
