package io.github.mazilin.nautile.ui.screen.log

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.rule.TimeLogRules
import io.github.mazilin.nautile.ui.designsystem.NautileBottomSheet
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.designsystem.SheetInputExclusion
import io.github.mazilin.nautile.ui.theme.NautileMotion
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 详情面板内的子视图。 */
private enum class LogDetailSubview { NONE, CATEGORY, DATE, START_TIME, END_TIME }

/**
 * 日志详情半屏（05 §3.3.1 C；AC-D-06 记录分支）。
 *
 * 字段直接编辑、编辑即保存；无效字段阻止切换/关闭并提示，修正后才能离开。
 * 运行中记录只提示“请先结束计时再编辑”，不静默结束；删除前确认，运行中禁用。
 */
@Composable
fun LogDetailSheet(
    state: LogDetailUiState,
    pickerState: CategoryPickerState,
    now: Instant,
    zoneId: ZoneId,
    onCategorySelected: (TimeCategoryId) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onTimeConfirmed: (Boolean, LocalTime) -> Unit,
    onTimeCleared: (Boolean) -> Unit,
    onEditBlocked: () -> Unit,
    onDelete: () -> Unit,
    onCloseRequested: () -> Unit,
    onDismissRequest: () -> Unit,
    canDismiss: () -> Boolean,
    visible: Boolean = true,
    onExitFinished: () -> Unit = {},
) {
    var subview by remember { mutableStateOf(LogDetailSubview.NONE) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val inputExclusion = remember { SheetInputExclusion() }

    LaunchedEffect(visible) {
        if (!visible) {
            subview = LogDetailSubview.NONE
            confirmDelete = false
            inputExclusion.clear()
        }
    }

    LogSheetBackHandler(
        subviewOpen = subview != LogDetailSubview.NONE,
        visible = visible,
        onCloseSubview = { subview = LogDetailSubview.NONE },
    )

    val dismissRequest: () -> Unit = {
        if (subview != LogDetailSubview.NONE) {
            subview = LogDetailSubview.NONE
        } else if (canDismiss()) {
            onDismissRequest()
        }
    }

    NautileBottomSheet(
        visible = visible,
        onDismissRequest = dismissRequest,
        canDismiss = { if (subview != LogDetailSubview.NONE) true else canDismiss() },
        onDismissed = onExitFinished,
        contentModifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        contentScrollState = scrollState,
        pullDownInputExclusion = inputExclusion,
    ) {
        // 面板内子视图（详情/分类/日期/时间）切换只做内容区 crossfade（ANIM-S08 同源）。
        Crossfade(
            targetState = subview,
            animationSpec = NautileMotion.StandardEffects,
            label = "log_detail_subview_crossfade",
        ) { currentSubview ->
            when (currentSubview) {
            LogDetailSubview.CATEGORY -> CategoryPicker(
                state = pickerState,
                onSelect = { categoryId ->
                    if (state.running) onEditBlocked() else onCategorySelected(categoryId)
                    subview = LogDetailSubview.NONE
                },
                onBack = { subview = LogDetailSubview.NONE },
            )

            LogDetailSubview.DATE -> LogDatePanel(
                date = state.startDate,
                onDateSelected = {
                    if (state.running) onEditBlocked() else onDateSelected(it)
                    subview = LogDetailSubview.NONE
                },
                onBack = { subview = LogDetailSubview.NONE },
            )

            LogDetailSubview.START_TIME,
            LogDetailSubview.END_TIME,
            -> {
                val isStart = currentSubview == LogDetailSubview.START_TIME
                LogTimePanel(
                    label = if (isStart) "开始" else "结束",
                    time = if (isStart) state.startTime else state.endTime,
                    onConfirmed = {
                        if (state.running) onEditBlocked() else onTimeConfirmed(isStart, it)
                        subview = LogDetailSubview.NONE
                    },
                    onCleared = {
                        if (state.running) onEditBlocked() else onTimeCleared(isStart)
                        subview = LogDetailSubview.NONE
                    },
                    onBack = { subview = LogDetailSubview.NONE },
                    inputExclusion = inputExclusion,
                )
            }

            LogDetailSubview.NONE -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("log_detail_sheet"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "记录详情",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    NautileIconButton(
                        iconRes = R.drawable.ic_close,
                        contentDescription = "关闭",
                        onClick = onCloseRequested,
                        testTag = "log_detail_close",
                    )
                }

                if (state.running) {
                    LogValidationText(
                        message = "请先结束计时再编辑",
                        testTag = "log_detail_running_hint",
                    )
                }

                val blockedClick: () -> Unit = { if (state.running) onEditBlocked() }
                LogFieldRow(
                    iconRes = R.drawable.ic_category,
                    label = "分类",
                    value = pickerState.pathOf(state.categoryId),
                    testTag = "log_detail_category",
                    onClick = {
                        if (state.running) {
                            blockedClick()
                        } else {
                            subview = LogDetailSubview.CATEGORY
                        }
                    },
                )
                LogFieldRow(
                    iconRes = R.drawable.ic_calendar_today,
                    label = "日期",
                    value = formatLogDate(state.startDate),
                    testTag = "log_detail_date",
                    onClick = {
                        if (state.running) blockedClick() else subview = LogDetailSubview.DATE
                    },
                )
                LogTimeRow(
                    label = "开始",
                    time = state.startTime,
                    testTag = "log_detail_start_time",
                    clearTag = "log_detail_clear_start",
                    onOpen = {
                        if (state.running) blockedClick() else subview = LogDetailSubview.START_TIME
                    },
                    onClear = { if (!state.running) onTimeCleared(true) },
                )
                if (!state.running) {
                    LogTimeRow(
                        label = "结束",
                        time = state.endTime,
                        testTag = "log_detail_end_time",
                        clearTag = "log_detail_clear_end",
                        onOpen = { subview = LogDetailSubview.END_TIME },
                        onClear = { onTimeCleared(false) },
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "结束：计时中",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = NautileSpacing.space2),
                        )
                    }
                }

                val duration = if (state.running) {
                    TimeLogRules.elapsed(state.startInstant(zoneId) ?: now, now)
                } else {
                    val start = state.startInstant(zoneId)
                    val end = state.endInstant(zoneId)
                    if (start != null && end != null) TimeLogRules.duration(start, end) else null
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "时长",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = NautileSpacing.space2),
                    )
                    Text(
                        text = when {
                            duration == null -> "—"
                            state.running -> "计时中 ${formatElapsedText(duration)}"
                            else -> formatDurationText(duration)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = NautileSpacing.space2),
                    )
                }

                state.rangeError?.let { message ->
                    LogValidationText(message = message, testTag = "log_detail_validation")
                }
                state.saveError?.let { message ->
                    LogValidationText(message = message, testTag = "log_detail_error")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = NautileSpacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        enabled = !state.running && !state.isSaving,
                        modifier = Modifier.testTag("log_detail_delete"),
                    ) {
                        Text("删除记录", color = MaterialTheme.colorScheme.error)
                    }
                    Box(modifier = Modifier.weight(1f))
                }
                if (state.isSaving) {
                    Text("保存中…", style = MaterialTheme.typography.labelSmall)
                }
                }
            }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条记录？") },
            text = {
                Text(
                    "${formatLogDate(state.startDate)} " +
                        "${state.startTime?.format(DETAIL_TIME_FORMATTER) ?: "--:--"}–" +
                        "${state.endTime?.format(DETAIL_TIME_FORMATTER) ?: "计时中"}",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("log_detail_delete_confirm"),
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            },
        )
    }
}

private val DETAIL_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
