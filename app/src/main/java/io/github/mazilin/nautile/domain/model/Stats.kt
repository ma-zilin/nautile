package io.github.mazilin.nautile.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * 统计用时间片段（架构 §3.4）。
 *
 * 一条日志跨天时按本地 00:00 拆成多段，但日志本身仍是一条逻辑记录。
 * [running] 为 true 表示该段来自运行中日志（`endAt == null`），实时值由调用方注入的 `now` 决定。
 */
data class TimedSegment(
    val logId: TimeLogId,
    val categoryId: TimeCategoryId,
    val start: Instant,
    val end: Instant,
    val running: Boolean,
)

/** 叶子分类累计；时长含并行重叠重复计入（架构 §3.4）。 */
data class CategoryAggregate(
    val categoryId: TimeCategoryId,
    val duration: Duration,
    val logCount: Int,
)

/**
 * 父级展开项（架构 §3.4）。
 *
 * [categoryId] 为 null 表示“直属”项（父级自身的日志），避免与子级 id 混淆。
 * [fraction] 以父级总时长为分母；父级总时长为 0 时为 0，不产生 NaN。
 */
data class ChildShare(
    val categoryId: TimeCategoryId?,
    val label: String,
    val duration: Duration,
    val logCount: Int,
    val fraction: Double,
)

/** 父级汇总：父级直属 + 子级；比例合计 100%（按时长，架构 §3.4/AC-L-03）。 */
data class ParentAggregate(
    val parentId: TimeCategoryId,
    val duration: Duration,
    val logCount: Int,
    val children: List<ChildShare>,
)

/** 单日完整统计（架构 §3.4）。 */
data class DayTimeStats(
    val date: LocalDate,
    val segments: List<TimedSegment>,
    val totalAccumulated: Duration,
    val dedupCovered: Duration,
    val duplicateDuration: Duration,
    val denominator: Duration,
    val untracked: Duration,
    val categories: List<CategoryAggregate>,
    val parents: List<ParentAggregate>,
)

/** 多日范围统计（架构 §3.4）。 */
data class RangeTimeStats(
    val range: LocalDateRange,
    val days: List<DayTimeStats>,
    val totalAccumulated: Duration,
    val dedupCovered: Duration,
    val duplicateDuration: Duration,
    val denominator: Duration,
    val untracked: Duration,
    val categories: List<CategoryAggregate>,
    val parents: List<ParentAggregate>,
)

/**
 * 饼图分片（架构 §3.4；05 §3.5）。
 *
 * [fraction] 以分母（24h × 天数 + 重复计时）为基准，不是“一天占比”；
 * [untracked] 为 true 表示未追踪片，单独成片。
 */
data class ChartSlice(
    val categoryId: TimeCategoryId?,
    val label: String,
    val duration: Duration,
    val fraction: Double,
    val untracked: Boolean,
)

/**
 * 统计周期（架构 §3.4、§5.2）。
 *
 * 日/月/年/自定义全部显示饼图；周期不持久化，退出统计或离开日志后恢复今日（AC-T-25）。
 */
sealed interface StatsPeriod {

    data class Day(val date: LocalDate) : StatsPeriod

    data class Month(val yearMonth: YearMonth) : StatsPeriod

    data class Year(val year: Int) : StatsPeriod

    data class Custom(val start: LocalDate, val end: LocalDate) : StatsPeriod
}
