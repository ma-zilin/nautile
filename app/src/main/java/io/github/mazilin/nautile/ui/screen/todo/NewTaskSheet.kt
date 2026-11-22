package io.github.mazilin.nautile.ui.screen.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.domain.model.GtdPosition

/**
 * 新建底部面板（S1-T09/T-071、FR-D-02、BR-69）。
 *
 * - 打开时先选“待办/项目”，不自动弹键盘；
 * - 待办：只有“创建”才写库；已结束清单内添加时可选四个非 ENDED 目标清单，默认收件箱；
 * - 项目：复用既有 ProjectCommands.create；
 * - 校验失败留在面板并显示字段错误；取消/关闭不生成半成品。
 */
@Composable
fun NewTaskSheet(
    state: NewTaskUiState,
    onKindChanged: (NewTaskKind) -> Unit = {},
    onTargetChanged: (GtdPosition) -> Unit = {},
    onTitleChanged: (String) -> Unit = {},
    onProjectNameChanged: (String) -> Unit = {},
    onCreate: () -> Unit = {},
    onCreateProject: () -> Unit = {},
    onCancel: () -> Unit = {},
    onDismissRequest: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .pointerInput(Unit) {
                detectTapGestures { onDismissRequest() }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("新建", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.kind == NewTaskKind.TODO,
                        onClick = { onKindChanged(NewTaskKind.TODO) },
                        label = { Text("待办") },
                    )
                    FilterChip(
                        selected = state.kind == NewTaskKind.PROJECT,
                        onClick = { onKindChanged(NewTaskKind.PROJECT) },
                        label = { Text("项目") },
                    )
                }

                when (state.kind) {
                    NewTaskKind.TODO -> {
                        if (state.showTargetPicker) {
                            Text(
                                text = "目标清单（已结束不可选）",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GtdPosition.entries
                                    .filter { it != GtdPosition.ENDED }
                                    .forEach { position ->
                                        FilterChip(
                                            selected = state.targetPosition == position,
                                            onClick = { onTargetChanged(position) },
                                            label = { Text(position.title) },
                                        )
                                    }
                            }
                        } else {
                            Text(
                                text = "清单：${state.targetPosition.title}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.title,
                            onValueChange = onTitleChanged,
                            label = { Text("标题") },
                            singleLine = true,
                            isError = state.titleInvalid,
                            supportingText = if (state.titleInvalid) {
                                { Text("标题不能为空") }
                            } else {
                                null
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onCreate() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    NewTaskKind.PROJECT -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.projectName,
                            onValueChange = onProjectNameChanged,
                            label = { Text("项目名称") },
                            singleLine = true,
                            isError = state.projectNameInvalid,
                            supportingText = if (state.projectNameInvalid) {
                                { Text("项目名称不能为空") }
                            } else {
                                null
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onCreateProject() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                state.error?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.saving) {
                    Text("创建中…", style = MaterialTheme.typography.labelSmall)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = onCancel,
                        enabled = !state.saving,
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = if (state.kind == NewTaskKind.TODO) {
                            onCreate
                        } else {
                            onCreateProject
                        },
                        enabled = !state.saving,
                    ) {
                        Text(if (state.kind == NewTaskKind.TODO) "创建" else "创建项目")
                    }
                }
            }
        }
    }
}
