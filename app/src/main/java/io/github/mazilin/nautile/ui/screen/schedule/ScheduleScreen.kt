package io.github.mazilin.nautile.ui.screen.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSourceFilter
import io.github.mazilin.nautile.domain.model.ScheduleViewMode
import io.github.mazilin.nautile.ui.designsystem.NautileEmptyState
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.theme.NautileScheduleMetrics
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import java.time.LocalDate

/**
 * 日程页 P-05（R6-G2-T3；设计 05 §2.2、§3.1）。
 *
 * 顶部“日程”+范围标题、搜索（未开放时禁用）与设置；日期条最左为视图按钮；
 * 日/三天/周共用固定时间密度；来源筛选三态不改变数据；无事件时显示空态，
 * 日期条/视图/来源/新建仍可用。新建与详情面板属 G3，本页只保留入口与回调。
 */
@Composable
fun ScheduleScreen(
    state: ScheduleUiState = ScheduleUiState(),
    onOpenSettings: () -> Unit = {},
    onDateSelected: (LocalDate) -> Unit = {},
    onRangeStep: (Int) -> Unit = {},
    onToday: () -> Unit = {},
    onViewModeSelected: (ScheduleViewMode) -> Unit = {},
    onSourceFilterSelected: (ScheduleSourceFilter) -> Unit = {},
    onOpenNew: () -> Unit = {},
    onOpenDetail: (ScheduleId) -> Unit = {},
    onDismissMessage: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScheduleTopBar(
                rangeTitle = state.rangeTitle,
                onOpenSettings = onOpenSettings,
            )
            ScheduleDateStrip(
                dates = stripDates(state),
                selectedDate = state.selectedDate,
                today = state.today,
                viewMode = state.viewMode,
                onDateSelected = onDateSelected,
                onPrevious = { onRangeStep(-1) },
                onNext = { onRangeStep(1) },
                onToday = onToday,
                onViewModeSelected = onViewModeSelected,
            )
            SourceFilterRow(
                selected = state.sourceFilter,
                onSelected = onSourceFilterSelected,
            )
            if (state.message != null) {
                MessageBanner(message = state.message, onDismiss = onDismissMessage)
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ScheduleTimeGrid(
                    dates = state.columnDates,
                    viewMode = state.viewMode,
                    today = state.today,
                    now = state.now,
                    zoneId = state.zoneId,
                    blocks = state.events,
                    tagNames = state.tags.associate { it.id.value to it.name },
                    onEventClick = onOpenDetail,
                )
                if (state.isEmpty) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("schedule_empty_state"),
                        contentAlignment = Alignment.Center,
                    ) {
                        NautileEmptyState(
                            title = "暂无日程",
                            description = emptyDescription(state.sourceFilter),
                            iconRes = R.drawable.ic_calendar_today,
                        )
                    }
                }
            }
        }
        // 新建入口：右下、导航上方；面板在 G3 接入，本闸门只保证入口可见（AC-D-07 部分）。
        FloatingActionButton(
            onClick = onOpenNew,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(NautileSpacing.space4)
                .testTag("add_schedule"),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = "新建日程",
            )
        }
    }
}

/** 顶部栏：标题 + 范围副标题 + 搜索（未开放禁用） + 设置。 */
@Composable
private fun ScheduleTopBar(
    rangeTitle: String,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "日程",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (rangeTitle.isNotEmpty()) {
                        Text(
                            text = rangeTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("schedule_range_title"),
                        )
                    }
                }
                NautileIconButton(
                    iconRes = R.drawable.ic_search,
                    contentDescription = "搜索日程，功能尚未开放",
                    onClick = {},
                    enabled = false,
                )
                NautileIconButton(
                    iconRes = R.drawable.ic_settings,
                    contentDescription = "设置",
                    onClick = onOpenSettings,
                    testTag = "settings_entry",
                )
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/** 来源筛选三态：课程表 / 合并 / 仅普通；标签不参与筛选（D-new1）。 */
@Composable
private fun SourceFilterRow(
    selected: ScheduleSourceFilter,
    onSelected: (ScheduleSourceFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NautileScheduleMetrics.PAGE_HORIZONTAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space2),
    ) {
        ScheduleSourceFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelected(filter) },
                label = { Text(filter.displayLabel()) },
                modifier = Modifier.testTag(sourceFilterTag(filter)),
            )
        }
    }
}

/** 错误/提示横幅：error 角色 + liveRegion=Polite，点按关闭（不假成功）。 */
@Composable
private fun MessageBanner(message: String, onDismiss: () -> Unit) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss)
            .padding(horizontal = NautileScheduleMetrics.PAGE_HORIZONTAL_PADDING)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("schedule_message"),
    )
}

/** 日期条数据：周视图从周一 7 天；日/三天从可见范围前 3 天起共 14 天。 */
private fun stripDates(state: ScheduleUiState): List<LocalDate> {
    val start = if (state.viewMode == ScheduleViewMode.WEEK) {
        state.visibleRange.startInclusive
    } else {
        state.visibleRange.startInclusive.minusDays(3)
    }
    return List(14) { start.plusDays(it.toLong()) }
}

private fun emptyDescription(filter: ScheduleSourceFilter): String =
    if (filter == ScheduleSourceFilter.COURSE_ONLY) {
        "课程表暂无日程"
    } else {
        "点右下角新建一条日程"
    }

private fun sourceFilterTag(filter: ScheduleSourceFilter): String = when (filter) {
    ScheduleSourceFilter.COURSE_ONLY -> "schedule_source_course"
    ScheduleSourceFilter.MERGED -> "schedule_source_merged"
    ScheduleSourceFilter.REGULAR_ONLY -> "schedule_source_regular"
}
