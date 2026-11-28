package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.LocalDateRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 泛化 24h 时间轴日内槽位布局（R7 DS-04、架构 §6.1；日程与时间日志共用）。
 *
 * 纯函数输出“日内比例 + 重叠簇分列”，UI 仅负责像素换算，便于 JVM 单测。
 *
 * @property item 业务条目（如 `ScheduleEvent` 或 `TimeLog`）
 * @property dayIndex 可见日期范围内的列下标（日 0 / 三天 0..2 / 周 0..6）
 * @property topFraction 相对当日 00:00 的起始比例（0f..1f）
 * @property heightFraction 相对整天 24 小时的高度比例（0f..1f）
 * @property column 所属重叠簇内的列下标（0 until [columnCount]）
 * @property columnCount 所属重叠簇的总列数（至少 1）
 */
data class TimelineSlot<T>(
    val item: T,
    val dayIndex: Int,
    val topFraction: Float,
    val heightFraction: Float,
    val column: Int,
    val columnCount: Int,
)

/** 泛化 24h 时间轴布局纯函数；支持跨天按本地 00:00 切段与并行簇分列，不依赖 Android。 */
object TimelineLayout {

    private const val MILLIS_PER_DAY = 86_400_000.0

    /** 对可见范围内每一天分别切段并布局；跨天条目在每一天各生成一块。 */
    fun <T> layoutRange(
        items: List<T>,
        range: LocalDateRange,
        zoneId: ZoneId,
        startOf: (T) -> Instant,
        endOf: (T) -> Instant,
        tieBreakerId: (T) -> Long,
    ): List<TimelineSlot<T>> =
        range.dates().flatMapIndexed { dayIndex, date ->
            layoutDay(
                items = items,
                date = date,
                dayIndex = dayIndex,
                zoneId = zoneId,
                startOf = startOf,
                endOf = endOf,
                tieBreakerId = tieBreakerId,
            )
        }

    /** 单日切段与重叠簇分列：[dayIndex] 为可见范围内的列下标。 */
    fun <T> layoutDay(
        items: List<T>,
        date: LocalDate,
        dayIndex: Int,
        zoneId: ZoneId,
        startOf: (T) -> Instant,
        endOf: (T) -> Instant,
        tieBreakerId: (T) -> Long,
    ): List<TimelineSlot<T>> {
        if (items.isEmpty()) return emptyList()
        val dayStart = date.atStartOfDay(zoneId).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant()

        val clipped = items.mapNotNull { item ->
            val rawStart = startOf(item)
            val rawEnd = endOf(item)
            val start = if (rawStart.isBefore(dayStart)) dayStart else rawStart
            val end = if (rawEnd.isAfter(dayEnd)) dayEnd else rawEnd
            if (!end.isAfter(start)) {
                null
            } else {
                Clipped(
                    item = item,
                    start = start,
                    end = end,
                    tieBreakerId = tieBreakerId(item),
                )
            }
        }.sortedWith(
            compareBy<Clipped<T>>({ it.start }, { it.end }, { it.tieBreakerId }),
        )

        val result = mutableListOf<TimelineSlot<T>>()
        var cluster = mutableListOf<Clipped<T>>()
        var clusterEnd: Instant? = null

        fun flushCluster() {
            if (cluster.isEmpty()) return
            val columnCount = cluster.maxOf { it.column } + 1
            cluster.forEach { slot ->
                result.add(
                    TimelineSlot(
                        item = slot.item,
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
            // 复用已结束的列；否则追加新列，保证同簇内不重叠的条目不占额外列。
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

    private class Clipped<T>(
        val item: T,
        val start: Instant,
        val end: Instant,
        val tieBreakerId: Long,
    ) {
        var column: Int = 0
    }
}
