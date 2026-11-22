package io.github.mazilin.nautile.ui.screen.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.domain.model.TaskId

/**
 * 项目半屏详情（S1-T08、T-071/D-02/D-03、AC-D-06、FR-V-08）。
 *
 * - 项目名称编辑即保存；子任务只读展示，点按先关项目详情再开任务详情（单主覆盖层）；
 * - 折叠状态由父层内存持有，不持久化；
 * - 拖柄或内容到顶继续下拉都先经父层 canLeave 检查，无效字段阻止并提示。
 */
@Composable
fun ProjectDetailSheet(
    state: ProjectDetailUiState,
    onNameChanged: (String) -> Unit,
    onCommitName: () -> Unit,
    onToggleCollapsed: () -> Unit,
    onOpenChild: (TaskId) -> Unit = {},
    onCloseRequested: () -> Unit,
) {
    val density = LocalDensity.current
    val closeThreshold = with(density) { 64.dp.toPx() }
    var dragTotal by remember { mutableFloatStateOf(0f) }
    val scrollState = rememberScrollState()
    var contentPull by remember { mutableFloatStateOf(0f) }
    val contentConnection = remember(closeThreshold) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput ||
                    available.y <= 0f ||
                    scrollState.canScrollBackward
                ) {
                    return Offset.Zero
                }
                contentPull += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (contentPull > closeThreshold) onCloseRequested()
                contentPull = 0f
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f)),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState)
                    .nestedScroll(contentConnection),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .pointerInput(closeThreshold) {
                            detectVerticalDragGestures(
                                onDragStart = { dragTotal = 0f },
                                onVerticalDrag = { _, dragAmount -> dragTotal += dragAmount },
                                onDragEnd = {
                                    if (dragTotal > closeThreshold) onCloseRequested()
                                    dragTotal = 0f
                                },
                                onDragCancel = { dragTotal = 0f },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = RoundedCornerShape(2.dp),
                            ),
                    )
                }

                Text("项目详情", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.nameValue,
                    onValueChange = onNameChanged,
                    label = { Text("项目名称") },
                    singleLine = true,
                    isError = state.nameInvalid,
                    supportingText = if (state.nameInvalid) {
                        { Text("项目名称不能为空") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCommitName() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focus ->
                            if (!focus.isFocused && state.nameDraft != null) onCommitName()
                        },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("状态：${state.closedLabel}", modifier = Modifier.weight(1f))
                    TextButton(onClick = onToggleCollapsed) {
                        Text(if (state.collapsed) "展开子任务" else "收起子任务")
                    }
                }
                if (state.saving) {
                    Text("保存中…", style = MaterialTheme.typography.labelSmall)
                }
                state.error?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!state.collapsed) {
                    if (state.children.isEmpty()) {
                        Text("暂无子任务", style = MaterialTheme.typography.bodySmall)
                    } else {
                        state.children.forEach { child ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenChild(child.id) }
                                    .testTag("project_child_${child.id.value}")
                                    .padding(vertical = 6.dp),
                            ) {
                                Text(child.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = child.detailStatusLabel(),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onCloseRequested,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("关闭")
                }
            }
        }
    }
}
