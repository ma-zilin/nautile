package io.github.mazilin.nautile.ui.screen.todo

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ViewClassification
import io.github.mazilin.nautile.domain.model.ViewOptions
import io.github.mazilin.nautile.domain.model.ViewSort
import io.github.mazilin.nautile.domain.rule.StrictTimeParseResult
import io.github.mazilin.nautile.domain.rule.TaskTimeRules
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.theme.NautileMotion
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import io.github.mazilin.nautile.ui.theme.NautileStates
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * G6 属性工具栏与锚定菜单（CMP-30—CMP-37），新建面板与详情面板共用同一套实现（CMP-37）。
 *
 * 只承载视图与语义：选中值由调用方状态提供，选择/清除立即回调；本文件不写业务数据。
 */

/** 属性入口顺序固定：日期、优先级、项目、清单、详情（CMP-30）。 */
internal enum class TaskAttribute { DATE, PRIORITY, PROJECT, CHECKLIST, DETAIL }

/**
 * 底部面板内日期与 24h 时间圆盘分层子视图（R5-3/R5-4、03 规格 §1.5、§2.5）。
 *
 * 返回递退路径固定为 `TIME_PICKER → CALENDAR → NONE`，严禁一次关闭两层。
 */
internal enum class DateSubView {
    NONE,
    CALENDAR,
    TIME_PICKER,
}

private val MenuWidth = 248.dp
private val MenuMaxHeight = 320.dp
private val MenuShadow = 3.dp
private val DatePickerMaxHeight = 560.dp

/** 工具栏单项：图标 24dp、触控 ≥48dp、摘要单行省略，按钮合并子节点语义。 */
@Composable
internal fun AttributeToolbarItem(
    iconRes: Int,
    summary: String?,
    description: String,
    onActivate: () -> Unit,
    onBounds: (Rect) -> Unit,
    iconTint: Color = LocalContentColor.current,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onActivate)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            // 图标自身不重复朗读，由按钮提供合并后的完整语义（CMP-30.3）。
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (enabled) {
                    onClick(label = description) {
                        onActivate()
                        true
                    }
                } else {
                    disabled()
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
internal fun AttributeMenuOverlay(
    anchor: Rect?,
    origin: Offset,
    rootSize: IntSize,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    // MOT-16：锚定菜单打开用 StandardEffects 淡入；首帧测量完成后再开始，避免跳位。
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(menuSize.height) {
        if (menuSize.height > 0) appeared = true
    }
    val menuAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = NautileMotion.StandardEffects,
        label = "attribute_menu_fade",
    )
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
                    // 首帧尚未测量时先隐藏，避免菜单从触发图标位置跳到目标位置；随后按 Standard 淡入。
                    .alpha(menuAlpha),
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
                    content = content,
                )
            }
        }
    }
}

@Composable
internal fun AttributeMenuItem(
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

/** 优先级四项固定顺序：高、中、低、无；彩旗 + 中文文字，颜色不是唯一区分（CMP-33）。 */
@Composable
internal fun PriorityMenuContent(selected: Priority, onSelect: (Priority) -> Unit) {
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

/**
 * 项目菜单：无项目、活跃项目、新建项目（CMP-34）。
 * 只列出传入的 [projects]（调用方过滤为活跃项目），已结项项目不可选。
 */
@Composable
internal fun ProjectMenuContent(
    selectedProjectId: ProjectId?,
    projects: List<Project>,
    draftName: String,
    draftError: String?,
    creating: Boolean,
    onProjectSelected: (ProjectId?) -> Unit,
    onDraftChanged: (String) -> Unit,
    onCreateProject: () -> Unit,
) {
    var showCreateForm by remember { mutableStateOf(false) }
    AttributeMenuItem(
        text = "无项目",
        description = if (selectedProjectId == null) "项目：无项目，已选择" else "项目：无项目",
        selected = selectedProjectId == null,
        onClick = { onProjectSelected(null) },
        leading = { MenuLeadingIcon(R.drawable.ic_folder) },
    )
    projects.forEach { project ->
        AttributeMenuItem(
            text = project.name,
            description = "项目：${project.name}",
            selected = selectedProjectId == project.id,
            onClick = { onProjectSelected(project.id) },
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
                value = draftName,
                onValueChange = onDraftChanged,
                label = { Text("项目名称") },
                singleLine = true,
                isError = draftError != null,
                supportingText = draftError?.let { message ->
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
                    enabled = !creating,
                ) {
                    Text("取消")
                }
                Button(
                    onClick = onCreateProject,
                    enabled = !creating && draftName.isNotBlank(),
                ) {
                    Text("创建")
                }
            }
        }
    }
}

/** GTD 清单五项：ENDED 显示为禁用项并附说明（CMP-35）。 */
@Composable
internal fun ChecklistMenuContent(
    selected: GtdPosition,
    onSelect: (GtdPosition) -> Unit,
    entryEnabled: Boolean = true,
    disabledSupporting: String? = null,
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
            enabled = !ended && entryEnabled,
            supporting = if (ended) "已结束不可直接移入" else disabledSupporting,
            onClick = { onSelect(position) },
            leading = { MenuLeadingIcon(R.drawable.ic_checklist) },
        )
    }
}

@Composable
internal fun DetailMenuContent(
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
        leading = { MenuLeadingIcon(R.drawable.ic_close) },
    )
}

/**
 * G9 视图选项菜单（CMP-39）：两组单选，当前项打勾；不提供标签分类，也没有“应用”按钮。
 * 选择立即回调；[error] 非 null 时在菜单底部提示保存失败。
 */
@Composable
internal fun ViewOptionsMenuContent(
    options: ViewOptions,
    error: String?,
    onClassificationSelected: (ViewClassification) -> Unit,
    onSortSelected: (ViewSort) -> Unit,
) {
    MenuGroupTitle("分类")
    ViewClassification.entries.forEach { classification ->
        val label = classification.menuLabel()
        AttributeMenuItem(
            text = label,
            description = "分类：$label",
            selected = options.classification == classification,
            onClick = { onClassificationSelected(classification) },
            leading = { MenuLeadingIcon(classification.menuIconRes()) },
        )
    }
    MenuGroupTitle("排序")
    // R4 G6：手动排序入口隐藏（CMP-40.3、NO-19）；枚举与数据保留，恢复时去掉过滤即可。
    ViewSort.entries.filter { it != ViewSort.MANUAL }.forEach { sort ->
        val label = sort.menuLabel()
        AttributeMenuItem(
            text = label,
            description = "排序：$label",
            selected = options.sort == sort,
            onClick = { onSortSelected(sort) },
            leading = { MenuLeadingIcon(sort.menuIconRes()) },
        )
    }
    if (error != null) {
        Text(
            text = error,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(
                start = NautileSpacing.space3,
                end = NautileSpacing.space3,
                top = NautileSpacing.space1,
                bottom = NautileSpacing.space2,
            ),
        )
    }
}

@Composable
private fun MenuGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = NautileSpacing.space3,
            end = NautileSpacing.space3,
            top = NautileSpacing.space2,
            bottom = NautileSpacing.space1,
        ),
    )
}

internal fun ViewClassification.menuLabel(): String = when (this) {
    ViewClassification.PROJECT -> "项目"
    ViewClassification.PRIORITY -> "优先级"
    ViewClassification.DUE -> "截止日期"
}

internal fun ViewSort.menuLabel(): String = when (this) {
    ViewSort.DUE -> "截止日期"
    ViewSort.PRIORITY -> "优先级"
    ViewSort.CREATED -> "创建时间"
    ViewSort.MANUAL -> "手动"
}

/** CMP-48 图标映射：分类项目/优先级/截止日期用 folder/flag/calendar_today。 */
internal fun ViewClassification.menuIconRes(): Int = when (this) {
    ViewClassification.PROJECT -> R.drawable.ic_folder
    ViewClassification.PRIORITY -> R.drawable.ic_flag
    ViewClassification.DUE -> R.drawable.ic_calendar_today
}

/** CMP-48 图标映射：排序截止日期/优先级/创建时间用 calendar_today/flag/schedule。 */
internal fun ViewSort.menuIconRes(): Int = when (this) {
    ViewSort.DUE -> R.drawable.ic_calendar_today
    ViewSort.PRIORITY -> R.drawable.ic_flag
    ViewSort.CREATED -> R.drawable.ic_schedule
    // 手动项本轮不显示，仅保留映射以便恢复入口。
    ViewSort.MANUAL -> R.drawable.ic_schedule
}

/** 菜单项前导图标：24dp、装饰性，语义由条目合并描述承担（CMP-48）。 */
@Composable
private fun MenuLeadingIcon(iconRes: Int) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
    )
}

/**
 * 日期日历与 24h 时间圆盘子视图（同一底部面板内切换，R5-3/R5-4、03 规格 §2.5）。
 *
 * - [DateSubView.CALENDAR]：无冗余 title/headline 的 M3 DatePicker（防表头重叠）+ 下方“时间”行 + 清除截止日期；
 * - [DateSubView.TIME_PICKER]：M3 24h TimePicker 圆盘 + 严格 24h `HH:mm` 防回环同步校验输入框；
 * - 子视图间采用 `NautileMotion.StandardEffects` 淡入淡出（ANIM-04），不叠加第二层遮罩；
 * - 调用方需将日历槽位外层水平 padding 下沉至非日历区域，以保证 360dp 宽屏下 7 列不挤压。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateAttributePanel(
    subView: DateSubView,
    dueDate: LocalDate?,
    dueTime: LocalTime?,
    onDueDateChanged: (LocalDate?) -> Unit,
    onDueTimeChanged: (LocalTime?) -> Unit,
    onSubViewChanged: (DateSubView) -> Unit,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dueDate?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli(),
    )
    val expectedDateMillis = dueDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    LaunchedEffect(expectedDateMillis) {
        if (datePickerState.selectedDateMillis != expectedDateMillis) {
            datePickerState.selectedDateMillis = expectedDateMillis
        }
    }
    LaunchedEffect(datePickerState.selectedDateMillis) {
        val millis = datePickerState.selectedDateMillis ?: return@LaunchedEffect
        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        if (picked != dueDate) onDueDateChanged(picked)
    }

    Crossfade(
        targetState = subView,
        animationSpec = NautileMotion.StandardEffects,
        label = "date_sub_view_transition",
    ) { activeSubView ->
        when (activeSubView) {
            DateSubView.CALENDAR -> {
                DateCalendarSubPanel(
                    datePickerState = datePickerState,
                    dueDate = dueDate,
                    dueTime = dueTime,
                    onOpenTimePicker = { onSubViewChanged(DateSubView.TIME_PICKER) },
                    onClearTime = { onDueTimeChanged(null) },
                    onClearDate = {
                        datePickerState.selectedDateMillis = null
                        onDueDateChanged(null)
                        onDueTimeChanged(null)
                    },
                    onBack = { onSubViewChanged(DateSubView.NONE) },
                )
            }

            DateSubView.TIME_PICKER -> {
                TimePickerSubPanel(
                    dueTime = dueTime,
                    onConfirm = { time ->
                        onDueTimeChanged(time)
                        onSubViewChanged(DateSubView.CALENDAR)
                    },
                    onClearTime = {
                        onDueTimeChanged(null)
                        onSubViewChanged(DateSubView.CALENDAR)
                    },
                    onCancel = { onSubViewChanged(DateSubView.CALENDAR) },
                )
            }

            DateSubView.NONE -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateCalendarSubPanel(
    datePickerState: androidx.compose.material3.DatePickerState,
    dueDate: LocalDate?,
    dueTime: LocalTime?,
    onOpenTimePicker: () -> Unit,
    onClearTime: () -> Unit,
    onClearDate: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = NautileSpacing.space3,
                    end = NautileSpacing.space4,
                    top = NautileSpacing.space1,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NautileIconButton(
                iconRes = R.drawable.ic_arrow_back,
                contentDescription = "返回",
                onClick = onBack,
                testTag = "date_panel_back",
            )
            Text(
                text = "选择截止日期",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = NautileSpacing.space1),
            )
        }
        // R5-3：显式隐藏默认 title/headline、不叠加多余水平 padding、解除 420dp 硬截断，
        // 仅保留足够高的有限上界供年份网格安全测量，根除星期表头与首排日期重叠。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = DatePickerMaxHeight),
        ) {
            DatePicker(
                state = datePickerState,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // R5-4：日历下方“时间”行（最小高 ≥48dp，有值时独立清除按钮不误触发圆盘）。
        DatePanelTimeRow(
            dueDate = dueDate,
            dueTime = dueTime,
            onOpenTimePicker = onOpenTimePicker,
            onClearTime = onClearTime,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = NautileSpacing.space6,
                    end = NautileSpacing.space6,
                    top = NautileSpacing.space2,
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = NautileSpacing.space6,
                    end = NautileSpacing.space6,
                    top = NautileSpacing.space1,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onClearDate,
                enabled = dueDate != null,
            ) {
                Text("清除截止日期")
            }
        }
    }
}

@Composable
private fun DatePanelTimeRow(
    dueDate: LocalDate?,
    dueTime: LocalTime?,
    onOpenTimePicker: () -> Unit,
    onClearTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = dueDate != null
    val formattedTime = dueTime?.let(TaskTimeRules::format24hTime)
    val rowDescription = when {
        !enabled -> "时间，先选择日期，再设置时间"
        formattedTime != null -> "截止时间：$formattedTime"
        else -> "截止时间：未设置"
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = enabled, onClick = onOpenTimePicker)
                    .semantics(mergeDescendants = true) {
                        contentDescription = rowDescription
                        role = Role.Button
                        if (!enabled) disabled()
                    }
                    .testTag("date_panel_time_row")
                    .alpha(if (enabled) 1f else NautileStates.DISABLED_CONTENT_ALPHA)
                    .padding(horizontal = NautileSpacing.space3, vertical = NautileSpacing.space2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space3),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_schedule),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "时间",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!enabled) {
                        Text(
                            text = "先选择日期，再设置时间",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = formattedTime ?: "未设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (formattedTime != null && enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (formattedTime == null || !enabled) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            if (enabled && formattedTime != null) {
                NautileIconButton(
                    iconRes = R.drawable.ic_close,
                    contentDescription = "清除时间",
                    onClick = onClearTime,
                    testTag = "date_panel_clear_time",
                )
            }
        }
    }
}

/**
 * 24 小时制时间圆盘选择子视图（R5-3/R5-4、03 规格 §2.5.3、架构核查 §4.2）。
 *
 * - 使用 M3 `TimePicker(..., is24Hour = true)`（零新增依赖）；
 * - 内联提供严格 24h `HH:mm` 文本输入框（[TaskTimeRules.parseStrict24hTime]）；
 * - 采用 `lastSyncedFromPicker` 防回环双向同步：表盘变化才覆写输入框，输入非法文本绝不被静默修正。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSubPanel(
    dueTime: LocalTime?,
    onConfirm: (LocalTime) -> Unit,
    onClearTime: () -> Unit,
    onCancel: () -> Unit,
) {
    val initialHour = dueTime?.hour ?: 9
    val initialMinute = dueTime?.minute ?: 0
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    var textInput by remember(dueTime) {
        mutableStateOf(dueTime?.let(TaskTimeRules::format24hTime) ?: "")
    }
    var parseResult by remember(dueTime) {
        mutableStateOf(TaskTimeRules.parseStrict24hTime(textInput))
    }
    var lastSyncedFromPicker by remember(dueTime) {
        mutableStateOf(initialHour to initialMinute)
    }

    // 表盘 → 文本：仅当表盘小时或分钟相对上次同步值真正变化时回填，不覆盖用户非法输入。
    LaunchedEffect(timePickerState.hour, timePickerState.minute) {
        val current = timePickerState.hour to timePickerState.minute
        if (current != lastSyncedFromPicker) {
            lastSyncedFromPicker = current
            val picked = LocalTime.of(timePickerState.hour, timePickerState.minute)
            textInput = TaskTimeRules.format24hTime(picked)
            parseResult = StrictTimeParseResult.Valid(picked)
        }
    }

    val errorMessage = (parseResult as? StrictTimeParseResult.Invalid)?.message
    val confirmEnabled = parseResult !is StrictTimeParseResult.Invalid
    fun submitConfirmedTime() {
        val confirmed = when (val result = parseResult) {
            is StrictTimeParseResult.Valid -> result.time
            StrictTimeParseResult.Empty -> LocalTime.of(
                timePickerState.hour,
                timePickerState.minute,
            )

            is StrictTimeParseResult.Invalid -> null
        }
        if (confirmed != null) onConfirm(confirmed)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("time_picker_panel"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = NautileSpacing.space3,
                    end = NautileSpacing.space4,
                    top = NautileSpacing.space1,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NautileIconButton(
                iconRes = R.drawable.ic_arrow_back,
                contentDescription = "返回日期选择",
                onClick = onCancel,
                testTag = "time_picker_back",
            )
            Text(
                text = "选择时间",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = NautileSpacing.space1),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NautileSpacing.space2),
            contentAlignment = Alignment.Center,
        ) {
            TimePicker(
                state = timePickerState,
                colors = TimePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        }
        OutlinedTextField(
            value = textInput,
            onValueChange = { raw ->
                textInput = raw
                val result = TaskTimeRules.parseStrict24hTime(raw)
                parseResult = result
                if (result is StrictTimeParseResult.Valid) {
                    lastSyncedFromPicker = result.time.hour to result.time.minute
                    timePickerState.hour = result.time.hour
                    timePickerState.minute = result.time.minute
                }
            },
            label = { Text("时间（24小时制 HH:mm）") },
            placeholder = { Text("例如 09:30") },
            singleLine = true,
            isError = errorMessage != null,
            supportingText = errorMessage?.let { message ->
                {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("time_picker_error"),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submitConfirmedTime() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = NautileSpacing.space6,
                    end = NautileSpacing.space6,
                    top = NautileSpacing.space2,
                )
                .testTag("time_picker_input"),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = NautileSpacing.space4,
                    end = NautileSpacing.space4,
                    top = NautileSpacing.space2,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onClearTime,
                enabled = dueTime != null || textInput.isNotBlank(),
            ) {
                Text("清除时间")
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) {
                Text("取消")
            }
            Button(
                onClick = { submitConfirmedTime() },
                enabled = confirmEnabled,
                modifier = Modifier.testTag("time_picker_confirm"),
            ) {
                Text("确定")
            }
        }
    }
}

internal fun Priority.chineseLabel(): String = when (this) {
    Priority.HIGH -> "高"
    Priority.MEDIUM -> "中"
    Priority.LOW -> "低"
    Priority.NONE -> "无"
}

/** 日期摘要：今天/明天/“M月d日”，有时刻追加“ HH:mm”（CMP-32）。 */
internal fun dueSummaryText(dueDate: LocalDate?, dueTime: LocalTime?): String? =
    TaskTimeRules.formatTaskDueLabel(dueDate = dueDate, dueTime = dueTime, today = LocalDate.now())
