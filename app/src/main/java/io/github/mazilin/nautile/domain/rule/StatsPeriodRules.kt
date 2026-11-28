package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.LocalDateRange
import io.github.mazilin.nautile.domain.model.StatsPeriod
import java.time.LocalDate

/**
 * 统计周期规则（架构 §3.4 边界 10、§5.2；05 §6.5）。
 *
 * 纯 Kotlin + java.time，无 Android import；`today` 由调用方注入，不读系统时钟。
 * 自定义范围：起止必填、结束不早于开始、结束不晚于今天、最长 366 天；
 * 非法只返回提示，不由本规则静默修正（AC-L-07/11）。
 */
object StatsPeriodRules {

    /** 自定义范围最长天数（含首尾，AC-L-11：366 通过、367 拒绝）。 */
    const val MAX_DAYS: Int = 366

    const val REQUIRED_ERROR: String = "请选择开始和结束日期"
    const val END_BEFORE_START_ERROR: String = "结束日期不能早于开始日期"
    const val END_IN_FUTURE_ERROR: String = "结束日期不能晚于今天"
    const val TOO_LONG_ERROR: String = "统计范围最长 366 天"

    /**
     * 周期对应的具体日期范围（用户 2026-11-28 定）。
     *
     * 日按单日；当前月/当前年截至今天（含今天），已过去的完整月/年按整月/整年；
     * 自定义严格按起止。分母与未追踪由此范围的天数推出，范围与标题保持一致。
     */
    fun rangeFor(period: StatsPeriod, today: LocalDate): LocalDateRange = when (period) {
        is StatsPeriod.Day -> LocalDateRange(period.date, period.date)
        is StatsPeriod.Month -> {
            val start = period.yearMonth.atDay(1)
            val fullEnd = period.yearMonth.atEndOfMonth()
            // 当前月截止今天；未来月（理论上不可选）防御性保持整月。
            val end = when {
                today.isBefore(start) -> fullEnd
                today.isBefore(fullEnd) -> today
                else -> fullEnd
            }
            LocalDateRange(start, end)
        }

        is StatsPeriod.Year -> {
            val start = LocalDate.of(period.year, 1, 1)
            val fullEnd = LocalDate.of(period.year, 12, 31)
            val end = when {
                today.isBefore(start) -> fullEnd
                today.isBefore(fullEnd) -> today
                else -> fullEnd
            }
            LocalDateRange(start, end)
        }

        is StatsPeriod.Custom -> LocalDateRange(period.start, period.end)
    }

    /**
     * 校验自定义起止；合法返回 null，非法返回内联提示。
     *
     * 判定顺序：必填 → 起止顺序 → 未来结束 → 366 天上限。
     */
    fun validateCustom(start: LocalDate?, end: LocalDate?, today: LocalDate): String? {
        if (start == null || end == null) return REQUIRED_ERROR
        if (end.isBefore(start)) return END_BEFORE_START_ERROR
        if (end.isAfter(today)) return END_IN_FUTURE_ERROR
        val dayCount = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
        if (dayCount > MAX_DAYS) return TOO_LONG_ERROR
        return null
    }

    /** 自定义范围是否可确定；供“确定”按钮禁用。 */
    fun isCustomRangeValid(start: LocalDate?, end: LocalDate?, today: LocalDate): Boolean =
        validateCustom(start, end, today) == null

    /**
     * 当前范围标题：日 `2026-11-27`、月 `2026年11月`、年 `2026年`、自定义 `11/01–11/27`。
     *
     * 当前月/当前年只统计到今天是新口径（用户 2026-11-28 定），标题同步标出“截至”；
     * 已过去的完整月/年保持原样，避免标题与分母范围不一致。
     */
    fun title(period: StatsPeriod, range: LocalDateRange): String = when (period) {
        is StatsPeriod.Day -> period.date.toString()
        is StatsPeriod.Month -> {
            val fullEnd = period.yearMonth.atEndOfMonth()
            if (range.endInclusive.isBefore(fullEnd)) {
                "${period.yearMonth.year}年${period.yearMonth.monthValue}月" +
                    "（截至${range.endInclusive.monthValue}/${range.endInclusive.dayOfMonth}）"
            } else {
                "${period.yearMonth.year}年${period.yearMonth.monthValue}月"
            }
        }

        is StatsPeriod.Year -> {
            val fullEnd = LocalDate.of(period.year, 12, 31)
            if (range.endInclusive.isBefore(fullEnd)) {
                "${period.year}年（截至${range.endInclusive.monthValue}/${range.endInclusive.dayOfMonth}）"
            } else {
                "${period.year}年"
            }
        }

        is StatsPeriod.Custom -> {
            if (range.startInclusive.year == range.endInclusive.year) {
                "%02d/%02d–%02d/%02d".format(
                    range.startInclusive.monthValue,
                    range.startInclusive.dayOfMonth,
                    range.endInclusive.monthValue,
                    range.endInclusive.dayOfMonth,
                )
            } else {
                "${range.startInclusive}–${range.endInclusive}"
            }
        }
    }
}
