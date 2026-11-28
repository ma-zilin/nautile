package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.LocalDateRange
import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.rule.ScheduleLayout
import io.github.mazilin.nautile.domain.rule.TimelineLayout
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R7-G1 · 泛化 24h 时间轴布局引擎验证（DS-04、架构 §6.1）。
 *
 * 覆盖：空列表、单日比例计算、跨天 00:00–24:00 切段、并行重叠簇分列与列复用、
 * 零时长剔除、亚秒保底、以及 ScheduleLayout 薄包装一致性。
 */
class TimelineLayoutTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val day1: LocalDate = LocalDate.of(2026, 11, 28)
    private val day2: LocalDate = day1.plusDays(1)
    private val day3: LocalDate = day1.plusDays(2)

    private data class Item(
        val id: Long,
        val start: Instant,
        val end: Instant,
    )

    private fun instant(date: LocalDate, hour: Int, minute: Int = 0): Instant =
        date.atTime(hour, minute).atZone(zone).toInstant()

    private fun layoutRange(
        items: List<Item>,
        range: LocalDateRange,
    ) = TimelineLayout.layoutRange(
        items = items,
        range = range,
        zoneId = zone,
        startOf = { it.start },
        endOf = { it.end },
        tieBreakerId = { it.id },
    )

    @Test
    fun r7_emptyItems_returnsEmptyList() {
        val slots = layoutRange(emptyList(), LocalDateRange(day1, day3))
        assertTrue(slots.isEmpty())
    }

    @Test
    fun r7_singleDayFractions_matchExact24HourProportions() {
        val item = Item(1L, instant(day1, 6, 0), instant(day1, 12, 0))
        val slots = layoutRange(listOf(item), LocalDateRange(day1, day1))

        assertEquals(1, slots.size)
        val slot = slots.single()
        assertEquals(0, slot.dayIndex)
        assertEquals(0.25f, slot.topFraction, 1e-5f)
        assertEquals(0.25f, slot.heightFraction, 1e-5f)
        assertEquals(0, slot.column)
        assertEquals(1, slot.columnCount)
    }

    @Test
    fun r7_crossDayItem_clipsCleanlyAtMidnightAcrossMultipleDays() {
        // day1 22:00 → day3 03:00：应切成 day1(22-24)、day2(00-24)、day3(00-03) 三段。
        val multiDay = Item(1L, instant(day1, 22, 0), instant(day3, 3, 0))
        val slots = layoutRange(listOf(multiDay), LocalDateRange(day1, day3))

        assertEquals(3, slots.size)

        assertEquals(0, slots[0].dayIndex)
        assertEquals(22f / 24f, slots[0].topFraction, 1e-5f)
        assertEquals(2f / 24f, slots[0].heightFraction, 1e-5f)

        assertEquals(1, slots[1].dayIndex)
        assertEquals(0f, slots[1].topFraction, 1e-5f)
        assertEquals(1f, slots[1].heightFraction, 1e-5f)

        assertEquals(2, slots[2].dayIndex)
        assertEquals(0f, slots[2].topFraction, 1e-5f)
        assertEquals(3f / 24f, slots[2].heightFraction, 1e-5f)
    }

    @Test
    fun r7_overlappingClusters_assignColumnsReuseFreedColumnAndIsolateSeparateClusters() {
        val items = listOf(
            // 簇 1：A(9-12) 与 B(9-10) 重叠，C(10-12) 复用 B 释放的列 1，因此簇 1 仅需 2 列。
            Item(1L, instant(day1, 9, 0), instant(day1, 12, 0)),
            Item(2L, instant(day1, 9, 0), instant(day1, 10, 0)),
            Item(3L, instant(day1, 10, 0), instant(day1, 12, 0)),
            // 簇 2（与簇 1 仅在 12:00 相接不重叠）：独立 1 列，不被簇 1 污染。
            Item(4L, instant(day1, 12, 0), instant(day1, 13, 0)),
            // 簇 3：三并行重叠，需 3 列。
            Item(5L, instant(day1, 14, 0), instant(day1, 16, 0)),
            Item(6L, instant(day1, 14, 30), instant(day1, 15, 30)),
            Item(7L, instant(day1, 15, 0), instant(day1, 16, 0)),
        )

        val slots = layoutRange(items, LocalDateRange(day1, day1))
        val byId = slots.associateBy { it.item.id }

        assertEquals(2, byId.getValue(1L).columnCount)
        assertEquals(2, byId.getValue(2L).columnCount)
        assertEquals(2, byId.getValue(3L).columnCount)
        assertEquals(0, byId.getValue(2L).column)
        assertEquals(1, byId.getValue(1L).column)
        assertEquals("C 应复用 B 释放的列 0", 0, byId.getValue(3L).column)

        assertEquals("独立簇列数应为 1", 1, byId.getValue(4L).columnCount)
        assertEquals(0, byId.getValue(4L).column)

        assertEquals(3, byId.getValue(5L).columnCount)
        assertEquals(3, byId.getValue(6L).columnCount)
        assertEquals(3, byId.getValue(7L).columnCount)
        assertEquals(setOf(0, 1, 2), setOf(byId.getValue(5L).column, byId.getValue(6L).column, byId.getValue(7L).column))
    }

    @Test
    fun r7_zeroDurationExcluded_andSubSecondProducesPositiveFraction() {
        val start = instant(day1, 10, 0)
        val zero = Item(1L, start, start)
        val reversed = Item(2L, start.plusSeconds(60), start)
        val subSecond = Item(3L, start, start.plusMillis(250))

        val slots = layoutRange(listOf(zero, reversed, subSecond), LocalDateRange(day1, day1))

        assertEquals("零时长与倒置条目应被安全排除，仅保留亚秒正时长条目", 1, slots.size)
        val slot = slots.single()
        assertEquals(3L, slot.item.id)
        assertTrue("亚秒条目 heightFraction 必须大于 0", slot.heightFraction > 0f)
        assertTrue("亚秒条目 heightFraction 不得超过 1", slot.heightFraction <= 1f)
    }

    @Test
    fun r7_scheduleLayoutDelegation_matchesTimelineLayout() {
        val event = ScheduleEvent(
            id = ScheduleId(10L),
            title = "委托一致性",
            note = null,
            startAt = instant(day1, 8, 30),
            endAt = instant(day1, 10, 0),
            source = ScheduleSource.MANUAL,
            tagId = null,
            seriesId = null,
            originalStartAt = null,
            createdAt = instant(day1, 8, 0),
            updatedAt = instant(day1, 8, 0),
            colorIndex = 2,
        )
        val range = LocalDateRange(day1, day2)
        val direct = TimelineLayout.layoutRange(
            items = listOf(event),
            range = range,
            zoneId = zone,
            startOf = { it.startAt },
            endOf = { it.endAt },
            tieBreakerId = { it.id.value },
        )
        val viaScheduleLayout = ScheduleLayout.layoutRange(listOf(event), range, zone)

        assertEquals(direct.size, viaScheduleLayout.size)
        assertEquals(direct[0].dayIndex, viaScheduleLayout[0].dayIndex)
        assertEquals(direct[0].topFraction, viaScheduleLayout[0].topFraction, 1e-6f)
        assertEquals(direct[0].heightFraction, viaScheduleLayout[0].heightFraction, 1e-6f)
        assertEquals(direct[0].column, viaScheduleLayout[0].column)
        assertEquals(direct[0].columnCount, viaScheduleLayout[0].columnCount)
    }
}
