package io.github.mazilin.nautile.ui.screen.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.ui.designsystem.dragScrimFactor
import io.github.mazilin.nautile.ui.designsystem.nautileSheetBottomInsets
import io.github.mazilin.nautile.ui.designsystem.nautileSheetPullDown
import io.github.mazilin.nautile.ui.designsystem.nautileSheetShape
import io.github.mazilin.nautile.ui.designsystem.rememberSheetMotion
import io.github.mazilin.nautile.ui.designsystem.shouldCommit
import io.github.mazilin.nautile.ui.theme.NautileMotion
import io.github.mazilin.nautile.ui.theme.NautileSheetMetrics
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import io.github.mazilin.nautile.ui.theme.NautileStates
import kotlinx.coroutines.launch

/**
 * 项目半屏详情（S1-T08、T-071/D-02/D-03、AC-D-06、FR-V-08、MOT-08）。
 *
 * - 项目名称编辑即保存；子任务只读展示，点按先关项目详情再开任务详情（单主覆盖层）；
 * - 折叠状态由父层内存持有，不持久化；
 * - 拖柄或内容到顶继续下拉都先经父层 canLeave 检查，无效字段阻止并提示；
 * - 内边距与 inset 与 [TaskDetailSheet] 一致：24/8/24 + 导航栏 inset，面板唯一消费 inset；
 * - 下拉阈值 max(25% 高度, 96dp) 或速度 800dp/s，未过阈值用 BoundaryRebound 回弹；
 * 退出动效结束后才回调 [onExitFinished]。
 */
@Composable
fun ProjectDetailSheet(
    state: ProjectDetailUiState,
    onNameChanged: (String) -> Unit,
    onCommitName: () -> Unit,
    onToggleCollapsed: () -> Unit,
    onOpenChild: (TaskId) -> Unit = {},
    onCloseRequested: () -> Unit,
    onOpenEdit: () -> Unit = {},
    visible: Boolean = true,
    onExitFinished: () -> Unit = {},
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val motion = rememberSheetMotion(visible = visible, onExitFinished = onExitFinished)
    val currentVisible by rememberUpdatedState(visible)
    val currentOnCloseRequested by rememberUpdatedState(onCloseRequested)

    // T-088：输入框聚焦或手势起点落在输入框内时，不触发遮罩/内容的下拉关闭。
    val nameFocused = remember { mutableStateOf(false) }
    val nameBounds = remember { mutableStateOf<Rect?>(null) }
    val handleBounds = remember { mutableStateOf<Rect?>(null) }
    val boxOrigin = remember { mutableStateOf(Offset.Zero) }
    val gestureFromInput = remember { mutableStateOf(false) }

    fun inputActive(): Boolean = nameFocused.value

    var contentPull by remember { mutableFloatStateOf(0f) }
    val contentConnection = remember(motion, scrollState, visible, density) {
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
                if (contentPull == 0f) scope.launch { motion.takeOver() }
                contentPull += available.y
                if (visible) motion.dragBy(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (visible && !gestureFromInput.value && contentPull > 0f) {
                    if (motion.shouldCommit(density, contentPull, available.y)) {
                        onCloseRequested()
                        motion.settle(scope, 0f, NautileMotion.StandardEffects)
                    } else {
                        motion.settle(scope, 0f, NautileMotion.BoundaryRebound)
                    }
                }
                contentPull = 0f
                return Velocity.Zero
            }
        }
    }

    if (!motion.rendered) return

    val scrimColor = MaterialTheme.colorScheme.scrim
    val sheetShape = nautileSheetShape()
    val sheetInsets = nautileSheetBottomInsets()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { boxOrigin.value = it.positionInRoot() }
            // 祖先层 Initial pass 观察下拉，不与 verticalScroll 竞争；拖拽柄由自身手势处理。
            // T-088：输入框聚焦或起点在输入框内时整段手势不参与下拉关闭。
            .nautileSheetPullDown(
                motion = motion,
                visible = { currentVisible },
                scope = scope,
                onCommitClose = onCloseRequested,
                pass = PointerEventPass.Initial,
                canStart = {
                    !scrollState.canScrollBackward && !scrollState.canScrollForward
                },
                isExcluded = { point ->
                    val excluded = inputActive() ||
                        nameBounds.value?.contains(point + boxOrigin.value) == true ||
                        handleBounds.value?.contains(point + boxOrigin.value) == true
                    gestureFromInput.value = excluded
                    excluded
                },
            ),
    ) {
        // 遮罩层：绘制与“点按遮罩关闭”都只在遮罩层完成（MOT-08.3、COL-10、T-088）。
        // 遮罩位于面板下方，面板区域的点按由上层 Surface 自行消费，不会被误判为点按遮罩；
        // 无效名称仍由父层 canDismiss 阻止。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = motion.scrim.value * motion.dragScrimFactor() }
                .background(scrimColor.copy(alpha = NautileStates.SCRIM_ALPHA))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // 遮罩上的按下；已在别处消费的按下（按钮、输入框等）不参与点按判定。
                        val down = awaitFirstDown(requireUnconsumed = true)
                        var dragged = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!dragged &&
                                (change.isConsumed ||
                                    (change.position - down.position).getDistance() >=
                                    viewConfiguration.touchSlop)
                            ) {
                                dragged = true
                            }
                            if (dragged) change.consume()
                            if (!change.pressed) {
                                if (!dragged && currentVisible) currentOnCloseRequested()
                                break
                            }
                        }
                    }
                },
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // inset 只在此消费一次：键盘与导航栏取较大者（SPC-08、CMP-17）。
                .windowInsetsPadding(sheetInsets)
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
                        .onGloballyPositioned { handleBounds.value = it.boundsInRoot() }
                        .nautileSheetPullDown(
                            motion = motion,
                            visible = { currentVisible },
                            scope = scope,
                            onCommitClose = onCloseRequested,
                            // 输入框聚焦时拖拽柄也不参与下拉（T-088）。
                            isExcluded = { inputActive() },
                        ),
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
                        .padding(
                            start = NautileSpacing.space6,
                            end = NautileSpacing.space6,
                            top = NautileSpacing.space2,
                            bottom = NautileSpacing.space6,
                        )
                        .verticalScroll(scrollState)
                        .nestedScroll(contentConnection),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "项目详情",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        // 头部“编辑”入口（CMP-37.1）：打开只含名称的编辑面板。
                        IconButton(onClick = onOpenEdit) {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = "编辑项目",
                            )
                        }
                    }
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
                                        .padding(vertical = NautileSpacing.space2),
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
