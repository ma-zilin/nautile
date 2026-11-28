package io.github.mazilin.nautile.ui.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.rule.StrictTimeParseResult
import io.github.mazilin.nautile.domain.rule.TaskTimeRules
import io.github.mazilin.nautile.ui.theme.NautileMotion
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import io.github.mazilin.nautile.ui.theme.NautileStates
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** [NautileDateTimePanel] 当前激活的内部子视图。 */
enum class NautileDateTimeSubView {
    CALENDAR,
    TIME,
}

/** 格式化单行日期时间展示文案：`M月d日 HH:mm` 或 `M月d日`（无时间）。 */
fun formatNautileDateTime(date: LocalDate, time: LocalTime?): String {
    val dateText = "${date.monthValue}月${date.dayOfMonth}日"
    return if (time != null) {
        "$dateText ${TaskTimeRules.format24hTime(time)}"
    } else {
        dateText
    }
}

/**
 * 单行日期+时间组合按钮（DS-03；06_R7统一视觉与交互规格 §1.2、§5.1、§6.3）。
 *
 * 最小高度 48dp；前导 `ic_calendar_today` + 标签（如“开始”/“结束”）+ 右对齐日期时间值 + 尾部箭头；
 * 整行合并无障碍语义，装饰图标 `contentDescription = null`。
 */
@Composable
fun NautileDateTimeRow(
    label: String,
    valueText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIconRes: Int = R.drawable.ic_calendar_today,
    enabled: Boolean = true,
    isPlaceholder: Boolean = false,
    contentDescription: String? = null,
    testTag: String? = null,
) {
    val mergedDescription = contentDescription ?: "${label}时间：$valueText，点按修改"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = NautileSpacing.space12)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.contentDescription = mergedDescription
                role = Role.Button
                if (!enabled) disabled()
            }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .alpha(if (enabled) 1f else NautileStates.DISABLED_CONTENT_ALPHA)
            .padding(horizontal = NautileSpacing.space1, vertical = NautileSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space2),
    ) {
        Icon(
            painter = painterResource(leadingIconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ROW_ICON_SIZE),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isPlaceholder) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ROW_ICON_SIZE),
        )
    }
}

/**
 * 全仓统一日期日历 + 24h 时间圆盘复合选择面板（DS-03；06_R7统一视觉与交互规格 §1.2、§5.2、§9）。
 *
 * 供待办（可选日期/时间）、日程（起止时间）与时间日志（补录/详情起止时间）100% 复用：
 * - 日历视图去除默认 `title`/`headline`，配合有限高容器杜绝 360dp 宽下星期表头与首排日期重叠；
 * - 顶栏统一使用 `NautileIconButton(ic_arrow_back)`，禁用文字“返回”按钮；
 * - 时间视图提供 24h M3 `TimePicker` 圆盘 + 严格 24h `HH:mm` 直输框，采用防回环同步，
 *   非法值内联 `NautileInlineError` 报错并禁用“确定”，绝不静默修正；
 * - 支持 [maxDate] 最大可选日期约束与分层系统返回（IME → 时间圆盘 → 日历 → 表单）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NautileDateTimePanel(
    title: String,
    date: LocalDate?,
    time: LocalTime?,
    onDateSelected: (LocalDate) -> Unit,
    onTimeConfirmed: (LocalTime) -> Unit,
    onBackToForm: () -> Unit,
    modifier: Modifier = Modifier,
    allowOptionalTime: Boolean = false,
    allowClearDate: Boolean = false,
    onTimeCleared: (() -> Unit)? = null,
    onDateCleared: (() -> Unit)? = null,
    maxDate: LocalDate? = null,
    startWithTime: Boolean = false,
    backEnabled: Boolean = true,
    inputExclusion: SheetInputExclusion? = null,
    onSubViewChanged: ((NautileDateTimeSubView) -> Unit)? = null,
) {
    var activeSubView by remember(title, startWithTime) {
        mutableStateOf(
            if (startWithTime) NautileDateTimeSubView.TIME else NautileDateTimeSubView.CALENDAR,
        )
    }
    var timeTextInvalid by remember(title) { mutableStateOf(false) }

    LaunchedEffect(activeSubView) {
        onSubViewChanged?.invoke(activeSubView)
    }

    fun handleBack() {
        when {
            // 时间文本非法时停留在圆盘并保持报错，绝不静默接受或丢弃错误状态。
            activeSubView == NautileDateTimeSubView.TIME && timeTextInvalid -> Unit
            activeSubView == NautileDateTimeSubView.TIME -> {
                timeTextInvalid = false
                activeSubView = NautileDateTimeSubView.CALENDAR
            }

            else -> onBackToForm()
        }
    }

    // 返回优先级：IME 先收起键盘，再分层退回日历/表单。
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    BackHandler(enabled = backEnabled && !imeVisible) { handleBack() }

    Crossfade(
        targetState = activeSubView,
        animationSpec = NautileMotion.StandardEffects,
        label = "unified_datetime_crossfade",
        modifier = modifier.fillMaxWidth(),
    ) { subView ->
        when (subView) {
            NautileDateTimeSubView.CALENDAR -> {
                DateCalendarSubView(
                    title = title,
                    date = date,
                    time = time,
                    maxDate = maxDate,
                    allowOptionalTime = allowOptionalTime,
                    allowClearDate = allowClearDate,
                    onDateSelected = onDateSelected,
                    onOpenTimePicker = {
                        timeTextInvalid = false
                        activeSubView = NautileDateTimeSubView.TIME
                    },
                    onClearTime = onTimeCleared,
                    onClearDate = onDateCleared,
                    onBackToForm = onBackToForm,
                )
            }

            NautileDateTimeSubView.TIME -> {
                TimeDialSubView(
                    time = time,
                    allowOptionalTime = allowOptionalTime,
                    onBackToCalendar = { handleBack() },
                    onTimeConfirmed = { confirmed ->
                        onTimeConfirmed(confirmed)
                        timeTextInvalid = false
                        activeSubView = NautileDateTimeSubView.CALENDAR
                    },
                    onTimeCleared = if (allowOptionalTime && onTimeCleared != null) {
                        {
                            timeTextInvalid = false
                            onTimeCleared()
                            activeSubView = NautileDateTimeSubView.CALENDAR
                        }
                    } else {
                        null
                    },
                    onInvalidChanged = { timeTextInvalid = it },
                    inputExclusion = inputExclusion,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateCalendarSubView(
    title: String,
    date: LocalDate?,
    time: LocalTime?,
    maxDate: LocalDate?,
    allowOptionalTime: Boolean,
    allowClearDate: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    onOpenTimePicker: () -> Unit,
    onClearTime: (() -> Unit)?,
    onClearDate: (() -> Unit)?,
    onBackToForm: () -> Unit,
) {
    val selectableDates = remember(maxDate) {
        dateRangeSelectableDates(minDate = null, maxDate = maxDate)
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = date?.toUtcEpochMillis(),
        selectableDates = selectableDates,
    )
    val expectedMillis = date?.toUtcEpochMillis()
    LaunchedEffect(expectedMillis) {
        if (datePickerState.selectedDateMillis != expectedMillis) {
            datePickerState.selectedDateMillis = expectedMillis
        }
    }
    LaunchedEffect(datePickerState.selectedDateMillis, maxDate) {
        val millis = datePickerState.selectedDateMillis ?: return@LaunchedEffect
        val picked = millis.toUtcLocalDate()
        if (maxDate != null && picked.isAfter(maxDate)) return@LaunchedEffect
        if (picked != date) {
            onDateSelected(picked)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = NautileSpacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NautileIconButton(
                iconRes = R.drawable.ic_arrow_back,
                contentDescription = "返回",
                onClick = onBackToForm,
                testTag = "unified_dt_back",
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = NautileSpacing.space1),
            )
            if (allowClearDate && onClearDate != null) {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis = null
                        onClearDate()
                    },
                    enabled = date != null,
                ) {
                    Text("清除日期")
                }
            }
        }

        // 显式去除默认 title/headline 并限制高区，根除 360dp 屏宽与大字号下星期表头重叠。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DATE_PICKER_MIN_HEIGHT, max = DATE_PICKER_MAX_HEIGHT)
                .testTag("unified_dt_calendar"),
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

        PanelTimeEntryRow(
            dateSelected = date != null,
            time = time,
            allowOptionalTime = allowOptionalTime,
            onOpenTimePicker = onOpenTimePicker,
            onClearTime = onClearTime,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NautileSpacing.space2),
        )
    }
}

@Composable
private fun PanelTimeEntryRow(
    dateSelected: Boolean,
    time: LocalTime?,
    allowOptionalTime: Boolean,
    onOpenTimePicker: () -> Unit,
    onClearTime: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val formattedTime = time?.let(TaskTimeRules::format24hTime)
    val showClearButton = dateSelected && allowOptionalTime && formattedTime != null && onClearTime != null
    val rowDescription = when {
        !dateSelected -> "时间，先选择日期，再设置时间"
        formattedTime != null -> "时间：$formattedTime，点按修改"
        else -> "时间：未设置，点按设置"
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
                .heightIn(min = NautileSpacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = NautileSpacing.space12)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = dateSelected, onClick = onOpenTimePicker)
                    .semantics(mergeDescendants = true) {
                        contentDescription = rowDescription
                        role = Role.Button
                        if (!dateSelected) disabled()
                    }
                    .testTag("unified_dt_time_row")
                    .alpha(if (dateSelected) 1f else NautileStates.DISABLED_CONTENT_ALPHA)
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
                    if (!dateSelected) {
                        Text(
                            text = "先选择日期，再设置时间",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = formattedTime ?: "未设置",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (formattedTime != null && dateSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (!showClearButton) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            if (showClearButton) {
                NautileIconButton(
                    iconRes = R.drawable.ic_close,
                    contentDescription = "清除时间",
                    onClick = onClearTime,
                    testTag = "unified_dt_clear_time",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialSubView(
    time: LocalTime?,
    allowOptionalTime: Boolean,
    onBackToCalendar: () -> Unit,
    onTimeConfirmed: (LocalTime) -> Unit,
    onTimeCleared: (() -> Unit)?,
    onInvalidChanged: (Boolean) -> Unit,
    inputExclusion: SheetInputExclusion?,
) {
    val initialHour = time?.hour ?: DEFAULT_PICKER_HOUR
    val initialMinute = time?.minute ?: 0
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    var textInput by remember(time) {
        mutableStateOf(time?.let(TaskTimeRules::format24hTime) ?: "")
    }
    var parseResult by remember(time) {
        mutableStateOf(TaskTimeRules.parseStrict24hTime(textInput))
    }
    var lastSyncedFromPicker by remember(time) {
        mutableStateOf(initialHour to initialMinute)
    }

    // 外部切换字段导致传入 time 变化时，同步更新圆盘与防回环基准。
    LaunchedEffect(time) {
        val targetHour = time?.hour ?: DEFAULT_PICKER_HOUR
        val targetMinute = time?.minute ?: 0
        lastSyncedFromPicker = targetHour to targetMinute
        timePickerState.hour = targetHour
        timePickerState.minute = targetMinute
    }

    // 防回环同步：仅当圆盘小时或分钟相对上次同步值真正变化时回填文本框，不覆盖非法输入。
    LaunchedEffect(timePickerState.hour, timePickerState.minute) {
        val current = timePickerState.hour to timePickerState.minute
        if (current != lastSyncedFromPicker) {
            lastSyncedFromPicker = current
            val picked = LocalTime.of(timePickerState.hour, timePickerState.minute)
            textInput = TaskTimeRules.format24hTime(picked)
            parseResult = StrictTimeParseResult.Valid(picked)
            onInvalidChanged(false)
        }
    }

    val errorMessage = (parseResult as? StrictTimeParseResult.Invalid)?.message
    val confirmEnabled = parseResult !is StrictTimeParseResult.Invalid

    fun submitTime() {
        val confirmed = when (val result = parseResult) {
            is StrictTimeParseResult.Valid -> result.time
            StrictTimeParseResult.Empty -> LocalTime.of(
                timePickerState.hour,
                timePickerState.minute,
            )

            is StrictTimeParseResult.Invalid -> null
        }
        if (confirmed != null) {
            onTimeConfirmed(confirmed)
        } else {
            onInvalidChanged(true)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = NautileSpacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NautileIconButton(
                iconRes = R.drawable.ic_arrow_back,
                contentDescription = "返回日期选择",
                onClick = onBackToCalendar,
                testTag = "unified_dt_time_back",
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
                modifier = Modifier
                    .widthIn(max = TIME_PICKER_MAX_WIDTH)
                    .testTag("unified_dt_time_picker"),
            )
        }

        OutlinedTextField(
            value = textInput,
            onValueChange = { raw ->
                textInput = raw
                val result = TaskTimeRules.parseStrict24hTime(raw)
                parseResult = result
                onInvalidChanged(result is StrictTimeParseResult.Invalid)
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
                    NautileInlineError(
                        message = message,
                        testTag = "unified_dt_time_error",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submitTime() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NautileSpacing.space2)
                .then(
                    if (inputExclusion != null) {
                        Modifier.sheetInputArea(inputExclusion, "unified_dt_time_input")
                    } else {
                        Modifier
                    },
                )
                .testTag("unified_dt_time_input"),
        )

        NautileSheetFooter(
            showDivider = false,
            leading = if (allowOptionalTime && onTimeCleared != null) {
                {
                    TextButton(
                        onClick = {
                            textInput = ""
                            parseResult = StrictTimeParseResult.Empty
                            onTimeCleared()
                        },
                        enabled = time != null || textInput.isNotBlank(),
                    ) {
                        Text("清除时间")
                    }
                }
            } else {
                null
            },
            trailing = {
                TextButton(onClick = onBackToCalendar) {
                    Text("取消")
                }
                Button(
                    onClick = { submitTime() },
                    enabled = confirmEnabled,
                    modifier = Modifier.testTag("unified_dt_time_confirm"),
                ) {
                    Text("确定")
                }
            },
        )
    }
}

/**
 * 纯日期快速跳转弹窗（DS-03；供日程/日志日期条左侧日历按钮与统计自定义范围共用）。
 *
 * 封装 M3 `DatePickerDialog` 与去冗余头 `DatePicker(title = null, headline = null)`，
 * 支持可选 [minDate] / [maxDate] 范围约束。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NautileDatePickerDialog(
    visible: Boolean,
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    confirmText: String = "确定",
    dismissText: String = "取消",
    testTag: String = "nautile_date_picker_dialog",
) {
    if (!visible) return

    val selectableDates = remember(minDate, maxDate) {
        dateRangeSelectableDates(minDate = minDate, maxDate = maxDate)
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toUtcEpochMillis(),
        selectableDates = selectableDates,
    )

    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    val pickedMillis = state.selectedDateMillis ?: return@TextButton
                    val pickedDate = pickedMillis.toUtcLocalDate()
                    if (minDate != null && pickedDate.isBefore(minDate)) return@TextButton
                    if (maxDate != null && pickedDate.isAfter(maxDate)) return@TextButton
                    onDateSelected(pickedDate)
                    onDismissRequest()
                },
                enabled = state.selectedDateMillis != null,
                modifier = Modifier.testTag("${testTag}_confirm"),
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag("${testTag}_dismiss"),
            ) {
                Text(dismissText)
            }
        },
        modifier = modifier.testTag(testTag),
    ) {
        DatePicker(
            state = state,
            title = null,
            headline = null,
            showModeToggle = false,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun dateRangeSelectableDates(
    minDate: LocalDate?,
    maxDate: LocalDate?,
): SelectableDates = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val date = utcTimeMillis.toUtcLocalDate()
        if (minDate != null && date.isBefore(minDate)) return false
        if (maxDate != null && date.isAfter(maxDate)) return false
        return true
    }

    override fun isSelectableYear(year: Int): Boolean {
        if (minDate != null && year < minDate.year) return false
        if (maxDate != null && year > maxDate.year) return false
        return true
    }
}

private fun LocalDate.toUtcEpochMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private const val DEFAULT_PICKER_HOUR = 9
private val ROW_ICON_SIZE = 20.dp
private val DATE_PICKER_MIN_HEIGHT = 340.dp
private val DATE_PICKER_MAX_HEIGHT = 460.dp
private val TIME_PICKER_MAX_WIDTH = 360.dp
