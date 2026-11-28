package io.github.mazilin.nautile.ui.screen.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日志记录列表（05 §3.3；AC-L-02 倒序）。
 *
 * 按开始时间倒序，日期分组标题；行内显示分类路径、起止时间与时长；
 * 运行中行显示“计时中”和实时已用。整行可点打开详情，行尾箭头仅装饰。
 */
@Composable
fun LogRecordList(
    logs: List<TimeLogRow>,
    today: LocalDate,
    zoneId: ZoneId,
    onOpenLog: (TimeLogId) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val groups = logs.groupBy { it.log.startAt.atZone(zoneId).toLocalDate() }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .testTag("log_record_list"),
    ) {
        groups.forEach { (date, rows) ->
            item(key = "day_$date") {
                Text(
                    text = formatLogDayLabel(date, today),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = NautileSpacing.space4,
                            end = NautileSpacing.space4,
                            top = NautileSpacing.space3,
                            bottom = NautileSpacing.space1,
                        ),
                )
            }
            items(rows, key = { it.log.id.value }) { row ->
                LogRecordRow(row = row, zoneId = zoneId, onClick = { onOpenLog(row.log.id) })
            }
        }
    }
}

@Composable
private fun LogRecordRow(
    row: TimeLogRow,
    zoneId: ZoneId,
    onClick: () -> Unit,
) {
    val description = buildString {
        append(row.categoryPath)
        append("，")
        append(formatLogRange(row, zoneId))
        append("，时长 ")
        if (row.running) {
            append(formatElapsedText(row.duration))
            append("，计时中")
        } else {
            append(formatDurationText(row.duration))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = NautileSpacing.space4, vertical = NautileSpacing.space2)
            .testTag("log_record_${row.log.id.value}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.categoryPath,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(NautileSpacing.space1))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatLogRange(row, zoneId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(NautileSpacing.space2))
                Text(
                    text = if (row.running) {
                        "计时中 ${formatElapsedText(row.duration)}"
                    } else {
                        formatDurationText(row.duration)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.running) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = if (row.running) Modifier.testTag("log_running_label") else Modifier,
                )
            }
        }
        // 装饰性箭头：整行已有“打开记录详情”语义，不重复朗读（§4.2）。
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}
