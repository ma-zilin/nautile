package io.github.mazilin.nautile.ui.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R

/**
 * 任务行（设计 §7.2 CMP-04、§4.2）。
 *
 * 扁平行、无卡片圆角；最小高 56dp、水平 16dp、上下 12dp；标题 bodyLarge、次要信息 bodySmall。
 * closed 时标题改用 onSurfaceVariant + 删除线，语义通过 stateDescription 保留。
 */
@Composable
fun NautileTaskRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    closed: Boolean = false,
    showDivider: Boolean = false,
) {
    NautileListRow(
        onClick = onClick,
        modifier = modifier,
        closed = closed,
        showDivider = showDivider,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (closed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (closed) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 项目行（设计 §7.2 CMP-04、§4.2）。
 *
 * 与任务行同高同内边距；项目名不截断（最多两行），计数与项目名间距 8dp，计数用 onSurfaceVariant。
 */
@Composable
fun NautileProjectRow(
    title: String,
    taskCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    closed: Boolean = false,
    showDivider: Boolean = false,
) {
    NautileListRow(
        onClick = onClick,
        modifier = modifier,
        closed = closed,
        showDivider = showDivider,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (closed) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (closed) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$taskCount 个任务",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * 列表行公共骨架：整行可点、合并子语义、行尾装饰箭头、可选 1dp 分隔线。
 * 分隔线颜色只用 outlineVariant，左侧内缩 16dp（§1.2、§4.2）。
 */
@Composable
private fun NautileListRow(
    onClick: () -> Unit,
    modifier: Modifier,
    closed: Boolean,
    showDivider: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    if (closed) stateDescription = "已结项"
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
            Spacer(modifier = Modifier.width(8.dp))
            // 装饰性箭头：整行已有可点语义，不重复朗读（§6.2 #14）。
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}
