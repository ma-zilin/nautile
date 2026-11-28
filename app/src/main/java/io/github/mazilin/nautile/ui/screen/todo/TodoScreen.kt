package io.github.mazilin.nautile.ui.screen.todo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.model.ViewClassification
import io.github.mazilin.nautile.domain.model.ViewSort
import io.github.mazilin.nautile.domain.rule.TaskGroup
import io.github.mazilin.nautile.domain.rule.TaskTimeRules
import io.github.mazilin.nautile.interaction.BoundaryPullReleaseResult
import io.github.mazilin.nautile.interaction.BoundaryPullState
import io.github.mazilin.nautile.interaction.BoundaryPullStateMachine
import io.github.mazilin.nautile.interaction.GestureArbiter
import io.github.mazilin.nautile.ui.designsystem.NautileEmptyState
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.theme.NautileMotion
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 待办页 P-02（S1-T07 / R5-5,6,7）。
 *
 * 标题按钮/空白右滑打开 GTD 选择器；条目右滑完成、左滑放弃；项目行滑动先请求数量确认。
 * 任务完成由前置 Checkbox 与右滑共用同一退场状态机（220ms 线性淡出/滑出收拢后落库）。
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
    onViewClassificationSelected: (ViewClassification) -> Unit = {},
    onViewSortSelected: (ViewSort) -> Unit = {},
    onReorderGroup: (String, List<TaskId>) -> Unit = { _, _ -> },
    onDismissViewError: () -> Unit = {},
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

    // G9 视图菜单：锚定在视图图标上方；关闭不写状态（CMP-39）。
    var viewMenuOpen by remember { mutableStateOf(false) }
    var viewMenuAnchor by remember { mutableStateOf<Rect?>(null) }
    var viewMenuOrigin by remember { mutableStateOf(Offset.Zero) }
    var viewMenuRootSize by remember { mutableStateOf(IntSize.Zero) }

    BackHandler(enabled = viewMenuOpen) { viewMenuOpen = false }
    LaunchedEffect(state.position, state.searchActive) { viewMenuOpen = false }

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
    val hintThresholdPx = with(LocalDensity.current) { NautileSpacing.space2.toPx() }
    val pullCommitThresholdPx = GestureArbiter.BOUNDARY_PULL_THRESHOLD_DP * density
    val coroutineScope = rememberCoroutineScope()
    var gtdSwipe by remember { mutableFloatStateOf(0f) }
    var blockedByRow by remember { mutableStateOf(false) }
    var rowDragging by remember { mutableStateOf(false) }

    // 完成退场统一状态机映射（R5-6、ANIM-09）：key=正在完成退场的任务 ID，value=起播水平偏移（px）。
    // Checkbox 入口传 0f，右滑入口传松手时偏移量；两者共享 220ms 线性退场与防重入。
    var completingOffsets by remember { mutableStateOf<Map<TaskId, Float>>(emptyMap()) }
    LaunchedEffect(state.completingResetVersion) {
        if (state.completingResetVersion > 0) {
            completingOffsets = emptyMap()
        }
    }
    val activeGroupTaskIds = remember(state.groups) {
        state.groups.flatMapTo(HashSet()) { group -> group.tasks.map { it.id } }
    }
    LaunchedEffect(activeGroupTaskIds) {
        if (completingOffsets.isNotEmpty()) {
            val remaining = completingOffsets.filterKeys { it in activeGroupTaskIds }
            if (remaining.size != completingOffsets.size) {
                completingOffsets = remaining
            }
        }
    }
    val startCompleteTask: (TaskId, Float) -> Unit = { taskId, initialOffsetPx ->
        if (taskId !in completingOffsets && taskId !in state.inFlightCompletingIds) {
            completingOffsets = completingOffsets + (taskId to initialOffsetPx)
        }
    }

    // 本地清单位置（CMP-14）：提交后先同步这里，避免下一次快速拉拽读到旧 position。
    var localPosition by remember { mutableStateOf(state.position) }
    // 单次边界拉动手势的不可变状态（R5-2、FR-G-18、BR-61）：起手锁定方向与目标，反向不换目标。
    var pullState by remember { mutableStateOf(BoundaryPullState()) }
    // 拖动期直接读 pullState.offsetPx 实现零延迟跟手；未提交松手交 visualPullOffset 按 BoundaryRebound 回弹。
    val visualPullOffset = remember { Animatable(0f) }
    var reboundJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(state.position) {
        // 外部（含点按抽屉切换与异步回流）位置变化：同步本地值并回到列表顶部。
        if (state.position != localPosition) {
            localPosition = state.position
            listState.scrollToItem(0)
            reboundJob?.cancel()
            reboundJob = null
            pullState = BoundaryPullState()
            visualPullOffset.snapTo(0f)
        }
    }

    // 导航隐藏：累计位移阈值 24dp＋16dp 滞后；仅普通纵向滚动触发（AC-N-15、T-11）。
    var navigationVisible by remember { mutableStateOf(true) }
    var navigationAccumulator by remember { mutableFloatStateOf(0f) }
    val hideThresholdPx = with(LocalDensity.current) { 24.dp.toPx() }
    val showThresholdPx = with(LocalDensity.current) { 16.dp.toPx() }
    val latestOnNavigationVisibilityChanged by rememberUpdatedState(onNavigationVisibilityChanged)
    val latestOnSwitchPosition by rememberUpdatedState(onSwitchPosition)

    suspend fun settleBoundaryPull() {
        val currentPull = pullState
        if (!currentPull.active) return
        val startOffset = currentPull.offsetPx
        val result = BoundaryPullStateMachine.onRelease(
            state = currentPull,
            currentPosition = localPosition,
            thresholdPx = pullCommitThresholdPx,
        )
        // 结算立即清空手势状态，确保连续两次快速拉拽彼此隔离、不跳过或跳错清单。
        pullState = BoundaryPullState()
        reboundJob?.cancel()
        reboundJob = null
        when (result) {
            is BoundaryPullReleaseResult.Commit -> {
                // 先同步本地 position 与滚动位置，再异步持久化（CMP-14）。
                localPosition = result.target
                visualPullOffset.snapTo(0f)
                latestOnSwitchPosition(result.target)
                listState.scrollToItem(0)
            }

            is BoundaryPullReleaseResult.Rebound -> {
                if (result.shouldAnimate) {
                    visualPullOffset.snapTo(startOffset)
                    reboundJob = coroutineScope.launch {
                        visualPullOffset.animateTo(0f, NautileMotion.BoundaryRebound)
                    }
                } else {
                    visualPullOffset.snapTo(0f)
                }
            }
        }
    }

    // 全屏边界拉动（R5-2、FR-G-18、BR-61、CMP-14、ANIM-07）：普通滚动优先，到顶/底后继续拉才预览；
    // 起手按边界侧锁定方向与目标，反向拖动仅减位移不换目标、越过 0 立即取消并隐藏提示；
    // 松手仅朝锁定方向且达阈值时提交，否则经 BoundaryRebound 回弹；首尾不循环。
    val boundaryConnection = remember(
        maxPullPx,
        density,
        pullCommitThresholdPx,
        navigationUpdatesEnabled,
        hideThresholdPx,
        showThresholdPx,
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) {
                    return Offset.Zero
                }
                if (!pullState.active && (reboundJob?.isActive == true || visualPullOffset.value != 0f)) {
                    reboundJob?.cancel()
                    reboundJob = null
                    coroutineScope.launch { visualPullOffset.snapTo(0f) }
                }
                val (nextState, consumedY) = BoundaryPullStateMachine.onPreDrag(
                    state = pullState,
                    availableY = available.y,
                )
                if (consumedY == 0f) return Offset.Zero
                pullState = nextState
                return Offset(0f, consumedY)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (navigationUpdatesEnabled && consumed.y != 0f && !pullState.active) {
                    if (navigationVisible) {
                        // 上滑（consumed.y 为负）累计绝对值，超过 24dp 隐藏。
                        if (consumed.y < 0f) {
                            navigationAccumulator += -consumed.y
                            if (navigationAccumulator > hideThresholdPx) {
                                navigationVisible = false
                                navigationAccumulator = 0f
                                latestOnNavigationVisibilityChanged(false)
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
                                latestOnNavigationVisibilityChanged(true)
                            }
                        } else {
                            navigationAccumulator = 0f
                        }
                    }
                }
                if (available.y == 0f) return Offset.Zero
                val atTop = !listState.canScrollBackward
                val atBottom = !listState.canScrollForward
                val (nextState, dragConsumed) = BoundaryPullStateMachine.onDrag(
                    state = pullState,
                    availableY = available.y,
                    atTop = atTop,
                    atBottom = atBottom,
                    currentPosition = localPosition,
                    maxPullPx = maxPullPx,
                )
                if (!dragConsumed) return Offset.Zero
                if (reboundJob?.isActive == true) {
                    reboundJob?.cancel()
                    reboundJob = null
                }
                pullState = nextState
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!pullState.active) return Velocity.Zero
                val consumeVelocity = !pullState.cancelled && pullState.signedOffsetPx > 0f
                settleBoundaryPull()
                return if (consumeVelocity) available else Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (pullState.active) {
                    settleBoundaryPull()
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
            // 供视图锚定菜单换算浮层坐标（与既有手势分层，不消费事件）。
            .onGloballyPositioned { viewMenuOrigin = it.positionInRoot() }
            .onSizeChanged { viewMenuRootSize = it }
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
                            if (!navigationVisible && !pullState.active && totalDown > showThresholdPx) {
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
                            onClick = { viewMenuOpen = true },
                            enabled = state.viewOptionsAvailable,
                            modifier = Modifier.onGloballyPositioned {
                                viewMenuAnchor = it.boundsInRoot()
                            },
                        )
                        NautileIconButton(
                            iconRes = R.drawable.ic_settings,
                            contentDescription = "设置",
                            onClick = onOpenSettings,
                            testTag = "settings_entry",
                        )
                    }
                    // 搜索栏插在标题行下方，标题与三图标保持可见（CMP-38/43、ANIM-11）。
                    AnimatedVisibility(
                        visible = state.searchActive,
                        enter = expandVertically(animationSpec = NautileMotion.ContainerExpand) +
                            fadeIn(animationSpec = NautileMotion.StandardEffects),
                        exit = shrinkVertically(animationSpec = NautileMotion.ContainerExpand) +
                            fadeOut(animationSpec = NautileMotion.StandardEffects),
                    ) {
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
            val displayedPullOffsetPx = if (pullState.active) pullState.offsetPx else visualPullOffset.value
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
                        .graphicsLayer { translationY = displayedPullOffsetPx },
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
                        item(key = group.key) {
                            TaskGroupCard(
                                group = group,
                                // R4 G6：手动排序暂缓（CMP-40.3、NO-19）——长按拖拽入口不可达；
                                // 重排实现保留在下方供恢复时启用，不删除数据/枚举。
                                manualSortEnabled = false,
                                overdueTaskIds = state.overdueTaskIds,
                                referenceDate = state.referenceDate,
                                completingOffsets = completingOffsets,
                                inFlightCompletingIds = state.inFlightCompletingIds,
                                completingResetVersion = state.completingResetVersion,
                                onStartCompleteTask = startCompleteTask,
                                onReorder = { orderedIds -> onReorderGroup(group.key, orderedIds) },
                                onCompleteTask = onCompleteTask,
                                onAbandonTask = onAbandonTask,
                                onRequestProjectClose = onRequestProjectClose,
                                onOpenTaskDetail = onOpenTaskDetail,
                                onOpenProjectDetail = onOpenProjectDetail,
                                onRowDragStart = { rowDragging = true },
                                onRowDragEnd = { rowDragging = false },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = NautileMotion.StandardEffects,
                                    fadeOutSpec = NautileMotion.StandardEffects,
                                    placementSpec = NautileMotion.ListItemPlacement,
                                ),
                            )
                        }
                    }
                }

                // MOT-15：预览层不透明并裁剪；空间不足时隐藏文字，不溢出到列表交界。
                // R5-2 / CMP-14：仅未取消且朝锁定方向拉动时提示“松手切换到 X”或首/尾边界提示。
                val pullHint = BoundaryPullStateMachine.hintText(
                    state = pullState,
                    minSignedOffsetPx = hintThresholdPx,
                    label = { it.title },
                )
                if (pullHint != null) {
                    val reveal = with(LocalDensity.current) { abs(displayedPullOffsetPx).toDp() }
                    // 空间不足时隐藏文字而不是溢出（MOT-15）。
                    if (reveal >= NautileSpacing.space8) {
                        val atTop = pullState.direction > 0
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
                                text = pullHint,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(horizontal = NautileSpacing.space4)
                                    .testTag("boundary_pull_hint"),
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

        // 任务完成/放弃或手动排序写入失败提示（NO-07、CMP-40.2）：失败已回滚，点按可关闭提示。
        val actionError = state.taskActionError ?: state.manualOrderError
        if (actionError != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 88.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .clickable(onClick = onDismissViewError),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 3.dp,
            ) {
                Text(
                    text = actionError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // 视图锚定菜单（CMP-39）：透明点按层关闭；不遮罩、不收键盘、不改页面导航。
        if (viewMenuOpen && state.viewOptionsAvailable) {
            AttributeMenuOverlay(
                anchor = viewMenuAnchor,
                origin = viewMenuOrigin,
                rootSize = viewMenuRootSize,
                onDismiss = { viewMenuOpen = false },
            ) {
                ViewOptionsMenuContent(
                    options = state.viewOptions,
                    error = state.viewOptionsError,
                    onClassificationSelected = onViewClassificationSelected,
                    onSortSelected = onViewSortSelected,
                )
            }
        }
    }

    state.projectConfirmation?.let { pending ->
        val action = if (pending.outcome == ClosedOutcome.COMPLETED) "完成" else "放弃"
        val isAbandon = pending.outcome == ClosedOutcome.ABANDONED
        AlertDialog(
            onDismissRequest = onCancelProjectClose,
            title = { Text(pending.projectName) },
            text = { Text("将$action ${pending.taskCount} 个子待办") },
            confirmButton = {
                TextButton(
                    onClick = onConfirmProjectClose,
                    colors = if (isAbandon) {
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        ButtonDefaults.textButtonColors()
                    },
                ) { Text("确认") }
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
            iconRes = R.drawable.ic_close,
            contentDescription = "清除搜索",
            onClick = onClear,
            enabled = query.isNotEmpty(),
        )
    }
}

/**
 * 分组卡片（SHP-06/07、SPC-07）：整组一张 16dp 圆角卡片，无阴影无描边；
 * 组头名称与数量；空组只保留组头且可点入项目详情；组内行间 8dp 留白、无分隔线。
 *
 * G9：排序=手动且非搜索态时，行支持长按纵向拖拽在组内重排（CMP-40.2）；
 * 未长按前横向左右滑仍按完成/放弃处理，长按进入拖拽后该行禁用横向滑动。
 */
@Composable
private fun TaskGroupCard(
    group: TaskGroup,
    manualSortEnabled: Boolean,
    overdueTaskIds: Set<TaskId>,
    referenceDate: LocalDate?,
    completingOffsets: Map<TaskId, Float>,
    inFlightCompletingIds: Set<TaskId>,
    completingResetVersion: Int,
    onStartCompleteTask: (TaskId, Float) -> Unit,
    onReorder: (List<TaskId>) -> Unit,
    onCompleteTask: (TaskId) -> Unit,
    onAbandonTask: (TaskId) -> Unit,
    onRequestProjectClose: (ProjectId, ClosedOutcome) -> Unit,
    onOpenTaskDetail: (TaskId) -> Unit,
    onOpenProjectDetail: (ProjectId) -> Unit,
    onRowDragStart: () -> Unit,
    onRowDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLowest
    // 行最小高 56dp + 行间 8dp，用于把纵向位移换算成跨越的行数。
    val rowHeightPx = with(LocalDensity.current) { 64.dp.toPx() }
    var localTasks by remember(group.key) { mutableStateOf(group.tasks) }
    var draggingId by remember { mutableStateOf<TaskId?>(null) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val latestTasks by rememberUpdatedState(group.tasks)
    val latestReorder by rememberUpdatedState(onReorder)

    LaunchedEffect(group.tasks) {
        if (draggingId == null) localTasks = group.tasks
    }

    fun startReorder(id: TaskId) {
        draggingId = id
        dragAccumulator = 0f
    }

    fun moveDragged(deltaY: Float) {
        val dragging = draggingId ?: return
        dragAccumulator += deltaY
        val current = localTasks
        val from = current.indexOfFirst { it.id == dragging }
        if (from < 0) return
        val shift = (dragAccumulator / rowHeightPx).toInt()
        if (shift == 0) return
        val to = (from + shift).coerceIn(0, current.lastIndex)
        if (to == from) {
            dragAccumulator = 0f
            return
        }
        val moved = current.toMutableList()
        val item = moved.removeAt(from)
        moved.add(to, item)
        localTasks = moved
        dragAccumulator -= shift * rowHeightPx
    }

    fun finishReorder(commit: Boolean) {
        if (draggingId == null) return
        draggingId = null
        dragAccumulator = 0f
        if (!commit) {
            localTasks = latestTasks
            return
        }
        if (localTasks.map { it.id } != latestTasks.map { it.id }) {
            latestReorder(localTasks.map { it.id })
        }
    }

    Surface(
        modifier = modifier
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
                    // R5-7：右滑完成、左滑放弃；项目行同步对调并先弹批量子待办数量确认对话框。
                    onSwipeRight = { _ -> onRequestProjectClose(project.id, ClosedOutcome.COMPLETED) },
                    onSwipeLeft = { _ -> onRequestProjectClose(project.id, ClosedOutcome.ABANDONED) },
                    onRowDragStart = onRowDragStart,
                    onRowDragEnd = onRowDragEnd,
                    onClick = { onOpenProjectDetail(project.id) },
                    holdOffsetOnRightCommit = false,
                    containerColor = cardColor,
                ) {
                    GroupHeader(
                        name = project.name,
                        count = localTasks.size,
                        combinedLabel = true,
                    )
                }
            } else {
                // 优先级/截止日期组：组头不可折叠、不可点入项目详情（CMP-42）。
                GroupHeader(
                    name = group.headerTitle,
                    count = localTasks.size,
                    combinedLabel = false,
                )
            }
            localTasks.forEachIndexed { index, task ->
                // key 跟随任务：拖拽重排与完成退场动画期间组合身份不随槽位改变。
                key(task.id) {
                    val reorderModifier = if (manualSortEnabled) {
                        Modifier.pointerInput(task.id, manualSortEnabled) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    startReorder(task.id)
                                    onRowDragStart()
                                },
                                onDrag = { change, dragAmount ->
                                    // 长按后的纵向拖拽归重排；消费事件避免列表同时滚动。
                                    change.consume()
                                    moveDragged(dragAmount.y)
                                },
                                onDragEnd = {
                                    finishReorder(commit = true)
                                    onRowDragEnd()
                                },
                                onDragCancel = {
                                    finishReorder(commit = false)
                                    onRowDragEnd()
                                },
                            )
                        }
                    } else {
                        Modifier
                    }
                    CompletableTaskRow(
                        task = task,
                        showTopSpacer = index > 0,
                        isOverdue = task.id in overdueTaskIds,
                        referenceDate = referenceDate,
                        isCompleting = (task.id in completingOffsets) || (task.id in inFlightCompletingIds),
                        initialOffsetPx = completingOffsets[task.id] ?: 0f,
                        completingResetVersion = completingResetVersion,
                        reorderDragging = draggingId != null,
                        cardColor = cardColor,
                        reorderModifier = reorderModifier,
                        onStartComplete = onStartCompleteTask,
                        onCompleteTask = onCompleteTask,
                        onAbandonTask = onAbandonTask,
                        onOpenTaskDetail = onOpenTaskDetail,
                        onRowDragStart = onRowDragStart,
                        onRowDragEnd = onRowDragEnd,
                    )
                }
            }
        }
    }
}

/**
 * 任务行完成退场容器（R5-6、ANIM-09）：
 * Checkbox 点击与右滑 ≥72dp 共用同一状态机：
 * 1. 立刻切为勾选态并禁用点击、Checkbox 与滑动（防重入）；
 * 2. 播放 220ms 线性淡出/滑出与垂直高度收拢（[NautileMotion.TaskCompleteExit] + [NautileMotion.TaskCompleteCollapse]）；
 * 3. 动画结束单次回调 [onCompleteTask] 落库；若中途切页/切清单卸载，由 [DisposableEffect] 兜底幂等提交防丢单。
 */
@Composable
private fun CompletableTaskRow(
    task: Task,
    showTopSpacer: Boolean,
    isOverdue: Boolean,
    referenceDate: LocalDate?,
    isCompleting: Boolean,
    initialOffsetPx: Float,
    completingResetVersion: Int,
    reorderDragging: Boolean,
    cardColor: Color,
    reorderModifier: Modifier,
    onStartComplete: (TaskId, Float) -> Unit,
    onCompleteTask: (TaskId) -> Unit,
    onAbandonTask: (TaskId) -> Unit,
    onOpenTaskDetail: (TaskId) -> Unit,
    onRowDragStart: () -> Unit,
    onRowDragEnd: () -> Unit,
) {
    val completeSlideDistancePx = with(LocalDensity.current) { 24.dp.toPx() }
    val exitProgress = remember(task.id, completingResetVersion) { Animatable(0f) }
    var completeCommitted by remember(task.id, completingResetVersion) { mutableStateOf(false) }
    val latestOnCompleteTask by rememberUpdatedState(onCompleteTask)
    val latestIsCompleting by rememberUpdatedState(isCompleting)

    LaunchedEffect(task.id, isCompleting, completingResetVersion) {
        if (isCompleting && !completeCommitted) {
            exitProgress.animateTo(
                targetValue = 1f,
                animationSpec = NautileMotion.TaskCompleteExit,
            )
            if (!completeCommitted) {
                completeCommitted = true
                latestOnCompleteTask(task.id)
            }
        }
    }

    // 切页/切清单/退出搜索导致行在 220ms 退场动画结束前被卸载时，兜底幂等提交完成，避免丢单。
    DisposableEffect(task.id, completingResetVersion) {
        onDispose {
            if (latestIsCompleting && !completeCommitted) {
                completeCommitted = true
                latestOnCompleteTask(task.id)
            }
        }
    }

    AnimatedVisibility(
        visible = !isCompleting,
        exit = shrinkVertically(animationSpec = NautileMotion.TaskCompleteCollapse) +
            fadeOut(animationSpec = NautileMotion.TaskCompleteExit),
    ) {
        val completingTranslationX = if (isCompleting) {
            initialOffsetPx + completeSlideDistancePx * exitProgress.value
        } else {
            0f
        }
        Column(
            modifier = Modifier.graphicsLayer {
                translationX = completingTranslationX
            },
        ) {
            if (showTopSpacer) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            SwipeableRow(
                // R5-7：右滑完成（走统一退场状态机）、左滑放弃。
                onSwipeRight = { offsetPx ->
                    if (task.closedOutcome == null && !isCompleting) {
                        onStartComplete(task.id, offsetPx)
                    }
                },
                onSwipeLeft = { _ ->
                    if (task.closedOutcome == null && !isCompleting) {
                        onAbandonTask(task.id)
                    }
                },
                onRowDragStart = onRowDragStart,
                onRowDragEnd = onRowDragEnd,
                onClick = {
                    if (!isCompleting) onOpenTaskDetail(task.id)
                },
                clickEnabled = !isCompleting,
                // 已结项行与完成退场中禁用滑动（T-10、AC-T-29、R5-6）；拖拽重排中同样禁用横向滑动。
                swipeEnabled = task.closedOutcome == null && !isCompleting && !reorderDragging,
                holdOffsetOnRightCommit = true,
                containerColor = cardColor,
                modifier = reorderModifier.testTag("task_row_${task.id.value}"),
            ) {
                TaskRowContent(
                    task = task,
                    isCompleting = isCompleting,
                    isOverdue = isOverdue,
                    referenceDate = referenceDate,
                    onToggleComplete = {
                        if (task.closedOutcome == null && !isCompleting) {
                            onStartComplete(task.id, 0f)
                        }
                    },
                )
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

/**
 * 扁平任务行内容（SHP-06、R5-5、03_交互与动效规格 §2.2）：
 * - 标题前置标准 M3 [Checkbox]（48×48dp 触控区，已结项/完成中禁用，点击不误触发打开详情）；
 * - 标题下方按需展示详情摘要（仅非空时渲染，bodySmall/onSurfaceVariant，最多 2 行省略）；
 * - 按需展示截止时间（仅 dueDate 非空时渲染；逾期用 error 色 + “已逾期 · ”可读语义前缀，颜色不作唯一信息）；
 * - 已结项任务标题灰化加删除线，行尾清晰区分“已完成”/“已放弃”。
 */
@Composable
private fun TaskRowContent(
    task: Task,
    isCompleting: Boolean,
    isOverdue: Boolean,
    referenceDate: LocalDate?,
    onToggleComplete: () -> Unit,
) {
    val isClosed = task.closedOutcome != null
    val checked = isCompleting || task.closedOutcome == ClosedOutcome.COMPLETED
    val checkboxEnabled = !isClosed && !isCompleting
    val checkboxDescription = when {
        isCompleting -> "正在完成任务：${task.title}"
        task.closedOutcome == ClosedOutcome.COMPLETED -> "已完成任务不可勾选：${task.title}"
        task.closedOutcome == ClosedOutcome.ABANDONED -> "已放弃任务不可勾选：${task.title}"
        else -> "完成任务：${task.title}"
    }
    val detailPreview = task.detail?.takeIf { it.isNotBlank() }
    val dueLabel = TaskTimeRules.formatTaskDueLabel(
        dueDate = task.dueDate,
        dueTime = task.dueTime,
        today = referenceDate,
    )
    // 已结项任务不标逾期红（FR-G-09、BR-38、03_交互与动效规格 §2.2）。
    val showOverdue = !isClosed && isOverdue && dueLabel != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { shouldComplete ->
                if (shouldComplete && checkboxEnabled) {
                    onToggleComplete()
                }
            },
            enabled = checkboxEnabled,
            modifier = Modifier
                .size(48.dp)
                .testTag("task_checkbox_${task.id.value}")
                .semantics { contentDescription = checkboxDescription },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(NautileSpacing.space1),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isClosed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (isClosed) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                task.closedOutcome?.let { outcome ->
                    val statusLabel = when (outcome) {
                        ClosedOutcome.COMPLETED -> "已完成"
                        ClosedOutcome.ABANDONED -> "已放弃"
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.padding(start = NautileSpacing.space2),
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = NautileSpacing.space2,
                                vertical = NautileSpacing.space1,
                            ),
                        )
                    }
                }
            }
            if (detailPreview != null) {
                Text(
                    text = detailPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_detail_preview_${task.id.value}"),
                )
            }
            if (dueLabel != null) {
                val dueColor = if (showOverdue) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val dueDisplayText = if (showOverdue) "已逾期 · $dueLabel" else dueLabel
                Row(
                    modifier = Modifier
                        .testTag("task_due_${task.id.value}")
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space1),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_schedule),
                        contentDescription = null,
                        tint = dueColor,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = dueDisplayText,
                        style = MaterialTheme.typography.bodySmall,
                        color = dueColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 条目左右滑（R5-7、BR-46、BR-60、AC-T-17、ANIM-08）：
 * - 右滑（正位移）= 完成（左侧露出整行高 primaryContainer + ic_check + “完成”）；
 * - 左滑（负位移）= 放弃（右侧露出整行高 errorContainer + ic_close + “放弃”）；
 * - 仅越过 72dp 阈值才提交；未达阈值松手经 [NautileMotion.BoundaryRebound] 平滑回弹且可打断；
 * - [swipeEnabled] 为 false 时（已结项、完成退场中或拖拽重排中）不挂横向拖拽；
 * - 活跃条目水平滑动由本组件消费，绝不冒泡触发 GTD 左抽屉（BR-60 / FR-G-16）。
 */
@Composable
private fun SwipeableRow(
    onSwipeLeft: (Float) -> Unit,
    onSwipeRight: (Float) -> Unit,
    onRowDragStart: () -> Unit,
    onRowDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    clickEnabled: Boolean = true,
    swipeEnabled: Boolean = true,
    holdOffsetOnRightCommit: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val offsetAnim = remember { Animatable(0f) }
    var reboundJob by remember { mutableStateOf<Job?>(null) }
    val latestOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val latestOnSwipeRight by rememberUpdatedState(onSwipeRight)
    val latestOnRowDragStart by rememberUpdatedState(onRowDragStart)
    val latestOnRowDragEnd by rememberUpdatedState(onRowDragEnd)
    val latestHoldRight by rememberUpdatedState(holdOffsetOnRightCommit)

    LaunchedEffect(swipeEnabled) {
        if (!swipeEnabled) {
            reboundJob?.cancel()
            reboundJob = null
            isDragging = false
            dragOffset = 0f
            offsetAnim.snapTo(0f)
        }
    }

    val displayedOffset = if (isDragging) dragOffset else offsetAnim.value

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
    ) {
        if (swipeEnabled) {
            // R5-7 / CMP-05：右滑露出左侧“完成”（primaryContainer + check），
            // 左滑露出右侧“放弃”（errorContainer + close）；色条占满整行高，颜色不是唯一区分。
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .clipToBounds(),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = NautileSpacing.space4),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space2),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = "完成",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = NautileSpacing.space4),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space2),
                    ) {
                        Text(
                            text = "放弃",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 1,
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
        val dragModifier = if (swipeEnabled) {
            Modifier.draggable(
                state = rememberDraggableState { delta ->
                    if (!isDragging) {
                        reboundJob?.cancel()
                        reboundJob = null
                        dragOffset = offsetAnim.value
                        isDragging = true
                        latestOnRowDragStart()
                    }
                    dragOffset += delta
                },
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    reboundJob?.cancel()
                    reboundJob = null
                    dragOffset = offsetAnim.value
                    isDragging = true
                    latestOnRowDragStart()
                },
                onDragStopped = {
                    val finalOffset = dragOffset
                    latestOnRowDragEnd()
                    val committed = GestureArbiter.isCommitSwipe(finalOffset, density)
                    when {
                        committed && finalOffset > 0f -> {
                            if (latestHoldRight) {
                                // 任务行右滑完成：交由外层退场状态机从 finalOffset 顺势继续滑出，避免闪回。
                                offsetAnim.snapTo(0f)
                                isDragging = false
                                dragOffset = 0f
                                latestOnSwipeRight(finalOffset)
                            } else {
                                // 项目行右滑完成：平滑回弹归零并弹出数量确认框。
                                offsetAnim.snapTo(finalOffset)
                                isDragging = false
                                dragOffset = 0f
                                reboundJob = coroutineScope.launch {
                                    offsetAnim.animateTo(0f, NautileMotion.BoundaryRebound)
                                }
                                latestOnSwipeRight(finalOffset)
                            }
                        }

                        committed && finalOffset < 0f -> {
                            offsetAnim.snapTo(finalOffset)
                            isDragging = false
                            dragOffset = 0f
                            reboundJob = coroutineScope.launch {
                                offsetAnim.animateTo(0f, NautileMotion.BoundaryRebound)
                            }
                            latestOnSwipeLeft(finalOffset)
                        }

                        else -> {
                            offsetAnim.snapTo(finalOffset)
                            isDragging = false
                            dragOffset = 0f
                            if (finalOffset != 0f) {
                                reboundJob = coroutineScope.launch {
                                    offsetAnim.animateTo(0f, NautileMotion.BoundaryRebound)
                                }
                            }
                        }
                    }
                },
            )
        } else {
            Modifier
        }
        val accessibilityModifier = if (swipeEnabled) {
            Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction("标记完成") {
                        latestOnSwipeRight(0f)
                        true
                    },
                    CustomAccessibilityAction("标记放弃") {
                        latestOnSwipeLeft(0f)
                        true
                    },
                )
            }
        } else {
            Modifier
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = displayedOffset }
                .then(accessibilityModifier)
                .then(dragModifier)
                .clickable(enabled = clickEnabled, onClick = onClick),
            color = containerColor,
        ) {
            content()
        }
    }
}
