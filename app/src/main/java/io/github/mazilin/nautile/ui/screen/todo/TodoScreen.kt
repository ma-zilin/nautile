package io.github.mazilin.nautile.ui.screen.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.rule.TaskGroup
import io.github.mazilin.nautile.interaction.GestureArbiter
import io.github.mazilin.nautile.ui.designsystem.NautileEmptyState
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import kotlin.math.abs

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
    onOpenSearch: () -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    newTaskOpen: Boolean = false,
    navigationUpdatesEnabled: Boolean = true,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current.density
    var savedFirstIndex by remember { mutableIntStateOf(0) }
    var savedFirstOffset by remember { mutableIntStateOf(0) }
    var wasNewTaskOpen by remember { mutableStateOf(false) }

    // 搜索前记录滚动位置，退出搜索后恢复（CMP-38：退出恢复搜索前的清单与滚动位置）。
    var savedSearchIndex by remember { mutableIntStateOf(0) }
    var savedSearchOffset by remember { mutableIntStateOf(0) }
    var wasSearchActive by remember { mutableStateOf(false) }

    LaunchedEffect(state.searchActive) {
        if (state.searchActive) {
            savedSearchIndex = listState.firstVisibleItemIndex
            savedSearchOffset = listState.firstVisibleItemScrollOffset
        } else if (wasSearchActive) {
            listState.scrollToItem(savedSearchIndex, savedSearchOffset)
        }
        wasSearchActive = state.searchActive
    }

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
            }
            // T-11 第三轮：下滑显示不依赖列表消费（从标题/顶部起手时列表拿不到滚动事件）。
            // 仅“导航已隐藏且向下拖超过 16dp”时补发显示；不消费事件、不干扰滚动/边界拉动。
            .pointerInput(showThresholdPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    var totalDown = 0f
                    var started = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!navigationVisible) {
                            val dy = change.positionChange().y
                            if (!started) {
                                if (abs(dy) < viewConfiguration.touchSlop) {
                                    if (!change.pressed) break
                                    continue
                                }
                                started = true
                            }
                            if (dy > 0f) totalDown += dy
                        }
                        if (!change.pressed) {
                            if (!navigationVisible && !pullActive && totalDown > showThresholdPx) {
                                navigationVisible = true
                                navigationAccumulator = 0f
                                onNavigationVisibilityChanged(true)
                            }
                            totalDown = 0f
                            break
                        }
                    }
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏等价结构（设计 §7.1、§4.2）：GTD → 标题区 → 搜索 → 视图 → 设置。
            // 在页面内组装以保留 gtd_entry / settings_entry 的 testTag（NautileTopBar 不支持传入）。
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 独立 GTD 入口；标题文字不再兼任按钮（T-02、AC-G-04）。
                        NautileIconButton(
                            iconRes = R.drawable.ic_menu,
                            contentDescription = "打开 GTD 清单选择器",
                            onClick = onOpenSelector,
                            testTag = "gtd_entry",
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "待办",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                text = state.position.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // 搜索入口（CMP-38）：就绪后可点；视图仍未就绪，保持可见禁用（RDY-02/04）。
                        NautileIconButton(
                            iconRes = R.drawable.ic_search,
                            contentDescription = unavailableDescription("搜索待办", state.searchAvailable),
                            onClick = onOpenSearch,
                            enabled = state.searchAvailable,
                        )
                        NautileIconButton(
                            iconRes = R.drawable.ic_tune,
                            contentDescription = unavailableDescription("视图选项", state.viewOptionsAvailable),
                            onClick = {},
                            enabled = state.viewOptionsAvailable,
                        )
                        NautileIconButton(
                            iconRes = R.drawable.ic_settings,
                            contentDescription = "设置",
                            onClick = onOpenSettings,
                            testTag = "settings_entry",
                        )
                    }
                    // 搜索栏插在标题行下方，标题与三图标保持可见（CMP-38/43）。
                    if (state.searchActive) {
                        SearchBar(
                            query = state.searchQuery,
                            onQueryChanged = onSearchQueryChanged,
                            onClear = onClearSearch,
                            onClose = onCloseSearch,
                        )
                    }
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }

            // 页面背景只用 background 角色；分组卡片用 surfaceContainerLowest 分层（SHP-07）。
            val pageBackground = MaterialTheme.colorScheme.background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pageBackground)
                    .nestedScroll(boundaryConnection),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        // MOT-15：平移内容裁剪在不透明列表层内，不绘制到预览区外。
                        .clipToBounds()
                        .background(pageBackground)
                        .graphicsLayer { translationY = pullOffset },
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.groups.isEmpty()) {
                        // 搜索无命中与清单本身为空使用不同空态（CMP-38）。
                        val noSearchHit = state.searchActive && state.searchQuery.isNotBlank()
                        item(key = if (noSearchHit) "search_empty" else "empty") {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (noSearchHit) {
                                    NautileEmptyState(
                                        title = "无搜索结果",
                                        description = "试试其他任务标题或项目名称",
                                        iconRes = R.drawable.ic_search,
                                    )
                                } else {
                                    // 空清单空态（设计 §7 CMP-20、§4.2）：保留可见标题文案供既有断言。
                                    NautileEmptyState(
                                        title = "该清单暂无任务",
                                        description = null,
                                        iconRes = R.drawable.ic_inbox,
                                    )
                                }
                            }
                        }
                    }
                    state.groups.forEach { group ->
                        item(key = groupKey(group)) {
                            TaskGroupCard(
                                group = group,
                                onCompleteTask = onCompleteTask,
                                onAbandonTask = onAbandonTask,
                                onRequestProjectClose = onRequestProjectClose,
                                onOpenTaskDetail = onOpenTaskDetail,
                                onOpenProjectDetail = onOpenProjectDetail,
                                onRowDragStart = { rowDragging = true },
                                onRowDragEnd = { rowDragging = false },
                            )
                        }
                    }
                }

                // MOT-15：预览层不透明并裁剪；空间不足时隐藏文字，不溢出到列表交界。
                pullTarget?.let { target ->
                    val reveal = with(LocalDensity.current) { abs(pullOffset).toDp() }
                    if (reveal >= 32.dp) {
                        val atTop = pullOffset > 0f
                        Box(
                            modifier = Modifier
                                .align(
                                    if (atTop) Alignment.TopCenter else Alignment.BottomCenter,
                                )
                                .fillMaxWidth()
                                .height(reveal)
                                .background(pageBackground)
                                .clipToBounds(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "松手切换到 ${target.title}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
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
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = "新建待办或项目",
            )
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
 * 未就绪动作的读屏描述（设计 §10 RDY-03）：功能实现后只去掉后缀，布局与图标位置不变。
 */
private fun unavailableDescription(base: String, available: Boolean): String =
    if (available) base else "$base，功能尚未开放"

/** 稳定的列表 key：沿用原有 project_<id> / ungrouped_header，避免滚动位置错乱。 */
private fun groupKey(group: TaskGroup): String =
    group.project?.let { "project_${it.id.value}" } ?: "ungrouped_header"

/**
 * 搜索栏（CMP-38）：左侧返回退出搜索、右侧清除查询；自动聚焦并弹键盘。
 *
 * 输入实时生效、不需要提交；清除只清空查询，仍保留搜索栏与焦点。
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NautileIconButton(
            iconRes = R.drawable.ic_arrow_back,
            contentDescription = "退出搜索",
            onClick = onClose,
        )
        TextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            singleLine = true,
            placeholder = {
                Text(
                    text = "搜索标题或项目",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        NautileIconButton(
            iconRes = R.drawable.ic_cancel,
            contentDescription = "清除搜索",
            onClick = onClear,
            enabled = query.isNotEmpty(),
        )
    }
}

/**
 * 分组卡片（SHP-06/07、SPC-07）：整组一张 16dp 圆角卡片，无阴影无描边；
 * 组头名称与数量；空组只保留组头且可点入项目详情；组内行间 8dp 留白、无分隔线。
 */
@Composable
private fun TaskGroupCard(
    group: TaskGroup,
    onCompleteTask: (TaskId) -> Unit,
    onAbandonTask: (TaskId) -> Unit,
    onRequestProjectClose: (ProjectId, ClosedOutcome) -> Unit,
    onOpenTaskDetail: (TaskId) -> Unit,
    onOpenProjectDetail: (ProjectId) -> Unit,
    onRowDragStart: () -> Unit,
    onRowDragEnd: () -> Unit,
) {
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLowest
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = cardColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            val project = group.project
            if (project != null) {
                SwipeableRow(
                    onSwipeLeft = { onRequestProjectClose(project.id, ClosedOutcome.COMPLETED) },
                    onSwipeRight = { onRequestProjectClose(project.id, ClosedOutcome.ABANDONED) },
                    onRowDragStart = onRowDragStart,
                    onRowDragEnd = onRowDragEnd,
                    onClick = { onOpenProjectDetail(project.id) },
                    containerColor = cardColor,
                ) {
                    GroupHeader(name = project.name, count = group.tasks.size, combinedLabel = true)
                }
            } else {
                GroupHeader(name = "单独任务", count = group.tasks.size, combinedLabel = false)
            }
            group.tasks.forEachIndexed { index, task ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                SwipeableRow(
                    onSwipeLeft = { onCompleteTask(task.id) },
                    onSwipeRight = { onAbandonTask(task.id) },
                    onRowDragStart = onRowDragStart,
                    onRowDragEnd = onRowDragEnd,
                    onClick = { onOpenTaskDetail(task.id) },
                    // 已结项行禁用滑动，只保留查看详情（T-10、AC-T-29）。
                    swipeEnabled = task.closedOutcome == null,
                    containerColor = cardColor,
                ) {
                    TaskRowContent(task = task)
                }
            }
        }
    }
}

/**
 * 组头：名称 titleMedium + 数量 bodySmall；间距 8dp；不显示展开箭头（CMP-07）。
 *
 * [combinedLabel] 为 true 时读屏合并为“名称（数量）”一次读出，保留既有项目行文案断言；
 * 为 false 时只合并子项语义，“单独任务”文案仍可单独匹配。
 */
@Composable
private fun GroupHeader(name: String, count: Int, combinedLabel: Boolean) {
    val semanticsModifier = if (combinedLabel) {
        Modifier.clearAndSetSemantics { text = AnnotatedString("$name（$count）") }
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            .then(semanticsModifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 扁平行内容（SHP-06）：无卡片圆角、无分隔线；最小高 56dp，标题 bodyLarge。 */
@Composable
private fun TaskRowContent(task: Task) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (task.closedOutcome != null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (task.closedOutcome != null) {
            Text(
                text = "已结项",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * 条目左右滑：左滑完成、右滑放弃；仅越过阈值才提交，否则回原位（BR-46、AC-T-17）。
 * [swipeEnabled] 为 false 时（已结项）不挂拖拽，只保留点按。
 * [containerColor] 跟随所在分组卡片，避免行自身底色切断卡片层级。
 */
@Composable
private fun SwipeableRow(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onRowDragStart: () -> Unit,
    onRowDragEnd: () -> Unit,
    onClick: () -> Unit = {},
    swipeEnabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
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
            color = containerColor,
        ) {
            content()
        }
    }
}
