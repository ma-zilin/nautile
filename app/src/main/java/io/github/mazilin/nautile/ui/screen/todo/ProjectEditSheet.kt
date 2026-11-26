package io.github.mazilin.nautile.ui.screen.todo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
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

/**
 * 项目编辑面板（R4 G5、CMP-37.1、AC-D-07）。
 *
 * - 与任务编辑面板同构的下半屏面板，但只编辑项目名称（FR-D-02）；
 * - 预填当前名称；点“保存”写库，失败保留输入并显示错误；空名阻止关闭并提示；
 * - 返回/遮罩点按关闭编辑面板，不直接关项目详情；
 * - 遮罩 32%、顶部 28dp 圆角、22dp 拖拽柄区、24/8/24 + 导航栏 inset 沿用 G4；
 * - 退出动效结束后才移除。
 */
@Composable
fun ProjectEditSheet(
    state: ProjectEditUiState,
    onNameChanged: (String) -> Unit = {},
    onSave: () -> Unit = {},
    onDismissRequest: () -> Unit = {},
    visible: Boolean = true,
    onExitFinished: () -> Unit = {},
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val motion = rememberSheetMotion(visible = visible, onExitFinished = onExitFinished)
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentVisible by rememberUpdatedState(visible)

    // 返回只关编辑面板；保存中或空名由 ViewModel 阻止并提示。
    BackHandler(enabled = visible) { currentOnDismissRequest() }

    // T-088：输入框聚焦或手势起点落在输入框内时不触发遮罩/内容下拉关闭。
    val nameFocused = remember { mutableStateOf(false) }
    val nameBounds = remember { mutableStateOf<Rect?>(null) }
    val boxOrigin = remember { mutableStateOf(Offset.Zero) }

    fun inputActive(): Boolean = nameFocused.value

    var contentPull by remember { mutableFloatStateOf(0f) }
    val contentConnection = remember(motion, scrollState, visible, density) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (inputActive() ||
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

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                if (visible && !inputActive() && contentPull > 0f) {
                    if (motion.shouldCommit(density, contentPull, available.y)) {
                        currentOnDismissRequest()
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
            // 遮罩点按关闭：只有未移动的点按才关闭（T-088）。
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
                            if (!dragged && currentVisible) currentOnDismissRequest()
                            break
                        }
                    }
                }
            }
            .nautileSheetPullDown(
                motion = motion,
                visible = { currentVisible },
                scope = scope,
                onCommitClose = { currentOnDismissRequest() },
                pass = PointerEventPass.Initial,
                canStart = { !scrollState.canScrollBackward && !scrollState.canScrollForward },
                isExcluded = { point ->
                    inputActive() || nameBounds.value?.contains(point + boxOrigin.value) == true
                },
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 遮罩 alpha 随动效与拖拽位移联动（MOT-08.3、COL-10）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = motion.scrim.value * motion.dragScrimFactor() }
                .background(scrimColor.copy(alpha = NautileStates.SCRIM_ALPHA)),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                // inset 只在此消费一次：键盘与导航栏取较大者（SPC-08、CMP-17）。
                .windowInsetsPadding(sheetInsets)
                .onSizeChanged { motion.sheetHeightPx = it.height.toFloat() }
                .graphicsLayer {
                    translationY = (1f - motion.reveal.value) * size.height + motion.dragOffsetPx
                },
            shape = sheetShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(NautileSheetMetrics.HANDLE_AREA_DP)
                        .nautileSheetPullDown(
                            motion = motion,
                            visible = { currentVisible },
                            scope = scope,
                            onCommitClose = { currentOnDismissRequest() },
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
                    Text("编辑项目", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = state.name,
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
                        keyboardActions = KeyboardActions(onDone = { onSave() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { nameBounds.value = it.boundsInRoot() }
                            .onFocusChanged { nameFocused.value = it.isFocused }
                            .testTag("project_edit_name"),
                    )
                    state.error?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.saving) {
                        Text("保存中…", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = NautileSpacing.space2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = onDismissRequest,
                            enabled = !state.saving,
                        ) {
                            Text("取消")
                        }
                        Button(
                            onClick = onSave,
                            enabled = !state.saving,
                        ) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }
}
