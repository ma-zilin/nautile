package io.github.mazilin.nautile.ui.screen.schedule

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.ui.designsystem.NautileBottomSheet
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import java.time.LocalDate
import java.time.LocalTime

/**
 * 日程详情半屏（G3-T2；05 §2.3/§3.2、AC-D-06/08）。
 *
 * 无整页保存按钮：标题/备注在字段结束或 IME 完成时保存；日期/标签选择立即保存。
 * 无效字段阻止关闭并提示；来源只读展示，不提供修改入口（设计默认，待用户确认）。
 * 删除入口在表单底部左侧，确认对话框说明不可恢复。
 */
@Composable
fun ScheduleDetailSheet(
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
    onCloseRequested: () -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit,
    canDismiss: () -> Boolean,
    visible: Boolean = true,
    onExitFinished: () -> Unit = {},
) {
    var panelField by remember { mutableStateOf<ScheduleEditorField?>(null) }
    var panelStartWithTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(visible) {
        if (!visible) {
            panelField = null
            panelStartWithTime = false
            confirmDelete = false
        }
    }

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("schedule_detail_sheet"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "日程详情",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            NautileIconButton(
                iconRes = R.drawable.ic_close,
                contentDescription = "关闭",
                onClick = onCloseRequested,
                testTag = "schedule_close",
            )
        }

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
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !state.isSaving,
                    modifier = Modifier.testTag("schedule_delete"),
                ) {
                    Text(
                        text = "删除日程",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (state.isSaving) {
                Text("保存中…", style = MaterialTheme.typography.labelSmall)
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

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除日程？") },
            text = { Text("删除后不可恢复") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("schedule_delete_confirm"),
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
