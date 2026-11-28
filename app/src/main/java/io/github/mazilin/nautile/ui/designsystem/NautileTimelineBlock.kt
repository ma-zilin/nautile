package io.github.mazilin.nautile.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.ui.theme.LocalNautileEventColors

/**
 * 纯 UI 时间轴色块渲染项（DS-04；日程与时间日志 100% 共用，零业务模型耦合）。
 *
 * @property id 唯一渲染键
 * @property item 原始业务对象（点击事件原样回传给调用方）
 * @property dayIndex 所在日列索引（0 until dates.size）
 * @property startMinute 当日起始分钟（0..1440）
 * @property endMinute 当日结束分钟（0..1440）
 * @property column 重叠分列索引（0 until totalColumns）
 * @property totalColumns 所在重叠簇总列数（>= 1）
 * @property title 主标题（日程标题或日志分类路径）
 * @property timeLabel 时间段文案（如 `08:00–09:30` 或 `08:30–计时中`）
 * @property subtitle 可选第三行信息（日程标签名 / 日志时长或运行中实时时长）
 * @property colorIndex 8 色槽索引（0..7，由 `LocalNautileEventColors` 自动规整）
 * @property contentDescription 可选无障碍朗读文案；为 null 时自动拼装完整标题、时间与副标题
 * @property testTag 可选测试标签（如 `schedule_event_1`、`log_block_1`）
 */
@Immutable
data class TimelineBlockRenderItem<T>(
    val id: Any,
    val item: T,
    val dayIndex: Int,
    val startMinute: Int,
    val endMinute: Int,
    val column: Int,
    val totalColumns: Int,
    val title: String,
    val timeLabel: String,
    val subtitle: String? = null,
    val colorIndex: Int,
    val contentDescription: String? = null,
    val testTag: String? = null,
)

/**
 * 统一时间轴色块卡片（DS-04；06_R7统一视觉与交互规格 §3.2，#15/#16/#22）。
 *
 * - 消费 `LocalNautileEventColors.current.slot(colorIndex)`，自动适配浅/深色 8 组色槽；
 * - 8dp 圆角（`MaterialTheme.shapes.small`）+ `BorderStroke(1.dp, slot.border)` 清晰外框；
 * - 左侧 3dp `slot.accent` 满高强调竖条，窄列（`< 52.dp`）自动隐藏以优先保障文字可读宽度；
 * - 内边距：`horizontal = 8.dp, vertical = 4.dp`（显示 3dp 竖条时起始偏移 10dp；compact 态 6/2dp）；
 * - 三档高度自适应排版：`≥ 48dp` 标准三行、`30–47dp` 中等两行、`< 30dp` compact 单行降级。
 */
@Composable
fun NautileTimelineBlock(
    title: String,
    timeLabel: String,
    colorIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    contentDescription: String? = null,
    testTag: String? = null,
) {
    val slot = LocalNautileEventColors.current.slot(colorIndex)
    val secondaryColor = slot.onContainer.copy(alpha = SECONDARY_TEXT_ALPHA)
    val mergedDescription = contentDescription ?: buildString {
        append(title)
        if (timeLabel.isNotBlank()) {
            append("，")
            append(timeLabel)
        }
        if (!subtitle.isNullOrBlank()) {
            append("，")
            append(subtitle)
        }
    }

    Surface(
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.contentDescription = mergedDescription
                role = Role.Button
            }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        shape = MaterialTheme.shapes.small,
        color = slot.container,
        contentColor = slot.onContainer,
        border = BorderStroke(BLOCK_BORDER_WIDTH, slot.border),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val showAccentBar = maxWidth >= ACCENT_BAR_MIN_BLOCK_WIDTH
            val isCompact = maxHeight < COMPACT_HEIGHT_THRESHOLD
            val isStandard = maxHeight >= STANDARD_HEIGHT_THRESHOLD
            val isWideBlock = maxWidth >= WIDE_BLOCK_WIDTH

            if (showAccentBar) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(ACCENT_BAR_WIDTH)
                        .background(slot.accent),
                )
            }

            if (isCompact) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = if (showAccentBar) COMPACT_START_WITH_ACCENT else COMPACT_HORIZONTAL_PADDING,
                            end = COMPACT_HORIZONTAL_PADDING,
                            top = COMPACT_VERTICAL_PADDING,
                            bottom = COMPACT_VERTICAL_PADDING,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = slot.onContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val startTimePreview = extractStartTimePreview(timeLabel)
                    if (isWideBlock && startTimePreview.isNotEmpty()) {
                        Text(
                            text = " · $startTimePreview",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = if (showAccentBar) STANDARD_START_WITH_ACCENT else STANDARD_HORIZONTAL_PADDING,
                            end = STANDARD_HORIZONTAL_PADDING,
                            top = STANDARD_VERTICAL_PADDING,
                            bottom = STANDARD_VERTICAL_PADDING,
                        ),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = slot.onContainer,
                        maxLines = if (isStandard && isWideBlock) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (timeLabel.isNotBlank()) {
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isStandard && !subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 从形如 `09:00–09:20` 或 `09:00-09:20` 的时间段提取紧凑起始时刻预览。 */
private fun extractStartTimePreview(timeLabel: String): String {
    val trimmed = timeLabel.trim()
    if (trimmed.isEmpty()) return ""
    val dashIndex = trimmed.indexOfFirst { it == '–' || it == '-' || it == '—' }
    return if (dashIndex > 0) trimmed.substring(0, dashIndex).trim() else trimmed
}

private const val SECONDARY_TEXT_ALPHA = 0.78f
private val BLOCK_BORDER_WIDTH = 1.dp
private val ACCENT_BAR_WIDTH = 3.dp
private val ACCENT_BAR_MIN_BLOCK_WIDTH = 52.dp
private val WIDE_BLOCK_WIDTH = 140.dp
private val COMPACT_HEIGHT_THRESHOLD = 30.dp
private val STANDARD_HEIGHT_THRESHOLD = 48.dp
private val STANDARD_HORIZONTAL_PADDING = 8.dp
private val STANDARD_START_WITH_ACCENT = 10.dp
private val STANDARD_VERTICAL_PADDING = 4.dp
private val COMPACT_HORIZONTAL_PADDING = 6.dp
private val COMPACT_START_WITH_ACCENT = 8.dp
private val COMPACT_VERTICAL_PADDING = 2.dp
