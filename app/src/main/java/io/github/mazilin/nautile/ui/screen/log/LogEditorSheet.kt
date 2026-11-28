package io.github.mazilin.nautile.ui.screen.log

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.rule.ScheduleRules
import io.github.mazilin.nautile.ui.designsystem.NautileBottomSheet
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 补录面板内的子视图；返回先退出子视图，再关闭面板。 */
private enum class LogEditorSubview { NONE, CATEGORY, DATE, START_TIME, END_TIME }

/**
 * 日志补录/新建半屏（05 §3.3.1 A；AC-L-02）。
 *
 * 分类、日期、起止时间必填；结束必须晚于开始。保存失败保留输入并报错，取消不生成半成品。
 * 与详情共用 [CategoryPicker]；时间只接受严格 24 小时 `HH:mm`，不静默修正。
 */
@Composable
fun LogEditorSheet(
    state: LogEditorUiState,
    pickerState: CategoryPickerState,
    onCategorySelected: (TimeCategoryId) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onTimeConfirmed: (Boolean, LocalTime) -> Unit,
    onTimeCleared: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDismissRequest: () -> Unit,
    canDismiss: () -> Boolean,
    visible: Boolean = true,
    onExitFinished: () -> Unit = {},
) {
    var subview by remember { mutableStateOf(LogEditorSubview.NONE) }
    val scrollState = rememberScrollState()

    LaunchedEffect(visible) {
        if (!visible) subview = LogEditorSubview.NONE
    }

    LogSheetBackHandler(
        subviewOpen = subview != LogEditorSubview.NONE,
        visible = visible,
        onCloseSubview = { subview = LogEditorSubview.NONE },
    )

    val dismissRequest: () -> Unit = {
        if (subview != LogEditorSubview.NONE) {
            subview = LogEditorSubview.NONE
        } else if (canDismiss()) {
            onDismissRequest()
        }
    }

    NautileBottomSheet(
        visible = visible,
        onDismissRequest = dismissRequest,
        canDismiss = { if (subview != LogEditorSubview.NONE) true else canDismiss() },
        onDismissed = onExitFinished,
        contentModifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
    ) {
        when (subview) {
            LogEditorSubview.CATEGORY -> CategoryPicker(
                state = pickerState,
                onSelect = { categoryId ->
                    onCategorySelected(categoryId)
                    subview = LogEditorSubview.NONE
                },
                onBack = { subview = LogEditorSubview.NONE },
            )

            LogEditorSubview.DATE -> LogDatePanel(
                date = state.startDate,
                onDateSelected = {
                    onDateSelected(it)
                    subview = LogEditorSubview.NONE
                },
                onBack = { subview = LogEditorSubview.NONE },
            )

            LogEditorSubview.START_TIME,
            LogEditorSubview.END_TIME,
            -> {
                val isStart = subview == LogEditorSubview.START_TIME
                LogTimePanel(
                    label = if (isStart) "开始" else "结束",
                    time = if (isStart) state.startTime else state.endTime,
                    onConfirmed = {
                        onTimeConfirmed(isStart, it)
                        subview = LogEditorSubview.NONE
                    },
                    onCleared = {
                        onTimeCleared(isStart)
                        subview = LogEditorSubview.NONE
                    },
                    onBack = { subview = LogEditorSubview.NONE },
                )
            }

            LogEditorSubview.NONE -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "补录记录",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("log_editor_sheet"),
                    )
                    NautileIconButton(
                        iconRes = R.drawable.ic_close,
                        contentDescription = "关闭",
                        onClick = { if (canDismiss()) onDismissRequest() },
                        testTag = "log_editor_close",
                    )
                }

                LogFieldRow(
                    iconRes = R.drawable.ic_category,
                    label = "分类",
                    value = state.categoryId?.let { pickerState.pathOf(it) } ?: "未选择",
                    testTag = "log_editor_category",
                    onClick = { subview = LogEditorSubview.CATEGORY },
                )
                LogFieldRow(
                    iconRes = R.drawable.ic_calendar_today,
                    label = "日期",
                    value = formatLogDate(state.startDate),
                    testTag = "log_editor_date",
                    onClick = { subview = LogEditorSubview.DATE },
                )
                LogTimeRow(
                    label = "开始",
                    time = state.startTime,
                    testTag = "log_editor_start_time",
                    clearTag = "log_editor_clear_start",
                    onOpen = { subview = LogEditorSubview.START_TIME },
                    onClear = { onTimeCleared(true) },
                )
                LogTimeRow(
                    label = "结束",
                    time = state.endTime,
                    testTag = "log_editor_end_time",
                    clearTag = "log_editor_clear_end",
                    onOpen = { subview = LogEditorSubview.END_TIME },
                    onClear = { onTimeCleared(false) },
                )

                state.validationMessage?.let { message ->
                    LogValidationText(message = message, testTag = "log_editor_validation")
                }
                state.saveError?.let { message ->
                    LogValidationText(message = message, testTag = "log_editor_error")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = NautileSpacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onCancel,
                        enabled = !state.isSaving,
                        modifier = Modifier.testTag("log_editor_cancel"),
                    ) {
                        Text("取消")
                    }
                    Box(modifier = Modifier.weight(1f))
                    Button(
                        onClick = onSave,
                        enabled = state.isValid && !state.isSaving,
                        modifier = Modifier.testTag("log_editor_save"),
                    ) {
                        Text("保存")
                    }
                }
                if (state.isSaving) {
                    Text("保存中…", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** 子视图返回：IME 优先（由 Activity 统一收键盘），键盘收起后本层关闭子视图。 */
@Composable
internal fun LogSheetBackHandler(
    subviewOpen: Boolean,
    visible: Boolean,
    onCloseSubview: () -> Unit,
) {
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    BackHandler(enabled = visible && subviewOpen && !imeVisible) { onCloseSubview() }
}

@Composable
internal fun LogFieldRow(
    iconRes: Int,
    label: String,
    value: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = NautileSpacing.space2),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = NautileSpacing.space2),
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
        )
    }
}

@Composable
internal fun LogTimeRow(
    label: String,
    time: LocalTime?,
    testTag: String,
    clearTag: String,
    onOpen: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onOpen)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(R.drawable.ic_schedule), contentDescription = null)
        Text(
            text = "${label}时间",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = NautileSpacing.space2),
        )
        Text(
            text = time?.format(LOG_TIME_FORMATTER) ?: "未设置",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = NautileSpacing.space2),
        )
        if (time != null) {
            NautileIconButton(
                iconRes = R.drawable.ic_close,
                contentDescription = "清除${label}时间",
                onClick = onClear,
                testTag = clearTag,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
            )
        }
    }
}

@Composable
internal fun LogValidationText(message: String, testTag: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(testTag),
    )
}

/** 面板内日期日历（只选日期，时间由单独行编辑）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogDatePanel(
    date: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    LaunchedEffect(datePickerState.selectedDateMillis) {
        val millis = datePickerState.selectedDateMillis ?: return@LaunchedEffect
        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        if (picked != date) onDateSelected(picked)
    }
    Column(modifier = Modifier.fillMaxWidth().testTag("log_date_panel")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "选择日期",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack, modifier = Modifier.testTag("log_date_back")) {
                Text("返回")
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(DATE_PICKER_HEIGHT)) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false,
                title = null,
                headline = null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 面板内 24h 时间圆盘 + 严格 `HH:mm` 直输；非法不静默修正（AC-M-01 辅助核对）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogTimePanel(
    label: String,
    time: LocalTime?,
    onConfirmed: (LocalTime) -> Unit,
    onCleared: () -> Unit,
    onBack: () -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = time?.hour ?: DEFAULT_HOUR,
        initialMinute = time?.minute ?: 0,
        is24Hour = true,
    )
    var timeText by remember(label) { mutableStateOf(time?.format(LOG_TIME_FORMATTER) ?: "") }
    var textError by remember(label) { mutableStateOf<String?>(null) }
    var dialInitialized by remember(label) { mutableStateOf(false) }

    LaunchedEffect(timePickerState.hour, timePickerState.minute) {
        if (!dialInitialized) {
            dialInitialized = true
            return@LaunchedEffect
        }
        timeText = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
        textError = null
    }

    val parsed = ScheduleRules.parseTimeText(timeText)
    Column(modifier = Modifier.fillMaxWidth().testTag("log_time_panel")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${label}时间",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { if (parsed != null) onBack() },
                modifier = Modifier.testTag("log_time_back"),
            ) {
                Text("返回")
            }
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimePicker(
                state = timePickerState,
                modifier = Modifier.widthIn(max = 360.dp).testTag("log_time_picker"),
            )
        }
        OutlinedTextField(
            value = timeText,
            onValueChange = { input ->
                timeText = input
                textError = if (input.isBlank()) null else ScheduleRules.timeTextError(input)
                ScheduleRules.parseTimeText(input)?.let { valid ->
                    timePickerState.hour = valid.hour
                    timePickerState.minute = valid.minute
                }
            },
            label = { Text("时间（HH:mm，24 小时制）") },
            singleLine = true,
            isError = textError != null,
            supportingText = textError?.let { message ->
                {
                    Text(
                        text = message,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (parsed != null) onConfirmed(parsed) else textError = ScheduleRules.TIME_FORMAT_ERROR
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("log_time_input"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (time != null || timeText.isNotBlank()) {
                TextButton(
                    onClick = onCleared,
                    modifier = Modifier.testTag("log_time_clear"),
                ) {
                    Text("清除")
                }
            }
            Box(modifier = Modifier.weight(1f))
            Button(
                onClick = { parsed?.let(onConfirmed) },
                enabled = parsed != null,
                modifier = Modifier.testTag("log_time_confirm"),
            ) {
                Text("确定")
            }
        }
    }
}

/** 分类路径回显：与列表同一规则；找不到返回空串。 */
internal fun CategoryPickerState.pathOf(categoryId: TimeCategoryId): String =
    categoryPath(categories, categoryId)

internal fun formatLogDate(date: LocalDate): String =
    "${date.monthValue}月${date.dayOfMonth}日"

internal val LOG_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val DATE_PICKER_HEIGHT = 360.dp
private const val DEFAULT_HOUR = 9
