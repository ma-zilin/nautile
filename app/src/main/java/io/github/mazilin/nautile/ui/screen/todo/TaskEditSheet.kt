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
 * 任务编辑面板（R4 G5、FR-D-02/03、CMP-36/37.1、AC-D-07）。
 *
 * - 与新建面板同构：标题、固定第二行详情、四项属性工具栏（日期/优先级/项目/清单）、取消/保存；
 * - 打开时由 [TaskEditUiState] 预填当前值；点“保存”一次提交全部改动，失败保留输入；
 * - 标题无效阻止关闭并提示；GTD 清单 ENDED 不可选、已结项任务入口禁用（CMP-35）；
 * - 返回顺序：先关菜单/日期面板，再关编辑面板，不直接关详情；
 * - 遮罩 32%、顶部 28dp 圆角、22dp 拖拽柄区、24/8/24 + 导航栏 inset 沿用 G4/G6；
 * - 下拉跟手 1:1，阈值 max(25% 高度, 96dp) 或 800dp/s，未过阈值回弹；退出动效结束后才移除。
 *
 * @param visible 进出场目标可见性；退出动效结束后才回调 [onExitFinished]。
 * @param projects 项目菜单可选项；调用方只传活跃项目。
 */
@Composable
fun TaskEditSheet(
    state: TaskEditUiState,
    projects: List<Project> = emptyList(),
    onTitleChanged: (String) -> Unit = {},
    onDetailChanged: (String) -> Unit = {},
    onPriorityChanged: (Priority) -> Unit = {},
    onDueDateChanged: (LocalDate?) -> Unit = {},
    onDueTimeChanged: (LocalTime?) -> Unit = {},
    onProjectChanged: (ProjectId?) -> Unit = {},
    onProjectDraftChanged: (String) -> Unit = {},
    onCreateProjectForTask: () -> Unit = {},
    onPositionChanged: (GtdPosition) -> Unit = {},
    onSave: () -> Unit = {},
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

    // 面板内瞬时界面状态：属性菜单、日期选择面板，不持久化（CMP-31）。
    var openMenu by remember { mutableStateOf<TaskAttribute?>(null) }
    var datePanelOpen by remember { mutableStateOf(false) }
    val attributeAnchors = remember { mutableStateMapOf<TaskAttribute, Rect>() }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val detailFocusRequester = remember { FocusRequester() }
    var awaitingProjectCreate by remember { mutableStateOf(false) }

    // 返回先关菜单/日期面板，再关编辑面板；详情保持（返回顺序：菜单→编辑面板→详情）。
    BackHandler(enabled = visible) {
        when {
            openMenu != null -> openMenu = null
            datePanelOpen -> datePanelOpen = false
            else -> currentOnDismissRequest()
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

    // T-088：输入框聚焦或手势起点落在输入框内时不触发遮罩/内容下拉关闭。
    val titleFocused = remember { mutableStateOf(false) }
    val detailFocused = remember { mutableStateOf(false) }
    val titleBounds = remember { mutableStateOf<Rect?>(null) }
    val detailBounds = remember { mutableStateOf<Rect?>(null) }
    val boxOrigin = remember { mutableStateOf(Offset.Zero) }

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

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
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

    val dueSummary = dueSummaryText(state.dueDate, state.dueTime)
    val prioritySummary = state.priority.chineseLabel()
    val dueLabel = dueSummary ?: "日期"
    val priorityLabel = if (state.priority == Priority.NONE) "优先级" else prioritySummary
    val projectLabel = state.projectName ?: "项目"
    val priorityTint = when (state.priority) {
        Priority.HIGH -> MaterialTheme.colorScheme.error
        Priority.MEDIUM -> MaterialTheme.colorScheme.primary
        Priority.LOW -> MaterialTheme.colorScheme.secondary
        Priority.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val checklistDescription = if (state.closed) {
        "已结项，请在详情重新打开"
    } else {
        "移动到 GTD 清单，当前：${state.position.title}"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { boxOrigin.value = it.positionInRoot() }
            .onSizeChanged { rootSize = it }
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
        // 点按顺序与系统返回同规则：菜单→日期面板→编辑面板。
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
                        .verticalScroll(scrollState)
                        .nestedScroll(contentConnection),
                ) {
                    Text("编辑任务", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(NautileSpacing.space2))
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
                            keyboardActions = KeyboardActions(onDone = { onSave() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { titleBounds.value = it.boundsInRoot() }
                                .onFocusChanged { titleFocused.value = it.isFocused }
                                .testTag("task_edit_title"),
                        )
                        // 固定第二行：详情小字行，始终显示（FR-D-03、CMP-36）。
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
                                .testTag("task_edit_detail"),
                        )
                        // 四项属性：日期、优先级、项目、清单，始终“图标+文字”（CMP-30/31）。
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = NautileSpacing.space2)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space2),
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
                                summary = state.position.title,
                                description = checklistDescription,
                                enabled = !state.closed,
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

                    state.error?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.saving) {
                        Text("保存中…", style = MaterialTheme.typography.labelSmall)
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
                                onClick = onDismissRequest,
                                enabled = !state.saving,
                            ) {
                                Text("取消")
                            }
                            Button(
                                onClick = onSave,
                                enabled = !state.saving,
                            ) {
                                Text("保存")
                            }
                        }
                    }
                }
            }
        }
        // 锚定菜单：面板内浮层，透明点按层关闭菜单，不加遮罩、不收键盘（CMP-31.1）。
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
                        selected = state.position,
                        entryEnabled = !state.closed,
                        disabledSupporting = "已结项，请在详情重新打开",
                        onSelect = { position ->
                            onPositionChanged(position)
                            openMenu = null
                        },
                    )

                    TaskAttribute.DETAIL -> Unit
                    TaskAttribute.DATE -> Unit
                }
            }
        }
    }
}
