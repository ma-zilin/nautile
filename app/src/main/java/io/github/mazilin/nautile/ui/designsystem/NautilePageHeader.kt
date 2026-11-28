package io.github.mazilin.nautile.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import io.github.mazilin.nautile.ui.theme.NautileSpacing

/**
 * 统一一级页顶栏（DS-01；06_R7统一视觉与交互规格 §1.2、§2.2）。
 *
 * - 固定最小高度 64dp（不含底部 1dp 分隔线），保证待办/日程/日志切换时分隔线 Y 轴零跳动；
 * - 无左侧导航图标时自动施加 `start = 16.dp` 使标题左缘精确对齐 16dp 基准线；
 *   存在 48dp 左侧图标按钮时自动使用 `start = 4.dp`（图标视觉左缘同样对齐 16dp）；
 * - 标题使用 `headlineSmall` + `heading()` 语义；副标题使用 `titleSmall`/`onSurfaceVariant`；
 * - 支持标题右侧紧邻槽位 [titleTrailing]（如 `NautileCalendarViewMenu`）与可展开区 [expandedContent]；
 * - 底部统一绘制 1dp `outlineVariant` 分隔线，滚动不叠加阴影（COL-06）。
 */
@Composable
fun NautilePageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleMaxLines: Int = 1,
    leading: (@Composable RowScope.() -> Unit)? = null,
    titleTrailing: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    expandedContent: (@Composable ColumnScope.() -> Unit)? = null,
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
                    .heightIn(min = PAGE_HEADER_MIN_HEIGHT)
                    .padding(
                        start = if (leading != null) NautileSpacing.space1 else NautileSpacing.space4,
                        end = NautileSpacing.space1,
                        top = NautileSpacing.space1,
                        bottom = NautileSpacing.space1,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    leading()
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NautileSpacing.space1),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space1),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .semantics { heading() },
                        )
                        if (titleTrailing != null) {
                            titleTrailing()
                        }
                    }
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = subtitleMaxLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                actions()
            }
            if (expandedContent != null) {
                expandedContent()
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

private val PAGE_HEADER_MIN_HEIGHT = 64.dp
