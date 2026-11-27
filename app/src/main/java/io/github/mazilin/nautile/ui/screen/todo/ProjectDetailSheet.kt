package io.github.mazilin.nautile.ui.screen.todo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
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
 * 项目半屏详情（R5-9、AC-D-06、FR-V-08、03 规格 §2.6、ANIM-13）。
 *
 * - 废除独立编辑面板与头部编辑图标，右上角使用关闭图标按钮 `project_detail_close_button`；
 * - 项目名称内联编辑即保存，空名报错并阻止全部关闭路径；
 * - 子任务收起/展开使用纯图标按钮 `project_children_toggle`（`ic_expand_more`，0°↔180° 旋转，无文字），
 *   子任务区通过 `AnimatedVisibility` + `NautileMotion.ContainerExpand` 平滑展开/收起；
 * - 保留已结项项目“重新打开”操作位、完成率/计数展示与子任务项只读列表（点按进入任务详情）。
 */
@Composable
fun ProjectDetailSheet(
    state: ProjectDetailUiState,
    onNameChanged: (String) -> Unit,
    onCommitName: () -> Unit,
    onToggleCollapsed: () -> Unit,
    onOpenChild: (TaskId) -> Unit = {},
    onCloseRequested: () -> Unit,
    onReopen: () -> Unit = {},
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
                    inputActive() ||
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
                if (visible && !gestureFromInput.value && !inputActive() && contentPull > 0f) {
                    if (motion.shouldCommit(density, contentPull, available.y)) {
                        currentOnCloseRequested()
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
    // ANIM-13：子任务折叠 chevron 旋转（收起 0° 朝下 ↔ 展开 180° 朝上）。
    val chevronRotation by animateFloatAsState(
        targetValue = if (state.collapsed) 0f else 180f,
        animationSpec = NautileMotion.StandardSpatial,
        label = "project_children_chevron_rotation",
    )
    val statusSummary = state.completionPercent?.let { percent ->
        "状态：${state.closedLabel} · 已完成 ${state.completedChildCount}/${state.totalChildCount}（$percent%）"
    } ?: "状态：${state.closedLabel}"

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
                onCommitClose = { currentOnCloseRequested() },
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
        // 遮罩层：绘制与“点按遮罩关闭”均在遮罩层完成；无效名称由父层 canDismiss 阻止。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = motion.scrim.value * motion.dragScrimFactor() }
                .background(scrimColor.copy(alpha = NautileStates.SCRIM_ALPHA))
                .pointerInput(Unit) {
                    awaitEachGesture {
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
                            onCommitClose = { currentOnCloseRequested() },
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
                        NautileIconButton(
                            iconRes = R.drawable.ic_close,
                            contentDescription = "关闭",
                            onClick = { currentOnCloseRequested() },
                            testTag = "project_detail_close_button",
                        )
                    }
                    Spacer(modifier = Modifier.height(NautileSpacing.space2))
                    OutlinedTextField(
                        value = state.nameValue,
                        onValueChange = onNameChanged,
                        label = { Text("项目名称") },
                        singleLine = true,
                        isError = state.nameInvalid,
                        supportingText = if (state.nameInvalid) {
                            {
                                Text(
                                    text = "项目名称不能为空",
                                    modifier = Modifier.semantics {
                                        liveRegion = LiveRegionMode.Polite
                                    },
                                )
                            }
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
                            }
                            .testTag("project_detail_name"),
                    )
                    Spacer(modifier = Modifier.height(NautileSpacing.space2))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "子任务（${state.totalChildCount}）",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = statusSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // R5-9：纯图标折叠按钮（无文字），随展开态旋转 0°↔180°。
                        NautileIconButton(
                            iconRes = R.drawable.ic_expand_more,
                            contentDescription = if (state.collapsed) "展开子任务" else "收起子任务",
                            onClick = onToggleCollapsed,
                            modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                            testTag = "project_children_toggle",
                        )
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
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    AnimatedVisibility(
                        visible = !state.collapsed,
                        enter = expandVertically(animationSpec = NautileMotion.ContainerExpand) +
                            fadeIn(animationSpec = NautileMotion.StandardEffects),
                        exit = shrinkVertically(animationSpec = NautileMotion.ContainerExpand) +
                            fadeOut(animationSpec = NautileMotion.StandardEffects),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (state.children.isEmpty()) {
                                Text(
                                    text = "暂无子任务",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = NautileSpacing.space2),
                                )
                            } else {
                                state.children.forEach { child ->
                                    val closed = child.closedOutcome != null
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .clickable { onOpenChild(child.id) }
                                            .testTag("project_child_${child.id.value}")
                                            .padding(vertical = NautileSpacing.space2),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = child.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (closed) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                textDecoration = if (closed) {
                                                    TextDecoration.LineThrough
                                                } else {
                                                    TextDecoration.None
                                                },
                                            )
                                            Text(
                                                text = child.detailStatusLabel(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Icon(
                                            painter = painterResource(R.drawable.ic_chevron_right),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
