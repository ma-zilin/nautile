package io.github.mazilin.nautile.ui.screen.todo

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.interaction.GestureArbiter
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.theme.NautileMotion
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * GTD 全屏左右分屏选择器（S1-T07、G1-A2、G3、AC-G-04/05）。
 *
 * 覆盖全屏、无遮罩；左列五项、右侧当前清单；只有点按左列才切换；切换后仍全屏。
 * 左列底 `surfaceContainerLow`、右列底 `background`，两区边界相接（设计 §1.2）。
 * 左列宽度按 CMP-10 测量：`W_L = max(五项标题文本宽) + 2 × 12dp`，右侧占余下宽度。
 * 右侧清单普通滚动优先，到顶/底后继续拉才预览边界切换，松手过阈值提交、否则回弹；首尾不循环。
 * 关闭：关闭按钮、空白左滑、系统返回（由 BackDispatcher 关闭覆盖层）。
 *
 * 开合动效（MOT-07/MOT-10）由 [animateReveal] 开启：左列 `translationX = -W_L → 0`、
 * 右列 `0 → W_L` 用同一 progress 联动，位移用 [NautileMotion.Expressive]，左列 alpha 用
 * [NautileMotion.StandardEffects]；动画目标变化时从当前值与速度重定向，中途反向不回到起点。
 * 初次组合直接终态，不播放入场（MOT-14）。
 * 默认 [animateReveal] = false，直接组合时保持原有的即时布局与手势行为。
 *
 * @param visible 动效目标可见性；仅 [animateReveal] = true 时生效。
 * @param onExitFinished 退出动效结束后的通知（宿主据此移除覆盖层）。
 */
@Composable
fun GtdSelector(
    currentPosition: GtdPosition,
    tasks: List<Task>,
    onSelect: (GtdPosition) -> Unit,
    onClose: () -> Unit,
    animateReveal: Boolean = false,
    visible: Boolean = true,
    onExitFinished: () -> Unit = {},
) {
    val density = LocalDensity.current
    val densityValue = density.density
    val maxPullPx = with(density) { 120.dp.toPx() }
    val previous = GestureArbiter.adjacentPosition(currentPosition, -1)
    val next = GestureArbiter.adjacentPosition(currentPosition, 1)

    // 开合动效：位移用 Expressive，透明度用 StandardEffects（MOT-07）。
    val revealMotion = remember { Animatable(if (visible) 1f else 0f) }
    val effectsMotion = remember { Animatable(if (visible) 1f else 0f) }
    var firstComposition by remember { mutableStateOf(true) }
    var rendered by remember { mutableStateOf(!animateReveal || visible) }
    val latestOnExitFinished by rememberUpdatedState(onExitFinished)

    LaunchedEffect(animateReveal, visible) {
        if (!animateReveal) {
            // 未启用动效的直接组合：保持原即时行为，也不按 visible 隐藏。
            revealMotion.snapTo(1f)
            effectsMotion.snapTo(1f)
            rendered = true
            return@LaunchedEffect
        }
        if (firstComposition) {
            // 首帧直接终态，不播放“从无到有”的开场（MOT-07.5、MOT-14）。
            firstComposition = false
            return@LaunchedEffect
        }
        val target = if (visible) 1f else 0f
        if (target == 1f) rendered = true
        // 从当前值与当前速度重定向；中途反向不回到起点（MOT-10）。
        coroutineScope {
            launch { revealMotion.animateTo(target, NautileMotion.Expressive) }
            launch { effectsMotion.animateTo(target, NautileMotion.StandardEffects) }
        }
        if (target == 0f) {
            rendered = false
            latestOnExitFinished()
        }
    }

    val rightListState = rememberLazyListState()
    var pullOffset by remember { mutableFloatStateOf(0f) }
    var pullTarget by remember { mutableStateOf<GtdPosition?>(null) }
    var pullActive by remember { mutableStateOf(false) }

    // 切换清单只换右侧内容（StandardEffects 淡入），不改动面板形态与左宽（MOT-07.3、MOT-12）。
    val switchFade = remember { Animatable(1f) }
    var switchedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(currentPosition) {
        rightListState.scrollToItem(0)
        pullOffset = 0f
        pullTarget = null
        pullActive = false
        if (animateReveal) {
            if (switchedOnce) {
                switchFade.snapTo(0f)
                switchFade.animateTo(1f, NautileMotion.StandardEffects)
            } else {
                switchedOnce = true
            }
        }
    }

    fun resetPull() {
        pullOffset = 0f
        pullTarget = null
        pullActive = false
    }

    /** 空/短清单没有可滚动源，由指针层直接累积预览偏移。 */
    fun accumulatePull(dragY: Float) {
        val target = if (dragY > 0f) previous else next
        pullActive = true
        pullTarget = target
        // 首尾无相邻清单时阻力更大且不提交（AC-G-05 首尾阻力）。
        val resistance = if (target == null) 0.15f else 0.5f
        pullOffset = (pullOffset + dragY * resistance).coerceIn(-maxPullPx, maxPullPx)
    }

    fun commitPull() {
        val target = pullTarget
        if (target != null && GestureArbiter.isBoundaryPullCommit(pullOffset, densityValue)) {
            onSelect(target)
        }
        resetPull()
    }

    val connection = remember(currentPosition, maxPullPx, densityValue) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                val atTop = !rightListState.canScrollBackward
                val atBottom = !rightListState.canScrollForward
                val target = when {
                    available.y > 0f && atTop -> previous
                    available.y < 0f && atBottom -> next
                    else -> return Offset.Zero
                }
                pullActive = true
                pullTarget = target
                // 首尾无相邻清单时阻力更大且不提交（AC-G-05 首尾阻力）。
                val resistance = if (target == null) 0.15f else 0.5f
                pullOffset = (pullOffset + available.y * resistance).coerceIn(-maxPullPx, maxPullPx)
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (pullActive) {
                    val target = pullTarget
                    if (target != null && GestureArbiter.isBoundaryPullCommit(pullOffset, densityValue)) {
                        onSelect(target)
                    }
                    pullOffset = 0f
                    pullTarget = null
                    pullActive = false
                }
                return Velocity.Zero
            }
        }
    }

    // 退出动效完成后不再绘制；composable 仍保持组合，以便再次打开时沿用同一动效状态。
    if (!rendered) return

    val textMeasurer = rememberTextMeasurer()
    val rowTextStyle = MaterialTheme.typography.bodyLarge
    val leftColumnWidth = remember(textMeasurer, density, density.fontScale, rowTextStyle) {
        // 无界宽测量五项标题，取最大文本宽；键含密度与字体缩放，字号变化后重算（CMP-10）。
        val unbounded = Constraints()
        val widestTextPx = GtdPosition.entries.maxOf { position ->
            textMeasurer.measure(
                text = AnnotatedString(position.title),
                style = rowTextStyle,
                constraints = unbounded,
            ).size.width
        }
        with(density) { widestTextPx.toDp() } + NautileSpacing.space3 * 2
    }
    val leftColumnWidthPx = with(density) { leftColumnWidth.toPx() }

    val selectedContainer = MaterialTheme.colorScheme.secondaryContainer
    val selectedContent = MaterialTheme.colorScheme.onSecondaryContainer
    val unselectedContent = MaterialTheme.colorScheme.onSurface

    // 全屏覆盖：不设遮罩、不做 60% 高度与底部圆角（SHP-08、CMP-13）。
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 宿主不再加 inset：状态栏上、导航栏下各由选择器消费一次（CMP-10、SPC-05）。
                .windowInsetsPadding(WindowInsets.systemBars)
                .pointerInput(densityValue) {
                    var leftDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { leftDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            if (dragAmount < 0f) {
                                leftDrag += dragAmount
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            if (leftDrag < -GestureArbiter.GTD_OPEN_SWIPE_DP * densityValue) onClose()
                            leftDrag = 0f
                        },
                        onDragCancel = { leftDrag = 0f },
                    )
                },
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .width(leftColumnWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            // 左列从 -W_L 滑入；宽度本身不做动画（MOT-07、MOT-12）。
                            translationX = -(1f - revealMotion.value) * leftColumnWidthPx
                            alpha = effectsMotion.value
                        },
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            // 左列自身水平 padding 为 0，水平 12dp 已并入 W_L（CMP-10）。
                            .padding(vertical = NautileSpacing.space2),
                    ) {
                        Text(
                            text = "清单",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(
                                horizontal = NautileSpacing.space3,
                                vertical = NautileSpacing.space1,
                            ),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(NautileSpacing.space1)) {
                            GtdPosition.entries.forEach { position ->
                                val selected = position == currentPosition
                                Surface(
                                    onClick = { onSelect(position) },
                                    shape = CircleShape,
                                    color = if (selected) selectedContainer else Color.Transparent,
                                    contentColor = if (selected) selectedContent else unselectedContent,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = NautileSpacing.space12)
                                            .padding(
                                                horizontal = NautileSpacing.space3,
                                                vertical = NautileSpacing.space3,
                                            ),
                                    ) {
                                        Text(text = position.title, style = rowTextStyle)
                                    }
                                }
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // 右列底用页面 background，与左列 surfaceContainerLow 区分（设计 §1.2）。
                        .background(MaterialTheme.colorScheme.background)
                        .graphicsLayer {
                            // 右列与左列同一 progress 联动，从右侧滑入（MOT-07.2）。
                            translationX = (1f - revealMotion.value) * leftColumnWidthPx
                        }
                        // 拉动预览不得绘制到列表区外（MOT-15）。
                        .clipToBounds()
                        .nestedScroll(connection),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationY = pullOffset
                                alpha = switchFade.value
                            }
                            .padding(NautileSpacing.space4),
                    ) {
                        Text(currentPosition.title, style = MaterialTheme.typography.titleMedium)
                        pullTarget?.let { target ->
                            Text(
                                text = "松手切换到 ${target.title}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        if (tasks.isEmpty()) {
                            Text(
                                text = "该清单暂无任务",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = NautileSpacing.space3),
                            )
                        } else {
                            LazyColumn(
                                state = rightListState,
                                modifier = Modifier.weight(1f),
                            ) {
                                items(tasks, key = { it.id.value }) { task ->
                                    Text(
                                        text = task.title,
                                        modifier = Modifier.padding(vertical = 10.dp),
                                    )
                                }
                            }
                        }
                    }
                    // 空/短清单没有任何可滚动余量，嵌套滚动拿不到事件；
                    // 用仅在该状态下启用的纵向指针层完成边界拉动预览与提交。
                    if (!rightListState.canScrollBackward && !rightListState.canScrollForward) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(previous, next, densityValue) {
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            accumulatePull(dragAmount)
                                        },
                                        onDragEnd = { commitPull() },
                                        onDragCancel = { resetPull() },
                                    )
                                },
                        )
                    }
                }
            }
            // 动作图标 ic_close（设计 §6.2 #11）；触控 48dp 由组件保证（CMP-09）。
            NautileIconButton(
                iconRes = R.drawable.ic_close,
                contentDescription = "关闭",
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}
