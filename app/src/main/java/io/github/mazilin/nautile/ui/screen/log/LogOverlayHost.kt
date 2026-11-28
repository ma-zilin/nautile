package io.github.mazilin.nautile.ui.screen.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.ui.designsystem.NautileBottomSheet

/**
 * 日志补录/详情/“全部分类”覆盖层宿主（R6-G4-T3/T4；架构 §5.4）。
 *
 * - 只渲染一个主覆盖层：补录、详情与分类选择互斥，不叠第二层遮罩（NO-08）；
 * - 覆盖层关闭后保留最后一次状态快照用于退出动效，动效结束才释放（CMP-19）；
 * - 取消/关闭先校验；无效字段由 ViewModel 阻止并保留面板。
 *
 * @param onOverlayClosed 覆盖层真正关闭时回调；由 MainActivity 同步关闭 OverlayBackStack。
 */
@Composable
fun LogOverlayHost(
    viewModel: LogViewModel,
    editor: LogEditorUiState?,
    detail: LogDetailUiState?,
    uiState: LogUiState,
    pickerOpen: Boolean,
    onCategoryPicked: (TimeCategoryId) -> Unit,
    onPickerClosed: () -> Unit,
    onOverlayClosed: () -> Unit,
) {
    var lastEditor by remember { mutableStateOf(editor) }
    if (editor != null) lastEditor = editor
    var lastDetail by remember { mutableStateOf(detail) }
    if (detail != null) lastDetail = detail
    var lastPickerOpen by remember { mutableStateOf(pickerOpen) }
    if (pickerOpen) lastPickerOpen = true

    val pickerState = CategoryPickerState(
        categories = uiState.categories,
        recentCategoryIds = uiState.recentCategoryIds,
        selectedId = editor?.categoryId ?: detail?.categoryId,
    )

    if (lastPickerOpen) {
        NautileBottomSheet(
            visible = pickerOpen,
            onDismissRequest = onPickerClosed,
            canDismiss = { true },
            onDismissed = {
                if (!pickerOpen) lastPickerOpen = false
            },
        ) {
            CategoryPicker(
                state = pickerState,
                onSelect = { categoryId ->
                    onCategoryPicked(categoryId)
                    onPickerClosed()
                },
                onBack = onPickerClosed,
            )
        }
    } else if (editor != null || lastEditor != null) {
        lastEditor?.let { state ->
            LogEditorSheet(
                state = state,
                pickerState = pickerState,
                onCategorySelected = viewModel::onEditorCategorySelected,
                onDateSelected = viewModel::onEditorDateSelected,
                onTimeConfirmed = viewModel::onEditorTimeConfirmed,
                onTimeCleared = viewModel::onEditorTimeCleared,
                onSave = { viewModel.createManual(onOverlayClosed) },
                onCancel = {
                    viewModel.closeEditor()
                    onOverlayClosed()
                },
                onDismissRequest = {
                    if (viewModel.dismissEditor()) onOverlayClosed()
                },
                canDismiss = { viewModel.dismissEditor() },
                visible = editor != null,
                onExitFinished = {
                    if (editor == null) lastEditor = null
                },
            )
        }
    } else {
        lastDetail?.let { state ->
            LogDetailSheet(
                state = state,
                pickerState = pickerState,
                now = uiState.now,
                zoneId = uiState.zoneId,
                onCategorySelected = viewModel::onDetailCategorySelected,
                onDateSelected = viewModel::onDetailDateSelected,
                onTimeConfirmed = viewModel::onDetailTimeConfirmed,
                onTimeCleared = viewModel::onDetailTimeCleared,
                onEditBlocked = viewModel::onDetailEditBlocked,
                onDelete = { viewModel.deleteLog(onOverlayClosed) },
                onCloseRequested = {
                    if (viewModel.dismissDetail()) onOverlayClosed()
                },
                onDismissRequest = {
                    if (viewModel.dismissDetail()) onOverlayClosed()
                },
                canDismiss = { viewModel.dismissDetail() },
                visible = detail != null,
                onExitFinished = {
                    if (detail == null) lastDetail = null
                },
            )
        }
    }
}
