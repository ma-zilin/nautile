package io.github.mazilin.nautile.ui.screen.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.interaction.GestureArbiter

/**
 * GTD 半屏选择器（S1-T07、AC-G-04/05）。
 *
 * 左列五项、右侧当前清单；只有点按左列才切换；切换后保持半屏。
 * 右侧清单普通滚动优先，到顶/底后继续拉才预览边界切换，松手过阈值提交、否则回弹；首尾不循环。
 * 关闭：关闭按钮、空白左滑、系统返回（由 BackDispatcher 关闭覆盖层）。
 */
@Composable
fun GtdSelector(
    currentPosition: GtdPosition,
    tasks: List<Task>,
    onSelect: (GtdPosition) -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    val densityValue = density.density
    val maxPullPx = with(density) { 120.dp.toPx() }
    val previous = GestureArbiter.adjacentPosition(currentPosition, -1)
    val next = GestureArbiter.adjacentPosition(currentPosition, 1)

    val rightListState = rememberLazyListState()
    var pullOffset by remember { mutableFloatStateOf(0f) }
    var pullTarget by remember { mutableStateOf<GtdPosition?>(null) }
    var pullActive by remember { mutableStateOf(false) }

    LaunchedEffect(currentPosition) {
        rightListState.scrollToItem(0)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
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
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(0.38f)
                            .fillMaxHeight()
                            .padding(8.dp),
                    ) {
                        Text("清单", style = MaterialTheme.typography.labelLarge)
                        GtdPosition.entries.forEach { position ->
                            Surface(
                                onClick = { onSelect(position) },
                                color = if (position == currentPosition) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                            ) {
                                Text(
                                    text = position.title,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(0.62f)
                            .fillMaxHeight()
                            .nestedScroll(connection),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationY = pullOffset }
                                .padding(8.dp),
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
                                    modifier = Modifier.padding(top = 12.dp),
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
                    }
                }
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text("关闭")
                }
            }
        }
    }
}
