package io.github.mazilin.nautile.ui.screen.todo

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.interaction.GestureArbiter

/**
 * 待办页 P-02（S1-T07）。
 *
 * 标题按钮/空白右滑打开 GTD 选择器；条目左滑完成、右滑放弃；项目行滑动先请求数量确认。
 * 这里不实现分组/排序菜单、详情、新建面板（D-10、T08、T09）。
 */
@Composable
fun TodoScreen(
    state: TodoUiState = TodoUiState(),
    onOpenSettings: () -> Unit = {},
    onOpenSelector: () -> Unit = {},
    onCompleteTask: (TaskId) -> Unit = {},
    onAbandonTask: (TaskId) -> Unit = {},
    onRequestProjectClose: (ProjectId, ClosedOutcome) -> Unit = { _, _ -> },
    onConfirmProjectClose: () -> Unit = {},
    onCancelProjectClose: () -> Unit = {},
    onNavigationVisibilityChanged: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current.density
    var gtdSwipe by remember { mutableFloatStateOf(0f) }
    var blockedByRow by remember { mutableStateOf(false) }
    var rowDragging by remember { mutableStateOf(false) }

    // 导航隐藏：仅普通页面纵向滚动触发（AC-N-15）；覆盖层/键盘由 AppRoot/返回层处理。
    LaunchedEffect(listState) {
        var previous = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { current ->
                when {
                    current.first == 0 && current.second == 0 -> onNavigationVisibilityChanged(true)
                    current.first > previous.first ||
                        (current.first == previous.first && current.second > previous.second) ->
                        onNavigationVisibilityChanged(false)
                    else -> onNavigationVisibilityChanged(true)
                }
                previous = current
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(density) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        gtdSwipe = 0f
                        blockedByRow = rowDragging
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (blockedByRow || change.isConsumed) return@detectHorizontalDragGestures
                        gtdSwipe += dragAmount
                        if (gtdSwipe > 0f) change.consume()
                    },
                    onDragEnd = {
                        if (!blockedByRow && GestureArbiter.isGtdOpenSwipe(gtdSwipe, density)) {
                            onOpenSelector()
                        }
                        gtdSwipe = 0f
                        blockedByRow = false
                    },
                    onDragCancel = {
                        gtdSwipe = 0f
                        blockedByRow = false
                    },
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenSelector) {
                    Text(state.position.title, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenSettings) {
                    Text("设置")
                }
            }

            if (state.isEmpty) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("该清单暂无任务", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    state.groups.forEach { group ->
                        val project = group.project
                        if (project != null) {
                            item(key = "project_${project.id.value}") {
                                SwipeableRow(
                                    onSwipeLeft = { onRequestProjectClose(project.id, ClosedOutcome.COMPLETED) },
                                    onSwipeRight = { onRequestProjectClose(project.id, ClosedOutcome.ABANDONED) },
                                    onRowDragStart = { rowDragging = true },
                                    onRowDragEnd = { rowDragging = false },
                                ) {
                                    Text(
                                        text = "${project.name}（${group.tasks.size}）",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    )
                                }
                            }
                        } else {
                            item(key = "ungrouped_header") {
                                Text(
                                    text = "单独任务",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                        items(group.tasks, key = { "task_${it.id.value}" }) { task ->
                            SwipeableRow(
                                onSwipeLeft = { onCompleteTask(task.id) },
                                onSwipeRight = { onAbandonTask(task.id) },
                                onRowDragStart = { rowDragging = true },
                                onRowDragEnd = { rowDragging = false },
                            ) {
                                TaskTitle(task)
                            }
                        }
                    }
                }
            }
        }
    }

    state.projectConfirmation?.let { pending ->
        val action = if (pending.outcome == ClosedOutcome.COMPLETED) "完成" else "放弃"
        AlertDialog(
            onDismissRequest = onCancelProjectClose,
            title = { Text(pending.projectName) },
            text = { Text("将$action${pending.taskCount} 个子待办") },
            confirmButton = {
                TextButton(onClick = onConfirmProjectClose) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = onCancelProjectClose) { Text("取消") }
            },
        )
    }
}

@Composable
private fun TaskTitle(task: Task) {
    Text(
        text = task.title,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

/**
 * 条目左右滑：左滑完成、右滑放弃；仅越过阈值才提交，否则回原位（BR-46、AC-T-17）。
 */
@Composable
private fun SwipeableRow(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onRowDragStart: () -> Unit,
    onRowDragEnd: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    var offset by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("放弃", style = MaterialTheme.typography.labelMedium)
            Text("完成", style = MaterialTheme.typography.labelMedium)
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offset }
                .draggable(
                    state = rememberDraggableState { delta ->
                        offset += delta
                        if (offset != 0f) onRowDragStart()
                    },
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        onRowDragEnd()
                        when {
                            GestureArbiter.isCommitSwipe(offset, density) && offset < 0f -> onSwipeLeft()
                            GestureArbiter.isCommitSwipe(offset, density) && offset > 0f -> onSwipeRight()
                        }
                        offset = 0f
                    },
                ),
        ) {
            content()
        }
    }
}
