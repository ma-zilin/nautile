package io.github.mazilin.nautile.ui.screen.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.ui.theme.NautileSpacing

/**
 * 日志记录页分类快捷入口（05 §3.3）。
 *
 * 最近使用优先；末尾“全部分类”入口打开两级选择器。点分类开始计时；
 * 运行中的分类显示已用时长与“计时中”，再次点按只提示，不重置、不结束（AC-T-21）。
 * 不使用无限动画：运行中只切换为静态 `primaryContainer` 样式（MOT-13）。
 */
@Composable
fun CategoryQuickEntry(
    categories: List<TimeCategory>,
    running: List<RunningTimerUi>,
    onCategoryTap: (TimeCategoryId) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val runningById = running.associateBy { it.categoryId }
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NautileSpacing.space4),
        horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space2),
    ) {
        items(categories, key = { it.id.value }) { category ->
            val runningTimer = runningById[category.id]
            val path = categoryPath(categories, category.id)
            val description = if (runningTimer != null) {
                "正在计时：$path，已用 ${formatElapsedText(runningTimer.elapsed)}"
            } else {
                "开始计时：$path"
            }
            Column(
                modifier = Modifier
                    .widthIn(min = 96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (runningTimer != null) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    )
                    .clickable { onCategoryTap(category.id) }
                    .padding(horizontal = NautileSpacing.space3, vertical = NautileSpacing.space2)
                    .heightIn(min = 64.dp)
                    // 合并子语义为一次朗读，同时保留整块点击动作（§1.3）。
                    .semantics(mergeDescendants = true) { this.contentDescription = description }
                    .testTag("log_category_quick_${category.id.value}"),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (runningTimer != null) {
                        Icon(
                            painter = painterResource(R.drawable.ic_timer),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_play_arrow),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (runningTimer != null) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(start = NautileSpacing.space1),
                    )
                }
                Text(
                    text = if (runningTimer != null) {
                        "计时中 ${formatElapsedText(runningTimer.elapsed)}"
                    } else {
                        "点按开始"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (runningTimer != null) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(start = NautileSpacing.space1),
                )
            }
        }
        // “全部分类”常驻末尾：无论有无最近使用、分类是否为空都可见（AC-T-21）。
        item(key = "all_categories") {
            AllCategoriesEntry(onClick = onShowAll)
        }
    }
}

/** “全部分类”入口：常驻快捷入口末尾（05 §3.3）。 */
@Composable
private fun AllCategoriesEntry(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(min = 96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = NautileSpacing.space3, vertical = NautileSpacing.space2)
            .heightIn(min = 64.dp)
            .testTag("log_all_categories"),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_category),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "全部分类",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = NautileSpacing.space1),
            )
        }
        Text(
            text = "选择分类",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = NautileSpacing.space1),
        )
    }
}
