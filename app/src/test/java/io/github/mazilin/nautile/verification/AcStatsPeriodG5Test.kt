package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.LocalDateRange
import io.github.mazilin.nautile.domain.model.StatsPeriod
import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.domain.rule.StatsPeriodRules
import io.github.mazilin.nautile.domain.rule.TimeStats
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R6-G5-V1 · AC-L-07 / AC-L-11 统计周期（JVM 纯函数 `StatsPeriodRules` + `TimeStats`）。
 *
 * 用户 2026-11-28 确认：当前月/当前年分母截至今天（含今天），已过去完整周期按整月/整年，
 * 自定义按起止天数；366 天通过、367 天拒绝、未来结束拒绝、结束早于开始拒绝；周期不混合。
 */
class AcStatsPeriodG5Test {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 11, 28)
    private val createdAt: Instant = today.minusDays(400).atTime(7, 0).atZone(zone).toInstant()

    private fun at(date: LocalDate, hour: Int): Instant =
        date.atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant()

    private fun category(id: Long, name: String): TimeCategory = TimeCategory(
        id = TimeCategoryId(id),
        name = name,
        parentId = null,
        sortOrder = id.toInt(),
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun log(id: Long, categoryId: TimeCategoryId, date: LocalDate, startHour: Int, endHour: Int): TimeLog =
        TimeLog(
            id = TimeLogId(id),
            categoryId = categoryId,
            startAt = at(date, startHour),
            endAt = at(date, endHour),
            createdAt = createdAt,
            updatedAt = createdAt,
        )

    // ---------------- AC-L-07 周期与标题一致 ----------------

    @Test
    fun AC_L_07_dayPeriodIsSingleDayAndTitleMatches() {
        val range = StatsPeriodRules.rangeFor(StatsPeriod.Day(today), today)
        assertEquals(LocalDateRange(today, today), range)
        assertEquals(1, range.dayCount)
        assertEquals("2026-11-28", StatsPeriodRules.title(StatsPeriod.Day(today), range))
    }

    @Test
    fun AC_L_07_currentMonthStopsAtToday() {
        val period = StatsPeriod.Month(YearMonth.of(2026, 11))
        val range = StatsPeriodRules.rangeFor(period, today)
        assertEquals(LocalDate.of(2026, 11, 1), range.startInclusive)
        assertEquals("截至今天（含）", today, range.endInclusive)
        assertEquals("11/1—11/28 共 28 天", 28, range.dayCount)
        assertEquals("2026年11月（截至11/28）", StatsPeriodRules.title(period, range))
    }

    @Test
    fun AC_L_07_pastMonthUsesFullMonth() {
        val period = StatsPeriod.Month(YearMonth.of(2026, 10))
        val range = StatsPeriodRules.rangeFor(period, today)
        assertEquals(LocalDate.of(2026, 10, 1), range.startInclusive)
        assertEquals(LocalDate.of(2026, 10, 31), range.endInclusive)
        assertEquals(31, range.dayCount)
        assertEquals("2026年10月", StatsPeriodRules.title(period, range))
    }

    @Test
    fun AC_L_07_currentYearStopsAtTodayAndPastYearIsFullYear() {
        val current = StatsPeriodRules.rangeFor(StatsPeriod.Year(2026), today)
        assertEquals(LocalDate.of(2026, 1, 1), current.startInclusive)
        assertEquals(today, current.endInclusive)
        assertEquals("1/1—11/28 共 332 天", 332, current.dayCount)
        assertEquals("2026年（截至11/28）", StatsPeriodRules.title(StatsPeriod.Year(2026), current))

        val past = StatsPeriodRules.rangeFor(StatsPeriod.Year(2025), today)
        assertEquals(LocalDate.of(2025, 1, 1), past.startInclusive)
        assertEquals(LocalDate.of(2025, 12, 31), past.endInclusive)
        assertEquals(365, past.dayCount)
        assertEquals("2025年", StatsPeriodRules.title(StatsPeriod.Year(2025), past))
    }

    @Test
    fun AC_L_07_customRangeUsesExactStartAndEnd() {
        val start = LocalDate.of(2026, 11, 1)
        val range = StatsPeriodRules.rangeFor(StatsPeriod.Custom(start, today), today)
        assertEquals(LocalDateRange(start, today), range)
        assertEquals(28, range.dayCount)
        assertEquals("11/01–11/28", StatsPeriodRules.title(StatsPeriod.Custom(start, today), range))
    }

    @Test
    fun AC_L_07_multiDayDenominatorAndUntrackedFollowFormula() {
        val study = category(1, "学习")
        val categories = listOf(study)
        val range = LocalDateRange(today.minusDays(1), today)
        val logs = listOf(
            log(1, study.id, today.minusDays(1), 8, 10),
            log(2, study.id, today, 8, 10),
        )

        val stats = TimeStats.computeRange(logs, categories, range, zone, at(today, 23))

        assertEquals("2 天", 2, stats.days.size)
        assertEquals("总累计 4h", 240L, stats.totalAccumulated.toMinutes())
        assertEquals("分母 24h×2 = 48h", 2880L, stats.denominator.toMinutes())
        assertEquals("未追踪 48h-4h", 2640L, stats.untracked.toMinutes())
    }

    @Test
    fun AC_L_07_monthDoesNotMixOtherPeriods() {
        val study = category(1, "学习")
        val categories = listOf(study)
        val logs = listOf(
            log(1, study.id, LocalDate.of(2026, 10, 31), 8, 10),
            log(2, study.id, today, 8, 10),
        )
        val period = StatsPeriod.Month(YearMonth.of(2026, 11))
        val range = StatsPeriodRules.rangeFor(period, today)

        val stats = TimeStats.computeRange(logs, categories, range, zone, at(today, 23))

        assertEquals("只统计 11 月内记录", 120L, stats.totalAccumulated.toMinutes())
        assertFalse("10 月记录不混入", stats.days.any { it.date == LocalDate.of(2026, 10, 31) })
    }

    @Test
    fun AC_L_07_366DayRangeIsSupported() {
        val study = category(1, "学习")
        val start = today.minusDays(365)
        val range = LocalDateRange(start, today)
        assertEquals("含首尾 366 天", 366, range.dayCount)
        assertNull(StatsPeriodRules.validateCustom(start, today, today))

        val stats = TimeStats.computeRange(
            listOf(log(1, study.id, today, 8, 9)),
            listOf(study),
            range,
            zone,
            at(today, 23),
        )
        assertEquals("366 天", 366, stats.days.size)
        assertEquals("分母 24h×366", 8784L, stats.denominator.toHours())
        assertTrue("未追踪非负", !stats.untracked.isNegative)
    }

    // ---------------- AC-L-11 边界 ----------------

    @Test
    fun AC_L_11_366DaysAcceptedAnd367Rejected() {
        val start366 = today.minusDays(365)
        assertEquals("366 天通过", null, StatsPeriodRules.validateCustom(start366, today, today))
        assertTrue(StatsPeriodRules.isCustomRangeValid(start366, today, today))

        val start367 = today.minusDays(366)
        assertEquals("367 天拒绝", StatsPeriodRules.TOO_LONG_ERROR, StatsPeriodRules.validateCustom(start367, today, today))
        assertFalse(StatsPeriodRules.isCustomRangeValid(start367, today, today))
    }

    @Test
    fun AC_L_11_futureEndRejected() {
        assertEquals(
            StatsPeriodRules.END_IN_FUTURE_ERROR,
            StatsPeriodRules.validateCustom(today.minusDays(1), today.plusDays(1), today),
        )
        assertEquals(
            "结束等于今天通过",
            null,
            StatsPeriodRules.validateCustom(today.minusDays(1), today, today),
        )
    }

    @Test
    fun AC_L_11_endBeforeStartRejected() {
        assertEquals(
            StatsPeriodRules.END_BEFORE_START_ERROR,
            StatsPeriodRules.validateCustom(today, today.minusDays(1), today),
        )
    }

    @Test
    fun AC_L_11_requiredFieldsRejected() {
        assertEquals(
            StatsPeriodRules.REQUIRED_ERROR,
            StatsPeriodRules.validateCustom(null, today, today),
        )
        assertEquals(
            StatsPeriodRules.REQUIRED_ERROR,
            StatsPeriodRules.validateCustom(today, null, today),
        )
    }

    @Test
    fun AC_L_11_errorOrderIsStable() {
        // 同时满足“结束早于开始”和“超过 366 天”时，先报顺序错误，提示不被上限覆盖。
        assertEquals(
            StatsPeriodRules.END_BEFORE_START_ERROR,
            StatsPeriodRules.validateCustom(today, today.minusDays(500), today),
        )
        // 结束在未来且超过 366 天时，先报未来错误。
        assertEquals(
            StatsPeriodRules.END_IN_FUTURE_ERROR,
            StatsPeriodRules.validateCustom(today.minusDays(500), today.plusDays(1), today),
        )
    }
}
