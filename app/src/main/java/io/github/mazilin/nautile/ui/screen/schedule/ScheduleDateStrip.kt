package io.github.mazilin.nautile.ui.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.ScheduleViewMode
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.theme.NautileScheduleMetrics
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import java.time.LocalDate

/**
 * 顶部日期滑动条（P-05；设计 §3.1、05 §1.2）。
 *
 * 最左侧为视图按钮（锚定菜单三选一，不轮换）；左右按钮按视图步进；
 * 日期格只点击选日期，左右滑动仅滚动日期条，不切日期（FR-S-14、AC-S-13）。
 */
@Composable
fun ScheduleDateStrip(
    dates: List<LocalDate>,
    selectedDate: LocalDate,
    today: LocalDate,
    viewMode: ScheduleViewMode,
    onDateSelected: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onViewModeSelected: (ScheduleViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var viewMenuOpen by remember { mutableStateOf(false) }

    // 选中日变化（点日期/步进/今天）时滚到可见；用户左右滑动不触发选中变化。
    LaunchedEffect(selectedDate, dates) {
        val index = dates.indexOf(selectedDate)
        if (index >= 0) listState.scrollToItem(index)
    }

    Row(
        modifier = modifier
            .padding(
                horizontal = NautileScheduleMetrics.PAGE_HORIZONTAL_PADDING,
                vertical = NautileSpacing.space2,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 最左侧视图按钮 + 锚定菜单；当前项尾部 check（05 §6.2）。
        Box {
            NautileIconButton(
                iconRes = R.drawable.ic_calendar_today,
                contentDescription = "视图：日/三天/周，当前：${viewMode.displayLabel()}",
                onClick = { viewMenuOpen = true },
                testTag = "schedule_view_button",
            )
            DropdownMenu(
                expanded = viewMenuOpen,
                onDismissRequest = { viewMenuOpen = false },
            ) {
                ScheduleViewMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.displayLabel()) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(mode.menuIconRes()),
                                contentDescription = null,
                            )
                        },
                        trailingIcon = if (mode == viewMode) {
                            {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            viewMenuOpen = false
                            onViewModeSelected(mode)
                        },
                        modifier = Modifier.testTag("schedule_view_mode_${mode.name}"),
                    )
                }
            }
        }
        NautileIconButton(
            iconRes = R.drawable.ic_chevron_left,
            contentDescription = previousDescription(viewMode),
            onClick = onPrevious,
        )
        LazyRow(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .testTag("schedule_date_strip"),
            horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(dates, key = { it.toEpochDay() }) { date ->
                ScheduleDateCell(
                    date = date,
                    selected = date == selectedDate,
                    isToday = date == today,
                    onClick = { onDateSelected(date) },
                )
            }
        }
        NautileIconButton(
            iconRes = R.drawable.ic_chevron_right,
            contentDescription = nextDescription(viewMode),
            onClick = onNext,
        )
        NautileIconButton(
            iconRes = R.drawable.ic_today,
            contentDescription = "回到今天",
            onClick = onToday,
            enabled = selectedDate != today,
            testTag = "schedule_today_button",
        )
    }
}

/** 单个日期格：48×64dp，选中态 secondaryContainer 全圆角；今天加圆点。 */
@Composable
private fun ScheduleDateCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(NautileScheduleMetrics.DATE_CELL_WIDTH)
            .height(NautileScheduleMetrics.DATE_CELL_HEIGHT)
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .testTag("schedule_date_$date"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = date.dayOfWeek.shortChinese(),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (isToday) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        ),
                )
            }
        }
    }
}

private fun previousDescription(mode: ScheduleViewMode): String = when (mode) {
    ScheduleViewMode.DAY -> "前一天"
    ScheduleViewMode.THREE_DAY -> "前三天"
    ScheduleViewMode.WEEK -> "上一周"
}

private fun nextDescription(mode: ScheduleViewMode): String = when (mode) {
    ScheduleViewMode.DAY -> "后一天"
    ScheduleViewMode.THREE_DAY -> "后三天"
    ScheduleViewMode.WEEK -> "下一周"
}

/** 视图菜单项图标（设计 05 §4.1）。 */
private fun ScheduleViewMode.menuIconRes(): Int = when (this) {
    ScheduleViewMode.DAY -> R.drawable.ic_calendar_view_day
    ScheduleViewMode.THREE_DAY -> R.drawable.ic_date_range
    ScheduleViewMode.WEEK -> R.drawable.ic_view_week
}
