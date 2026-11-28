package io.github.mazilin.nautile.ui.screen.log

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.ChartSlice
import io.github.mazilin.nautile.domain.model.ChildShare
import io.github.mazilin.nautile.domain.model.ParentAggregate
import io.github.mazilin.nautile.domain.model.StatsPeriod
import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.rule.TimeStats
import io.github.mazilin.nautile.ui.designsystem.NautileEmptyState
import io.github.mazilin.nautile.ui.theme.NautileMotion
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import java.time.Duration
import kotlin.math.PI

/**
 * 统计页内容 P-08（05 §2.5/§3.5；AC-L-03/07/15/16/19；AC-T-25）。
 *
 * 日/月/年/自定义全部显示同一套饼图、图例与父级汇总；
 * 分母与未追踪由 [io.github.mazilin.nautile.domain.rule.TimeStats] 计算，
 * 界面标注“含重叠累计”，不把百分比写成“一天占比”。饼图首绘为静态（P-01 未批准，不新增 token）。
 */
@Composable
fun StatsContent(
    state: StatsUiState,
    categories: List<TimeCategory>,
    onSelectDay: () -> Unit,
    onSelectMonth: () -> Unit,
    onSelectYear: () -> Unit,
    onOpenCustom: () -> Unit,
    onToggleParent: (TimeCategoryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val categoryNames = categories.associate { it.id to it.name }
    val percentLabels = TimeStats.percentLabels(state.slices.map { it.fraction })

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = NautileSpacing.space4)
            .testTag("stats_content"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space2),
        ) {
            PeriodChip(
                label = "日",
                iconRes = R.drawable.ic_calendar_today,
                selected = state.period is StatsPeriod.Day,
                testTag = "stats_period_day",
                onClick = onSelectDay,
            )
            PeriodChip(
                label = "月",
                iconRes = R.drawable.ic_calendar_view_month,
                selected = state.period is StatsPeriod.Month,
                testTag = "stats_period_month",
                onClick = onSelectMonth,
            )
            PeriodChip(
                label = "年",
                iconRes = R.drawable.ic_date_range,
                selected = state.period is StatsPeriod.Year,
                testTag = "stats_period_year",
                onClick = onSelectYear,
            )
            PeriodChip(
                label = "自定义",
                iconRes = R.drawable.ic_tune,
                selected = state.period is StatsPeriod.Custom,
                testTag = "stats_period_custom",
                onClick = onOpenCustom,
            )
        }
        Text(
            text = state.rangeTitle,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(top = NautileSpacing.space2)
                .testTag("stats_range_title"),
        )
        if (!state.hasRecords) {
            // 空态：不画 NaN/0%，未追踪仍按口径（24h × 范围天数）在图例中显示。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = NautileSpacing.space2)
                    .testTag("stats_empty"),
            ) {
                NautileEmptyState(
                    title = "该范围没有记录",
                    iconRes = R.drawable.ic_pie_chart,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = NautileSpacing.space3),
                contentAlignment = Alignment.Center,
            ) {
                StatsPieChart(
                    slices = state.slices,
                    modifier = Modifier
                        .size(CHART_SIZE)
                        .testTag("stats_pie_chart"),
                )
            }
        }

        StatsLegend(
            slices = state.slices,
            percentLabels = percentLabels,
            modifier = Modifier.padding(top = NautileSpacing.space3),
        )
        Text(
            text = "含重叠累计 · 合计 ${formatStatsDuration(state.denominator)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NautileSpacing.space1)
                .testTag("stats_overlap_note"),
        )

        HorizontalDivider(
            modifier = Modifier.padding(top = NautileSpacing.space3),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        state.parents.forEach { parent ->
            ParentStatsRow(
                parent = parent,
                name = categoryNames[parent.parentId].orEmpty(),
                expanded = parent.parentId in state.expandedParentIds,
                onToggle = { onToggleParent(parent.parentId) },
            )
        }
        Spacer(modifier = Modifier.height(NautileSpacing.space3))
    }
}

/**
 * Compose Canvas 自绘饼图（05 §3.5；CMP-58）。
 *
 * 父级分类 + 未追踪片，相邻片 1dp 分隔；空数据画静态圆环，不产生 NaN。
 * 首绘与实时更新均为静态重绘（P-01/P-02 未批准，不新增动画常量）。
 */
@Composable
fun StatsPieChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
) {
    val palette = statsPalette()
    // 未追踪用中性 onSurfaceVariant：浅/深两套主题下都与背景有足够对比，不用低对比的容器色。
    val untrackedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val emptyColor = MaterialTheme.colorScheme.outline
    val description = buildChartDescription(slices)

    Canvas(
        modifier = modifier.semantics { contentDescription = description },
    ) {
        if (slices.isEmpty()) {
            drawCircle(
                color = emptyColor,
                radius = size.minDimension / 2f - SLICE_GAP.toPx(),
                style = Stroke(width = SLICE_GAP.toPx() * 2f),
            )
            return@Canvas
        }
        val gapDegrees = if (slices.size > 1) {
            (SLICE_GAP.toPx() / (size.minDimension / 2f)) * (180f / PI.toFloat())
        } else {
            0f
        }
        var startAngle = -90f
        var colorIndex = 0
        slices.forEach { slice ->
            val color = if (slice.untracked) untrackedColor else palette[colorIndex++ % palette.size]
            val sweep = (slice.fraction * 360.0).toFloat()
            val drawnSweep = (sweep - gapDegrees).coerceAtLeast(0f)
            drawArc(
                color = color,
                startAngle = startAngle + gapDegrees / 2f,
                sweepAngle = drawnSweep,
                useCenter = true,
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun PeriodChip(
    label: String,
    iconRes: Int,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        modifier = Modifier.testTag(testTag),
    )
}

@Composable
private fun StatsLegend(
    slices: List<ChartSlice>,
    percentLabels: List<Int>,
    modifier: Modifier = Modifier,
) {
    val palette = statsPalette()
    val untrackedColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier.fillMaxWidth()) {
        var colorIndex = 0
        slices.forEachIndexed { index, slice ->
            val color = if (slice.untracked) untrackedColor else palette[colorIndex++ % palette.size]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(LEGEND_DOT)
                        .clip(CircleShape)
                        .background(color),
                )
                Text(
                    text = slice.label.ifEmpty { "未命名分类" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = NautileSpacing.space2),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${formatStatsDuration(slice.duration)} · ${percentLabels.getOrElse(index) { 0 }}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ParentStatsRow(
    parent: ParentAggregate,
    name: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    // 只有“父级 + 至少一个子级”或“两个以上展开项”才需要展开；纯直属的顶级行不显示箭头。
    val expandable = parent.children.size > 1 ||
        parent.children.singleOrNull()?.categoryId != null
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = NautileMotion.StandardSpatial,
        label = "stats_parent_chevron",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(enabled = expandable, onClick = onToggle)
                .semantics {
                    if (expandable) {
                        stateDescription = if (expanded) "已展开" else "已收起"
                    }
                }
                .testTag("stats_parent_${parent.parentId.value}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "合计 ${formatStatsDuration(parent.duration)} · ${parent.logCount} 条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expandable) {
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = NautileSpacing.space1)
                        .rotate(rotation),
                )
            }
        }
        AnimatedVisibility(
            visible = expandable && expanded,
            enter = expandVertically(animationSpec = NautileMotion.ContainerExpand),
            exit = shrinkVertically(animationSpec = NautileMotion.ContainerExpand),
        ) {
            ChildShareList(parent.children)
        }
    }
}

@Composable
private fun ChildShareList(children: List<ChildShare>) {
    val percentLabels = TimeStats.percentLabels(children.map { it.fraction })
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = NautileSpacing.space2),
    ) {
        children.forEachIndexed { index, child ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .padding(start = NautileSpacing.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = child.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${formatStatsDuration(child.duration)} · " +
                        "${percentLabels.getOrElse(index) { 0 }}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 分类色板：只用现有主题角色，按顺序循环；未追踪用中性灰。
 *
 * 使用 main 色而非容器色：容器色在深色主题下与 surface 对比不足（G6 深色可读性）。
 * 颜色不承担唯一信息，图例逐项给出文字、时长与百分比。
 */
@Composable
private fun statsPalette(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.secondary,
    MaterialTheme.colorScheme.tertiary,
    MaterialTheme.colorScheme.error,
)

private fun buildChartDescription(slices: List<ChartSlice>): String {
    if (slices.isEmpty()) return "时间分布饼图：暂无数据"
    val parts = slices.joinToString("，") { slice ->
        "${slice.label.ifEmpty { "未命名分类" }}${formatStatsDuration(slice.duration)}"
    }
    return "时间分布饼图：$parts"
}

/** 统计时长：`2h` / `1h 30m` / `45m`；不含秒，不显示负数。 */
internal fun formatStatsDuration(duration: Duration): String {
    val totalMinutes = duration.toMinutes().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

private val CHART_SIZE = 220.dp
private val SLICE_GAP = 1.dp
private val LEGEND_DOT = 12.dp
