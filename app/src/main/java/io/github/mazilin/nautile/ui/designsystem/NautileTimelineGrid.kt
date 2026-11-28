package io.github.mazilin.nautile.ui.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.ui.theme.NautileScheduleMetrics
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 共享 24 小时固定密度时间轴网格（DS-04；06_R7统一视觉与交互规格 §1.2、§3.2、§5.3、§6.2）。
 *
 * - 支持 1 / 3 / 7 列（日 / 三天 / 周），固定小时行高 `HOUR_HEIGHT = 60.dp`（1 min = 1 dp），不做双指缩放；
 * - 泛型接收 [TimelineBlockRenderItem]，零业务模型耦合，日程与时间日志 100% 复用；
 * - 无数据时直接展示干净 24h 时间轴网格，不叠加任何遮挡性空态覆盖层；
 * - 今天列内绘制当前时间横线与左侧圆点；支持外部传入 [scrollState] 或 [autoScrollToNowKey] 纵向定位。
 */
@Composable
fun <T> NautileTimelineGrid(
    dates: List<LocalDate>,
    today: LocalDate,
    now: Instant,
    zoneId: ZoneId,
    items: List<TimelineBlockRenderItem<T>>,
    onItemClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    autoScrollToNowKey: Any? = null,
    nowIndicatorColor: Color = MaterialTheme.colorScheme.primary,
    columnHeaderFormatter: (LocalDate, Int) -> String = ::defaultTimelineColumnHeader,
    gridTestTag: String? = null,
    hourRowTestTagPrefix: String = "timeline_hour_row_",
    columnTestTagPrefix: String = "timeline_grid_column_",
) {
    val density = LocalDensity.current

    // 可选自动滚动到当前时刻视野（保留约 2 小时上方上下文，便于立即看到当前时段）。
    LaunchedEffect(autoScrollToNowKey) {
        if (autoScrollToNowKey == null || today !in dates) return@LaunchedEffect
        val zonedNow = now.atZone(zoneId)
        val minutesOfDay = zonedNow.hour * 60 + zonedNow.minute
        val targetMinute = (minutesOfDay - AUTO_SCROLL_CONTEXT_MINUTES).coerceAtLeast(0)
        val targetPx = with(density) {
            (NautileScheduleMetrics.HOUR_HEIGHT * (targetMinute / 60f)).roundToPx()
        }
        scrollState.scrollTo(targetPx)
    }

    Column(
        modifier = modifier
            .then(if (gridTestTag != null) Modifier.testTag(gridTestTag) else Modifier),
    ) {
        // 固定列头行：日视图“11月28日 周六”、三天视图“周六 11/28”、周视图“11/28”。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NautileSpacing.space8)
                .padding(horizontal = NautileScheduleMetrics.PAGE_HORIZONTAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(NautileScheduleMetrics.HOUR_LABEL_WIDTH))
            dates.forEach { date ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = columnHeaderFormatter(date, dates.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (date == today) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                }
            }
        }

        // 24h 可垂直滚动主体（无数据时直接展示干净网格）。
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = NautileScheduleMetrics.PAGE_HORIZONTAL_PADDING),
        ) {
            TimelineHourScale(hourRowTestTagPrefix = hourRowTestTagPrefix)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(NautileScheduleMetrics.DAY_HEIGHT),
            ) {
                dates.forEachIndexed { index, date ->
                    val dayItems = items.filter { it.dayIndex == index }
                    TimelineDayColumn(
                        date = date,
                        dayIndex = index,
                        today = today,
                        now = now,
                        zoneId = zoneId,
                        items = dayItems,
                        onItemClick = onItemClick,
                        nowIndicatorColor = nowIndicatorColor,
                        columnTestTag = "$columnTestTagPrefix$index",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(
                                start = if (index == 0) {
                                    0.dp
                                } else {
                                    NautileScheduleMetrics.COLUMN_GAP / 2
                                },
                            ),
                    )
                }
            }
        }
    }
}

/** 左侧 24 小时固定刻度列（宽 44dp，每行 60dp）。 */
@Composable
private fun TimelineHourScale(
    hourRowTestTagPrefix: String,
) {
    Column(modifier = Modifier.width(NautileScheduleMetrics.HOUR_LABEL_WIDTH)) {
        repeat(HOURS_PER_DAY) { hour ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NautileScheduleMetrics.HOUR_HEIGHT)
                    .testTag("$hourRowTestTagPrefix$hour"),
                contentAlignment = Alignment.TopEnd,
            ) {
                Text(
                    text = "%02d:00".format(hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = NautileSpacing.space1),
                )
            }
        }
    }
}

/** 单日列：整点/半点 1dp 网格线 + 8 色事件/日志块 + 今天当前时间线。 */
@Composable
private fun <T> TimelineDayColumn(
    date: LocalDate,
    dayIndex: Int,
    today: LocalDate,
    now: Instant,
    zoneId: ZoneId,
    items: List<TimelineBlockRenderItem<T>>,
    onItemClick: (T) -> Unit,
    nowIndicatorColor: Color,
    columnTestTag: String,
    modifier: Modifier = Modifier,
) {
    val hourLineColor = MaterialTheme.colorScheme.outlineVariant
    val halfHourLineColor = MaterialTheme.colorScheme.surfaceContainerHighest

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .testTag(columnTestTag),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hourPx = NautileScheduleMetrics.HOUR_HEIGHT.toPx()
            val strokePx = 1.dp.toPx()
            for (hour in 0..HOURS_PER_DAY) {
                val y = hour * hourPx
                drawLine(
                    color = hourLineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokePx,
                )
                if (hour < HOURS_PER_DAY) {
                    drawLine(
                        color = halfHourLineColor,
                        start = Offset(0f, y + hourPx / 2f),
                        end = Offset(size.width, y + hourPx / 2f),
                        strokeWidth = strokePx,
                    )
                }
            }
        }

        items.forEach { block ->
            key(block.id, dayIndex) {
                val clampedStart = block.startMinute.coerceIn(0, MINUTES_PER_DAY)
                val clampedEnd = block.endMinute.coerceIn(clampedStart, MINUTES_PER_DAY)
                val top = NautileScheduleMetrics.HOUR_HEIGHT * (clampedStart / 60f)
                val rawHeight = NautileScheduleMetrics.HOUR_HEIGHT * ((clampedEnd - clampedStart) / 60f)
                val maxAvailableHeight = (NautileScheduleMetrics.DAY_HEIGHT - top)
                    .coerceAtLeast(NautileScheduleMetrics.EVENT_BLOCK_MIN_HEIGHT)
                val height = rawHeight
                    .coerceAtLeast(NautileScheduleMetrics.EVENT_BLOCK_MIN_HEIGHT)
                    .coerceAtMost(maxAvailableHeight)
                val columnCount = block.totalColumns.coerceAtLeast(1)
                val slotWidth = maxWidth / columnCount
                val blockWidth = if (columnCount > 1) {
                    (slotWidth - NautileScheduleMetrics.COLUMN_GAP).coerceAtLeast(MIN_BLOCK_WIDTH)
                } else {
                    slotWidth
                }
                val x = slotWidth * block.column.coerceIn(0, columnCount - 1)

                NautileTimelineBlock(
                    title = block.title,
                    timeLabel = block.timeLabel,
                    subtitle = block.subtitle,
                    colorIndex = block.colorIndex,
                    onClick = { onItemClick(block.item) },
                    contentDescription = block.contentDescription,
                    testTag = block.testTag,
                    modifier = Modifier
                        .offset(x = x, y = top)
                        .width(blockWidth)
                        .height(height),
                )
            }
        }

        // 当前时间线仅在“今天”的列内绘制，每分钟跳位、不使用无限动画。
        if (date == today) {
            val dayStart = date.atStartOfDay(zoneId).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant()
            if (!now.isBefore(dayStart) && now.isBefore(dayEnd)) {
                val fraction = (
                    (now.toEpochMilli() - dayStart.toEpochMilli()).toDouble() /
                        MILLIS_PER_DAY
                    ).toFloat().coerceIn(0f, 1f)
                val lineY = NautileScheduleMetrics.DAY_HEIGHT * fraction
                Box(
                    modifier = Modifier
                        .offset(y = lineY)
                        .fillMaxWidth()
                        .height(NautileScheduleMetrics.NOW_LINE_HEIGHT)
                        .background(nowIndicatorColor),
                )
                Box(
                    modifier = Modifier
                        .offset(
                            x = 0.dp,
                            y = (lineY - NautileScheduleMetrics.NOW_DOT_SIZE / 2)
                                .coerceAtLeast(0.dp),
                        )
                        .width(NautileScheduleMetrics.NOW_DOT_SIZE)
                        .height(NautileScheduleMetrics.NOW_DOT_SIZE)
                        .background(nowIndicatorColor, CircleShape),
                )
            }
        }
    }
}

/** 默认列头格式：1 列显示完整日期与星期，3 列显示星期+月/日，7 列显示月/日。 */
fun defaultTimelineColumnHeader(date: LocalDate, columnCount: Int): String = when {
    columnCount <= 1 -> "${date.monthValue}月${date.dayOfMonth}日 周${date.dayOfWeek.shortChineseLabel()}"
    columnCount <= 3 -> "周${date.dayOfWeek.shortChineseLabel()} ${date.monthValue}/${date.dayOfMonth}"
    else -> "${date.monthValue}/${date.dayOfMonth}"
}

private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_DAY = 24 * 60
private const val MILLIS_PER_DAY = 86_400_000.0
private const val AUTO_SCROLL_CONTEXT_MINUTES = 120
private val MIN_BLOCK_WIDTH = 24.dp
