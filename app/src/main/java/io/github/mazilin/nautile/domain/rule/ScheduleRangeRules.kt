package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.LocalDateRange
import io.github.mazilin.nautile.domain.model.ScheduleViewMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 日程可见范围规则（架构 §3.5、AC-S-03/13）。
 *
 * DAY = 1 天；THREE_DAY = 选中日起连续 3 天；WEEK = 同一周 7 天，周一起始。
 */
object ScheduleRangeRules {

    /** 按视图模式计算可见日期范围；周视图固定周一至周日。 */
    fun visibleRange(selectedDate: LocalDate, viewMode: ScheduleViewMode): LocalDateRange =
        when (viewMode) {
            ScheduleViewMode.DAY -> LocalDateRange(selectedDate, selectedDate)
            ScheduleViewMode.THREE_DAY ->
                LocalDateRange(selectedDate, selectedDate.plusDays(2))
            ScheduleViewMode.WEEK -> {
                val weekStart = selectedDate.with(
                    TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY),
                )
                LocalDateRange(weekStart, weekStart.plusDays(6))
            }
        }

    /** 左右步进的天数：日 1、三天 3、周 7；边界不循环，由调用方限制。 */
    fun stepDays(viewMode: ScheduleViewMode): Int = when (viewMode) {
        ScheduleViewMode.DAY -> 1
        ScheduleViewMode.THREE_DAY -> 3
        ScheduleViewMode.WEEK -> 7
    }

    /** 按视图步进选中日期；[delta] 为 -1/1，跨月/跨年由 java.time 处理。 */
    fun step(selectedDate: LocalDate, viewMode: ScheduleViewMode, delta: Int): LocalDate =
        selectedDate.plusDays((stepDays(viewMode) * delta).toLong())
}
