package io.github.mazilin.nautile.ui.screen.schedule

import io.github.mazilin.nautile.domain.model.LocalDateRange
import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleSourceFilter
import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.model.ScheduleViewMode
import io.github.mazilin.nautile.domain.rule.ScheduleRangeRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日程页状态（R6-G2-T3；架构 §5.1）。
 *
 * 只承载可见范围、来源筛选与布局结果；标签仅显示/录入，不参与筛选（D-new1）。
 * [events] 已按天拆块并计算比例与重叠列，UI 只做像素换算。
 */
data class ScheduleUiState(
    val today: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = today,
    val viewMode: ScheduleViewMode = ScheduleViewMode.DAY,
    val visibleRange: LocalDateRange = ScheduleRangeRules.visibleRange(selectedDate, viewMode),
    /** 可见范围标题（如“11月24日 – 11月30日”），用于“标题与范围一致”。 */
    val rangeTitle: String = "",
    val sourceFilter: ScheduleSourceFilter = ScheduleSourceFilter.MERGED,
    val tags: List<ScheduleTag> = emptyList(),
    val events: List<ScheduleEventBlock> = emptyList(),
    /** 当前时刻，用于“仅今天”的当前时间线；每分钟更新，不写库。 */
    val now: Instant = Instant.now(),
    /** 注入的时区，保证网格与日期换算一致。 */
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val isLoading: Boolean = false,
    val message: String? = null,
) {

    /** 可见范围内的日期列（日 1 / 三天 3 / 周 7）。 */
    val columnDates: List<LocalDate> get() = visibleRange.dates()

    val isEmpty: Boolean get() = events.isEmpty()

    /** 按 ID 取标签名；不存在返回 null（不伪造“无标签”文字）。 */
    fun tagName(tagId: ScheduleTagId?): String? =
        tagId?.let { id -> tags.firstOrNull { it.id == id }?.name }
}

/** 日内事件块：布局比例与重叠列由 `ScheduleLayout` 计算（架构 §3.5、§5.1）。 */
data class ScheduleEventBlock(
    val event: ScheduleEvent,
    val dayIndex: Int,
    val topFraction: Float,
    val heightFraction: Float,
    val column: Int,
    val columnCount: Int,
)
