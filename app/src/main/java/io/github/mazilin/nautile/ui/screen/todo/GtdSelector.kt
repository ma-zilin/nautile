package io.github.mazilin.nautile.ui.screen.todo

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.interaction.GestureArbiter
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.theme.NautileMotion
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import io.github.mazilin.nautile.ui.theme.NautileStates
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * GTD 左侧抽屉（R5-1；FR-G-15/16、AC-G-04、CMP-10—13、MOT-07、§15 CMP-47）。
 *
 * 全高贴左、形状 None、`surfaceContainerLow`；右侧整屏 32% `scrim`，点按遮罩关闭并隔离底层滚动。
 * 宽度按 R5 CMP-10 测量：`W_D = min(max(五项标题最长文本宽 + 2 × 24dp, 240dp), 320dp)`，
 * 文本按 `labelLarge`、当前 `fontScale` 测量；切换前后宽度不变。
 * 抽屉内容消费状态栏上/导航栏下 inset 一次，背景延伸到屏幕边缘；行水平 24dp、上下 12dp、
 * 最小高 48dp、项间 4dp，标题行含“清单”与关闭图标、高 48dp。
 *
 * 只有点按五项才切换清单，切换后保持抽屉打开且宽度不变；抽屉内禁用上下拉切换，
 * 不出现“松手切换到 …”。关闭路径：关闭图标、点遮罩、系统返回（宿主覆盖层）、抽屉上左滑。
 * 左滑关闭支持 1:1 跟手位移与遮罩联动，未达阈值松手经 [NautileMotion.BoundaryRebound] 回弹。
 *
 * 动效（MOT-07、ANIM-05/06）：位移 `translationX = -W_D → 0` 用 [NautileMotion.Expressive]，遮罩 alpha 用
 * [NautileMotion.StandardEffects]；首帧直接终态；目标变化从当前值与速度重定向（可打断）；
 * 退出动效结束才回调 [onExitFinished]，由宿主移除覆盖层。
 *
 * @param tasks 保留旧调用签名以兼容既有调用点与测试；左侧抽屉不再渲染任务列表（右列已移除）。
 * @param visible 动效目标可见性；仅 [animateReveal] = true 时生效。
 * @param onExitFinished 退出动效结束后的通知（宿主据此移除覆盖层）。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun GtdSelector(
    currentPosition: GtdPosition,
    tasks: List<Task> = emptyList(),
    onSelect: (GtdPosition) -> Unit,
    onClose: () -> Unit,
    animateReveal: Boolean = false,
    visible: Boolean = true,
    onExitFinished: () -> Unit = {},
) {
    val density = LocalDensity.current
    val uiScope = rememberCoroutineScope()

    val textMeasurer = rememberTextMeasurer()
    val rowTextStyle = MaterialTheme.typography.labelLarge
    val drawerWidth = remember(textMeasurer, density, density.fontScale, rowTextStyle) {
        // 无界宽测量五项标题，取最大文本宽；键含密度与字体缩放，字号变化后重算（R5-1、CMP-10）。
        val unbounded = Constraints()
        val widestTextPx = GtdPosition.entries.maxOf { position ->
            textMeasurer.measure(
                text = AnnotatedString(position.title),
                style = rowTextStyle,
                constraints = unbounded,
            ).size.width
        }
        val contentWidth = with(density) { widestTextPx.toDp() } + NautileSpacing.space6 * 2
        contentWidth.coerceIn(240.dp, 320.dp)
    }
    val drawerWidthPx = with(density) { drawerWidth.toPx() }
    val densityValue = density.density

    // 开合动效：位移用 Expressive，透明度用 StandardEffects（MOT-07、ANIM-05）。
    val revealMotion = remember { Animatable(if (visible) 1f else 0f) }
    val effectsMotion = remember { Animatable(if (visible) 1f else 0f) }
    // 左滑跟手位移与未达阈值回弹（ANIM-06）：拖动期用同步值 1:1 跟手，松手回弹交 Animatable。
    val dragRebound = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var liveDragPx by remember { mutableFloatStateOf(0f) }
    var pendingCloseDragPx by remember { mutableFloatStateOf(0f) }
    var reboundJob by remember { mutableStateOf<Job?>(null) }

    var firstComposition by remember { mutableStateOf(true) }
    var rendered by remember { mutableStateOf(!animateReveal || visible) }
    val latestOnClose by rememberUpdatedState(onClose)
    val latestOnExitFinished by rememberUpdatedState(onExitFinished)

    LaunchedEffect(animateReveal, visible) {
        if (!animateReveal) {
            // 未启用动效的直接组合：保持即时行为，也不按 visible 隐藏。
            reboundJob?.cancel()
            reboundJob = null
            isDragging = false
            liveDragPx = 0f
            pendingCloseDragPx = 0f
            dragRebound.snapTo(0f)
            revealMotion.snapTo(1f)
            effectsMotion.snapTo(1f)
            rendered = true
            return@LaunchedEffect
        }
        if (firstComposition) {
            // 首帧直接终态，不播放“从无到有”的开场（MOT-07.2、MOT-14）。
            firstComposition = false
            return@LaunchedEffect
        }
        val target = if (visible) 1f else 0f
        reboundJob?.cancel()
        reboundJob = null
        isDragging = false
        liveDragPx = 0f
        if (target == 1f) {
            pendingCloseDragPx = 0f
            dragRebound.snapTo(0f)
            rendered = true
        } else {
            // 若由左滑达阈值触发关闭，将当前左滑进度无缝并入开合动画，避免回弹闪跳（ANIM-06）。
            val carryDrag = pendingCloseDragPx
            pendingCloseDragPx = 0f
            if (carryDrag < 0f && drawerWidthPx > 0f) {
                val progress = (1f + carryDrag / drawerWidthPx).coerceIn(0f, 1f)
                revealMotion.snapTo(progress)
                effectsMotion.snapTo(progress)
            }
            dragRebound.snapTo(0f)
        }
        // 从当前值与当前速度重定向；中途反向不回到起点（MOT-07.6、MOT-10）。
        coroutineScope {
            launch { revealMotion.animateTo(target, NautileMotion.Expressive) }
            launch { effectsMotion.animateTo(target, NautileMotion.StandardEffects) }
        }
        if (target == 0f) {
            rendered = false
            latestOnExitFinished()
        }
    }

    // 退出动效完成后不再绘制；composable 仍保持组合，以便再次打开时沿用同一动效状态。
    if (!rendered) return

    val selectedContainer = MaterialTheme.colorScheme.secondaryContainer
    val selectedContent = MaterialTheme.colorScheme.onSecondaryContainer
    val unselectedContent = MaterialTheme.colorScheme.onSurface
    // 抽屉内容单独消费状态栏上、导航栏下 inset 一次（CMP-10/13、SPC-09 同一原则）。
    val drawerInsets = WindowInsets.systemBars.only(
        WindowInsetsSides.Top + WindowInsetsSides.Bottom,
    )

    val activeDragPx = when {
        isDragging -> liveDragPx
        pendingCloseDragPx < 0f -> pendingCloseDragPx
        else -> dragRebound.value
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 右侧整屏遮罩：点按关闭，并消费全部指针事件以隔离底层点击、滚动与上下拉（CMP-12/13）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("gtd_scrim")
                .graphicsLayer {
                    val dragFactor = if (drawerWidthPx > 0f) {
                        (1f + activeDragPx / drawerWidthPx).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                    alpha = (effectsMotion.value * dragFactor).coerceIn(0f, 1f)
                }
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = NautileStates.SCRIM_ALPHA))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var movedBeyondSlop = false
                        val touchSlop = viewConfiguration.touchSlop
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if ((change.position - down.position).getDistance() > touchSlop) {
                                movedBeyondSlop = true
                            }
                            if (!change.pressed) {
                                if (!movedBeyondSlop) {
                                    latestOnClose()
                                }
                                break
                            }
                        }
                    }
                },
        )
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(drawerWidth)
                .fillMaxHeight()
                .testTag("gtd_drawer")
                .graphicsLayer {
                    // 抽屉从 -W_D 滑入并叠加左滑跟手位移；宽度本身不做动画（MOT-07.2、MOT-12、ANIM-06）。
                    val baseTranslationX = -(1f - revealMotion.value) * drawerWidthPx
                    translationX = (baseTranslationX + activeDragPx).coerceIn(-drawerWidthPx, 0f)
                }
                // 在抽屉上左滑关闭：1:1 跟手、遮罩联动，未达阈值经 BoundaryRebound 回弹（ANIM-06）。
                .pointerInput(densityValue, drawerWidthPx, animateReveal) {
                    val closeThresholdPx = GestureArbiter.GTD_OPEN_SWIPE_DP * densityValue
                    detectHorizontalDragGestures(
                        onDragStart = {
                            reboundJob?.cancel()
                            reboundJob = null
                            pendingCloseDragPx = 0f
                            liveDragPx = dragRebound.value.coerceIn(-drawerWidthPx, 0f)
                            isDragging = true
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (dragAmount < 0f || liveDragPx < 0f) {
                                liveDragPx = (liveDragPx + dragAmount).coerceIn(-drawerWidthPx, 0f)
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            val finalDragPx = liveDragPx
                            isDragging = false
                            if (finalDragPx <= -closeThresholdPx) {
                                if (animateReveal) {
                                    pendingCloseDragPx = finalDragPx
                                } else {
                                    liveDragPx = 0f
                                    reboundJob = uiScope.launch { dragRebound.snapTo(0f) }
                                }
                                latestOnClose()
                            } else if (finalDragPx < 0f) {
                                liveDragPx = 0f
                                reboundJob = uiScope.launch {
                                    dragRebound.snapTo(finalDragPx)
                                    dragRebound.animateTo(0f, NautileMotion.BoundaryRebound)
                                }
                            } else {
                                liveDragPx = 0f
                            }
                        },
                        onDragCancel = {
                            val finalDragPx = liveDragPx
                            isDragging = false
                            liveDragPx = 0f
                            if (finalDragPx < 0f) {
                                reboundJob = uiScope.launch {
                                    dragRebound.snapTo(finalDragPx)
                                    dragRebound.animateTo(0f, NautileMotion.BoundaryRebound)
                                }
                            }
                        },
                    )
                },
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(drawerInsets),
            ) {
                // 标题行：左“清单”（titleMedium）、右关闭图标，高 48dp（设计 §2.3、§4.2、CMP-13）。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = NautileSpacing.space12)
                        .padding(start = NautileSpacing.space6, end = NautileSpacing.space1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "清单",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    // 关闭图标 ic_close；触控 48dp 由组件保证（CMP-09、设计 §6.2 #11）。
                    NautileIconButton(
                        iconRes = R.drawable.ic_close,
                        contentDescription = "关闭",
                        onClick = onClose,
                    )
                }
                // 五项清单可普通滚动浏览，但滚动不切换；仅点按切换（CMP-12）。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = NautileSpacing.space2),
                    verticalArrangement = Arrangement.spacedBy(NautileSpacing.space1),
                ) {
                    GtdPosition.entries.forEach { position ->
                        GtdListRow(
                            position = position,
                            selected = position == currentPosition,
                            selectedContainer = selectedContainer,
                            selectedContent = selectedContent,
                            unselectedContent = unselectedContent,
                            textStyle = rowTextStyle,
                            onClick = { onSelect(position) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 抽屉内单个清单行：水平 24dp、上下 12dp、最小高 48dp、项间 4dp；
 * 当前项 `secondaryContainer` 选中态（CMP-10/13）。
 */
@Composable
private fun GtdListRow(
    position: GtdPosition,
    selected: Boolean,
    selectedContainer: Color,
    selectedContent: Color,
    unselectedContent: Color,
    textStyle: TextStyle,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) selectedContainer else Color.Transparent,
        contentColor = if (selected) selectedContent else unselectedContent,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gtd_item_${position.name}")
            .semantics {
                this.selected = selected
                if (selected) {
                    stateDescription = "当前清单"
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = NautileSpacing.space12)
                .padding(
                    horizontal = NautileSpacing.space6,
                    vertical = NautileSpacing.space3,
                ),
        ) {
            Text(text = position.title, style = textStyle)
        }
    }
}
