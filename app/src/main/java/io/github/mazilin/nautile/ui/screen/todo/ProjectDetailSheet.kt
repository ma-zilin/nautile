package io.github.mazilin.nautile.ui.screen.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.ui.theme.NautileMotion
import io.github.mazilin.nautile.ui.theme.NautileSheetMetrics
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import io.github.mazilin.nautile.ui.theme.NautileStates
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 项目半屏详情（S1-T08、T-071/D-02/D-03、AC-D-06、FR-V-08）。
 *
 * - 项目名称编辑即保存；子任务只读展示，点按先关项目详情再开任务详情（单主覆盖层）；
 * - 折叠状态由父层内存持有，不持久化；
 * - 拖柄或内容到顶继续下拉都先经父层 canLeave 检查，无效字段阻止并提示。
 *
 * 视觉与动效与 [TaskDetailSheet] 一致（§3.2/§4.2、MOT-08）；退出动效结束后才回调
 * [onExitFinished]。默认 visible = true，直接组合时保持既有静态行为。
 */
@Composable
fun ProjectDetailSheet(
    state: ProjectDetailUiState,
    onNameChanged: (String) -> Unit,
    onCommitName: () -> Unit,
    onToggleCollapsed: () -> Unit,
    onOpenChild: (TaskId) -> Unit = {},
    onCloseRequested: () -> Unit,
    visible: Boolean = true,
    onExitFinished: () -> Unit = {},
) {
    val density = LocalDensity.current
    val closeThreshold = with(density) { 64.dp.toPx() }
    var dragTotal by remember { mutableFloatStateOf(0f) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val motion = rememberSheetMotion(visible = visible, onExitFinished = onExitFinished)

    // T-088：输入框聚焦或手势起点落在输入框内时，不触发遮罩/内容的下拉关闭。
    val nameFocused = remember { mutableStateOf(false) }
    val nameBounds = remember { mutableStateOf<Rect?>(null) }
    val boxOrigin = remember { mutableStateOf(Offset.Zero) }
    val gestureFromInput = remember { mutableStateOf(false) }

    var contentPull by remember { mutableFloatStateOf(0f) }
    val contentConnection = remember(closeThreshold, visible) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // T-088：输入框起手/聚焦的手势不累计，避免输入框内拖动误关。
                if (gestureFromInput.value ||
                    source != NestedScrollSource.UserInput ||
                    available.y <= 0f ||
                    scrollState.canScrollBackward
                ) {
                    return Offset.Zero
                }
                contentPull += available.y
                if (visible) motion.dragBy(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (visible && !gestureFromInput.value && contentPull > closeThreshold) {
                    onCloseRequested()
                    motion.settle(scope, 0f, NautileMotion.StandardEffects)
                } else if (visible && contentPull > 0f) {
                    motion.settle(scope, 0f, NautileMotion.BoundaryRebound)
                }
                contentPull = 0f
                return Velocity.Zero
            }
        }
    }

    if (!motion.rendered) return

    val scrimColor = MaterialTheme.colorScheme.scrim
    val sheetShape = MaterialTheme.shapes.extraLarge.copy(
        bottomStart = CornerSize(0.dp),
        bottomEnd = CornerSize(0.dp),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { boxOrigin.value = it.positionInRoot() }
            // T-084：祖先层 Initial pass 观察下拉，不与 verticalScroll 竞争；
            // 仅内容不可滚动且向下超过 64dp 时请求关闭，不消费事件、不遮挡子任务点击。
            // T-088：输入框聚焦或起点在输入框内时整段手势不参与下拉关闭。
            .pointerInput(closeThreshold, visible) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    gestureFromInput.value = nameFocused.value ||
                        nameBounds.value?.contains(down.position + boxOrigin.value) == true
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
                                // 拖动开始即接管进入动画（在外部作用域执行，避免受限挂起作用域限制）。
                                if (visible) scope.launch { motion.takeOver() }
                            }
                            if (dy > 0f) {
                                totalDown += dy
                                if (visible) motion.dragBy(dy)
                            }
                        }
                        if (!change.pressed) {
                            if (visible && !gestureFromInput.value && totalDown > closeThreshold) {
                                onCloseRequested()
                                motion.settle(scope, 0f, NautileMotion.StandardEffects)
                            } else if (visible && !gestureFromInput.value && totalDown > 0f) {
                                motion.settle(scope, 0f, NautileMotion.BoundaryRebound)
                            }
                            totalDown = 0f
                            break
                        }
                    }
                }
            },
    ) {
        // 遮罩 alpha 在图层中读取动效值，避免逐帧重组（MOT-08、COL-10）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = motion.scrim.value }
                .background(scrimColor.copy(alpha = NautileStates.SCRIM_ALPHA)),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .onSizeChanged { motion.sheetHeightPx = it.height.toFloat() }
                .graphicsLayer {
                    // 面板从自身高度滑入/滑出；60% 高度与 28dp 圆角形态保持（MOT-08、MOT-12）。
                    translationY = (1f - motion.reveal.value) * size.height + motion.dragOffsetPx
                },
            shape = sheetShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(NautileSheetMetrics.HANDLE_AREA_DP)
                        .pointerInput(closeThreshold, visible) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragTotal = 0f
                                    if (visible) scope.launch { motion.takeOver() }
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    dragTotal += dragAmount
                                    if (visible) motion.dragBy(dragAmount)
                                },
                                onDragEnd = {
                                    if (visible) {
                                        if (dragTotal > closeThreshold) {
                                            onCloseRequested()
                                            motion.settle(scope, 0f, NautileMotion.StandardEffects)
                                        } else if (dragTotal > 0f) {
                                            motion.settle(scope, 0f, NautileMotion.BoundaryRebound)
                                        }
                                    }
                                    dragTotal = 0f
                                },
                                onDragCancel = {
                                    dragTotal = 0f
                                    if (visible) {
                                        motion.settle(scope, 0f, NautileMotion.BoundaryRebound)
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(NautileSheetMetrics.HANDLE_WIDTH_DP)
                            .height(NautileSheetMetrics.HANDLE_HEIGHT_DP)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = NautileSpacing.space4)
                        .padding(bottom = NautileSpacing.space4)
                        .verticalScroll(scrollState)
                        .nestedScroll(contentConnection),
                ) {
                    Text("项目详情", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = state.nameValue,
                        onValueChange = onNameChanged,
                        label = { Text("项目名称") },
                        singleLine = true,
                        isError = state.nameInvalid,
                        supportingText = if (state.nameInvalid) {
                            { Text("项目名称不能为空") }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onCommitName() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { nameBounds.value = it.boundsInRoot() }
                            .onFocusChanged { focus ->
                                nameFocused.value = focus.isFocused
                                if (!focus.isFocused && state.nameDraft != null) onCommitName()
                            },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("状态：${state.closedLabel}", modifier = Modifier.weight(1f))
                        TextButton(onClick = onToggleCollapsed) {
                            Text(if (state.collapsed) "展开子任务" else "收起子任务")
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
                    if (!state.collapsed) {
                        if (state.children.isEmpty()) {
                            Text("暂无子任务", style = MaterialTheme.typography.bodySmall)
                        } else {
                            state.children.forEach { child ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenChild(child.id) }
                                        .testTag("project_child_${child.id.value}")
                                        .padding(vertical = 6.dp),
                                ) {
                                    Text(child.title, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = child.detailStatusLabel(),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(NautileSpacing.space2))
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
}
