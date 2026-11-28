package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.rule.ColoredTimeSlot
import io.github.mazilin.nautile.domain.rule.EventColorRules
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R7-G1 · 纯 Kotlin 8 色事件色槽选择与回退规则验证（#22、架构 §4.1）。
 *
 * 覆盖：色板容量、负数与 Long.MIN_VALUE 回退、落库值与越界值解析、
 * 重叠 ∪ 紧邻前驱 ∪ 紧邻后继避让、≥8 并行满色三级降级、确定性轮转。
 */
class EventColorRulesTest {

    private fun at(hour: Int, minute: Int = 0): Instant =
        Instant.parse("2026-11-28T${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}:00Z")

    private fun slot(
        startHour: Int,
        endHour: Int,
        colorIndex: Int?,
        fallbackId: Long = 0L,
        startMinute: Int = 0,
        endMinute: Int = 0,
    ): ColoredTimeSlot = ColoredTimeSlot(
        startAt = at(startHour, startMinute),
        endAt = at(endHour, endMinute),
        colorIndex = colorIndex,
        fallbackId = fallbackId,
    )

    @Test
    fun r7_paletteSize_isExactlyEight() {
        assertEquals(8, EventColorRules.PALETTE_SIZE)
    }

    @Test
    fun r7_fallbackColorIndex_handlesZeroPositiveNegativeAndMinValue() {
        assertEquals(0, EventColorRules.fallbackColorIndex(0L))
        assertEquals(5, EventColorRules.fallbackColorIndex(5L))
        assertEquals(1, EventColorRules.fallbackColorIndex(9L))
        assertEquals(7, EventColorRules.fallbackColorIndex(-1L))
        assertEquals(0, EventColorRules.fallbackColorIndex(-8L))
        assertEquals(7, EventColorRules.fallbackColorIndex(-9L))
        val minFallback = EventColorRules.fallbackColorIndex(Long.MIN_VALUE)
        assertTrue("Long.MIN_VALUE 回退必须落在 0..7，实际=$minFallback", minFallback in 0 until 8)
        assertEquals(0, minFallback)
    }

    @Test
    fun r7_resolveColorIndex_prefersValidStoredAndFallsBackForNullOrOutOfRange() {
        for (idx in 0 until EventColorRules.PALETTE_SIZE) {
            assertEquals(idx, EventColorRules.resolveColorIndex(stored = idx, id = 99L))
        }
        assertEquals(3, EventColorRules.resolveColorIndex(stored = null, id = 3L))
        assertEquals(3, EventColorRules.resolveColorIndex(stored = -1, id = 3L))
        assertEquals(3, EventColorRules.resolveColorIndex(stored = 8, id = 3L))
        assertEquals(3, EventColorRules.resolveColorIndex(stored = 100, id = 3L))
        assertEquals(7, EventColorRules.resolveColorIndex(stored = null, id = -1L))
    }

    @Test
    fun r7_pickColorIndex_avoidsOverlapPredecessorAndSuccessorSimultaneously() {
        val sameDay = listOf(
            // 非紧邻前驱（更早结束）：07:00-08:00，不应阻挡 0
            slot(7, 8, colorIndex = 0),
            // 紧邻前驱（最大 endAt <= 10:00）：08:30-09:30，占用 1
            slot(8, 9, colorIndex = 1, startMinute = 30, endMinute = 30),
            // 重叠事件：09:45-10:30，占用 2
            slot(9, 10, colorIndex = 2, startMinute = 45, endMinute = 30),
            // 紧邻后继（最小 startAt >= 11:00）：11:30-12:00，占用 3
            slot(11, 12, colorIndex = 3, startMinute = 30, endMinute = 0),
            // 远端后继（14:00-15:00）：不应阻挡
            slot(14, 15, colorIndex = 4),
        )

        val picked = EventColorRules.pickColorIndex(
            targetStart = at(10, 0),
            targetEnd = at(11, 0),
            sameDaySlots = sameDay,
            rotationSeed = 1L,
        )

        assertFalse("不得与紧邻前驱撞色", picked == 1)
        assertFalse("不得与重叠事件撞色", picked == 2)
        assertFalse("不得与紧邻后继撞色", picked == 3)
        assertEquals("从 rotationSeed=1 起跳过 1/2/3 后应选 4", 4, picked)
    }

    @Test
    fun r7_pickColorIndex_resolvesNullStoredColorsViaFallbackId() {
        val sameDay = listOf(
            slot(9, 10, colorIndex = null, fallbackId = 0L), // 紧邻前驱 -> 0
            slot(10, 11, colorIndex = null, fallbackId = 1L), // 重叠 -> 1
            slot(11, 12, colorIndex = null, fallbackId = 2L), // 紧邻后继 -> 2
        )

        val picked = EventColorRules.pickColorIndex(
            targetStart = at(10, 0),
            targetEnd = at(11, 0),
            sameDaySlots = sameDay,
            rotationSeed = 0L,
        )

        assertEquals(3, picked)
    }

    @Test
    fun r7_pickColorIndex_avoidsZeroLengthConcurrentRunningSlotAtSameStart() {
        val now = at(10, 0)
        val runningAtSameMs = ColoredTimeSlot(
            startAt = now,
            endAt = now,
            colorIndex = 0,
            fallbackId = 1L,
        )

        val picked = EventColorRules.pickColorIndex(
            targetStart = now,
            targetEnd = now.plusSeconds(1),
            sameDaySlots = listOf(runningAtSameMs),
            rotationSeed = 0L,
        )

        assertNotEquals("同毫秒并行启动不得撞色", 0, picked)
        assertEquals(1, picked)
    }

    @Test
    fun r7_pickColorIndex_degradesToAvoidAdjacentWhenAllEightColorsOverlapped() {
        // 8 条并行重叠占满 0..7，另加紧邻前驱(2)与紧邻后继(3)。
        val overlappedAllEight = (0 until 8).map { color ->
            slot(10, 11, colorIndex = color, fallbackId = color.toLong())
        }
        val adjacent = listOf(
            slot(9, 10, colorIndex = 2),
            slot(11, 12, colorIndex = 3),
        )

        val picked = EventColorRules.pickColorIndex(
            targetStart = at(10, 0),
            targetEnd = at(11, 0),
            sameDaySlots = overlappedAllEight + adjacent,
            rotationSeed = 2L,
        )

        assertNotEquals("满色退化后仍应避让紧邻前驱", 2, picked)
        assertNotEquals("满色退化后仍应避让紧邻后继", 3, picked)
        assertEquals("从 2 起跳过前驱 2 与后继 3 应选 4", 4, picked)
    }

    @Test
    fun r7_pickColorIndex_degradesToLowestFrequencyWhenAdjacentAlsoCoversAllEight() {
        // 紧邻前驱（都在 10:00 结束）与紧邻后继（都在 11:00 开始）合起来占满 0..7；
        // 其中除色槽 5 只出现 1 次外，其余色槽额外在更早时段出现，因此色槽 5 频次最低。
        val predecessors = (0 until 4).map { color -> slot(9, 10, colorIndex = color) }
        val successors = (4 until 8).map { color -> slot(11, 12, colorIndex = color) }
        val extraCounts = (0 until 8)
            .filter { it != 5 }
            .map { color -> slot(6, 7, colorIndex = color) }

        val picked = EventColorRules.pickColorIndex(
            targetStart = at(10, 0),
            targetEnd = at(11, 0),
            sameDaySlots = predecessors + successors + extraCounts,
            rotationSeed = 0L,
        )

        assertEquals("全满时应退化选择同日频次最低的色槽 5", 5, picked)
    }

    @Test
    fun r7_pickColorIndex_deterministicRotationAvoidsTwoColorAlternation() {
        val slots = mutableListOf<ColoredTimeSlot>()
        val pickedColors = mutableListOf<Int>()
        for (hour in 8 until 14) {
            val color = EventColorRules.pickColorIndex(
                targetStart = at(hour, 0),
                targetEnd = at(hour + 1, 0),
                sameDaySlots = slots,
            )
            pickedColors += color
            slots += slot(hour, hour + 1, colorIndex = color)
        }

        // 连续 6 个事件使用默认 rotationSeed = sameDaySlots.size，应产生 6 种互异色槽且相邻无重复。
        assertEquals("6 个连续事件不应退化为二色交替", 6, pickedColors.toSet().size)
        for (i in 1 until pickedColors.size) {
            assertNotEquals("相邻事件色槽必须不同", pickedColors[i - 1], pickedColors[i])
        }
        // 确定性：相同输入重复调用输出一致。
        val replay = EventColorRules.pickColorIndex(
            targetStart = at(14, 0),
            targetEnd = at(15, 0),
            sameDaySlots = slots,
        )
        val replayAgain = EventColorRules.pickColorIndex(
            targetStart = at(14, 0),
            targetEnd = at(15, 0),
            sameDaySlots = slots,
        )
        assertEquals(replay, replayAgain)
    }
}
