package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.ScheduleViewMode
import io.github.mazilin.nautile.domain.rule.ScheduleRangeRules
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R6-G2-V1 · AC-S-03 纯函数部分：日/三天/周可见范围（`ScheduleRangeRules.visibleRange`）。
 *
 * 预期来自需求基线 v2.16 AC-S-03（L531）与冻结架构 §3.5：DAY=1 天；THREE_DAY=选中日起连续 3 天；
 * WEEK=同一周 7 天、周一为周首。拒绝分支把“三天不是连续 3 天”“周不是同一周 7 天”写成可失败断言，
 * 不依赖当前实现取值。
 */
class AcScheduleS03RangeRulesTest {

    private val isoWeek = WeekFields.ISO

    private fun weekKey(date: LocalDate): Pair<Int, Int> =
        date.get(isoWeek.weekBasedYear()) to date.get(isoWeek.weekOfWeekBasedYear())

    @Test
    fun acS03_dayView_isExactlySelectedDay() {
        val selected = LocalDate.of(2026, 11, 25)

        val range = ScheduleRangeRules.visibleRange(selected, ScheduleViewMode.DAY)

        assertEquals(selected, range.startInclusive)
        assertEquals(selected, range.endInclusive)
        assertEquals(1, range.dayCount)
        assertEquals(listOf(selected), range.dates())
    }

    @Test
    fun acS03_threeDayView_startsAtSelectedAndCoversThreeContiguousDays() {
        val selected = LocalDate.of(2026, 11, 25)

        val range = ScheduleRangeRules.visibleRange(selected, ScheduleViewMode.THREE_DAY)

        // 拒绝：从次日开始 / 覆盖 4 天 / 不连续。
        assertEquals("三天视图必须从选中日开始", selected, range.startInclusive)
        assertEquals("三天视图必须恰好 3 天", 3, range.dayCount)
        assertEquals(LocalDate.of(2026, 11, 27), range.endInclusive)
        val dates = range.dates()
        assertEquals(3, dates.size)
        dates.zipWithNext().forEach { (a, b) ->
            assertEquals("三天视图日期必须连续", 1L, b.toEpochDay() - a.toEpochDay())
        }
    }

    @Test
    fun acS03_weekView_isSameIsoWeekSevenDaysStartingMonday() {
        val selected = LocalDate.of(2026, 11, 25) // 周三

        val range = ScheduleRangeRules.visibleRange(selected, ScheduleViewMode.WEEK)

        assertEquals("周首必须是周一", DayOfWeek.MONDAY, range.startInclusive.dayOfWeek)
        assertEquals("周尾必须是周日", DayOfWeek.SUNDAY, range.endInclusive.dayOfWeek)
        assertEquals("周视图必须 7 天", 7, range.dayCount)
        assertEquals(LocalDate.of(2026, 11, 23), range.startInclusive)
        assertEquals(LocalDate.of(2026, 11, 29), range.endInclusive)
        // 拒绝：不是同一周 7 天（跨周/8 天都会使 weekOfWeekBasedYear 不一致或天数不等于 7）。
        assertEquals(1, range.dates().map { weekKey(it) }.distinct().size)
    }

    @Test
    fun acS03_weekView_includesSelectedDateForEveryWeekday() {
        val monday = LocalDate.of(2026, 11, 23)
        (0L..6L).forEach { offset ->
            val selected = monday.plusDays(offset)
            val range = ScheduleRangeRules.visibleRange(selected, ScheduleViewMode.WEEK)

            assertEquals("星期 ${selected.dayOfWeek} 的周范围起点错误", monday, range.startInclusive)
            assertTrue("选中日必须落在周范围内", selected in range.dates())
        }
    }

    @Test
    fun acS03_weekView_crossesMonthAndYearBoundariesWithoutSplittingWeek() {
        // 跨月：2026-10-31 是周六，所在周为 10-26（周一）—11-01（周日）。
        val crossMonth = ScheduleRangeRules.visibleRange(
            LocalDate.of(2026, 10, 31),
            ScheduleViewMode.WEEK,
        )
        assertEquals(LocalDate.of(2026, 10, 26), crossMonth.startInclusive)
        assertEquals(LocalDate.of(2026, 11, 1), crossMonth.endInclusive)
        assertEquals(7, crossMonth.dayCount)

        // 跨年：2026-12-31 是周四，所在周为 2026-12-28—2027-01-03。
        val crossYear = ScheduleRangeRules.visibleRange(
            LocalDate.of(2026, 12, 31),
            ScheduleViewMode.WEEK,
        )
        assertEquals(LocalDate.of(2026, 12, 28), crossYear.startInclusive)
        assertEquals(LocalDate.of(2027, 1, 3), crossYear.endInclusive)
        assertEquals(7, crossYear.dayCount)
        assertEquals(1, crossYear.dates().map { weekKey(it) }.distinct().size)
    }

    @Test
    fun acS03_stepDays_followsViewMode() {
        assertEquals(1, ScheduleRangeRules.stepDays(ScheduleViewMode.DAY))
        assertEquals(3, ScheduleRangeRules.stepDays(ScheduleViewMode.THREE_DAY))
        assertEquals(7, ScheduleRangeRules.stepDays(ScheduleViewMode.WEEK))
    }

    @Test
    fun acS03_step_crossesMonthAndYearWithoutLooping() {
        assertEquals(
            LocalDate.of(2027, 1, 1),
            ScheduleRangeRules.step(LocalDate.of(2026, 12, 31), ScheduleViewMode.DAY, 1),
        )
        assertEquals(
            LocalDate.of(2026, 11, 30),
            ScheduleRangeRules.step(LocalDate.of(2026, 11, 27), ScheduleViewMode.THREE_DAY, 1),
        )
        assertEquals(
            LocalDate.of(2027, 1, 4),
            ScheduleRangeRules.step(LocalDate.of(2026, 12, 28), ScheduleViewMode.WEEK, 1),
        )
        assertEquals(
            LocalDate.of(2026, 12, 21),
            ScheduleRangeRules.step(LocalDate.of(2026, 12, 28), ScheduleViewMode.WEEK, -1),
        )
    }
}
