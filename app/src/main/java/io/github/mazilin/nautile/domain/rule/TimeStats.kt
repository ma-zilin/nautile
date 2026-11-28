package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.CategoryAggregate
import io.github.mazilin.nautile.domain.model.ChartSlice
import io.github.mazilin.nautile.domain.model.ChildShare
import io.github.mazilin.nautile.domain.model.DayTimeStats
import io.github.mazilin.nautile.domain.model.LocalDateRange
import io.github.mazilin.nautile.domain.model.ParentAggregate
import io.github.mazilin.nautile.domain.model.RangeTimeStats
import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimedSegment
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor

/**
 * 统计纯函数（架构 §3.4；05 §2.5/§3.5）。
 *
 * 全部纯 Kotlin + java.time，无 Android/Compose；[now] 与 [zoneId] 由调用方注入，不读系统时钟。
 * 口径：
 * - 单日分母 = 24h + 重复计时；多日分母 = 24h × 范围天数 + 重复计时；
 * - 范围天数由 [StatsPeriodRules.rangeFor] 决定：当前月/当前年截至今天（含今天），
 *   已过去的完整月/年按整月/整年，自定义按起止（用户 2026-11-28 定）；
 * - 未追踪 = max(0, 24h × 范围天数 − 去重覆盖)，永不为负；
 * - 总累计按段直接相加，并行重叠重复计入；去重覆盖取区间并集；
 * - 跨天日志仍一条，按本地 00:00 派生两段分别计入两天；
 * - 运行中按 `elapsed = max(0, now − startAt)` 实时计入，结束后落库不再回退。
 */
object TimeStats {

    val DAY: Duration = Duration.ofHours(24)

    private val ZERO: Duration = Duration.ZERO

    /**
     * 把日志按本地 00:00 拆成 [date] 当天的段。
     *
     * 已完成日志按起止裁剪；运行中日志结束取 `min(now, 次日 00:00)`，
     * `now <= startAt`（设备时间回拨/异常）时该段长度为 0，不产生负数。
     */
    fun segmentsForDay(
        logs: List<TimeLog>,
        date: LocalDate,
        zoneId: ZoneId,
        now: Instant,
        includeRunning: Boolean = true,
    ): List<TimedSegment> {
        val dayStart = date.atStartOfDay(zoneId).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        return logs.mapNotNull { log ->
            val effectiveEnd = when {
                log.endAt != null -> log.endAt
                !includeRunning -> null
                else -> if (now.isBefore(dayEnd)) now else dayEnd
            } ?: return@mapNotNull null
            val start = if (log.startAt.isAfter(dayStart)) log.startAt else dayStart
            val end = if (effectiveEnd.isBefore(dayEnd)) effectiveEnd else dayEnd
            if (!end.isAfter(start)) return@mapNotNull null
            TimedSegment(
                logId = log.id,
                categoryId = log.categoryId,
                start = start,
                end = end,
                running = log.isRunning,
            )
        }
    }

    /** 区间并集时长（去重覆盖）；重叠只算一次，防御 end <= start 的损坏段。 */
    fun unionCoveredDuration(segments: List<TimedSegment>): Duration {
        val sorted = segments
            .filter { it.end.isAfter(it.start) }
            .sortedBy { it.start }
        if (sorted.isEmpty()) return ZERO
        var cursorStart = sorted.first().start
        var cursorEnd = sorted.first().end
        var total = ZERO
        for (segment in sorted.drop(1)) {
            if (segment.start.isAfter(cursorEnd)) {
                total += Duration.between(cursorStart, cursorEnd)
                cursorStart = segment.start
                cursorEnd = segment.end
            } else if (segment.end.isAfter(cursorEnd)) {
                cursorEnd = segment.end
            }
        }
        total += Duration.between(cursorStart, cursorEnd)
        return total
    }

    /** 总累计 = 各段时长之和；并行重叠重复计入。 */
    fun totalAccumulated(segments: List<TimedSegment>): Duration =
        segments.fold(ZERO) { acc, segment ->
            acc + Duration.between(segment.start, segment.end).coerceAtLeastZero()
        }

    /** 重复计时 = max(0, 总累计 − 去重覆盖)。 */
    fun duplicateDuration(total: Duration, covered: Duration): Duration =
        (total - covered).coerceAtLeastZero()

    /** 日分母 = 24h + 重复计时。 */
    fun dayDenominator(duplicate: Duration): Duration = DAY + duplicate

    /** 未追踪 = max(0, 24h − 去重覆盖)；不为负。 */
    fun untracked(covered: Duration): Duration = (DAY - covered).coerceAtLeastZero()

    /** 叶子分类累计（含重叠重复计入）；0 时长分类不返回。 */
    fun categoryDurations(segments: List<TimedSegment>): Map<TimeCategoryId, Duration> {
        val durations = mutableMapOf<TimeCategoryId, Duration>()
        segments.forEach { segment ->
            val duration = Duration.between(segment.start, segment.end).coerceAtLeastZero()
            if (duration > ZERO) {
                durations[segment.categoryId] = (durations[segment.categoryId] ?: ZERO) + duration
            }
        }
        return durations
    }

    /**
     * 直接日志条数（AC-L-03 的“条数”口径）。
     *
     * 按记录开始时刻所属本地日期归属：跨天记录只计入开始日，时长仍按段分摊到两天。
     */
    fun directLogCount(
        logs: List<TimeLog>,
        categoryId: TimeCategoryId,
        date: LocalDate,
        zoneId: ZoneId,
    ): Int = logs.count { log ->
        log.categoryId == categoryId && log.startAt.atZone(zoneId).toLocalDate() == date
    }

    /**
     * 父级汇总：父级直属 + 子级。
     *
     * 父级时长 = 直属日志 + 子级日志；条数同理；展开项“直属”的 [ChildShare.categoryId] 为 null。
     * 比例以父级总时长为分母，父级总时长为 0 时不输出该父级，不构造 NaN。
     */
    fun parentAggregates(
        categories: List<TimeCategory>,
        leafDurations: Map<TimeCategoryId, Duration>,
        directCounts: Map<TimeCategoryId, Int>,
    ): List<ParentAggregate> {
        val byParent = categories.filter { it.parentId != null }.groupBy { it.parentId }
        return categories.filter { it.isTopLevel }.mapNotNull { parent ->
            val childrenCategories = byParent[parent.id].orEmpty()
            val directDuration = leafDurations[parent.id] ?: ZERO
            val childrenDurations = childrenCategories.map { it.id to (leafDurations[it.id] ?: ZERO) }
            val totalDuration = childrenDurations.fold(directDuration) { acc, (_, d) -> acc + d }
            val totalCount = (directCounts[parent.id] ?: 0) +
                childrenCategories.sumOf { directCounts[it.id] ?: 0 }
            if (totalDuration <= ZERO && totalCount == 0) return@mapNotNull null

            val shares = buildList {
                childrenCategories.forEach { child ->
                    val duration = leafDurations[child.id] ?: ZERO
                    val count = directCounts[child.id] ?: 0
                    if (duration > ZERO || count > 0) {
                        add(
                            ChildShare(
                                categoryId = child.id,
                                label = child.name,
                                duration = duration,
                                logCount = count,
                                fraction = fractionOf(duration, totalDuration),
                            ),
                        )
                    }
                }
                if (directDuration > ZERO) {
                    add(
                        ChildShare(
                            categoryId = null,
                            label = "直属",
                            duration = directDuration,
                            logCount = directCounts[parent.id] ?: 0,
                            fraction = fractionOf(directDuration, totalDuration),
                        ),
                    )
                }
            }
            ParentAggregate(
                parentId = parent.id,
                duration = totalDuration,
                logCount = totalCount,
                children = shares,
            )
        }
    }

    /** 单日完整统计（架构 §3.4）。 */
    fun computeDay(
        logs: List<TimeLog>,
        categories: List<TimeCategory>,
        date: LocalDate,
        zoneId: ZoneId,
        now: Instant,
    ): DayTimeStats {
        val segments = segmentsForDay(logs, date, zoneId, now)
        val total = totalAccumulated(segments)
        val covered = unionCoveredDuration(segments)
        val duplicate = duplicateDuration(total, covered)
        val leafDurations = categoryDurations(segments)
        val directCounts = categories.associate { it.id to directLogCount(logs, it.id, date, zoneId) }
        return DayTimeStats(
            date = date,
            segments = segments,
            totalAccumulated = total,
            dedupCovered = covered,
            duplicateDuration = duplicate,
            denominator = dayDenominator(duplicate),
            untracked = untracked(covered),
            categories = leafDurations.map { (id, duration) ->
                CategoryAggregate(
                    categoryId = id,
                    duration = duration,
                    logCount = directCounts[id] ?: 0,
                )
            }.sortedByDescending { it.duration },
            parents = parentAggregates(categories, leafDurations, directCounts),
        )
    }

    /**
     * 多日区间统计：逐日 [computeDay] 后聚合。
     *
     * [range] 已由 [StatsPeriodRules.rangeFor] 按“当前月/年截至今天”裁剪；
     * 分母 = 24h × 范围天数 + 重复计时合计；未追踪 = 24h × 范围天数 − 去重覆盖合计。
     * 非法范围（结束早于开始）防御性按单日处理，不抛异常；业务校验由 [StatsPeriodRules] 负责。
     */
    fun computeRange(
        logs: List<TimeLog>,
        categories: List<TimeCategory>,
        range: LocalDateRange,
        zoneId: ZoneId,
        now: Instant,
    ): RangeTimeStats {
        val safeRange = if (range.endInclusive.isBefore(range.startInclusive)) {
            LocalDateRange(range.startInclusive, range.startInclusive)
        } else {
            range
        }
        val days = safeRange.dates().map { date ->
            computeDay(logs, categories, date, zoneId, now)
        }
        val total = days.fold(ZERO) { acc, day -> acc + day.totalAccumulated }
        val covered = days.fold(ZERO) { acc, day -> acc + day.dedupCovered }
        val duplicate = duplicateDuration(total, covered)
        val dayCount = safeRange.dayCount
        val leafDurations = mutableMapOf<TimeCategoryId, Duration>()
        days.forEach { day ->
            day.categories.forEach { aggregate ->
                leafDurations[aggregate.categoryId] =
                    (leafDurations[aggregate.categoryId] ?: ZERO) + aggregate.duration
            }
        }
        val directCounts = categories.associate { category ->
            category.id to logs.count { log ->
                log.categoryId == category.id &&
                    log.startAt.atZone(zoneId).toLocalDate().let { date ->
                        !date.isBefore(safeRange.startInclusive) && !date.isAfter(safeRange.endInclusive)
                    }
            }
        }
        return RangeTimeStats(
            range = safeRange,
            days = days,
            totalAccumulated = total,
            dedupCovered = covered,
            duplicateDuration = duplicate,
            denominator = DAY.multipliedBy(dayCount.toLong()) + duplicate,
            untracked = (DAY.multipliedBy(dayCount.toLong()) - covered).coerceAtLeastZero(),
            categories = leafDurations.map { (id, duration) ->
                CategoryAggregate(
                    categoryId = id,
                    duration = duration,
                    logCount = directCounts[id] ?: 0,
                )
            }.sortedByDescending { it.duration },
            parents = parentAggregates(categories, leafDurations, directCounts),
        )
    }

    /**
     * 饼图分片：父级分类 + 未追踪单独成片。
     *
     * 分母与未追踪由 [computeDay]/[computeRange] 给出；父级时长与未追踪之和等于分母，
     * 因此各片比例合计 100%。0 时长片不输出，空数据不产生 NaN。
     */
    fun chartSlices(
        parents: List<ParentAggregate>,
        categoryNames: Map<TimeCategoryId, String>,
        denominator: Duration,
        untracked: Duration,
        untrackedLabel: String = "未追踪",
    ): List<ChartSlice> {
        if (denominator <= ZERO) return emptyList()
        val slices = parents.mapNotNull { parent ->
            if (parent.duration <= ZERO) return@mapNotNull null
            ChartSlice(
                categoryId = parent.parentId,
                label = categoryNames[parent.parentId] ?: "",
                duration = parent.duration,
                fraction = fractionOf(parent.duration, denominator),
                untracked = false,
            )
        }.toMutableList()
        if (untracked > ZERO) {
            slices.add(
                ChartSlice(
                    categoryId = null,
                    label = untrackedLabel,
                    duration = untracked,
                    fraction = fractionOf(untracked, denominator),
                    untracked = true,
                ),
            )
        }
        return slices
    }

    /**
     * 把比例换成整数百分比，且合计恒为 100（最大余数法）。
     *
     * 仅用于界面文本；比例真值仍保留在 [ChartSlice.fraction]/[ChildShare.fraction]。
     */
    fun percentLabels(fractions: List<Double>): List<Int> {
        if (fractions.isEmpty()) return emptyList()
        val safe = fractions.map { if (it.isFinite() && it > 0.0) it else 0.0 }
        val sum = safe.sum()
        if (sum <= 0.0) return List(safe.size) { 0 }
        val raw = safe.map { it / sum * 100.0 }
        val labels = raw.map { floor(it).toInt() }.toMutableList()
        var remaining = 100 - labels.sum()
        val order = raw.indices.sortedByDescending { raw[it] - labels[it] }
        var index = 0
        while (remaining > 0 && order.isNotEmpty()) {
            labels[order[index % order.size]] += 1
            remaining--
            index++
        }
        while (remaining < 0 && order.isNotEmpty()) {
            val target = order[index % order.size]
            if (labels[target] > 0) {
                labels[target] -= 1
                remaining++
            }
            index++
            if (index > order.size * 2) break
        }
        return labels
    }

    private fun fractionOf(part: Duration, total: Duration): Double =
        if (total <= ZERO) 0.0 else part.toNanos().toDouble() / total.toNanos().toDouble()

    private fun Duration.coerceAtLeastZero(): Duration = if (isNegative) ZERO else this
}
