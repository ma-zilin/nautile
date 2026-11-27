package io.github.mazilin.nautile.ui.screen.todo

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import io.github.mazilin.nautile.ui.theme.NautileMotion
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import io.github.mazilin.nautile.ui.theme.NautileStates
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * G6 属性工具栏与锚定菜单（CMP-30—CMP-37），新建面板与详情面板共用同一套实现（CMP-37）。
 *
 * 只承载视图与语义：选中值由调用方状态提供，选择/清除立即回调；本文件不写业务数据。
 */

/** 属性入口顺序固定：日期、优先级、项目、清单、详情（CMP-30）。 */
internal enum class TaskAttribute { DATE, PRIORITY, PROJECT, CHECKLIST, DETAIL }

private val MenuWidth = 248.dp
private val MenuMaxHeight = 320.dp
private val MenuShadow = 3.dp
private val AttributeTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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

/** 日期选择面板（同一底部面板内切换，CMP-31.2、CMP-32）。
 *
 * 先选日期才可填时刻；选择立即回调；日期面板不设“确定”总开关，“清除”清空日期与时刻。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateAttributePanel(
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
    var timeText by remember { mutableStateOf(dueTime?.format(AttributeTimeFormatter) ?: "") }
    LaunchedEffect(dueTime) {
        timeText = dueTime?.format(AttributeTimeFormatter) ?: ""
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

internal fun Priority.chineseLabel(): String = when (this) {
    Priority.HIGH -> "高"
    Priority.MEDIUM -> "中"
    Priority.LOW -> "低"
    Priority.NONE -> "无"
}

/** 日期摘要：今天/明天/“M月d日”，有时刻追加“ HH:mm”（CMP-32）。 */
internal fun dueSummaryText(dueDate: LocalDate?, dueTime: LocalTime?): String? {
    val date = dueDate ?: return null
    val today = LocalDate.now()
    val label = when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> "${date.monthValue}月${date.dayOfMonth}日"
    }
    val time = dueTime?.format(AttributeTimeFormatter)
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
