package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.LocalDateRange
import io.github.mazilin.nautile.domain.model.ScheduleEvent
import java.time.LocalDate
import java.time.ZoneId

/**
 * 事件块在一天内的布局（架构 §3.5）。
 *
 * 纯函数输出“日内比例 + 重叠分列”，UI 只做像素换算，便于 JVM 测试。
 * [topFraction] / [heightFraction] 相对整天 24 小时；同一天重叠事件按最大列数分列。
 */
data class ScheduleEventLayout(
    val event: ScheduleEvent,
    val dayIndex: Int,
    val topFraction: Float,
    val heightFraction: Float,
    val column: Int,
    val columnCount: Int,
)

/** 日内布局纯函数薄包装：委托给泛化 [TimelineLayout]，保持既有调用与单测完全兼容。 */
object ScheduleLayout {

    /** 对可见范围内每一天分别布局；跨天事件在每一天各生成一块。 */
    fun layoutRange(
        events: List<ScheduleEvent>,
        range: LocalDateRange,
        zoneId: ZoneId,
    ): List<ScheduleEventLayout> =
        TimelineLayout.layoutRange(
            items = events,
            range = range,
            zoneId = zoneId,
            startOf = { it.startAt },
            endOf = { it.endAt },
            tieBreakerId = { it.id.value },
        ).map { it.toScheduleEventLayout() }

    /** 单日布局：[dayIndex] 为可见范围内的列下标。 */
    fun layoutDay(
        events: List<ScheduleEvent>,
        date: LocalDate,
        dayIndex: Int,
        zoneId: ZoneId,
    ): List<ScheduleEventLayout> =
        TimelineLayout.layoutDay(
            items = events,
            date = date,
            dayIndex = dayIndex,
            zoneId = zoneId,
            startOf = { it.startAt },
            endOf = { it.endAt },
            tieBreakerId = { it.id.value },
        ).map { it.toScheduleEventLayout() }

    private fun TimelineSlot<ScheduleEvent>.toScheduleEventLayout(): ScheduleEventLayout =
        ScheduleEventLayout(
            event = item,
            dayIndex = dayIndex,
            topFraction = topFraction,
            heightFraction = heightFraction,
            column = column,
            columnCount = columnCount,
        )
}
