package io.github.mazilin.nautile.ui.screen.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 日程新建/详情覆盖层宿主（G3-T2/T4；架构 §5.4）。
 *
 * - 只渲染一个主覆盖层；新建与详情互斥，不叠第二层遮罩（NO-08）。
 * - 覆盖层关闭后保留最后一次状态快照用于退出动效，动效结束才释放（CMP-19）。
 * - 取消不生成半成品；遮罩/返回/关闭先校验，无效字段由 ViewModel 阻止并保留面板。
 *
 * @param onOverlayClosed 覆盖层真正关闭时回调；由 MainActivity 同步关闭 OverlayBackStack。
 */
@Composable
fun ScheduleOverlayHost(
    viewModel: ScheduleViewModel,
    editor: ScheduleEditorUiState?,
    onOverlayClosed: () -> Unit,
) {
    var lastEditor by remember { mutableStateOf(editor) }
    if (editor != null) lastEditor = editor
    val visible = editor != null

    lastEditor?.let { state ->
        val onExitFinished = {
            if (editor == null) lastEditor = null
        }
        when (state.mode) {
            ScheduleEditorMode.CREATE -> NewScheduleSheet(
                state = state,
                onTitleChanged = viewModel::onEditorTitleChanged,
                onCommitTitle = viewModel::commitEditorTitle,
                onNoteChanged = viewModel::onEditorNoteChanged,
                onCommitNote = viewModel::commitEditorNote,
                onStartDateSelected = viewModel::onEditorStartDateSelected,
                onEndDateSelected = viewModel::onEditorEndDateSelected,
                onTimeConfirmed = viewModel::onEditorTimeConfirmed,
                onTimeCleared = viewModel::onEditorTimeCleared,
                onCourseTagSelected = viewModel::onCourseTagSelected,
                onNoTagSelected = viewModel::onNoTagSelected,
                onCreate = { viewModel.createNew(onOverlayClosed) },
                onCancel = {
                    viewModel.closeEditor()
                    onOverlayClosed()
                },
                onDismissRequest = {
                    if (viewModel.dismissEditor()) onOverlayClosed()
                },
                canDismiss = { viewModel.dismissEditor() },
                visible = visible,
                onExitFinished = onExitFinished,
            )

            ScheduleEditorMode.EDIT -> ScheduleDetailSheet(
                state = state,
                onTitleChanged = viewModel::onEditorTitleChanged,
                onCommitTitle = viewModel::commitEditorTitle,
                onNoteChanged = viewModel::onEditorNoteChanged,
                onCommitNote = viewModel::commitEditorNote,
                onStartDateSelected = viewModel::onEditorStartDateSelected,
                onEndDateSelected = viewModel::onEditorEndDateSelected,
                onTimeConfirmed = viewModel::onEditorTimeConfirmed,
                onTimeCleared = viewModel::onEditorTimeCleared,
                onCourseTagSelected = viewModel::onCourseTagSelected,
                onNoTagSelected = viewModel::onNoTagSelected,
                onCloseRequested = {
                    if (viewModel.dismissEditor()) onOverlayClosed()
                },
                onDelete = { viewModel.deleteSchedule(onOverlayClosed) },
                onDismissRequest = {
                    if (viewModel.dismissEditor()) onOverlayClosed()
                },
                canDismiss = { viewModel.dismissEditor() },
                visible = visible,
                onExitFinished = onExitFinished,
            )
        }
    }
}
