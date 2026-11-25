package io.github.mazilin.nautile.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R

/**
 * 顶部栏动作（设计 §7.1 HeaderActionState 的最小化版本）。
 *
 * contentDescription 必填，对应 §6.2 功能性图标；enabled=false 时仍可见并保留 disabled 语义（A11Y-07）。
 */
data class NautileTopBarAction(
    val iconRes: Int,
    val contentDescription: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val testTag: String? = null,
)

/**
 * 顶部栏（设计 §7.1、§4.2）。
 *
 * 左侧 GTD 入口固定为 menu 图标；标题 headlineSmall、副标题 titleSmall/onSurfaceVariant；
 * 水平/垂直内边距 4dp，标题与副标题间距 4dp；底部 1dp outlineVariant 分隔线，不加阴影。
 */
@Composable
fun NautileTopBar(
    title: String,
    subtitle: String?,
    onOpenGtd: () -> Unit,
    actions: List<NautileTopBarAction>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 图标按钮触控区 48dp 自带内边距，标题直接从其右侧开始（§4.2）。
                NautileIconButton(
                    iconRes = R.drawable.ic_menu,
                    contentDescription = "打开 GTD 清单选择器",
                    onClick = onOpenGtd,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                actions.forEach { action ->
                    NautileIconButton(
                        iconRes = action.iconRes,
                        contentDescription = action.contentDescription,
                        onClick = action.onClick,
                        enabled = action.enabled,
                        testTag = action.testTag,
                    )
                }
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}
