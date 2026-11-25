package io.github.mazilin.nautile.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.ui.theme.NautileMotion
import io.github.mazilin.nautile.ui.theme.NautileSheetMetrics
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import io.github.mazilin.nautile.ui.theme.NautileStates
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 面板下拉阈值与拖拽常量（MOT-08、SPC-08）。
 *
 * 阈值只在此处定义，三个面板与设计系统共用：
 * 位移 ≥ max(25% 面板高度, 96dp) 或速度 > 800dp/s 时提交关闭。
 * 不改动条目滑动 72dp、空白右滑 48dp、清单边界 64dp（MOT-11/NO-10）。
 */
internal object SheetDragMetrics {
    /** 位移阈值下限：96dp（MOT-08）。 */
    val MIN_THRESHOLD_DP = 96.dp

    /** 位移阈值 = 面板高度 × 25%。 */
    const val HEIGHT_FRACTION = 0.25f

    /** 速度提交阈值：800dp/s。 */
    const val VELOCITY_DP_PER_SEC = 800f
}

/** 面板顶部 28dp 圆角、底角 0（SHP-02、CMP-17）；拖动与回弹中不做 shape morph（MOT-12）。 */
@Composable
internal fun nautileSheetShape(): Shape = MaterialTheme.shapes.extraLarge.copy(
    bottomStart = CornerSize(0.dp),
    bottomEnd = CornerSize(0.dp),
)

/**
 * 面板底部 inset 唯一消费点（SPC-08、CMP-17）。
 *
 * 取键盘与导航栏底部的较大者：键盘弹出时面板贴键盘，隐藏时留出导航栏；
 * 宿主不再额外加 inset，避免重复计算。左右 24dp、顶部 8dp、底部 24dp 由面板内容自行设置。
 *
 * 依赖宿主 Activity 使用 `windowSoftInputMode=adjustResize`：若为 adjustPan，
 * 窗口会被整体平移并与本 inset 叠加，出现双倍留白（G4 缺陷）。
 */
@Composable
internal fun nautileSheetBottomInsets(): WindowInsets =
    WindowInsets.ime.union(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))

/** 位移提交阈值（px）：max(25% 面板高度, 96dp)（MOT-08.3）。 */
internal fun SheetMotion.commitThresholdPx(density: Density): Float {
    val minThreshold = with(density) { SheetDragMetrics.MIN_THRESHOLD_DP.toPx() }
    return max(minThreshold, sheetHeightPx * SheetDragMetrics.HEIGHT_FRACTION)
}

/** 松手提交判定：位移超阈值或速度超 800dp/s（MOT-08.3）。 */
internal fun SheetMotion.shouldCommit(
    density: Density,
    displacementPx: Float,
    velocityPxPerSec: Float,
): Boolean {
    val velocityThreshold = with(density) {
        SheetDragMetrics.VELOCITY_DP_PER_SEC.dp.toPx()
    }
    return displacementPx >= commitThresholdPx(density) || velocityPxPerSec > velocityThreshold
}

/** 遮罩透明度随拖拽进度 1:1 联动（MOT-08.3）：位移越大越淡，最高 [NautileStates.SCRIM_ALPHA]。 */
internal fun SheetMotion.dragScrimFactor(): Float =
    if (sheetHeightPx <= 0f) {
        1f
    } else {
        (1f - dragOffsetPx / sheetHeightPx).coerceIn(0f, 1f)
    }

/**
 * 面板/详情共用的下拉手势（MOT-08、MOT-10、T-088）。
 *
 * - 拖动位移直接跟手，不经过动画；拖动开始即接管进行中的进入/回弹；
 * - 只在向下超过 touchSlop 后才接管；松手按 [SheetMotion.shouldCommit] 判定；
 * - [canStart] 为 false 时（如内容仍可滚动）让出，交由 nestedScroll/滚动处理；
 * - [isExcluded] 为 true 时整段手势不参与下拉（输入框聚焦或起点在输入框内）；
 * - 未提交用 [NautileMotion.BoundaryRebound]；提交关闭用 [NautileMotion.StandardEffects]
 *   （宿主阻止关闭时顺势回到原位并显示错误）。
 *
 * @param pass 祖先观察用 [PointerEventPass.Initial]；拖拽柄用 Main。
 */
internal fun Modifier.nautileSheetPullDown(
    motion: SheetMotion,
    visible: () -> Boolean,
    scope: CoroutineScope,
    onCommitClose: () -> Unit,
    pass: PointerEventPass = PointerEventPass.Main,
    canStart: () -> Boolean = { true },
    isExcluded: (Offset) -> Boolean = { false },
): Modifier = pointerInput(motion, pass) {
    val velocityTracker = VelocityTracker()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
        if (!visible() || isExcluded(down.position)) {
            return@awaitEachGesture
        }
        velocityTracker.resetTracking()
        velocityTracker.addPosition(down.uptimeMillis, down.position)
        var started = false
        var resolved = false
        var lastY = down.position.y
        var displacement = 0f
        while (true) {
            val event = awaitPointerEvent(pass)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            velocityTracker.addPosition(change.uptimeMillis, change.position)
            val totalDy = change.position.y - down.position.y
            if (!started && totalDy > 0f && abs(totalDy) >= viewConfiguration.touchSlop && canStart()) {
                started = true
                // 拖动开始即接管进入动画，把当前隐藏量折算为拖拽位移，衔接处不跳变。
                scope.launch { motion.takeOver() }
            }
            if (started) {
                val dy = change.position.y - lastY
                lastY = change.position.y
                displacement += dy
                motion.dragBy(dy)
                // 消费位移，避免同一手势再被点击/滚动识别为其它操作。
                change.consume()
            }
            if (!change.pressed) {
                if (started) {
                    resolved = true
                    val velocityY = velocityTracker.calculateVelocity().y
                    if (motion.shouldCommit(this, displacement, velocityY)) {
                        onCommitClose()
                        motion.settle(scope, 0f, NautileMotion.StandardEffects)
                    } else {
                        motion.settle(scope, 0f, NautileMotion.BoundaryRebound)
                    }
                }
                break
            }
        }
        if (started && !resolved && motion.dragOffsetPx > 0f) {
            // 手势被系统取消等异常路径：未结算时回弹，避免面板停在半空。
            motion.settle(scope, 0f, NautileMotion.BoundaryRebound)
        }
    }
}

/**
 * 底部面板结构（设计 §7.5 CMP-15/16/17/19、SPC-08）。
 *
 * 遮罩 32%、surfaceContainerLow 底、顶部 28dp 圆角（底角 0）、22dp 拖拽柄区；
 * 内容内边距左右 24dp、顶部 8dp、底部 24dp + 导航栏 inset；inset 只在此处消费一次。
 * 进入/退出/下拉/回弹按 MOT-08，退出动效结束后才回调 [onDismissed]。
 */
@Composable
fun NautileBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    canDismiss: () -> Boolean,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = rememberSheetMotion(visible = visible, onExitFinished = onDismissed)
    val scope = rememberCoroutineScope()
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentCanDismiss by rememberUpdatedState(canDismiss)
    if (!motion.rendered) return

    val sheetShape = nautileSheetShape()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = motion.scrim.value * motion.dragScrimFactor() }
                .background(
                    MaterialTheme.colorScheme.scrim.copy(alpha = NautileStates.SCRIM_ALPHA),
                )
                .pointerInput(Unit) {
                    detectTapGestures {
                        // canDismiss=false 时（如无效字段）不关闭，也不播退出。
                        if (currentCanDismiss()) {
                            currentOnDismissRequest()
                        }
                    }
                },
        )
        Surface(
            modifier = modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(nautileSheetBottomInsets())
                .onSizeChanged { motion.sheetHeightPx = it.height.toFloat() }
                .graphicsLayer {
                    // 面板从自身高度滑入/滑出；28dp 圆角固定，不做 shape morph（MOT-08、MOT-12）。
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
                            visible = { visible },
                            scope = scope,
                            onCommitClose = { currentOnDismissRequest() },
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
                        .then(contentModifier),
                    content = content,
                )
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

    /** 手势期间直接赋值：跟手 1:1，向下拉出、向上回推，均不经过动画。 */
    fun dragBy(dy: Float) {
        val max = if (sheetHeightPx > 0f) sheetHeightPx else Float.MAX_VALUE
        dragOffsetPx = (dragOffsetPx + dy).coerceIn(0f, max)
    }

    /** 松手后回弹：未超阈值用 BoundaryRebound；提交被无效字段阻止时用 StandardEffects。 */
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
 * 初次组合即播放入场；[visible] 变为 false 时先播退出，结束后置 [SheetMotion.rendered] = false
 * 并回调 [onExitFinished]；中途反向时从当前值重定向，不回到起点重播（MOT-10）。
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
