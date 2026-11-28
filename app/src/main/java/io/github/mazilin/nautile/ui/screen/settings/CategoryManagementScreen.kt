package io.github.mazilin.nautile.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.ui.designsystem.NautileBottomSheet
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.theme.NautileSpacing

/**
 * 分类管理全屏子页 P-09（R6-G4-T5；05 §2.6/§3.6、AC-L-10/18）。
 *
 * 两级列表、添加、改名、删除；子级行不显示“添加子分类”，第三级入口不存在。
 * 有子级或日志时删除被拒并列出原因；不提供批量清空/迁移（D-new2）。
 */
@Composable
fun CategoryManagementScreen(
    state: CategoryManagementUiState,
    onBack: () -> Unit,
    onCreateTopLevel: () -> Unit,
    onCreateChild: (TimeCategory) -> Unit,
    onEdit: (CategoryManagementRow) -> Unit,
    onRequestDelete: (CategoryManagementRow) -> Unit,
    onNameChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCloseEdit: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDeleteConfirm: () -> Unit,
    onDismissGuard: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastEdit by remember { mutableStateOf(state.edit) }
    if (state.edit != null) lastEdit = state.edit

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NautileSpacing.space1, vertical = NautileSpacing.space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NautileIconButton(
                iconRes = R.drawable.ic_arrow_back,
                contentDescription = "返回",
                onClick = onBack,
                testTag = "category_management_back",
            )
            Text(
                text = "分类管理",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            NautileIconButton(
                iconRes = R.drawable.ic_add,
                contentDescription = "添加分类",
                onClick = onCreateTopLevel,
                testTag = "category_add_top",
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (state.message != null) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismissMessage)
                    .padding(horizontal = NautileSpacing.space4, vertical = NautileSpacing.space1)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("category_message"),
            )
        }
        if (state.rows.isEmpty() && !state.isLoading) {
            Text(
                text = "还没有分类，点右上角添加",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(NautileSpacing.space4)
                    .testTag("category_empty"),
            )
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(state.rows, key = { it.category.id.value }) { row ->
                CategoryManagementRowItem(
                    row = row,
                    onEdit = { onEdit(row) },
                    onAddChild = { onCreateChild(row.category) },
                    onDelete = { onRequestDelete(row) },
                )
            }
            item(key = "hint") {
                Text(
                    text = "提示：最多两级；有子级或日志时拒绝删除，需逐条处理",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(NautileSpacing.space4),
                )
            }
        }
    }

    lastEdit?.let { edit ->
        CategoryEditSheet(
            state = edit,
            visible = state.edit != null,
            onNameChanged = onNameChanged,
            onSave = onSave,
            onCancel = onCloseEdit,
            onDismissRequest = onCloseEdit,
            onExitFinished = { if (state.edit == null) lastEdit = null },
        )
    }

    state.guard?.let { guard ->
        AlertDialog(
            onDismissRequest = onDismissGuard,
            title = { Text("无法删除分类") },
            text = {
                Text("${guard.categoryName}：${guard.reason}")
            },
            confirmButton = {
                TextButton(
                    onClick = onDismissGuard,
                    modifier = Modifier.testTag("category_guard_ok"),
                ) {
                    Text("知道了")
                }
            },
            modifier = Modifier.testTag("category_delete_guard"),
        )
    }

    state.pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = onDismissDeleteConfirm,
            title = { Text("删除分类？") },
            text = { Text("“${row.category.name}”删除后不可恢复") },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDelete,
                    modifier = Modifier.testTag("category_delete_confirm"),
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteConfirm) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun CategoryManagementRowItem(
    row: CategoryManagementRow,
    onEdit: () -> Unit,
    onAddChild: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onEdit)
            .padding(
                start = if (row.depth == 0) {
                    NautileSpacing.space4
                } else {
                    NautileSpacing.space8
                },
                end = NautileSpacing.space1,
            )
            .testTag("category_row_${row.category.id.value}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space1),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.category.name,
                style = if (row.depth == 0) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${row.childCount} 个子分类 · ${row.logCount} 条日志",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.depth == 0) {
            NautileIconButton(
                iconRes = R.drawable.ic_add,
                contentDescription = "添加子分类",
                onClick = onAddChild,
                testTag = "category_add_child_${row.category.id.value}",
            )
        }
        NautileIconButton(
            iconRes = R.drawable.ic_delete,
            contentDescription = "删除分类",
            onClick = onDelete,
            testTag = "category_delete_${row.category.id.value}",
        )
    }
}

/** 分类编辑半屏：名称输入 + 保存/取消；空名禁用保存。 */
@Composable
private fun CategoryEditSheet(
    state: CategoryEditUiState,
    visible: Boolean,
    onNameChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDismissRequest: () -> Unit,
    onExitFinished: () -> Unit,
) {
    NautileBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        canDismiss = { !state.isSaving },
        onDismissed = onExitFinished,
    ) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("category_edit_sheet"),
        )
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChanged,
            label = { Text("名称") },
            singleLine = true,
            isError = state.error != null,
            supportingText = state.error?.let { message ->
                {
                    Text(
                        text = message,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("category_name_field"),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NautileSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onCancel,
                enabled = !state.isSaving,
                modifier = Modifier.testTag("category_edit_cancel"),
            ) {
                Text("取消")
            }
            Box(modifier = Modifier.weight(1f))
            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier.testTag("category_edit_save"),
            ) {
                Text("保存")
            }
        }
    }
}
