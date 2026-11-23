package io.github.mazilin.nautile.ui.screen.todo

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
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
    onSwitchPosition: (GtdPosition) -> Unit = {},
    onOpenTaskDetail: (TaskId) -> Unit = {},
    onOpenProjectDetail: (ProjectId) -> Unit = {},
    onConfirmProjectReopen: () -> Unit = {},
    onCancelProjectReopen: () -> Unit = {},
    onOpenNewTask: () -> Unit = {},
    newTaskOpen: Boolean = false,
    navigationUpdatesEnabled: Boolean = true,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current.density
    var savedFirstIndex by remember { mutableIntStateOf(0) }
    var savedFirstOffset by remember { mutableIntStateOf(0) }
    var wasNewTaskOpen by remember { mutableStateOf(false) }

    // 打开新建面板前记录滚动位置，关闭后恢复；列表本身不因面板开合重建。
    LaunchedEffect(newTaskOpen) {
        if (newTaskOpen) {
            savedFirstIndex = listState.firstVisibleItemIndex
            savedFirstOffset = listState.firstVisibleItemScrollOffset
        } else if (wasNewTaskOpen) {
            listState.scrollToItem(savedFirstIndex, savedFirstOffset)
        }
        wasNewTaskOpen = newTaskOpen
    }
    val maxPullPx = with(LocalDensity.current) { 120.dp.toPx() }
    var gtdSwipe by remember { mutableFloatStateOf(0f) }
    var blockedByRow by remember { mutableStateOf(false) }
    var rowDragging by remember { mutableStateOf(false) }

    val previousPosition = GestureArbiter.adjacentPosition(state.position, -1)
    val nextPosition = GestureArbiter.adjacentPosition(state.position, 1)
    var pullOffset by remember { mutableFloatStateOf(0f) }
    var pullTarget by remember { mutableStateOf<GtdPosition?>(null) }
    var pullActive by remember { mutableStateOf(false) }

    LaunchedEffect(state.position) {
        listState.scrollToItem(0)
        pullOffset = 0f
        pullTarget = null
        pullActive = false
    }

    // 导航隐藏：累计位移阈值 24dp＋16dp 滞后；仅普通纵向滚动触发（AC-N-15、T-11）。
    var navigationVisible by remember { mutableStateOf(true) }
    var navigationAccumulator by remember { mutableFloatStateOf(0f) }
    val hideThresholdPx = with(LocalDensity.current) { 24.dp.toPx() }
    val showThresholdPx = with(LocalDensity.current) { 16.dp.toPx() }

    // 全屏边界拉动（FR-G-18、BR-61）：普通滚动优先，到顶/底后继续拉才预览；
    // 松手过阈值切换相邻清单并保持全屏，否则回弹；首尾不循环。
    val boundaryConnection = remember(
        state.position,
        maxPullPx,
        density,
        navigationUpdatesEnabled,
        hideThresholdPx,
        showThresholdPx,
    ) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (navigationUpdatesEnabled && consumed.y != 0f && !pullActive) {
                    if (navigationVisible) {
                        // 上滑（consumed.y 为负）累计绝对值，超过 24dp 隐藏。
                        if (consumed.y < 0f) {
                            navigationAccumulator += -consumed.y
                            if (navigationAccumulator > hideThresholdPx) {
                                navigationVisible = false
                                navigationAccumulator = 0f
                                onNavigationVisibilityChanged(false)
                            }
                        } else {
                            // 方向反转：重置累计，避免抖动。
                            navigationAccumulator = 0f
                        }
                    } else {
                        // 隐藏后下滑超过 16dp 显示；上滑反向则重置。
                        if (consumed.y > 0f) {
                            navigationAccumulator += consumed.y
                            if (navigationAccumulator > showThresholdPx) {
                                navigationVisible = true
                                navigationAccumulator = 0f
                                onNavigationVisibilityChanged(true)
                            }
                        } else {
                            navigationAccumulator = 0f
                        }
                    }
                }
                if (available.y == 0f) return Offset.Zero
                val atTop = !listState.canScrollBackward
                val atBottom = !listState.canScrollForward
                val target = when {
                    available.y > 0f && atTop -> previousPosition
                    available.y < 0f && atBottom -> nextPosition
                    else -> return Offset.Zero
                }
                pullActive = true
                pullTarget = target
                val resistance = if (target == null) 0.15f else 0.5f
                pullOffset = (pullOffset + available.y * resistance).coerceIn(-maxPullPx, maxPullPx)
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (pullActive) {
                    val target = pullTarget
                    if (target != null && GestureArbiter.isBoundaryPullCommit(pullOffset, density)) {
                        onSwitchPosition(target)
                    }
                    pullOffset = 0f
                    pullTarget = null
                    pullActive = false
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(navigationUpdatesEnabled, state.position) {
        navigationAccumulator = 0f
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
                // 独立 GTD 入口；标题文字不再兼任按钮（T-02、AC-G-04）。
                IconButton(
                    onClick = onOpenSelector,
                    modifier = Modifier
                        .testTag("gtd_entry")
                        .semantics { contentDescription = "GTD 清单" },
                ) {
                    Text("☰", style = MaterialTheme.typography.titleLarge)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("待办", style = MaterialTheme.typography.titleLarge)
                    Text(state.position.title, style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onOpenSettings) {
                    Text("设置")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(boundaryConnection),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = pullOffset },
                ) {
                    if (state.isEmpty) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("该清单暂无任务", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    state.groups.forEach { group ->
                        val project = group.project
                        if (project != null) {
                            item(key = "project_${project.id.value}") {
                                SwipeableRow(
                                    onSwipeLeft = { onRequestProjectClose(project.id, ClosedOutcome.COMPLETED) },
                                    onSwipeRight = { onRequestProjectClose(project.id, ClosedOutcome.ABANDONED) },
                                    onRowDragStart = { rowDragging = true },
                                    onRowDragEnd = { rowDragging = false },
                                    onClick = { onOpenProjectDetail(project.id) },
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
                                onClick = { onOpenTaskDetail(task.id) },
                                // 已结项行禁用滑动，只保留查看详情（T-10、AC-T-29）。
                                swipeEnabled = task.closedOutcome == null,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (task.closedOutcome != null) {
                                        Text(
                                            text = "已结项",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                pullTarget?.let { target ->
                    Text(
                        text = "松手切换到 ${target.title}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .align(
                                if (pullOffset > 0f) Alignment.TopCenter else Alignment.BottomCenter,
                            )
                            .padding(8.dp),
                    )
                }
            }
        }

        // 右侧、导航上方的添加按钮（FR-D-02）；面板打开时由覆盖层遮挡。
        FloatingActionButton(
            onClick = onOpenNewTask,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_task"),
        ) {
            Text("+")
        }
    }

    state.projectConfirmation?.let { pending ->
        val action = if (pending.outcome == ClosedOutcome.COMPLETED) "完成" else "放弃"
        AlertDialog(
            onDismissRequest = onCancelProjectClose,
            title = { Text(pending.projectName) },
            text = { Text("将$action ${pending.taskCount} 个子待办") },
            confirmButton = {
                TextButton(onClick = onConfirmProjectClose) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = onCancelProjectClose) { Text("取消") }
            },
        )
    }

    state.projectReopenPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = onCancelProjectReopen,
            title = { Text(prompt.projectName) },
            text = { Text("项目「${prompt.projectName}」已结项，是否重新打开项目？") },
            confirmButton = {
                TextButton(onClick = onConfirmProjectReopen) { Text("重新打开") }
            },
            dismissButton = {
                TextButton(onClick = onCancelProjectReopen) { Text("取消") }
            },
        )
    }
}

/**
 * 条目左右滑：左滑完成、右滑放弃；仅越过阈值才提交，否则回原位（BR-46、AC-T-17）。
 * [swipeEnabled] 为 false 时（已结项）不挂拖拽，只保留点按。
 */
@Composable
private fun SwipeableRow(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onRowDragStart: () -> Unit,
    onRowDragEnd: () -> Unit,
    onClick: () -> Unit = {},
    swipeEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    var offset by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (swipeEnabled) {
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
        }
        val dragModifier = if (swipeEnabled) {
            Modifier.draggable(
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
            )
        } else {
            Modifier
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offset }
                .then(dragModifier)
                .clickable(onClick = onClick),
        ) {
            content()
        }
    }
}
