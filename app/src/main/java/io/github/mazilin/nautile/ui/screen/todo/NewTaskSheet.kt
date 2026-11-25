package io.github.mazilin.nautile.ui.screen.todo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 新建底部面板（S1-T09/T-071、FR-D-02、BR-69、MOT-08）。
 *
 * - 打开时先选“待办/项目”，不自动弹键盘；
 * - 待办：只有“创建”才写库；已结束清单内添加时可选四个非 ENDED 目标清单，默认收件箱；
 * - 项目：复用既有 ProjectCommands.create；
 * - 校验失败留在面板并显示字段错误；取消/关闭不生成半成品；
 * - 下拉关闭与详情共用 [nautileSheetPullDown]：跟手 1:1，阈值 max(25% 高度, 96dp) 或 800dp/s，
 *   未过阈值回弹；输入框聚焦或起点在输入框内不触发（T-088）；
 * - 固定两行标题/详情（FR-D-03）；面板内边距 24/8/24 + 导航栏 inset（SPC-08）。
 * - G6 属性工具栏（CMP-31）：依次日期/优先级/项目/清单/详情，只图标+语义；
 *   菜单为面板内浮层（无遮罩、不收键盘），日期在同一面板内切换为选择面板。
 *
 * @param visible 进出场目标可见性；退出动效结束后才回调 [onExitFinished]。
 *   默认 true，直接组合时仍为最终静止态（测试与既有静态行为不变）。
 * @param projects 项目菜单可选项；只应传入活跃项目（调用方过滤）。
 */
@Composable
fun NewTaskSheet(
    state: NewTaskUiState,
    projects: List<Project> = emptyList(),
    onKindChanged: (NewTaskKind) -> Unit = {},
    onTargetChanged: (GtdPosition) -> Unit = {},
    onTitleChanged: (String) -> Unit = {},
    onDetailChanged: (String) -> Unit = {},
    onProjectNameChanged: (String) -> Unit = {},
    onPriorityChanged: (Priority) -> Unit = {},
    onDueDateChanged: (LocalDate?) -> Unit = {},
    onDueTimeChanged: (LocalTime?) -> Unit = {},
    onProjectChanged: (ProjectId?) -> Unit = {},
    onProjectDraftChanged: (String) -> Unit = {},
    onCreateProjectForTask: () -> Unit = {},
    onClearDetail: () -> Unit = {},
    onCreate: () -> Unit = {},
    onCreateProject: () -> Unit = {},
    onCancel: () -> Unit = {},
    onDismissRequest: () -> Unit = {},
    visible: Boolean = true,
    onExitFinished: () -> Unit = {},
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val motion = rememberSheetMotion(visible = visible, onExitFinished = onExitFinished)
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentVisible by rememberUpdatedState(visible)
    val scrollState = rememberScrollState()

    // ---- G6 属性菜单/日期面板：面板内的瞬时界面状态，不持久化 ----
    var openMenu by remember { mutableStateOf<NewTaskAttribute?>(null) }
    var datePanelOpen by remember { mutableStateOf(false) }
    val attributeAnchors = remember { mutableStateMapOf<NewTaskAttribute, Rect>() }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val detailFocusRequester = remember { FocusRequester() }
    // 项目菜单内“新建项目”等待新项目回填；成功后关闭菜单（CMP-34）。
    var awaitingProjectCreate by remember { mutableStateOf(false) }

    // 系统返回先关菜单/日期面板，不关闭面板（CMP-31）。
    BackHandler(enabled = visible && (openMenu != null || datePanelOpen)) {
        if (openMenu != null) openMenu = null else datePanelOpen = false
    }
    LaunchedEffect(openMenu) { menuSize = IntSize.Zero }
    LaunchedEffect(state.kind) {
        if (state.kind != NewTaskKind.TODO) {
            openMenu = null
            datePanelOpen = false
        }
    }
    LaunchedEffect(state.projectId) {
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
    val projectNameFocused = remember { mutableStateOf(false) }
    val titleBounds = remember { mutableStateOf<Rect?>(null) }
    val detailBounds = remember { mutableStateOf<Rect?>(null) }
    val projectNameBounds = remember { mutableStateOf<Rect?>(null) }
    val boxOrigin = remember { mutableStateOf(Offset.Zero) }

    fun inputActive(): Boolean =
        titleFocused.value || detailFocused.value || projectNameFocused.value

    fun pointInInput(pointInRoot: Offset): Boolean =
        titleBounds.value?.contains(pointInRoot) == true ||
            detailBounds.value?.contains(pointInRoot) == true ||
            projectNameBounds.value?.contains(pointInRoot) == true

    var contentPull by remember { mutableFloatStateOf(0f) }
    val contentConnection = remember(motion, scrollState, visible, density) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // 内容先滚动；到顶继续下拉才累计并拖动面板（MOT-08.3）。
                if (inputActive() ||
                    source != NestedScrollSource.UserInput ||
                    available.y <= 0f ||
                    scrollState.canScrollBackward
                ) {
                    return Offset.Zero
                }
                contentPull += available.y
                if (visible) motion.dragBy(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (visible && !inputActive() && contentPull > 0f) {
                    if (motion.shouldCommit(density, contentPull, available.y)) {
                        currentOnDismissRequest()
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

    // G6 摘要与语义文本（CMP-30.3、CMP-31）。
    val dueSummaryText = dueSummary(state)
    val projectName = projects.firstOrNull { it.id == state.projectId }?.name
    val prioritySummary = state.priority.chineseLabel()
    val priorityTint = when (state.priority) {
        Priority.HIGH -> MaterialTheme.colorScheme.error
        Priority.MEDIUM -> MaterialTheme.colorScheme.primary
        Priority.LOW -> MaterialTheme.colorScheme.secondary
        Priority.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { boxOrigin.value = it.positionInRoot() }
            .onSizeChanged { rootSize = it }
            // 遮罩点按关闭：与拖动手势分层，只有未移动的点按才关闭（T-088）。
            // 拖动被排除参与下拉时（输入框聚焦/起点在框内或拖拽柄）上层不会消费位移，
            // 这里按 touch slop 判定为拖动并消费，避免整段滑动被误判为点按而关闭面板。
            .pointerInput(Unit) {
                awaitEachGesture {
                    // 子元素已消费的按下（按钮、输入框等）不参与遮罩点按。
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
                        if (dragged) {
                            // 拖动位移在此消费，不落到点按判定。
                            change.consume()
                        }
                        if (!change.pressed) {
                            if (!dragged && currentVisible) currentOnDismissRequest()
                            break
                        }
                    }
                }
            }
            // 祖先层观察下拉，输入框聚焦/起手在输入框内时不参与（T-088）。
            .nautileSheetPullDown(
                motion = motion,
                visible = { currentVisible },
                scope = scope,
                onCommitClose = { currentOnDismissRequest() },
                pass = PointerEventPass.Initial,
                canStart = { !scrollState.canScrollBackward && !scrollState.canScrollForward },
                isExcluded = { point -> inputActive() || pointInInput(point + boxOrigin.value) },
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 遮罩 alpha 在图层中读取动效值，并随拖拽位移联动（MOT-08.3、COL-10）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = motion.scrim.value * motion.dragScrimFactor() }
                .background(scrimColor.copy(alpha = NautileStates.SCRIM_ALPHA)),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                // inset 只在此消费一次：键盘与导航栏取较大者（SPC-08、CMP-17）。
                .windowInsetsPadding(sheetInsets)
                .onSizeChanged { motion.sheetHeightPx = it.height.toFloat() }
                .graphicsLayer {
                    // 面板从自身高度滑入/滑出；高度形态不随内容变化做动画（MOT-08、MOT-12）。
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
                            visible = { currentVisible },
                            scope = scope,
                            onCommitClose = { currentOnDismissRequest() },
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
                        .fillMaxWidth()
                        .padding(
                            start = NautileSpacing.space6,
                            end = NautileSpacing.space6,
                            top = NautileSpacing.space2,
                            bottom = NautileSpacing.space6,
                        )
                        // 200% 字体下内容可滚动，不靠固定高度裁切（A11Y-13、A11Y-25）。
                        .verticalScroll(scrollState)
                        .nestedScroll(contentConnection),
                ) {
                    Text("新建", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.padding(top = NautileSpacing.space1),
                        horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space2),
                    ) {
                        FilterChip(
                            selected = state.kind == NewTaskKind.TODO,
                            onClick = { onKindChanged(NewTaskKind.TODO) },
                            label = { Text("待办") },
                        )
                        FilterChip(
                            selected = state.kind == NewTaskKind.PROJECT,
                            onClick = { onKindChanged(NewTaskKind.PROJECT) },
                            label = { Text("项目") },
                        )
                    }

                    when (state.kind) {
                        NewTaskKind.TODO -> {
                            if (datePanelOpen) {
                                // 日期为同一面板内的选择面板，不叠第二层遮罩（CMP-31.2、CMP-32）。
                                DateAttributePanel(
                                    dueDate = state.dueDate,
                                    dueTime = state.dueTime,
                                    onDueDateChanged = onDueDateChanged,
                                    onDueTimeChanged = onDueTimeChanged,
                                    onBack = { datePanelOpen = false },
                                )
                            } else {
                                if (state.showTargetPicker) {
                                    Text(
                                        text = "目标清单（已结束不可选）",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(top = NautileSpacing.space2),
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(
                                            NautileSpacing.space2,
                                        ),
                                    ) {
                                        GtdPosition.entries
                                            .filter { it != GtdPosition.ENDED }
                                            .forEach { position ->
                                                FilterChip(
                                                    selected = state.targetPosition == position,
                                                    onClick = { onTargetChanged(position) },
                                                    label = { Text(position.title) },
                                                )
                                            }
                                    }
                                } else {
                                    Text(
                                        text = "清单：${state.targetPosition.title}",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                Spacer(modifier = Modifier.height(NautileSpacing.space2))
                                OutlinedTextField(
                                    value = state.title,
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
                                    keyboardActions = KeyboardActions(onDone = { onCreate() }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned { titleBounds.value = it.boundsInRoot() }
                                        .onFocusChanged { titleFocused.value = it.isFocused },
                                )
                                // 固定第二行：详情小字行，始终显示，不提供展开/隐藏（FR-D-03）。
                                OutlinedTextField(
                                    value = state.detail,
                                    onValueChange = onDetailChanged,
                                    label = { Text("详情（可选）") },
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(detailFocusRequester)
                                        .onGloballyPositioned { detailBounds.value = it.boundsInRoot() }
                                        .onFocusChanged { detailFocused.value = it.isFocused }
                                        .testTag("new_task_detail"),
                                )
                                // G6 属性工具栏：日期、优先级、项目、清单、详情（CMP-30/31）。
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
                                        summary = dueSummaryText,
                                        description = "设置截止日期，当前：" +
                                            (dueSummaryText ?: "未设置"),
                                        onActivate = {
                                            openMenu = null
                                            datePanelOpen = true
                                        },
                                        onBounds = {
                                            attributeAnchors[NewTaskAttribute.DATE] = it
                                        },
                                    )
                                    AttributeToolbarItem(
                                        iconRes = R.drawable.ic_flag,
                                        summary = prioritySummary,
                                        description = "设置优先级，当前：$prioritySummary",
                                        iconTint = priorityTint,
                                        onActivate = {
                                            openMenu = if (openMenu == NewTaskAttribute.PRIORITY) {
                                                null
                                            } else {
                                                NewTaskAttribute.PRIORITY
                                            }
                                        },
                                        onBounds = {
                                            attributeAnchors[NewTaskAttribute.PRIORITY] = it
                                        },
                                    )
                                    AttributeToolbarItem(
                                        iconRes = R.drawable.ic_folder,
                                        summary = projectName,
                                        description = "设置项目，当前：${projectName ?: "无项目"}",
                                        onActivate = {
                                            openMenu = if (openMenu == NewTaskAttribute.PROJECT) {
                                                null
                                            } else {
                                                NewTaskAttribute.PROJECT
                                            }
                                        },
                                        onBounds = {
                                            attributeAnchors[NewTaskAttribute.PROJECT] = it
                                        },
                                    )
                                    AttributeToolbarItem(
                                        iconRes = R.drawable.ic_checklist,
                                        summary = state.targetPosition.title,
                                        description = "移动到 GTD 清单，当前：" +
                                            state.targetPosition.title,
                                        onActivate = {
                                            openMenu = if (openMenu == NewTaskAttribute.CHECKLIST) {
                                                null
                                            } else {
                                                NewTaskAttribute.CHECKLIST
                                            }
                                        },
                                        onBounds = {
                                            attributeAnchors[NewTaskAttribute.CHECKLIST] = it
                                        },
                                    )
                                    AttributeToolbarItem(
                                        iconRes = R.drawable.ic_notes,
                                        summary = null,
                                        description = if (state.detail.isBlank()) {
                                            "编辑详情，当前为空"
                                        } else {
                                            "编辑详情"
                                        },
                                        onActivate = {
                                            openMenu = if (openMenu == NewTaskAttribute.DETAIL) {
                                                null
                                            } else {
                                                NewTaskAttribute.DETAIL
                                            }
                                        },
                                        onBounds = {
                                            attributeAnchors[NewTaskAttribute.DETAIL] = it
                                        },
                                    )
                                }
                            }
                        }

                        NewTaskKind.PROJECT -> {
                            Spacer(modifier = Modifier.height(NautileSpacing.space2))
                            OutlinedTextField(
                                value = state.projectName,
                                onValueChange = onProjectNameChanged,
                                label = { Text("项目名称") },
                                singleLine = true,
                                isError = state.projectNameInvalid,
                                supportingText = if (state.projectNameInvalid) {
                                    { Text("项目名称不能为空") }
                                } else {
                                    null
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { onCreateProject() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned {
                                        projectNameBounds.value = it.boundsInRoot()
                                    }
                                    .onFocusChanged { projectNameFocused.value = it.isFocused },
                            )
                        }
                    }

                    state.error?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.saving) {
                        Text("创建中…", style = MaterialTheme.typography.labelSmall)
                    }
                    if (!datePanelOpen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = NautileSpacing.space2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = onCancel,
                                enabled = !state.saving,
                            ) {
                                Text("取消")
                            }
                            Button(
                                onClick = if (state.kind == NewTaskKind.TODO) {
                                    onCreate
                                } else {
                                    onCreateProject
                                },
                                enabled = !state.saving,
                            ) {
                                Text(if (state.kind == NewTaskKind.TODO) "创建" else "创建项目")
                            }
                        }
                    }
                }
            }
        }
        // G6 锚定菜单：面板内浮层，透明点按层关闭菜单，不加遮罩、不收键盘（CMP-31.1）。
        val menu = openMenu
        if (menu != null) {
            AttributeMenuOverlay(
                menu = menu,
                anchor = attributeAnchors[menu],
                origin = boxOrigin.value,
                rootSize = rootSize,
                state = state,
                projects = projects,
                onDismiss = { openMenu = null },
                onPriorityChanged = onPriorityChanged,
                onProjectChanged = onProjectChanged,
                onProjectDraftChanged = onProjectDraftChanged,
                onCreateProject = {
                    awaitingProjectCreate = true
                    onCreateProjectForTask()
                },
                onTargetChanged = onTargetChanged,
                onEditDetail = {
                    openMenu = null
                    detailFocusRequester.requestFocus()
                },
                onClearDetail = {
                    openMenu = null
                    onClearDetail()
                },
            )
        }
    }
}

// ---- G6 属性工具栏与锚定菜单（CMP-30—CMP-37） ----

/** 属性入口顺序固定：日期、优先级、项目、清单、详情（CMP-30）。 */
private enum class NewTaskAttribute { DATE, PRIORITY, PROJECT, CHECKLIST, DETAIL }

private val MenuWidth = 248.dp
private val MenuMaxHeight = 320.dp
private val MenuShadow = 3.dp
private val NewTaskTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 工具栏单项：图标 24dp、触控 ≥48dp、摘要单行省略，按钮合并子节点语义。 */
@Composable
private fun AttributeToolbarItem(
    iconRes: Int,
    summary: String?,
    description: String,
    onActivate: () -> Unit,
    onBounds: (Rect) -> Unit,
    iconTint: Color = LocalContentColor.current,
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onActivate)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            // 图标自身不重复朗读，由按钮提供合并后的完整语义（CMP-30.3）。
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = description) {
                    onActivate()
                    true
                }
            }
            .padding(horizontal = NautileSpacing.space1, vertical = NautileSpacing.space1),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 96.dp),
                )
            }
        }
    }
}

/**
 * 锚定菜单浮层：透明点按层关闭菜单，不改变面板高度、不打断输入焦点（CMP-31.1）。
 * 菜单在触发图标上方展开，超出面板高度时内部滚动。
 */
@Composable
private fun AttributeMenuOverlay(
    menu: NewTaskAttribute,
    anchor: Rect?,
    origin: Offset,
    rootSize: IntSize,
    state: NewTaskUiState,
    projects: List<Project>,
    onDismiss: () -> Unit,
    onPriorityChanged: (Priority) -> Unit,
    onProjectChanged: (ProjectId?) -> Unit,
    onProjectDraftChanged: (String) -> Unit,
    onCreateProject: () -> Unit,
    onTargetChanged: (GtdPosition) -> Unit,
    onEditDetail: () -> Unit,
    onClearDetail: () -> Unit,
) {
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier = Modifier.fillMaxSize()) {
        // 透明点按层：点菜单外关闭菜单，不写状态、不收键盘。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        if (anchor != null) {
            val gap = with(LocalDensity.current) { NautileSpacing.space1.roundToPx() }
            Surface(
                modifier = Modifier
                    .width(MenuWidth)
                    .heightIn(max = MenuMaxHeight)
                    .offset {
                        val x = (anchor.left - origin.x).toInt()
                            .coerceIn(0, (rootSize.width - menuSize.width).coerceAtLeast(0))
                        val y = (anchor.top - origin.y - menuSize.height - gap)
                            .toInt()
                            .coerceAtLeast(0)
                        IntOffset(x, y)
                    }
                    .onSizeChanged { menuSize = it }
                    // 首帧尚未测量时先隐藏，避免菜单从触发图标位置跳到目标位置。
                    .alpha(if (menuSize.height == 0) 0f else 1f),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = MenuShadow,
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .selectableGroup()
                        .padding(vertical = NautileSpacing.space1),
                ) {
                    when (menu) {
                        NewTaskAttribute.PRIORITY -> PriorityMenuContent(
                            selected = state.priority,
                            onSelect = { level ->
                                onPriorityChanged(level)
                                onDismiss()
                            },
                        )

                        NewTaskAttribute.PROJECT -> ProjectMenuContent(
                            state = state,
                            projects = projects,
                            onProjectChanged = { id ->
                                onProjectChanged(id)
                                onDismiss()
                            },
                            onProjectDraftChanged = onProjectDraftChanged,
                            onCreateProject = onCreateProject,
                        )

                        NewTaskAttribute.CHECKLIST -> ChecklistMenuContent(
                            selected = state.targetPosition,
                            onSelect = { position ->
                                onTargetChanged(position)
                                onDismiss()
                            },
                        )

                        NewTaskAttribute.DETAIL -> DetailMenuContent(
                            detail = state.detail,
                            onEditDetail = onEditDetail,
                            onClearDetail = onClearDetail,
                        )

                        NewTaskAttribute.DATE -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun AttributeMenuItem(
    text: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = description }
            .alpha(if (enabled) 1f else NautileStates.DISABLED_CONTENT_ALPHA)
            .padding(horizontal = NautileSpacing.space3, vertical = NautileSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space2),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
            supporting?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PriorityMenuContent(selected: Priority, onSelect: (Priority) -> Unit) {
    val order = listOf(Priority.HIGH, Priority.MEDIUM, Priority.LOW, Priority.NONE)
    order.forEach { level ->
        val tint = when (level) {
            Priority.HIGH -> MaterialTheme.colorScheme.error
            Priority.MEDIUM -> MaterialTheme.colorScheme.primary
            Priority.LOW -> MaterialTheme.colorScheme.secondary
            Priority.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        AttributeMenuItem(
            text = level.chineseLabel(),
            description = "优先级：${level.chineseLabel()}",
            selected = selected == level,
            onClick = { onSelect(level) },
            leading = {
                Icon(
                    painter = painterResource(R.drawable.ic_flag),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
            },
        )
    }
}

@Composable
private fun ProjectMenuContent(
    state: NewTaskUiState,
    projects: List<Project>,
    onProjectChanged: (ProjectId?) -> Unit,
    onProjectDraftChanged: (String) -> Unit,
    onCreateProject: () -> Unit,
) {
    var showCreateForm by remember { mutableStateOf(false) }
    AttributeMenuItem(
        text = "无项目",
        description = if (state.projectId == null) "项目：无项目，已选择" else "项目：无项目",
        selected = state.projectId == null,
        onClick = { onProjectChanged(null) },
    )
    projects.forEach { project ->
        AttributeMenuItem(
            text = project.name,
            description = "项目：${project.name}",
            selected = state.projectId == project.id,
            onClick = { onProjectChanged(project.id) },
            leading = {
                Icon(
                    painter = painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
        )
    }
    AttributeMenuItem(
        text = "新建项目",
        description = "新建项目",
        selected = false,
        onClick = { showCreateForm = !showCreateForm },
        leading = {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
    )
    if (showCreateForm) {
        Column(
            modifier = Modifier.padding(
                start = NautileSpacing.space3,
                end = NautileSpacing.space3,
                bottom = NautileSpacing.space2,
            ),
        ) {
            OutlinedTextField(
                value = state.projectDraftName,
                onValueChange = onProjectDraftChanged,
                label = { Text("项目名称") },
                singleLine = true,
                isError = state.projectDraftError != null,
                supportingText = state.projectDraftError?.let { message ->
                    { Text(message) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { showCreateForm = false },
                    enabled = !state.creatingProject,
                ) {
                    Text("取消")
                }
                Button(
                    onClick = onCreateProject,
                    enabled = !state.creatingProject && state.projectDraftName.isNotBlank(),
                ) {
                    Text("创建")
                }
            }
        }
    }
}

@Composable
private fun ChecklistMenuContent(
    selected: GtdPosition,
    onSelect: (GtdPosition) -> Unit,
) {
    GtdPosition.entries.forEach { position ->
        val ended = position == GtdPosition.ENDED
        AttributeMenuItem(
            text = position.title,
            description = if (ended) {
                "已结束，已结束不可直接移入"
            } else {
                "移动到 GTD 清单：${position.title}"
            },
            selected = selected == position,
            enabled = !ended,
            supporting = if (ended) "已结束不可直接移入" else null,
            onClick = { onSelect(position) },
        )
    }
}

@Composable
private fun DetailMenuContent(
    detail: String,
    onEditDetail: () -> Unit,
    onClearDetail: () -> Unit,
) {
    AttributeMenuItem(
        text = "编辑详情",
        description = "编辑详情",
        selected = false,
        onClick = onEditDetail,
        leading = {
            Icon(
                painter = painterResource(R.drawable.ic_notes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
    )
    AttributeMenuItem(
        text = "清空详情",
        description = if (detail.isBlank()) "清空详情，当前为空" else "清空详情",
        selected = false,
        onClick = onClearDetail,
    )
}

/**
 * 日期选择面板（同一底部面板内切换，CMP-31.2、CMP-32）。
 *
 * 先选日期才可填时刻；选择立即回调；日期面板不设“确定”总开关，“清除”清空日期与时刻。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateAttributePanel(
    dueDate: LocalDate?,
    dueTime: LocalTime?,
    onDueDateChanged: (LocalDate?) -> Unit,
    onDueTimeChanged: (LocalTime?) -> Unit,
    onBack: () -> Unit,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dueDate?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli(),
    )
    LaunchedEffect(datePickerState.selectedDateMillis) {
        val millis = datePickerState.selectedDateMillis ?: return@LaunchedEffect
        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        if (picked != dueDate) onDueDateChanged(picked)
    }
    var timeText by remember { mutableStateOf(dueTime?.format(NewTaskTimeFormatter) ?: "") }
    LaunchedEffect(dueTime) {
        timeText = dueTime?.format(NewTaskTimeFormatter) ?: ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = NautileSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "选择截止日期",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onBack) { Text("返回") }
    }
    // 给定有界高度，日期组件内部自行滚动，不与外层滚动冲突。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 320.dp, max = 420.dp),
    ) {
        DatePicker(
            state = datePickerState,
            showModeToggle = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value = timeText,
        onValueChange = { text ->
            timeText = text
            val parsed = parseTimeOrNull(text)
            if (parsed != null) {
                onDueTimeChanged(parsed)
            } else if (text.isBlank()) {
                onDueTimeChanged(null)
            }
        },
        label = { Text("时间（HH:mm，可选）") },
        singleLine = true,
        enabled = dueDate != null,
        supportingText = {
            Text(
                if (dueDate == null) "先选择日期，再填写时间" else "留空表示只设日期",
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        TextButton(
            onClick = {
                onDueDateChanged(null)
                onDueTimeChanged(null)
            },
            enabled = dueDate != null,
        ) {
            Text("清除截止日期")
        }
    }
}

private fun Priority.chineseLabel(): String = when (this) {
    Priority.HIGH -> "高"
    Priority.MEDIUM -> "中"
    Priority.LOW -> "低"
    Priority.NONE -> "无"
}

/** 日期摘要：今天/明天/“M月d日”，有时刻追加“ HH:mm”（CMP-32）。 */
private fun dueSummary(state: NewTaskUiState): String? {
    val date = state.dueDate ?: return null
    val today = LocalDate.now()
    val label = when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> "${date.monthValue}月${date.dayOfMonth}日"
    }
    val time = state.dueTime?.format(NewTaskTimeFormatter)
    return if (time == null) label else "$label $time"
}

private fun parseTimeOrNull(text: String): LocalTime? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return runCatching { LocalTime.of(hour, minute) }.getOrNull()
}
