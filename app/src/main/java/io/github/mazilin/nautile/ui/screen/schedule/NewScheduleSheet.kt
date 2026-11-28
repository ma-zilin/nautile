package io.github.mazilin.nautile.ui.screen.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.ui.designsystem.NautileBottomSheet
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 新建日程半屏（G3-T2；05 §2.3/§3.2、AC-D-07）。
 *
 * 只有“创建”落库；取消不生成半成品。标题空/起止非法时留在面板并提示，遮罩/返回被阻止。
 * 来源固定“普通”，标签“课程表/无标签”单选、默认无标签；标签不改变来源（AC-S-04）。
 */
@Composable
fun NewScheduleSheet(
    state: ScheduleEditorUiState,
    onTitleChanged: (String) -> Unit,
    onCommitTitle: () -> Unit,
    onNoteChanged: (String) -> Unit,
    onCommitNote: () -> Unit,
    onStartDateSelected: (LocalDate) -> Unit,
    onEndDateSelected: (LocalDate) -> Unit,
    onTimeConfirmed: (ScheduleEditorField, LocalTime) -> Unit,
    onTimeCleared: (ScheduleEditorField) -> Unit,
    onCourseTagSelected: () -> Unit,
    onNoTagSelected: () -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
    onDismissRequest: () -> Unit,
    canDismiss: () -> Boolean,
    visible: Boolean = true,
    onExitFinished: () -> Unit = {},
) {
    var panelField by remember { mutableStateOf<ScheduleEditorField?>(null) }
    var panelStartWithTime by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // 退出后清空面板内子视图，避免下次打开停在圆盘。
    LaunchedEffect(visible) {
        if (!visible) {
            panelField = null
            panelStartWithTime = false
        }
    }

    // 面板打开时，返回/遮罩/下拉先退出子视图，不关闭整个面板；
    // 面板关闭后仍由 canDismiss 校验，无效字段时下拉/遮罩不关闭。
    val dismissRequest: () -> Unit = {
        if (panelField != null) {
            panelField = null
            panelStartWithTime = false
        } else if (canDismiss()) {
            onDismissRequest()
        }
    }

    NautileBottomSheet(
        visible = visible,
        onDismissRequest = dismissRequest,
        canDismiss = { if (panelField != null) true else canDismiss() },
        onDismissed = onExitFinished,
        contentModifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
    ) {
        Text(
            text = "新建日程",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("new_schedule_sheet"),
        )
        val field = panelField
        if (field == null) {
            ScheduleEditorFields(
                state = state,
                onTitleChanged = onTitleChanged,
                onCommitTitle = onCommitTitle,
                onNoteChanged = onNoteChanged,
                onCommitNote = onCommitNote,
                onOpenDate = { selected ->
                    panelStartWithTime = false
                    panelField = selected
                },
                onOpenTime = { selected ->
                    panelStartWithTime = true
                    panelField = selected
                },
                onClearTime = onTimeCleared,
                onCourseTagSelected = onCourseTagSelected,
                onNoTagSelected = onNoTagSelected,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = NautileSpacing.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onCancel,
                    enabled = !state.isSaving,
                    modifier = Modifier.testTag("schedule_cancel"),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = onCreate,
                    enabled = state.isValid && !state.isSaving,
                    modifier = Modifier.testTag("schedule_create"),
                ) {
                    Text("创建")
                }
            }
            if (state.isSaving) {
                Text("创建中…", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            val isStart = field == ScheduleEditorField.START
            ScheduleDateTimePanel(
                field = field,
                date = if (isStart) state.startDate else state.endDate,
                time = if (isStart) state.startTime else state.endTime,
                startWithTime = panelStartWithTime,
                onDateSelected = { picked ->
                    if (isStart) onStartDateSelected(picked) else onEndDateSelected(picked)
                },
                onTimeConfirmed = { onTimeConfirmed(field, it) },
                onTimeCleared = { onTimeCleared(field) },
                onBackToForm = {
                    panelField = null
                    panelStartWithTime = false
                },
                backEnabled = visible,
            )
        }
    }
}

/**
 * 新建/详情共用的字段区（标题、备注、起止日期时间、来源、标签、校验提示）。
 *
 * 只承载视图与回调；数据写入由 ViewModel 决定，非法值不会被静默修正。
 */
@Composable
internal fun ScheduleEditorFields(
    state: ScheduleEditorUiState,
    onTitleChanged: (String) -> Unit,
    onCommitTitle: () -> Unit,
    onNoteChanged: (String) -> Unit,
    onCommitNote: () -> Unit,
    onOpenDate: (ScheduleEditorField) -> Unit,
    onOpenTime: (ScheduleEditorField) -> Unit,
    onClearTime: (ScheduleEditorField) -> Unit,
    onCourseTagSelected: () -> Unit,
    onNoTagSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var titleHadFocus by remember { mutableStateOf(false) }
    var noteHadFocus by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChanged,
            label = { Text("标题") },
            singleLine = true,
            isError = state.titleError != null,
            supportingText = state.titleError?.let { message ->
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
                    focusManager.clearFocus()
                    onCommitTitle()
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focus ->
                    if (focus.isFocused) {
                        titleHadFocus = true
                    } else if (titleHadFocus) {
                        titleHadFocus = false
                        onCommitTitle()
                    }
                }
                .testTag("schedule_title_field"),
        )
        OutlinedTextField(
            value = state.note,
            onValueChange = onNoteChanged,
            label = { Text("备注（可选）") },
            minLines = 1,
            maxLines = 3,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onCommitNote()
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focus ->
                    if (focus.isFocused) {
                        noteHadFocus = true
                    } else if (noteHadFocus) {
                        noteHadFocus = false
                        onCommitNote()
                    }
                }
                .testTag("schedule_note_field"),
        )

        ScheduleDateTimeRow(
            label = "开始",
            date = state.startDate,
            time = state.startTime,
            dateTag = "schedule_start_date",
            timeTag = "schedule_start_time",
            clearTag = "schedule_clear_start_time",
            onDateClick = { onOpenDate(ScheduleEditorField.START) },
            onTimeClick = { onOpenTime(ScheduleEditorField.START) },
            onClearTime = { onClearTime(ScheduleEditorField.START) },
        )
        ScheduleDateTimeRow(
            label = "结束",
            date = state.endDate,
            time = state.endTime,
            dateTag = "schedule_end_date",
            timeTag = "schedule_end_time",
            clearTag = "schedule_clear_end_time",
            onDateClick = { onOpenDate(ScheduleEditorField.END) },
            onTimeClick = { onOpenTime(ScheduleEditorField.END) },
            onClearTime = { onClearTime(ScheduleEditorField.END) },
        )
        // 起止错误紧跟日期/时间行，便于定位（05 §3.2）。
        state.rangeError?.let { message ->
            ValidationText(message = message, testTag = "schedule_validation_error")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "来源",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = state.sourceLabel,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("schedule_source_value"),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 200% 字体时标签行可横滚，不挤压/重叠（A11Y-13）。
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space2),
        ) {
            Text(
                text = "标签",
                style = MaterialTheme.typography.bodyMedium,
            )
            FilterChip(
                selected = state.isCourseTagSelected,
                onClick = onCourseTagSelected,
                label = { Text("课程表") },
                modifier = Modifier.testTag("schedule_tag_course"),
            )
            FilterChip(
                selected = state.tagId == null,
                onClick = onNoTagSelected,
                label = { Text("无标签") },
                modifier = Modifier.testTag("schedule_tag_none"),
            )
        }

        state.saveError?.let { message ->
            ValidationText(message = message, testTag = "schedule_save_error")
        }
    }
}

@Composable
private fun ScheduleDateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime?,
    dateTag: String,
    timeTag: String,
    clearTag: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onClearTime: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onDateClick)
            .testTag(dateTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space2),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_calendar_today),
            contentDescription = null,
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = formatDate(date),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onTimeClick)
            .testTag(timeTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space2),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_schedule),
            contentDescription = null,
        )
        Text(text = "${label}时间", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = time?.format(TIME_FORMATTER) ?: "未设置",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (time != null) {
            NautileIconButton(
                iconRes = R.drawable.ic_close,
                contentDescription = "清除${label}时间",
                onClick = onClearTime,
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
internal fun ValidationText(message: String, testTag: String) {
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

internal fun formatDate(date: LocalDate): String =
    "${date.monthValue}月${date.dayOfMonth}日"

internal val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
