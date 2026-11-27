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
import androidx.compose.material3.FilterChip
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

/**
 * 新建底部面板（S1-T09/T-071、FR-D-02、BR-69、MOT-08）。
 *
 * - 打开时先选“待办/项目”，不自动弹键盘；
 * - 待办：只有“创建”才写库；清单默认当前清单（当前为 ENDED 时用收件箱），
 *   只能在工具栏“清单”菜单中修改，ENDED 不可作为移入目标；
 * - 项目：复用既有 ProjectCommands.create；
 * - 校验失败留在面板并显示字段错误；取消/关闭不生成半成品；
 * - 下拉关闭与详情共用 [nautileSheetPullDown]：跟手 1:1，阈值 max(25% 高度, 96dp) 或 800dp/s，
 *   未过阈值回弹；输入框聚焦或起点在输入框内不触发（T-088）；
 * - 固定两行标题/详情（FR-D-03）；面板内边距 24/8/24 + 导航栏 inset（SPC-08）。
 * - G6 属性工具栏（CMP-31，R4 后四项）：依次日期/优先级/项目/清单，始终“图标＋文字”，
 *   未设置显示属性名、设置后显示摘要；详情不再是工具栏入口，固定为标题下第二行；
 *   清单只由“清单”菜单选择，不再有独立目标清单选择块（CMP-30/31、AC-D-07）；
 *   菜单为面板内浮层（无遮罩、不收键盘），日期在同一面板内切换为选择面板。
 * - 属性组件与详情面板共用 [TaskAttributeControls.kt]（CMP-37）。
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
    var openMenu by remember { mutableStateOf<TaskAttribute?>(null) }
    var datePanelOpen by remember { mutableStateOf(false) }
    val attributeAnchors = remember { mutableStateMapOf<TaskAttribute, Rect>() }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val detailFocusRequester = remember { FocusRequester() }
    // 项目菜单内“新建项目”等待新项目回填；成功后关闭菜单（CMP-34）。
    var awaitingProjectCreate by remember { mutableStateOf(false) }

    // 系统返回先关菜单/日期面板，不关闭面板（CMP-31）。
    BackHandler(enabled = visible && (openMenu != null || datePanelOpen)) {
        if (openMenu != null) openMenu = null else datePanelOpen = false
    }
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
    // R4 G4：四项入口始终“图标+文字”；未设置显示属性名，设置后显示摘要（CMP-30.1）。
    val dueSummary = dueSummaryText(state.dueDate, state.dueTime)
    val projectName = projects.firstOrNull { it.id == state.projectId }?.name
    val prioritySummary = state.priority.chineseLabel()
    val dueLabel = dueSummary ?: "日期"
    val priorityLabel = if (state.priority == Priority.NONE) "优先级" else prioritySummary
    val projectLabel = projectName ?: "项目"
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
        // 遮罩层：绘制与“点按遮罩关闭”都只在遮罩层完成（MOT-08.3、COL-10、T-088）。
        // 遮罩位于面板下方，面板区域的点按由上层 Surface 自行消费，不会被误判为点按遮罩。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = motion.scrim.value * motion.dragScrimFactor() }
                .background(scrimColor.copy(alpha = NautileStates.SCRIM_ALPHA))
                // 遮罩点按关闭：与拖动手势分层，只有未移动的点按才关闭（T-088）。
                // 按 touch slop 判定为拖动并消费，避免整段滑动被误判为点按而关闭面板。
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // 已在别处消费的按下（按钮、输入框等）不参与遮罩点按。
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
                                // 弹层关闭层级“最上层优先”：先关菜单，再关日期面板，最后才关新建面板。
                                if (!dragged && currentVisible) {
                                    when {
                                        openMenu != null -> openMenu = null
                                        datePanelOpen -> datePanelOpen = false
                                        else -> currentOnDismissRequest()
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
                                // R4 G4：删除独立目标清单选择块，清单只由工具栏“清单”菜单修改
                                // （CMP-31、AC-D-07）；showTargetPicker 字段保留兼容但不再渲染。
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
                                // R4 属性工具栏恰为四项：日期、优先级、项目、清单（CMP-30/31）。
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
                                        summary = dueLabel,
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
                                        summary = priorityLabel,
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
                                        summary = projectLabel,
                                        description = "设置项目，当前：${projectName ?: "无项目"}",
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
                                        summary = state.targetPosition.title,
                                        description = "移动到 GTD 清单，当前：" +
                                            state.targetPosition.title,
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
                anchor = attributeAnchors[menu],
                origin = boxOrigin.value,
                rootSize = rootSize,
                onDismiss = { openMenu = null },
            ) {
                when (menu) {
                    TaskAttribute.PRIORITY -> PriorityMenuContent(
                        selected = state.priority,
                        onSelect = { level ->
                            onPriorityChanged(level)
                            openMenu = null
                        },
                    )

                    TaskAttribute.PROJECT -> ProjectMenuContent(
                        selectedProjectId = state.projectId,
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
                        selected = state.targetPosition,
                        onSelect = { position ->
                            onTargetChanged(position)
                            openMenu = null
                        },
                    )

                    TaskAttribute.DETAIL -> DetailMenuContent(
                        detail = state.detail,
                        onEditDetail = {
                            openMenu = null
                            detailFocusRequester.requestFocus()
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
