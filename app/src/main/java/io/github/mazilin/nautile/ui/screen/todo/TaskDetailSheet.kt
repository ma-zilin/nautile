package io.github.mazilin.nautile.ui.screen.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import io.github.mazilin.nautile.domain.model.Priority
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
 * 任务半屏详情（S1-T08、AC-D-06/08、AC-T-29、MOT-08）。
 *
 * 无整页保存按钮：标题在字段结束/IME 完成时提交；无效标题由父层阻止离开并提示。
 * 下滑关闭与关闭按钮都先经父层 canLeave 检查；本组件不自行修改业务数据。
 *
 * 视觉按 SPC-08/CMP-17：surfaceContainerLow、顶部 28dp 圆角、22dp 拖拽柄区、
 * 内容内边距左右 24dp、顶部 8dp、底部 24dp + 导航栏 inset（面板唯一消费 inset）；
 * 下拉跟手 1:1，阈值 max(25% 高度, 96dp) 或速度 800dp/s，未过阈值用 BoundaryRebound 回弹；
 * 退出动效结束后才回调 [onExitFinished]（CMP-19）。
 *
 * @param visible 进出场目标可见性；默认 true，直接组合时仍为最终静止态。
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
    visible: Boolean = true,
    onExitFinished: () -> Unit = {},
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val motion = rememberSheetMotion(visible = visible, onExitFinished = onExitFinished)
    val currentVisible by rememberUpdatedState(visible)

    // T-088：输入框聚焦或手势起点落在输入框内时，不触发遮罩/内容的下拉关闭。
    val titleFocused = remember { mutableStateOf(false) }
    val detailFocused = remember { mutableStateOf(false) }
    val titleBounds = remember { mutableStateOf<Rect?>(null) }
    val detailBounds = remember { mutableStateOf<Rect?>(null) }
    val handleBounds = remember { mutableStateOf<Rect?>(null) }
    val boxOrigin = remember { mutableStateOf(Offset.Zero) }
    val gestureFromInput = remember { mutableStateOf(false) }

    fun inputActive(): Boolean = titleFocused.value || detailFocused.value

    fun pointInInput(pointInRoot: Offset): Boolean =
        titleBounds.value?.contains(pointInRoot) == true ||
            detailBounds.value?.contains(pointInRoot) == true

    var contentPull by remember { mutableFloatStateOf(0f) }
    val contentConnection = remember(motion, scrollState, visible, density) {
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
                if (contentPull == 0f) scope.launch { motion.takeOver() }
                contentPull += available.y
                if (visible) motion.dragBy(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
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
            // 祖先层 Initial pass 观察下拉，不与内容 verticalScroll 竞争；
            // 仅内容不可滚动时进入拖拽，不遮挡输入框/按钮；拖拽柄由自身手势处理，避免双重位移。
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
                        pointInInput(point + boxOrigin.value) ||
                        handleBounds.value?.contains(point + boxOrigin.value) == true
                    gestureFromInput.value = excluded
                    excluded
                },
            ),
    ) {
        // 遮罩 alpha 在图层中读取动效值，并随拖拽位移联动（MOT-08.3、COL-10）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = motion.scrim.value * motion.dragScrimFactor() }
                .background(scrimColor.copy(alpha = NautileStates.SCRIM_ALPHA)),
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
                    Text("任务详情", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(NautileSpacing.space2))
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
                        Spacer(modifier = Modifier.height(NautileSpacing.space2))
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

private fun priorityLabel(priority: Priority): String = when (priority) {
    Priority.HIGH -> "高"
    Priority.MEDIUM -> "中"
    Priority.LOW -> "低"
    Priority.NONE -> "无"
}
