package io.github.mazilin.nautile.ui.screen.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import io.github.mazilin.nautile.domain.rule.ScheduleRules
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 面板内日期日历 + 24h 时间圆盘（G3-T2；05 §1.5 CMP-49、§3.2）。
 *
 * - 日期：M3 `DatePicker`，星期表头与首排日期不重叠（固定最小高度，内部自行滚动）。
 * - 时间：M3 `TimePicker` 圆盘（`is24Hour = true`）+ 严格 `HH:mm` 直输；
 *   非法文本内联报错、禁用“确定”、不静默修正（AC-M-01 辅助核对）。
 * - 分层返回：时间圆盘 → 日期视图 → 表单；时间文本非法时留在圆盘并提示。
 * - 确认时间后回到日期视图，行内由父表单显示 `HH:mm`，右侧可清除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDateTimePanel(
    field: ScheduleEditorField,
    date: LocalDate,
    time: LocalTime?,
    startWithTime: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    onTimeConfirmed: (LocalTime) -> Unit,
    onTimeCleared: () -> Unit,
    onBackToForm: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
) {
    val fieldLabel = if (field == ScheduleEditorField.START) "开始" else "结束"
    var showTime by remember(field, startWithTime) { mutableStateOf(startWithTime) }
    var timeTextInvalid by remember(field) { mutableStateOf(false) }

    fun back() {
        when {
            // 时间文本非法：留在圆盘并提示，不静默接受。
            showTime && timeTextInvalid -> Unit
            showTime -> {
                timeTextInvalid = false
                showTime = false
            }

            else -> onBackToForm()
        }
    }

    // 返回顺序：IME 优先（由 Activity 分发收键盘），键盘收起后本层才处理面板层级。
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    BackHandler(enabled = backEnabled && !imeVisible) { back() }

    if (showTime) {
        TimeDialContent(
            fieldLabel = fieldLabel,
            time = time,
            onBack = { back() },
            // 确认后回到日期视图；行内 HH:mm 由父表单显示，仍可清除。
            onTimeConfirmed = { confirmed ->
                onTimeConfirmed(confirmed)
                timeTextInvalid = false
                showTime = false
            },
            onTimeCleared = {
                timeTextInvalid = false
                onTimeCleared()
                showTime = false
            },
            onInvalidChanged = { timeTextInvalid = it },
            modifier = modifier,
        )
    } else {
        DateCalendarContent(
            fieldLabel = fieldLabel,
            date = date,
            time = time,
            onDateSelected = onDateSelected,
            onOpenTime = {
                timeTextInvalid = false
                showTime = true
            },
            onBackToForm = onBackToForm,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateCalendarContent(
    fieldLabel: String,
    date: LocalDate,
    time: LocalTime?,
    onDateSelected: (LocalDate) -> Unit,
    onOpenTime: () -> Unit,
    onBackToForm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    LaunchedEffect(datePickerState.selectedDateMillis) {
        val millis = datePickerState.selectedDateMillis ?: return@LaunchedEffect
        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        if (picked != date) onDateSelected(picked)
    }

    Column(modifier = modifier.fillMaxWidth().testTag("schedule_datetime_panel")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "选择${fieldLabel}日期",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onBackToForm,
                modifier = Modifier.testTag("schedule_panel_back"),
            ) {
                Text("返回")
            }
        }
        // 固定高度给 M3 日历内部滚动；保证星期表头与首排日期不重叠。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DATE_PICKER_HEIGHT)
                .testTag("schedule_date_picker"),
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false,
                title = null,
                headline = null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onOpenTime)
                .padding(vertical = NautileSpacing.space2)
                .testTag("schedule_panel_time_row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space2),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_schedule),
                contentDescription = null,
                modifier = Modifier.padding(start = NautileSpacing.space1),
            )
            Text(
                text = "时间",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = time?.format(PANEL_TIME_FORMATTER) ?: "未设置",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialContent(
    fieldLabel: String,
    time: LocalTime?,
    onBack: () -> Unit,
    onTimeConfirmed: (LocalTime) -> Unit,
    onTimeCleared: () -> Unit,
    onInvalidChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = time?.hour ?: DEFAULT_HOUR,
        initialMinute = time?.minute ?: 0,
        is24Hour = true,
    )
    var timeText by remember(fieldLabel) {
        mutableStateOf(time?.format(PANEL_TIME_FORMATTER) ?: "")
    }
    var textError by remember(fieldLabel) { mutableStateOf<String?>(null) }
    var dialInitialized by remember(fieldLabel) { mutableStateOf(false) }

    // 圆盘转动后同步到文本；首次组合不把“未设置”改写成默认值。
    LaunchedEffect(timePickerState.hour, timePickerState.minute) {
        if (!dialInitialized) {
            dialInitialized = true
            return@LaunchedEffect
        }
        timeText = formatTime(timePickerState.hour, timePickerState.minute)
        textError = null
        onInvalidChanged(false)
    }

    val parsed = ScheduleRules.parseTimeText(timeText)
    val canConfirm = parsed != null

    Column(modifier = modifier.fillMaxWidth().testTag("schedule_datetime_panel")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${fieldLabel}时间",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag("schedule_panel_back"),
            ) {
                Text("返回")
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            TimePicker(
                state = timePickerState,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .testTag("schedule_time_picker"),
            )
        }
        OutlinedTextField(
            value = timeText,
            onValueChange = { input ->
                timeText = input
                val error = if (input.isBlank()) null else ScheduleRules.timeTextError(input)
                textError = error
                onInvalidChanged(error != null)
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
                    if (canConfirm) {
                        onTimeConfirmed(parsed)
                    } else {
                        textError = ScheduleRules.TIME_FORMAT_ERROR
                        onInvalidChanged(true)
                    }
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("schedule_time_input"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (time != null || timeText.isNotBlank()) {
                TextButton(
                    onClick = {
                        timeText = ""
                        textError = null
                        onInvalidChanged(false)
                        onTimeCleared()
                    },
                    modifier = Modifier.testTag("schedule_time_clear"),
                ) {
                    Text("清除")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { parsed?.let(onTimeConfirmed) },
                enabled = canConfirm,
                modifier = Modifier.testTag("schedule_time_confirm"),
            ) {
                Text("确定")
            }
        }
    }
}

private fun formatTime(hour: Int, minute: Int): String =
    "%02d:%02d".format(hour, minute)

private val PANEL_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_PICKER_HEIGHT = 360.dp
private const val DEFAULT_HOUR = 9
