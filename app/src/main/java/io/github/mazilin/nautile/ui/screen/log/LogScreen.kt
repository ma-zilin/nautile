package io.github.mazilin.nautile.ui.screen.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.ui.designsystem.NautileEmptyState
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.theme.NautileSpacing

/**
 * 日志记录页 P-07（R6-G4-T3/T4；05 §2.4/§3.3）。
 *
 * 顶部“日志”+记录模式标识+设置；快捷入口最近使用优先；记录按倒序分组；
 * 空态仍可用快捷入口与补录；FAB 补录。记录模式样式不使用无限动画。
 */
@Composable
fun LogScreen(
    state: LogUiState = LogUiState(),
    onOpenSettings: () -> Unit = {},
    onCategoryTap: (TimeCategoryId) -> Unit = {},
    onShowAllCategories: () -> Unit = {},
    onOpenLog: (TimeLogId) -> Unit = {},
    onOpenManual: () -> Unit = {},
    onDismissMessage: () -> Unit = {},
    onNavigationVisibilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val latestNavigationCallback by rememberUpdatedState(onNavigationVisibilityChanged)

    // 记录列表向下滚动时隐藏导航与计时条；向上或回到顶部恢复。空列表始终可见。
    LaunchedEffect(listState, state.isEmpty) {
        if (state.isEmpty) {
            latestNavigationCallback(true)
            return@LaunchedEffect
        }
        var lastIndex = 0
        var lastOffset = 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val movedDown = index > lastIndex || (index == lastIndex && offset > lastOffset + SCROLL_THRESHOLD_PX)
                val movedUp = index < lastIndex || (index == lastIndex && offset < lastOffset - SCROLL_THRESHOLD_PX)
                when {
                    index == 0 && offset == 0 -> latestNavigationCallback(true)
                    movedDown -> latestNavigationCallback(false)
                    movedUp -> latestNavigationCallback(true)
                }
                lastIndex = index
                lastOffset = offset
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LogTopBar(onOpenSettings = onOpenSettings)
            if (state.categories.isEmpty()) {
                Text(
                    text = "还没有分类，请到设置 > 分类管理添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NautileSpacing.space4, vertical = NautileSpacing.space2)
                        .testTag("log_no_categories"),
                )
            }
            // 快捷入口常驻：无历史或分类为空时只显示“全部分类”，仍可打开选择器（AC-T-21）。
            CategoryQuickEntry(
                categories = state.recentCategories,
                running = state.running,
                onCategoryTap = onCategoryTap,
                onShowAll = onShowAllCategories,
                modifier = Modifier.padding(vertical = NautileSpacing.space2),
            )
            if (state.message != null) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismissMessage)
                        .padding(horizontal = NautileSpacing.space4)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("log_message"),
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.isEmpty) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("log_empty_state"),
                        contentAlignment = Alignment.Center,
                    ) {
                        NautileEmptyState(
                            title = "还没有记录",
                            description = "点上方分类开始计时，或补录一条记录",
                            iconRes = R.drawable.ic_schedule,
                        )
                    }
                } else {
                    LogRecordList(
                        logs = state.logs,
                        today = state.today,
                        zoneId = state.zoneId,
                        onOpenLog = onOpenLog,
                        listState = listState,
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = onOpenManual,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(NautileSpacing.space4)
                .testTag("add_log"),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = "补录记录",
            )
        }
    }
}

@Composable
private fun LogTopBar(onOpenSettings: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NautileSpacing.space1, vertical = NautileSpacing.space1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space1),
            ) {
                Text(
                    text = "日志",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = NautileSpacing.space3)
                        .semantics { heading() },
                )
                // 模式切换属 G5 统计接入；本轮如实显示“记录”，不伪造可点轮换（AC-T-20 不计入 G4）。
                Row(
                    modifier = Modifier
                        .padding(horizontal = NautileSpacing.space2)
                        .testTag("log_mode_indicator"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_schedule),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "记录",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = NautileSpacing.space1),
                    )
                }
                NautileIconButton(
                    iconRes = R.drawable.ic_settings,
                    contentDescription = "设置",
                    onClick = onOpenSettings,
                    testTag = "log_settings_entry",
                )
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

private const val SCROLL_THRESHOLD_PX = 8
