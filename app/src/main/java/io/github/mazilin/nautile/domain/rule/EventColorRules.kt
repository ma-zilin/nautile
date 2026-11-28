package io.github.mazilin.nautile.domain.rule

import java.time.Instant

/**
 * 参与相邻异色判定的时间块抽象（日程与时间日志共用；R7 #22）。
 *
 * @property startAt 开始时刻
 * @property endAt 结束时刻（运行中日志由调用方传入当前有效结束时刻）
 * @property colorIndex 已落库色槽；旧记录为 null 时通过 [fallbackId] 确定性回退
 * @property fallbackId 确定性回退种子（通常传实体主键 ID）
 */
data class ColoredTimeSlot(
    val startAt: Instant,
    val endAt: Instant,
    val colorIndex: Int?,
    val fallbackId: Long = 0L,
)

/**
 * 8 色事件色槽选择与回退纯规则（R7 #22、架构 §4.1）。
 *
 * 纯 Kotlin + java.time，零 Android/Compose 依赖；所有算法完全确定可测。
 */
object EventColorRules {

    const val PALETTE_SIZE: Int = 8

    /** 兼容历史 `colorIndex == null` 旧记录的确定性回退色槽（`floorMod` 避免负数与 MIN_VALUE 溢出）。 */
    fun fallbackColorIndex(id: Long, paletteSize: Int = PALETTE_SIZE): Int {
        require(paletteSize > 0) { "paletteSize 必须大于 0" }
        return Math.floorMod(id, paletteSize.toLong()).toInt()
    }

    /** 解析有效色槽：合法落库值优先，否则回退确定性色槽。 */
    fun resolveColorIndex(
        stored: Int?,
        id: Long,
        paletteSize: Int = PALETTE_SIZE,
    ): Int {
        require(paletteSize > 0) { "paletteSize 必须大于 0" }
        return stored?.takeIf { it in 0 until paletteSize } ?: fallbackColorIndex(id, paletteSize)
    }

    /**
     * 收集与目标时段 `[targetStart, targetEnd)` 视觉相邻的全部色槽：
     * 重叠事件 ∪ 紧邻前驱（`endAt <= targetStart` 中 `endAt` 最大者）∪ 紧邻后继（`startAt >= targetEnd` 中 `startAt` 最小者）。
     */
    fun collectNeighborColorIndices(
        targetStart: Instant,
        targetEnd: Instant,
        sameDaySlots: List<ColoredTimeSlot>,
        paletteSize: Int = PALETTE_SIZE,
    ): Set<Int> {
        require(paletteSize > 0) { "paletteSize 必须大于 0" }
        if (sameDaySlots.isEmpty()) return emptySet()

        val result = LinkedHashSet<Int>()
        for (slot in sameDaySlots) {
            if (isOverlapping(slot, targetStart, targetEnd)) {
                result.add(resolveColorIndex(slot.colorIndex, slot.fallbackId, paletteSize))
            }
        }
        result.addAll(collectAdjacentColorIndices(targetStart, targetEnd, sameDaySlots, paletteSize))
        return result
    }

    /**
     * 避让指定相邻色槽集合，从 [rotationSeed] 偏移开始扫描第一个未占用的色槽；
     * 若所有色槽均被占用，则回退到偏移起点。
     */
    fun pickColorIndex(
        neighborIndices: Set<Int>,
        paletteSize: Int = PALETTE_SIZE,
        rotationSeed: Long = 0L,
    ): Int {
        require(paletteSize > 0) { "paletteSize 必须大于 0" }
        val startOffset = Math.floorMod(rotationSeed, paletteSize.toLong()).toInt()
        for (step in 0 until paletteSize) {
            val candidate = (startOffset + step) % paletteSize
            if (candidate !in neighborIndices) return candidate
        }
        return startOffset
    }

    /**
     * 为新事件选择避让重叠与时间紧邻前驱/后继的色槽（`0 until paletteSize`）。
     *
     * 三级优先级：
     * 1. 同时避让：重叠事件 ∪ 紧邻前驱 ∪ 紧邻后继；
     * 2. 若全部色槽被占满（极端 ≥8 并行），退化为仅避让紧邻前驱 ∪ 紧邻后继；
     * 3. 若仍全部占满，选择同日出现频次最低的色槽（平手按 [rotationSeed] 起始扫描顺序决定）。
     */
    fun pickColorIndex(
        targetStart: Instant,
        targetEnd: Instant,
        sameDaySlots: List<ColoredTimeSlot>,
        rotationSeed: Long = sameDaySlots.size.toLong(),
        paletteSize: Int = PALETTE_SIZE,
    ): Int {
        require(paletteSize > 0) { "paletteSize 必须大于 0" }
        val startOffset = Math.floorMod(rotationSeed, paletteSize.toLong()).toInt()

        // 优先级 1：避让重叠 ∪ 紧邻前驱 ∪ 紧邻后继。
        val allNeighbors = collectNeighborColorIndices(
            targetStart = targetStart,
            targetEnd = targetEnd,
            sameDaySlots = sameDaySlots,
            paletteSize = paletteSize,
        )
        firstAvailable(allNeighbors, startOffset, paletteSize)?.let { return it }

        // 优先级 2：极端多并行导致 8 色全满时，退化为仅避让紧邻前驱 ∪ 紧邻后继。
        val adjacentOnly = collectAdjacentColorIndices(
            targetStart = targetStart,
            targetEnd = targetEnd,
            sameDaySlots = sameDaySlots,
            paletteSize = paletteSize,
        )
        firstAvailable(adjacentOnly, startOffset, paletteSize)?.let { return it }

        // 优先级 3：仍全满时选同日出现频次最低的色槽。
        val counts = IntArray(paletteSize)
        for (slot in sameDaySlots) {
            val index = resolveColorIndex(slot.colorIndex, slot.fallbackId, paletteSize)
            counts[index]++
        }
        var bestIndex = startOffset
        var bestCount = counts[startOffset]
        for (step in 1 until paletteSize) {
            val candidate = (startOffset + step) % paletteSize
            if (counts[candidate] < bestCount) {
                bestCount = counts[candidate]
                bestIndex = candidate
            }
        }
        return bestIndex
    }

    /** 仅收集非重叠的紧邻前驱（最大 endAt <= targetStart）与紧邻后继（最小 startAt >= targetEnd）。 */
    private fun collectAdjacentColorIndices(
        targetStart: Instant,
        targetEnd: Instant,
        sameDaySlots: List<ColoredTimeSlot>,
        paletteSize: Int,
    ): Set<Int> {
        var maxPredecessorEnd: Instant? = null
        var minSuccessorStart: Instant? = null
        for (slot in sameDaySlots) {
            if (isOverlapping(slot, targetStart, targetEnd)) continue
            if (!slot.endAt.isAfter(targetStart)) {
                val current = maxPredecessorEnd
                if (current == null || slot.endAt.isAfter(current)) {
                    maxPredecessorEnd = slot.endAt
                }
            }
            if (!slot.startAt.isBefore(targetEnd)) {
                val current = minSuccessorStart
                if (current == null || slot.startAt.isBefore(current)) {
                    minSuccessorStart = slot.startAt
                }
            }
        }

        val result = LinkedHashSet<Int>()
        for (slot in sameDaySlots) {
            if (isOverlapping(slot, targetStart, targetEnd)) continue
            if (maxPredecessorEnd != null && slot.endAt == maxPredecessorEnd) {
                result.add(resolveColorIndex(slot.colorIndex, slot.fallbackId, paletteSize))
            }
            if (minSuccessorStart != null && slot.startAt == minSuccessorStart) {
                result.add(resolveColorIndex(slot.colorIndex, slot.fallbackId, paletteSize))
            }
        }
        return result
    }

    /**
     * 区间相交判定；对零长度（刚启动瞬时运行中记录 `startAt == endAt`）若落在目标区间起点/内部也视为重叠，
     * 确保同毫秒启动的并行计时互不撞色。
     */
    private fun isOverlapping(
        slot: ColoredTimeSlot,
        targetStart: Instant,
        targetEnd: Instant,
    ): Boolean {
        if (slot.startAt.isBefore(targetEnd) && slot.endAt.isAfter(targetStart)) return true
        return slot.startAt == slot.endAt &&
            !slot.startAt.isBefore(targetStart) &&
            slot.startAt.isBefore(targetEnd)
    }

    private fun firstAvailable(
        blocked: Set<Int>,
        startOffset: Int,
        paletteSize: Int,
    ): Int? {
        for (step in 0 until paletteSize) {
            val candidate = (startOffset + step) % paletteSize
            if (candidate !in blocked) return candidate
        }
        return null
    }
}
