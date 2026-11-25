package io.github.mazilin.nautile.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.ui.theme.NautileSheetMetrics
import io.github.mazilin.nautile.ui.theme.NautileStates

/**
 * 底部面板结构（设计 §7.5 CMP-15/16/17/19）。
 *
 * 本阶段只提供结构：遮罩 32%、surfaceContainerLow 底、顶部 28dp 圆角（从 extraLarge 派生，底角 0）、
 * 22dp 拖拽柄区与 16dp 内容内边距。拖拽、进出场动效与退出完成回调由 G1-A2 接线，
 * 因此这里既不做手势判定，也不写死任何 64dp 阈值；onDismissed 在 A2 退出动画结束后调用。
 *
 * contentModifier 供 A2 在内容列上接 nestedScroll（内容可滚动时先滚动，到边界再进入拖拽）。
 */
@Composable
fun NautileBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    canDismiss: () -> Boolean,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) {
        return
    }
    // 用 rememberUpdatedState 避免遮罩点击因 lambda 变化而重复绑定手势。
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentCanDismiss by rememberUpdatedState(canDismiss)

    val sheetShape = MaterialTheme.shapes.extraLarge.copy(
        bottomStart = CornerSize(0.dp),
        bottomEnd = CornerSize(0.dp),
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.scrim.copy(alpha = NautileStates.SCRIM_ALPHA),
                )
                .pointerInput(Unit) {
                    detectTapGestures {
                        // canDismiss=false 时（如无效字段）不关闭，也不播退出。
                        if (currentCanDismiss()) {
                            currentOnDismissRequest()
                        }
                    }
                },
        )
        Surface(
            modifier = modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = sheetShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(NautileSheetMetrics.HANDLE_AREA_DP),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(NautileSheetMetrics.HANDLE_WIDTH_DP)
                            .height(NautileSheetMetrics.HANDLE_HEIGHT_DP)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                        .then(contentModifier),
                    content = content,
                )
            }
        }
    }
}
