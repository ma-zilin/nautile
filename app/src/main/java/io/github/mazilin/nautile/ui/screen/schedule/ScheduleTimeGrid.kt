package io.github.mazilin.nautile.ui.screen.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleViewMode
import io.github.mazilin.nautile.ui.theme.NautileScheduleMetrics
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 固定密度 24h 时间网格（P-05；设计 §3.1）。
 *
 * 日/三天/周共用同一小时高度（[NautileScheduleMetrics.HOUR_HEIGHT]），垂直滚动，
 * 无缩放入口（AC-S-13）；事件块按 [ScheduleEventBlock] 比例定位并按重叠列分宽；
 * 当前时间线仅在“今天”的列内显示，每分钟跳位、无动画。
 */
@Composable
fun ScheduleTimeGrid(
    dates: List<LocalDate>,
    viewMode: ScheduleViewMode,
    today: LocalDate,
    now: Instant,
    zoneId: ZoneId,
    blocks: List<ScheduleEventBlock>,
    tagNames: Map<Long, String>,
    onEventClick: (ScheduleId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .testTag("schedule_time_grid")
            .verticalScroll(rememberScrollState()),
    ) {
        // 列头：三天显示“周三 11/27”，周显示“11/24”，日显示完整日期。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NautileSpacing.space8)
                .padding(horizontal = NautileScheduleMetrics.PAGE_HORIZONTAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(NautileScheduleMetrics.HOUR_LABEL_WIDTH))
            dates.forEach { date ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = columnHeader(date, viewMode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NautileScheduleMetrics.DAY_HEIGHT)
                .padding(horizontal = NautileScheduleMetrics.PAGE_HORIZONTAL_PADDING),
        ) {
            HourScale()
            Row(modifier = Modifier.weight(1f).fillMaxHeight()) {
                dates.forEachIndexed { index, date ->
                    DayColumn(
                        date = date,
                        dayIndex = index,
                        today = today,
                        now = now,
                        zoneId = zoneId,
                        blocks = blocks.filter { it.dayIndex == index },
                        tagNames = tagNames,
                        onEventClick = onEventClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(
                                start = if (index == 0) 0.dp else {
                                    NautileScheduleMetrics.COLUMN_GAP / 2
                                },
                            ),
                    )
                }
            }
        }
    }
}

/** 左侧小时刻度：24 行，每行固定小时高度；同时提供小时行高测试锚点。 */
@Composable
private fun HourScale() {
    Column(modifier = Modifier.width(NautileScheduleMetrics.HOUR_LABEL_WIDTH)) {
        repeat(24) { hour ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NautileScheduleMetrics.HOUR_HEIGHT)
                    .testTag("schedule_hour_row_$hour"),
                contentAlignment = Alignment.TopEnd,
            ) {
                Text(
                    text = hourLabel(hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = NautileSpacing.space1),
                )
            }
        }
    }
}

/** 单日列：整点/半点网格线 + 事件块 + 今天当前时间线。 */
@Composable
private fun DayColumn(
    date: LocalDate,
    dayIndex: Int,
    today: LocalDate,
    now: Instant,
    zoneId: ZoneId,
    blocks: List<ScheduleEventBlock>,
    tagNames: Map<Long, String>,
    onEventClick: (ScheduleId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hourColor = MaterialTheme.colorScheme.outlineVariant
    val halfHourColor = MaterialTheme.colorScheme.surfaceContainerHighest
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .testTag("schedule_grid_column_$dayIndex"),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hourPx = NautileScheduleMetrics.HOUR_HEIGHT.toPx()
            val stroke = 1.dp.toPx()
            for (hour in 0..24) {
                val y = hour * hourPx
                drawLine(
                    color = hourColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = stroke,
                )
                if (hour < 24) {
                    drawLine(
                        color = halfHourColor,
                        start = Offset(0f, y + hourPx / 2f),
                        end = Offset(size.width, y + hourPx / 2f),
                        strokeWidth = stroke,
                    )
                }
            }
        }

        blocks.forEach { block ->
            val top = NautileScheduleMetrics.DAY_HEIGHT * block.topFraction.coerceIn(0f, 1f)
            val rawHeight = NautileScheduleMetrics.DAY_HEIGHT * block.heightFraction.coerceIn(0f, 1f)
            val height = maxOf(rawHeight, NautileScheduleMetrics.EVENT_BLOCK_MIN_HEIGHT)
            val columnCount = block.columnCount.coerceAtLeast(1)
            val slotWidth = maxWidth / columnCount
            val x = slotWidth * block.column.coerceIn(0, columnCount - 1)
            ScheduleEventBlockView(
                block = block,
                tagName = block.event.tagId?.let { tagNames[it.value] },
                zoneId = zoneId,
                onClick = { onEventClick(block.event.id) },
                modifier = Modifier
                    .offset(x = x, y = top)
                    .width(slotWidth - 1.dp)
                    .height(height),
                compact = height < 48.dp,
            )
        }

        if (date == today) {
            val dayStart = date.atStartOfDay(zoneId).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant()
            if (!now.isBefore(dayStart) && now.isBefore(dayEnd)) {
                val fraction = (
                    (now.toEpochMilli() - dayStart.toEpochMilli()).toDouble() /
                        86_400_000.0
                    ).toFloat().coerceIn(0f, 1f)
                val lineY = NautileScheduleMetrics.DAY_HEIGHT * fraction
                Box(
                    modifier = Modifier
                        .offset(y = lineY)
                        .fillMaxWidth()
                        .height(NautileScheduleMetrics.NOW_LINE_HEIGHT)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Box(
                    modifier = Modifier
                        .offset(
                            x = 0.dp,
                            y = maxOf(
                                lineY - NautileScheduleMetrics.NOW_DOT_SIZE / 2,
                                0.dp,
                            ),
                        )
                        .width(NautileScheduleMetrics.NOW_DOT_SIZE)
                        .height(NautileScheduleMetrics.NOW_DOT_SIZE)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
    }
}

private fun hourLabel(hour: Int): String = "%02d:00".format(hour)

private fun columnHeader(date: LocalDate, viewMode: ScheduleViewMode): String =
    when (viewMode) {
        ScheduleViewMode.DAY -> "${date.monthValue}月${date.dayOfMonth}日 " +
            "周${date.dayOfWeek.shortChinese()}"
        ScheduleViewMode.THREE_DAY -> "周${date.dayOfWeek.shortChinese()} " +
            "${date.monthValue}/${date.dayOfMonth}"
        ScheduleViewMode.WEEK -> "${date.monthValue}/${date.dayOfMonth}"
    }
