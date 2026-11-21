package io.github.mazilin.nautile.ui.screen.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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

/**
 * 新建待办底部面板（S1-T09、FR-D-02、BR-69、D-04）。
 *
 * - 打开时不自动弹键盘；点输入框才弹；
 * - 只有“创建”才写库；取消/关闭不生成半成品；
 * - 校验失败留在面板并显示字段错误；创建中不可取消；
 * - 面板不属于全屏子页或三类半屏，不参与预测预览。
 */
@Composable
fun NewTaskSheet(
    state: NewTaskUiState,
    onTitleChanged: (String) -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
    onDismissRequest: () -> Unit,
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
                Text("新建待办", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "清单：${state.position.title}",
                    style = MaterialTheme.typography.labelMedium,
                )
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
                        onClick = onCreate,
                        enabled = !state.saving,
                    ) {
                        Text("创建")
                    }
                }
            }
        }
    }
}
