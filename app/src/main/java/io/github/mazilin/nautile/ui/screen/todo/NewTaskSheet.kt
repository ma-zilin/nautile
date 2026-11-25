package io.github.mazilin.nautile.ui.screen.todo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.ui.theme.NautileMotion
import io.github.mazilin.nautile.ui.theme.NautileSheetMetrics
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import io.github.mazilin.nautile.ui.theme.NautileStates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 新建底部面板（S1-T09/T-071、FR-D-02、BR-69）。
 *
 * - 打开时先选“待办/项目”，不自动弹键盘；
 * - 待办：只有“创建”才写库；已结束清单内添加时可选四个非 ENDED 目标清单，默认收件箱；
 * - 项目：复用既有 ProjectCommands.create；
 * - 校验失败留在面板并显示字段错误；取消/关闭不生成半成品。
 *
 * 视觉按 §3.2/§4.2：surfaceContainerLow 底、顶部 28dp 圆角（底角 0）、22dp 拖拽柄区、
 * 16dp 内边距；进出场按 MOT-08，遮罩用 [NautileStates.SCRIM_ALPHA]。
 *
 * @param visible 进出场目标可见性；退出动效结束后才回调 [onExitFinished]。
 *   默认 true，直接组合时仍为最终静止态（测试与既有静态行为不变）。
 */
@Composable
fun NewTaskSheet(
    state: NewTaskUiState,
    onKindChanged: (NewTaskKind) -> Unit = {},
    onTargetChanged: (GtdPosition) -> Unit = {},
    onTitleChanged: (String) -> Unit = {},
    onDetailChanged: (String) -> Unit = {},
    onProjectNameChanged: (String) -> Unit = {},
    onCreate: () -> Unit = {},
    onCreateProject: () -> Unit = {},
    onCancel: () -> Unit = {},
    onDismissRequest: () -> Unit = {},
    visible: Boolean = true,
    onExitFinished: () -> Unit = {},
) {
    val motion = rememberSheetMotion(visible = visible, onExitFinished = onExitFinished)
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentVisible by rememberUpdatedState(visible)
    // 新建面板只做进出场视觉，不新增下拉关闭路径；表单行为保持不变。
    if (!motion.rendered) return

    val scrimColor = MaterialTheme.colorScheme.scrim
    val sheetShape = MaterialTheme.shapes.extraLarge.copy(
        bottomStart = CornerSize(0.dp),
        bottomEnd = CornerSize(0.dp),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    // 无效字段的阻止由宿主 onDismissRequest 内部校验，行为不变。
                    if (currentVisible) currentOnDismissRequest()
                }
            },
        contentAlignment = Alignment.BottomCenter,
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
                .fillMaxWidth()
                .imePadding()
                .onSizeChanged { motion.sheetHeightPx = it.height.toFloat() }
                .graphicsLayer {
                    // 面板从自身高度滑入/滑出；高度形态不随内容变化做动画（MOT-08、MOT-12）。
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
                        .height(NautileSheetMetrics.HANDLE_AREA_DP),
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
                        .padding(horizontal = NautileSpacing.space4)
                        .padding(bottom = NautileSpacing.space4),
                ) {
                    Text("新建", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.padding(top = NautileSpacing.space1),
                        horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space2),
                    ) {
                        FilterChip(
                            selected = state.kind == NewTaskKind.TODO,
                            onClick = { onKindChanged(NewTaskKind.TODO) },
                            label = { Text("待办") },
                        )
                        FilterChip(
                            selected = state.kind == NewTaskKind.PROJECT,
                            onClick = { onKindChanged(NewTaskKind.PROJECT) },
                            label = { Text("项目") },
                        )
                    }

                    when (state.kind) {
                        NewTaskKind.TODO -> {
                            if (state.showTargetPicker) {
                                Text(
                                    text = "目标清单（已结束不可选）",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = NautileSpacing.space2),
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space2),
                                ) {
                                    GtdPosition.entries
                                        .filter { it != GtdPosition.ENDED }
                                        .forEach { position ->
                                            FilterChip(
                                                selected = state.targetPosition == position,
                                                onClick = { onTargetChanged(position) },
                                                label = { Text(position.title) },
                                            )
                                        }
                                }
                            } else {
                                Text(
                                    text = "清单：${state.targetPosition.title}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            Spacer(modifier = Modifier.height(NautileSpacing.space2))
                            OutlinedTextField(
                                value = state.title,
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
                                keyboardActions = KeyboardActions(onDone = { onCreate() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            // 固定第二行：详情小字行，始终显示，不提供展开/隐藏（FR-D-03）。
                            OutlinedTextField(
                                value = state.detail,
                                onValueChange = onDetailChanged,
                                label = { Text("详情（可选）") },
                                textStyle = MaterialTheme.typography.bodySmall,
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("new_task_detail"),
                            )
                        }

                        NewTaskKind.PROJECT -> {
                            Spacer(modifier = Modifier.height(NautileSpacing.space2))
                            OutlinedTextField(
                                value = state.projectName,
                                onValueChange = onProjectNameChanged,
                                label = { Text("项目名称") },
                                singleLine = true,
                                isError = state.projectNameInvalid,
                                supportingText = if (state.projectNameInvalid) {
                                    { Text("项目名称不能为空") }
                                } else {
                                    null
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { onCreateProject() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    state.error?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.saving) {
                        Text("创建中…", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = NautileSpacing.space2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = onCancel,
                            enabled = !state.saving,
                        ) {
                            Text("取消")
                        }
                        Button(
                            onClick = if (state.kind == NewTaskKind.TODO) {
                                onCreate
                            } else {
                                onCreateProject
                            },
                            enabled = !state.saving,
                        ) {
                            Text(if (state.kind == NewTaskKind.TODO) "创建" else "创建项目")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 面板/详情进出的共享状态机（MOT-08、MOT-10）。
 *
 * - [reveal] 1 = 完全打开、0 = 完全移出，进入/退出用 [NautileMotion.Expressive]；
 * - [scrim] 遮罩透明度比例，用 [NautileMotion.StandardEffects]；
 * - [dragOffsetPx] 手势期间的直接位移，跟随手指不经动画；松手才回弹/提交；
 * - [rendered] 退出动效结束后置 false，宿主据此移除节点。
 *
 * 状态放在 `remember` 中，动画只在 [visible] 变化时启动，不会因重组从头重播。
 */
@Stable
internal class SheetMotion internal constructor(
    val reveal: Animatable<Float, AnimationVector1D>,
    val scrim: Animatable<Float, AnimationVector1D>,
) {
    var dragOffsetPx by mutableFloatStateOf(0f)
    var rendered by mutableStateOf(true)
    /** 面板自身高度（px），由面板 onSizeChanged 写入，不需要参与组合。 */
    var sheetHeightPx = 0f
    internal var settleJob: Job? = null

    /** 拖动开始即接管进行中的进入动画，把当前隐藏量折算成拖拽位移，衔接处不跳变。 */
    suspend fun takeOver() {
        settleJob?.cancel()
        settleJob = null
        reveal.stop()
        scrim.stop()
        if (sheetHeightPx > 0f && reveal.value < 1f) {
            dragOffsetPx = (1f - reveal.value) * sheetHeightPx
            reveal.snapTo(1f)
        }
        scrim.snapTo(1f)
    }

    /** 手势期间直接赋值：只跟随向下位移，不经过动画。 */
    fun dragBy(dy: Float) {
        if (dy <= 0f) return
        val max = if (sheetHeightPx > 0f) sheetHeightPx else Float.MAX_VALUE
        dragOffsetPx = (dragOffsetPx + dy).coerceIn(0f, max)
    }

    /** 松手后回弹：未超阈值用 BoundaryRebound；提交关闭但被无效字段阻止时用 StandardEffects。 */
    fun settle(scope: CoroutineScope, target: Float, spec: AnimationSpec<Float>) {
        settleJob?.cancel()
        settleJob = scope.launch {
            val start = dragOffsetPx
            animate(start, target, animationSpec = spec) { value, _ -> dragOffsetPx = value }
        }
    }
}

/**
 * 创建并驱动面板进出场动效（MOT-08）。
 *
 * 初次组合即播放入场（与 GTD 选择器“首帧终态”不同，面板本身在打开时才有意义）；
 * [visible] 变为 false 时先播退出，结束后置 [SheetMotion.rendered] = false 并回调
 * [onExitFinished]；中途反向时从当前值重定向，不回到起点重播（MOT-10）。
 */
@Composable
internal fun rememberSheetMotion(
    visible: Boolean,
    onExitFinished: () -> Unit,
): SheetMotion {
    val motion = remember { SheetMotion(Animatable(0f), Animatable(0f)) }
    val latestOnExitFinished by rememberUpdatedState(onExitFinished)
    LaunchedEffect(visible) {
        if (visible) {
            motion.rendered = true
            motion.dragOffsetPx = 0f
            coroutineScope {
                launch { motion.reveal.animateTo(1f, NautileMotion.Expressive) }
                launch { motion.scrim.animateTo(1f, NautileMotion.StandardEffects) }
            }
        } else {
            motion.settleJob?.cancel()
            motion.settleJob = null
            val height = motion.sheetHeightPx
            if (height > 0f && motion.dragOffsetPx > 0f) {
                // 拖动中提交关闭：把剩余拖拽位移折算为 reveal 起点，避免跳变。
                motion.reveal.snapTo(
                    (1f - motion.dragOffsetPx / height).coerceIn(0f, 1f),
                )
            }
            motion.dragOffsetPx = 0f
            coroutineScope {
                launch { motion.reveal.animateTo(0f, NautileMotion.Expressive) }
                launch { motion.scrim.animateTo(0f, NautileMotion.StandardEffects) }
            }
            motion.rendered = false
            latestOnExitFinished()
        }
    }
    return motion
}
