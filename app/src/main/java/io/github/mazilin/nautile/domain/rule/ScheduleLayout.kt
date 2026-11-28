package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.LocalDateRange
import io.github.mazilin.nautile.domain.model.ScheduleEvent
import java.time.Instant
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

/** 日内布局纯函数；不依赖 Android，不读取系统时钟。 */
object ScheduleLayout {

    private const val MILLIS_PER_DAY = 86_400_000.0

    /** 对可见范围内每一天分别布局；跨天事件在每一天各生成一块。 */
    fun layoutRange(
        events: List<ScheduleEvent>,
        range: LocalDateRange,
        zoneId: ZoneId,
    ): List<ScheduleEventLayout> =
        range.dates().flatMapIndexed { dayIndex, date ->
            layoutDay(events, date, dayIndex, zoneId)
        }

    /** 单日布局：[dayIndex] 为可见范围内的列下标。 */
    fun layoutDay(
        events: List<ScheduleEvent>,
        date: LocalDate,
        dayIndex: Int,
        zoneId: ZoneId,
    ): List<ScheduleEventLayout> {
        if (events.isEmpty()) return emptyList()
        val dayStart = date.atStartOfDay(zoneId).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant()

        val clipped = events.mapNotNull { event ->
            val start = if (event.startAt.isBefore(dayStart)) dayStart else event.startAt
            val end = if (event.endAt.isAfter(dayEnd)) dayEnd else event.endAt
            if (!end.isAfter(start)) {
                null
            } else {
                Clipped(event = event, start = start, end = end)
            }
        }.sortedWith(
            compareBy<Clipped>({ it.start }, { it.end }, { it.event.id.value }),
        )

        val result = mutableListOf<ScheduleEventLayout>()
        var cluster = mutableListOf<Clipped>()
        var clusterEnd: Instant? = null

        fun flushCluster() {
            if (cluster.isEmpty()) return
            val columnCount = cluster.maxOf { it.column } + 1
            cluster.forEach { slot ->
                result.add(
                    ScheduleEventLayout(
                        event = slot.event,
                        dayIndex = dayIndex,
                        topFraction = fraction(dayStart, slot.start),
                        heightFraction = fraction(slot.start, slot.end),
                        column = slot.column,
                        columnCount = columnCount,
                    ),
                )
            }
            cluster = mutableListOf()
            clusterEnd = null
        }

        clipped.forEach { slot ->
            val currentEnd = clusterEnd
            if (currentEnd != null && !slot.start.isBefore(currentEnd)) {
                // 与上一重叠簇不再相交：先结算上一簇的最大列数。
                flushCluster()
            }
            // 复用已结束的列；否则追加新列，保证同簇内不重叠的事件不占额外列。
            slot.column = (0..cluster.size).firstOrNull { candidate ->
                cluster.none { it.column == candidate && it.end.isAfter(slot.start) }
            } ?: cluster.size
            cluster.add(slot)
            val end = clusterEnd
            clusterEnd = if (end == null || slot.end.isAfter(end)) slot.end else end
        }
        flushCluster()
        return result
    }

    /** 区间占整天的比例；clamp 到 [0, 1]，防止异常数据越界。 */
    private fun fraction(start: Instant, end: Instant): Float {
        val millis = (end.toEpochMilli() - start.toEpochMilli()).toDouble()
        return (millis / MILLIS_PER_DAY).toFloat().coerceIn(0f, 1f)
    }

    private class Clipped(val event: ScheduleEvent, val start: Instant, val end: Instant) {
        var column: Int = 0
    }
}
