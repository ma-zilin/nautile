package io.github.mazilin.nautile.ui.screen.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.ui.theme.NautileScheduleMetrics
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 事件块（P-05；设计 §3.1、05 §1.4）。
 *
 * 整块可点打开详情（详情在 G3 接入）；标题、时间、来源与标签均为文字信息，
 * 颜色不承担唯一识别。重叠分列由外层按 [ScheduleEventBlock.column] 定位。
 */
@Composable
fun ScheduleEventBlockView(
    block: ScheduleEventBlock,
    tagName: String?,
    zoneId: ZoneId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 小块只显示标题，避免 24dp 最小高度内文字被裁切（重叠可读性）。 */
    compact: Boolean = false,
) {
    val event = block.event
    val isImported = event.source == ScheduleSource.IMPORTED
    val container = if (isImported) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val content = if (isImported) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .testTag("schedule_event_${event.id.value}"),
        shape = RoundedCornerShape(NautileScheduleMetrics.EVENT_BLOCK_CORNER),
        color = container,
        contentColor = content,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (compact) return@Column
            Text(
                text = "${timeFormatter(zoneId).format(event.startAt)}-" +
                    timeFormatter(zoneId).format(event.endAt),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 来源与标签用文字表达（颜色不是唯一信息）；无标签时不显示“无标签”行。
            val sourceLabel = when (event.source) {
                ScheduleSource.MANUAL -> "手动创建"
                ScheduleSource.AI -> "AI创建"
                ScheduleSource.IMPORTED -> "导入"
            }
            Text(
                text = if (tagName != null) "$sourceLabel · $tagName" else sourceLabel,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 时间格式器按注入时区构造；不使用系统默认时区直接格式化。 */
private fun timeFormatter(zoneId: ZoneId): DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId)
