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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
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
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.launch

/**
 * 待办下半屏详情（R5-8、FR-D-01/02/03、FR-G-19、AC-D-06/07/08/09、AC-T-29、03 规格 §2.4）。
 *
 * 与 [NewTaskSheet] 完全同构的自适应高度下半屏布局（废除独立编辑面板与旧 60% 固定高度）：
 * - 顶部拖拽柄 + 简洁头部（左侧“任务详情”，右侧关闭图标按钮 `detail_close_button`，无编辑图标）；
 * - 第一行标题、固定第二行小字详情：直接编辑即保存（失焦 / IME Done / 关闭前提交），空标题报错并阻止关闭；
 * - 底部四属性工具栏（日期、优先级、项目、清单）：与新建共用 [TaskAttributeToolbar] 与 [TaskAttributeMenuHost]，
 *   始终“图标 + 文字”，选择即保存；已结项任务禁用清单入口并保留“重新打开”操作位；
 * - 下拉关闭（含 T-088 输入框聚焦/起手排除）、遮罩点按关闭、系统返回分层递退
 *   （锚定菜单 → 24h 时间圆盘 → 日期日历 → IME → 面板关闭校验）全部经过无效字段守卫。
 *
 * @param visible 进出场目标可见性；退出动效结束后才回调 [onExitFinished]。
 * @param projects 项目菜单可选项；调用方只应传入活跃项目。
 * @param onClearDetail 兼容保留参数，不再通过工具栏菜单触发（详情直接支持输入框清空即保存）。
 */
@Composable
fun TaskDetailSheet(
    state: TaskDetailUiState,
    onTitleChanged: (String) -> Unit,
    onCommitTitle: () -> Unit,
    onReopen: () -> Unit,
    onCloseRequested: () -> Unit,
    onDetailChanged: (String) -> Unit = {},
    onCommitDetail: () -> Unit = {},
    visible: Boolean = true,
    onExitFinished: () -> Unit = {},
    projects: List<Project> = emptyList(),
    onPriorityChanged: (Priority) -> Unit = {},
    onDueDateChanged: (LocalDate?) -> Unit = {},
    onDueTimeChanged: (LocalTime?) -> Unit = {},
    onProjectChanged: (ProjectId?) -> Unit = {},
    onGtdChanged: (GtdPosition) -> Unit = {},
    onProjectDraftChanged: (String) -> Unit = {},
    onCreateProjectForTask: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER")
    onClearDetail: () -> Unit = {},
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val motion = rememberSheetMotion(visible = visible, onExitFinished = onExitFinished)
    val currentVisible by rememberUpdatedState(visible)
    val currentOnCloseRequested by rememberUpdatedState(onCloseRequested)

    // 面板内瞬时界面状态：无遮罩锚定菜单、日期日历与 24h 时间圆盘子视图（不持久化）。
    var openMenu by remember { mutableStateOf<TaskAttribute?>(null) }
    var dateSubView by remember { mutableStateOf(DateSubView.NONE) }
    val attributeAnchors = remember { mutableStateMapOf<TaskAttribute, Rect>() }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    // 项目菜单内“新建项目”等待新项目回填；成功后自动关闭菜单。
    var awaitingProjectCreate by remember { mutableStateOf(false) }

    // 系统返回单层递退：锚定菜单 → 24h 时间圆盘 → 日期日历 → 表单（03 规格 §1.5）。
    BackHandler(enabled = visible && (openMenu != null || dateSubView != DateSubView.NONE)) {
        when {
            openMenu != null -> openMenu = null
            dateSubView == DateSubView.TIME_PICKER -> dateSubView = DateSubView.CALENDAR
            dateSubView == DateSubView.CALENDAR -> dateSubView = DateSubView.NONE
            else -> Unit
        }
    }
    LaunchedEffect(visible) {
        if (!visible) {
            openMenu = null
            dateSubView = DateSubView.NONE
        }
    }
    LaunchedEffect(state.task.projectId) {
        if (awaitingProjectCreate) {
            awaitingProjectCreate = false
            openMenu = null
        }
    }
    LaunchedEffect(state.projectDraftError) {
        if (state.projectDraftError != null) awaitingProjectCreate = false
    }

    // T-088：输入框聚焦或手势起点落在输入框内时，不触发遮罩/内容的下拉关闭。
    val titleFocused = remember { mutableStateOf(false) }
    val detailFocused = remember { mutableStateOf(false) }
    val titleBounds = remember { mutableStateOf<Rect?>(null) }
    val detailBounds = remember { mutableStateOf<Rect?>(null) }
    val handleBounds = remember { mutableStateOf<Rect?>(null) }
    val boxOrigin = remember { mutableStateOf(Offset.Zero) }
    val gestureFromInput = remember { mutableStateOf(false) }

    fun inputActive(): Boolean = titleFocused.value || detailFocused.value

    fun pointInInput(pointInRoot: Offset): Boolean =
        titleBounds.value?.contains(pointInRoot) == true ||
            detailBounds.value?.contains(pointInRoot) == true

    var contentPull by remember { mutableFloatStateOf(0f) }
    val contentConnection = remember(motion, scrollState, visible, density) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // 内容已到顶仍继续下拉时累计；无效字段由父层阻止关闭。
                // T-088：输入框起手/聚焦或日历/时间圆盘子视图打开时不累计，避免拖动误关。
                if (gestureFromInput.value ||
                    inputActive() ||
                    dateSubView != DateSubView.NONE ||
                    source != NestedScrollSource.UserInput ||
                    available.y <= 0f ||
                    scrollState.canScrollBackward
                ) {
                    return Offset.Zero
                }
                if (contentPull == 0f) scope.launch { motion.takeOver() }
                contentPull += available.y
                if (visible) motion.dragBy(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                if (visible && !gestureFromInput.value && !inputActive() && contentPull > 0f) {
                    if (motion.shouldCommit(density, contentPull, available.y)) {
                        currentOnCloseRequested()
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
    val checklistEnabled = state.task.closedOutcome == null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { boxOrigin.value = it.positionInRoot() }
            .onSizeChanged { rootSize = it }
            // 祖先层 Initial pass 观察下拉，不与内容 verticalScroll 竞争；
            // 仅内容不可滚动时进入拖拽；拖拽柄由自身手势处理，避免双重位移。
            // T-088：输入框聚焦或起点在输入框内时整段手势不参与下拉关闭。
            .nautileSheetPullDown(
                motion = motion,
                visible = { currentVisible },
                scope = scope,
                onCommitClose = { currentOnCloseRequested() },
                pass = PointerEventPass.Initial,
                canStart = {
                    !scrollState.canScrollBackward && !scrollState.canScrollForward
                },
                isExcluded = { point ->
                    val excluded = dateSubView != DateSubView.NONE ||
                        inputActive() ||
                        pointInInput(point + boxOrigin.value) ||
                        handleBounds.value?.contains(point + boxOrigin.value) == true
                    gestureFromInput.value = excluded
                    excluded
                },
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 遮罩层：绘制与“点按遮罩关闭”均在遮罩层完成，面板区域点按由上层 Surface 消费。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = motion.scrim.value * motion.dragScrimFactor() }
                .background(scrimColor.copy(alpha = NautileStates.SCRIM_ALPHA))
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
                                // 点按遮罩分层关闭：菜单 → 时间圆盘 → 日期日历 → 详情（含无效字段守卫）。
                                if (!dragged && currentVisible) {
                                    when {
                                        openMenu != null -> openMenu = null
                                        dateSubView == DateSubView.TIME_PICKER -> {
                                            dateSubView = DateSubView.CALENDAR
                                        }

                                        dateSubView == DateSubView.CALENDAR -> {
                                            dateSubView = DateSubView.NONE
                                        }

                                        else -> currentOnCloseRequested()
                                    }
                                }
                                break
                            }
                        }
                    }
                },
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                // inset 仅在此处消费一次：键盘与导航栏取较大者，贴键盘无空隙（SPC-08）。
                .windowInsetsPadding(sheetInsets)
                .onSizeChanged { motion.sheetHeightPx = it.height.toFloat() }
                .graphicsLayer {
                    // 自适应高度下半屏：从自身高度滑入/滑出，与 NewTaskSheet 完全同构（R5-8）。
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
                        .onGloballyPositioned { handleBounds.value = it.boundsInRoot() }
                        .nautileSheetPullDown(
                            motion = motion,
                            visible = { currentVisible },
                            scope = scope,
                            onCommitClose = { currentOnCloseRequested() },
                            // 输入框聚焦或处于日期/时间圆盘子视图时拖拽柄不参与下拉关闭。
                            isExcluded = { inputActive() || dateSubView != DateSubView.NONE },
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

                val inDateSubView = dateSubView != DateSubView.NONE
                val contentHorizontalPadding = if (inDateSubView) 0.dp else NautileSpacing.space6
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = contentHorizontalPadding,
                            end = contentHorizontalPadding,
                            top = NautileSpacing.space2,
                            bottom = NautileSpacing.space6,
                        )
                        // 200% 字体或子视图增高时内容可滚动，不锁死高度也不裁切（A11Y-13）。
                        .verticalScroll(scrollState)
                        .nestedScroll(contentConnection),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (inDateSubView) NautileSpacing.space6 else 0.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "任务详情",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        NautileIconButton(
                            iconRes = R.drawable.ic_close,
                            contentDescription = "关闭",
                            onClick = { currentOnCloseRequested() },
                            testTag = "detail_close_button",
                        )
                    }
                    if (inDateSubView) {
                        // 日期日历与 24h 时间圆盘在同一面板内切换，不叠加第二层遮罩（R5-3/R5-4）。
                        DateAttributePanel(
                            subView = dateSubView,
                            dueDate = state.task.dueDate,
                            dueTime = state.task.dueTime,
                            onDueDateChanged = onDueDateChanged,
                            onDueTimeChanged = onDueTimeChanged,
                            onSubViewChanged = { dateSubView = it },
                        )
                    } else {
                        Spacer(modifier = Modifier.height(NautileSpacing.space2))
                        OutlinedTextField(
                            value = state.titleValue,
                            onValueChange = onTitleChanged,
                            label = { Text("标题") },
                            singleLine = true,
                            isError = state.titleInvalid,
                            supportingText = if (state.titleInvalid) {
                                {
                                    Text(
                                        text = "标题不能为空",
                                        modifier = Modifier.semantics {
                                            liveRegion = LiveRegionMode.Polite
                                        },
                                    )
                                }
                            } else {
                                null
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onCommitTitle() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { titleBounds.value = it.boundsInRoot() }
                                .onFocusChanged { focus ->
                                    titleFocused.value = focus.isFocused
                                    if (!focus.isFocused && state.titleDraft != null) onCommitTitle()
                                }
                                .testTag("task_detail_title"),
                        )
                        // 固定第二行：小字详情输入框，与 NewTaskSheet 同构，直接编辑即保存（FR-D-03）。
                        OutlinedTextField(
                            value = state.detailValue,
                            onValueChange = onDetailChanged,
                            label = { Text("详情（可选）") },
                            minLines = 1,
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodySmall,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onCommitDetail() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { detailBounds.value = it.boundsInRoot() }
                                .onFocusChanged { focus ->
                                    detailFocused.value = focus.isFocused
                                    if (!focus.isFocused && state.detailDraft != null) {
                                        onCommitDetail()
                                    }
                                }
                                .testTag("task_detail_detail"),
                        )
                        // 与新建完全同构的四属性工具栏：日期、优先级、项目、清单，选择即保存。
                        TaskAttributeToolbar(
                            dueDate = state.task.dueDate,
                            dueTime = state.task.dueTime,
                            priority = state.task.priority,
                            projectName = state.projectName,
                            position = state.task.position,
                            onOpenDatePanel = {
                                openMenu = null
                                dateSubView = DateSubView.CALENDAR
                            },
                            onToggleMenu = { attr ->
                                openMenu = if (openMenu == attr) null else attr
                            },
                            onAnchorBounds = { attr, rect ->
                                attributeAnchors[attr] = rect
                            },
                            checklistEnabled = checklistEnabled,
                        )
                        // 仅已结项任务显示“重新打开”主操作位（FR-G-19 / AC-T-29）；活跃任务无底部文字按钮。
                        if (state.canReopen) {
                            Spacer(modifier = Modifier.height(NautileSpacing.space2))
                            Button(onClick = onReopen) {
                                Text("重新打开")
                            }
                        }
                    }
                    if (state.saving) {
                        Text(
                            text = "保存中…",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(
                                horizontal = if (inDateSubView) NautileSpacing.space6 else 0.dp,
                            ),
                        )
                    }
                    state.error?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(
                                    horizontal = if (inDateSubView) NautileSpacing.space6 else 0.dp,
                                )
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        }
        // 与新建共用的无遮罩锚定菜单：打开不收键盘、点菜单外或系统返回先关菜单。
        TaskAttributeMenuHost(
            openMenu = openMenu,
            anchors = attributeAnchors,
            origin = boxOrigin.value,
            rootSize = rootSize,
            priority = state.task.priority,
            projectId = state.task.projectId,
            projects = projects,
            projectDraftName = state.projectDraftName,
            projectDraftError = state.projectDraftError,
            creatingProject = state.creatingProject,
            position = state.task.position,
            onDismiss = { openMenu = null },
            onPrioritySelected = onPriorityChanged,
            onProjectSelected = onProjectChanged,
            onProjectDraftChanged = onProjectDraftChanged,
            onCreateProject = {
                awaitingProjectCreate = true
                onCreateProjectForTask()
            },
            onPositionSelected = onGtdChanged,
        )
    }
}
