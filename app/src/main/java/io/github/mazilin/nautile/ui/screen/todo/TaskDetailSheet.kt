package io.github.mazilin.nautile.ui.screen.todo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
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
 * 任务半屏详情（S1-T08、AC-D-06/08、AC-T-29、MOT-08；G7 属性编辑）。
 *
 * 无整页保存按钮：标题在字段结束/IME 完成时提交；无效标题由父层阻止离开并提示。
 * 下滑关闭与关闭按钮都先经父层 canLeave 检查；本组件不自行修改业务数据。
 *
 * G7：标题、详情之下是与新建面板相同的五图标属性工具栏（CMP-30—CMP-37）：
 * 日期在面板内切换为选择面板，优先级/项目/清单/详情打开锚定菜单；
 * 选择/清除立即回调（编辑即保存），摘要显示当前值；系统返回先关菜单再关面板。
 *
 * 视觉按 SPC-08/CMP-17：surfaceContainerLow、顶部 28dp 圆角、22dp 拖拽柄区、
 * 内容内边距左右 24dp、顶部 8dp、底部 24dp + 导航栏 inset（面板唯一消费 inset）；
 * 下拉跟手 1:1，阈值 max(25% 高度, 96dp) 或速度 800dp/s，未过阈值用 BoundaryRebound 回弹；
 * 退出动效结束后才回调 [onExitFinished]（CMP-19）。
 *
 * @param visible 进出场目标可见性；默认 true，直接组合时仍为最终静止态。
 * @param projects 项目菜单可选项；只应传入活跃项目（调用方过滤）。
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
    onClearDetail: () -> Unit = {},
    onOpenEdit: () -> Unit = {},
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val motion = rememberSheetMotion(visible = visible, onExitFinished = onExitFinished)
    val currentVisible by rememberUpdatedState(visible)
    val currentOnCloseRequested by rememberUpdatedState(onCloseRequested)

    // ---- G7 属性菜单/日期面板：面板内的瞬时界面状态，不持久化 ----
    var openMenu by remember { mutableStateOf<TaskAttribute?>(null) }
    var datePanelOpen by remember { mutableStateOf(false) }
    val attributeAnchors = remember { mutableStateMapOf<TaskAttribute, Rect>() }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val detailFocusRequester = remember { FocusRequester() }
    val detailBringIntoView = remember { BringIntoViewRequester() }
    // 项目菜单内“新建项目”等待新项目回填；成功后关闭菜单（CMP-34）。
    var awaitingProjectCreate by remember { mutableStateOf(false) }

    // 系统返回先关菜单/日期面板，不关闭面板（CMP-31）。
    BackHandler(enabled = visible && (openMenu != null || datePanelOpen)) {
        if (openMenu != null) openMenu = null else datePanelOpen = false
    }
    // 编辑面板打开时详情让位（单宿主切换）；关闭残留的菜单/日期面板，避免回来后停在子面板。
    LaunchedEffect(visible) {
        if (!visible) {
            openMenu = null
            datePanelOpen = false
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
                // T-088：输入框起手/聚焦的手势不累计，避免输入框内拖动误关。
                if (gestureFromInput.value ||
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
                if (visible && !gestureFromInput.value && contentPull > 0f) {
                    if (motion.shouldCommit(density, contentPull, available.y)) {
                        onCloseRequested()
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

    // G7 摘要与语义文本（CMP-30.3、CMP-37）：与新建面板同一套格式。
    val dueSummary = dueSummaryText(state.task.dueDate, state.task.dueTime)
    val prioritySummary = state.task.priority.chineseLabel()
    val priorityTint = when (state.task.priority) {
        Priority.HIGH -> MaterialTheme.colorScheme.error
        Priority.MEDIUM -> MaterialTheme.colorScheme.primary
        Priority.LOW -> MaterialTheme.colorScheme.secondary
        Priority.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // 已结项任务的 GTD 清单入口禁用并说明原因（CMP-35）。
    val checklistEnabled = state.task.closedOutcome == null
    val checklistDescription = if (checklistEnabled) {
        "移动到 GTD 清单，当前：${state.task.position.title}"
    } else {
        "已结项，请在详情重新打开"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { boxOrigin.value = it.positionInRoot() }
            .onSizeChanged { rootSize = it }
            // 祖先层 Initial pass 观察下拉，不与内容 verticalScroll 竞争；
            // 仅内容不可滚动时进入拖拽，不遮挡输入框/按钮；拖拽柄由自身手势处理，避免双重位移。
            // T-088：输入框聚焦或起点在输入框内时整段手势不参与下拉关闭。
            .nautileSheetPullDown(
                motion = motion,
                visible = { currentVisible },
                scope = scope,
                onCommitClose = onCloseRequested,
                pass = PointerEventPass.Initial,
                canStart = {
                    !scrollState.canScrollBackward && !scrollState.canScrollForward
                },
                isExcluded = { point ->
                    val excluded = inputActive() ||
                        pointInInput(point + boxOrigin.value) ||
                        handleBounds.value?.contains(point + boxOrigin.value) == true
                    gestureFromInput.value = excluded
                    excluded
                },
            ),
    ) {
        // 遮罩层：绘制与“点按遮罩关闭”都只在遮罩层完成（MOT-08.3、COL-10、T-088）。
        // 遮罩位于面板下方，面板区域的点按由上层 Surface 自行消费，不会被误判为点按遮罩。
        // 只有未移动的点按才关闭；拖动被排除参与下拉时（输入框聚焦/起点在框内）上层不消费位移，
        // 这里按 touch slop 判定为拖动并消费，避免整段滑动被误判为点按而关闭面板。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = motion.scrim.value * motion.dragScrimFactor() }
                .background(scrimColor.copy(alpha = NautileStates.SCRIM_ALPHA))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // 遮罩上的按下；已在别处消费的按下（按钮、输入框等）不参与点按判定。
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
                                // 点按遮罩关闭（§15 CMP-46）：先关菜单/日期面板，再关详情（T-088）。
                                if (!dragged && currentVisible) {
                                    when {
                                        openMenu != null -> openMenu = null
                                        datePanelOpen -> datePanelOpen = false
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
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // inset 只在此消费一次：键盘与导航栏取较大者（SPC-08、CMP-17）。
                .windowInsetsPadding(sheetInsets)
                .fillMaxHeight(0.6f)
                .onSizeChanged { motion.sheetHeightPx = it.height.toFloat() }
                .graphicsLayer {
                    // 面板从自身高度滑入/滑出；60% 高度与 28dp 圆角形态保持（MOT-08、MOT-12）。
                    translationY = (1f - motion.reveal.value) * size.height + motion.dragOffsetPx
                },
            shape = sheetShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(NautileSheetMetrics.HANDLE_AREA_DP)
                        .onGloballyPositioned { handleBounds.value = it.boundsInRoot() }
                        .nautileSheetPullDown(
                            motion = motion,
                            visible = { currentVisible },
                            scope = scope,
                            onCommitClose = onCloseRequested,
                            // 输入框聚焦时拖拽柄也不参与下拉（T-088）。
                            isExcluded = { inputActive() },
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
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(
                            start = NautileSpacing.space6,
                            end = NautileSpacing.space6,
                            top = NautileSpacing.space2,
                            bottom = NautileSpacing.space6,
                        )
                        .verticalScroll(scrollState)
                        .nestedScroll(contentConnection),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "任务详情",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        // 头部“编辑”入口（CMP-37.1）：打开与创建同构的编辑面板。
                        IconButton(onClick = onOpenEdit) {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = "编辑任务",
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(NautileSpacing.space2))
                    if (datePanelOpen) {
                        // 日期为同一面板内的选择面板，不叠第二层遮罩（CMP-31.2、CMP-32）。
                        DateAttributePanel(
                            dueDate = state.task.dueDate,
                            dueTime = state.task.dueTime,
                            onDueDateChanged = onDueDateChanged,
                            onDueTimeChanged = onDueTimeChanged,
                            onBack = { datePanelOpen = false },
                        )
                        state.error?.let { message ->
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = state.titleValue,
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
                            keyboardActions = KeyboardActions(onDone = { onCommitTitle() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { titleBounds.value = it.boundsInRoot() }
                                .onFocusChanged { focus ->
                                    titleFocused.value = focus.isFocused
                                    if (!focus.isFocused && state.titleDraft != null) onCommitTitle()
                                },
                        )
                        // 详情紧跟标题：多行自由文本，编辑即保存（FR-D-03、AC-D-09）。
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
                                .focusRequester(detailFocusRequester)
                                .bringIntoViewRequester(detailBringIntoView)
                                .onGloballyPositioned { detailBounds.value = it.boundsInRoot() }
                                .onFocusChanged { focus ->
                                    detailFocused.value = focus.isFocused
                                    if (!focus.isFocused && state.detailDraft != null) onCommitDetail()
                                }
                                .testTag("task_detail_detail"),
                        )
                        // G7 属性工具栏：与新建面板同一套五图标与摘要（CMP-30/31、CMP-37）。
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = NautileSpacing.space2)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(
                                NautileSpacing.space2,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AttributeToolbarItem(
                                iconRes = R.drawable.ic_calendar_today,
                                summary = dueSummary,
                                description = "设置截止日期，当前：" +
                                    (dueSummary ?: "未设置"),
                                onActivate = {
                                    openMenu = null
                                    datePanelOpen = true
                                },
                                onBounds = {
                                    attributeAnchors[TaskAttribute.DATE] = it
                                },
                            )
                            AttributeToolbarItem(
                                iconRes = R.drawable.ic_flag,
                                summary = prioritySummary,
                                description = "设置优先级，当前：$prioritySummary",
                                iconTint = priorityTint,
                                onActivate = {
                                    openMenu = if (openMenu == TaskAttribute.PRIORITY) {
                                        null
                                    } else {
                                        TaskAttribute.PRIORITY
                                    }
                                },
                                onBounds = {
                                    attributeAnchors[TaskAttribute.PRIORITY] = it
                                },
                            )
                            AttributeToolbarItem(
                                iconRes = R.drawable.ic_folder,
                                summary = state.projectName,
                                description = "设置项目，当前：${state.projectName ?: "无项目"}",
                                onActivate = {
                                    openMenu = if (openMenu == TaskAttribute.PROJECT) {
                                        null
                                    } else {
                                        TaskAttribute.PROJECT
                                    }
                                },
                                onBounds = {
                                    attributeAnchors[TaskAttribute.PROJECT] = it
                                },
                            )
                            AttributeToolbarItem(
                                iconRes = R.drawable.ic_checklist,
                                summary = state.task.position.title,
                                description = checklistDescription,
                                enabled = checklistEnabled,
                                onActivate = {
                                    openMenu = if (openMenu == TaskAttribute.CHECKLIST) {
                                        null
                                    } else {
                                        TaskAttribute.CHECKLIST
                                    }
                                },
                                onBounds = {
                                    attributeAnchors[TaskAttribute.CHECKLIST] = it
                                },
                            )
                            AttributeToolbarItem(
                                iconRes = R.drawable.ic_notes,
                                summary = null,
                                description = if (state.detailValue.isBlank()) {
                                    "编辑详情，当前为空"
                                } else {
                                    "编辑详情"
                                },
                                onActivate = {
                                    openMenu = if (openMenu == TaskAttribute.DETAIL) {
                                        null
                                    } else {
                                        TaskAttribute.DETAIL
                                    }
                                },
                                onBounds = {
                                    attributeAnchors[TaskAttribute.DETAIL] = it
                                },
                            )
                        }
                        if (state.canReopen) {
                            Spacer(modifier = Modifier.height(NautileSpacing.space2))
                            Button(onClick = onReopen) {
                                Text("重新打开")
                            }
                        }
                        if (state.saving) {
                            Text("保存中…", style = MaterialTheme.typography.labelSmall)
                        }
                        state.error?.let { message ->
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(modifier = Modifier.height(NautileSpacing.space2))
                        TextButton(
                            onClick = onCloseRequested,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("关闭")
                        }
                    }
                }
            }
        }
        // G7 锚定菜单：面板内浮层，透明点按层关闭菜单，不加遮罩、不收键盘（CMP-31.1）。
        val menu = openMenu
        if (menu != null) {
            AttributeMenuOverlay(
                anchor = attributeAnchors[menu],
                origin = boxOrigin.value,
                rootSize = rootSize,
                onDismiss = { openMenu = null },
            ) {
                when (menu) {
                    TaskAttribute.PRIORITY -> PriorityMenuContent(
                        selected = state.task.priority,
                        onSelect = { level ->
                            onPriorityChanged(level)
                            openMenu = null
                        },
                    )

                    TaskAttribute.PROJECT -> ProjectMenuContent(
                        selectedProjectId = state.task.projectId,
                        projects = projects,
                        draftName = state.projectDraftName,
                        draftError = state.projectDraftError,
                        creating = state.creatingProject,
                        onProjectSelected = { id ->
                            onProjectChanged(id)
                            openMenu = null
                        },
                        onDraftChanged = onProjectDraftChanged,
                        onCreateProject = {
                            awaitingProjectCreate = true
                            onCreateProjectForTask()
                        },
                    )

                    TaskAttribute.CHECKLIST -> ChecklistMenuContent(
                        selected = state.task.position,
                        onSelect = { position ->
                            onGtdChanged(position)
                            openMenu = null
                        },
                    )

                    TaskAttribute.DETAIL -> DetailMenuContent(
                        detail = state.detailValue,
                        onEditDetail = {
                            openMenu = null
                            scope.launch {
                                detailFocusRequester.requestFocus()
                                detailBringIntoView.bringIntoView()
                            }
                        },
                        onClearDetail = {
                            openMenu = null
                            onClearDetail()
                        },
                    )

                    TaskAttribute.DATE -> Unit
                }
            }
        }
    }
}
