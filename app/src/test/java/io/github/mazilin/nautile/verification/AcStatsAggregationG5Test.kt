package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.domain.model.LocalDateRange
import io.github.mazilin.nautile.domain.rule.TimeStats
import io.github.mazilin.nautile.testing.InMemoryTimeLogRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R6-G5-V1 · AC-L-03 父级汇总、AC-L-06 跨天两段、AC-L-15 运行中/未追踪（JVM 纯函数）。
 *
 * 口径来自基线 v2.16 与用户 2026-11-28 确认：
 * - 父级 = 直属 + 子级，条数与比例按同一父级合计，比例合计 100%；
 * - 跨天日志仍一条逻辑记录，统计在本地 00:00 派生两段分别计入两天；
 * - 运行中按 `max(0, now-startAt)` 实时计入，结束后不回退；未追踪不为负；
 * - 并行重叠重复计入总累计，去重覆盖只算一次。
 */
class AcStatsAggregationG5Test {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val day: LocalDate = LocalDate.of(2026, 11, 28)
    private val createdAt: Instant = day.minusDays(2).atTime(7, 0).atZone(zone).toInstant()

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant =
        date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun at(hour: Int, minute: Int = 0): Instant = at(day, hour, minute)

    private fun category(id: Long, name: String, parentId: TimeCategoryId? = null): TimeCategory =
        TimeCategory(
            id = TimeCategoryId(id),
            name = name,
            parentId = parentId,
            sortOrder = id.toInt(),
            createdAt = createdAt,
            updatedAt = createdAt,
        )

    private fun log(id: Long, categoryId: TimeCategoryId, startAt: Instant, endAt: Instant?): TimeLog =
        TimeLog(
            id = TimeLogId(id),
            categoryId = categoryId,
            startAt = startAt,
            endAt = endAt,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

    private fun minutes(duration: Duration): Long = duration.toMinutes()

    // ---------------- AC-L-03 父级汇总 ----------------

    @Test
    fun AC_L_03_parentAggregatesThreeLogsAndSharesSumTo100() {
        // 夹具：父级“学习”直属 1 条 + 两个子级各 1 条；时长取等长 1h，使比例可精确核对
        // （AC-L-03 原文未给时长，AMB-04 建议等长夹具；条数与时长口径分开断言）。
        val parent = category(10, "学习")
        val math = category(11, "数学", parent.id)
        val english = category(12, "英语", parent.id)
        val categories = listOf(parent, math, english)
        val logs = listOf(
            log(1, parent.id, at(8), at(9)),
            log(2, math.id, at(9), at(10)),
            log(3, english.id, at(10), at(11)),
        )

        val stats = TimeStats.computeDay(logs, categories, day, zone, at(12))

        assertEquals("只应出现 1 个父级行", 1, stats.parents.size)
        val aggregate = stats.parents.single()
        assertEquals("父级为学习", parent.id, aggregate.parentId)
        assertEquals("父级合计 3 条", 3, aggregate.logCount)
        assertEquals("父级合计 3h", 180L, minutes(aggregate.duration))
        assertEquals("父级直属 1 条", 1, TimeStats.directLogCount(logs, parent.id, day, zone))

        assertEquals("展开项 = 两个子级 + 直属", 3, aggregate.children.size)
        val byLabel = aggregate.children.associateBy { it.label }
        assertEquals("数学 1h", 60L, minutes(byLabel.getValue("数学").duration))
        assertEquals("英语 1h", 60L, minutes(byLabel.getValue("英语").duration))
        assertEquals("直属 1h", 60L, minutes(byLabel.getValue("直属").duration))
        assertTrue("直属项用 null 区分，不与子级 id 混淆", byLabel.getValue("直属").categoryId == null)
        assertNotNull("子级保留分类 id", byLabel.getValue("数学").categoryId)

        assertEquals("子级比例合计 100%", 1.0, aggregate.children.sumOf { it.fraction }, 1e-9)
        assertEquals(
            "整数百分比合计 100",
            100,
            TimeStats.percentLabels(aggregate.children.map { it.fraction }).sum(),
        )
        aggregate.children.forEach { share ->
            assertTrue("比例 ${share.fraction} 与 1/3 偏差小", abs(share.fraction - 1.0 / 3.0) < 1e-9)
        }

        val topLevels = stats.parents.map { it.parentId }
        assertFalse("子级不得作为顶层行混层", math.id in topLevels)
        assertFalse("子级不得作为顶层行混层", english.id in topLevels)
    }

    // ---------------- AC-L-06 跨天 ----------------

    @Test
    fun AC_L_06_crossMidnightIsOneLogAndTwoSegments() {
        val study = category(1, "学习")
        val categories = listOf(study)
        val startDay = LocalDate.of(2026, 11, 27)
        val crossing = log(1, study.id, at(startDay, 23, 50), at(day, 0, 10))

        val firstDay = TimeStats.segmentsForDay(listOf(crossing), startDay, zone, at(2))
        val secondDay = TimeStats.segmentsForDay(listOf(crossing), day, zone, at(2))

        assertEquals("开始日为 1 段", 1, firstDay.size)
        assertEquals("次日为 1 段", 1, secondDay.size)
        assertEquals("两段同属一条逻辑记录", firstDay.single().logId, secondDay.single().logId)
        assertEquals("开始日 23:50—00:00 = 10 分钟", 10L, minutes(firstDay.single().end.let {
            Duration.between(firstDay.single().start, it)
        }))
        assertEquals("次日 00:00—00:10 = 10 分钟", 10L, minutes(
            Duration.between(secondDay.single().start, secondDay.single().end),
        ))
        assertFalse("跨天不产生两条日志", firstDay.single().logId != secondDay.single().logId)

        // 持久层仍是一条：插入后快照 size 为 1（编辑/删除作用于整条）。
        val store = InMemoryTimeLogRepository()
        runBlocking { store.insert(crossing) }
        assertEquals("仓库仍为一条记录", 1, store.snapshot.size)

        // 条数口径：只计入开始日，不在次日重复计数。
        assertEquals("开始日计 1 条", 1, TimeStats.directLogCount(listOf(crossing), study.id, startDay, zone))
        assertEquals("次日不重复计条", 0, TimeStats.directLogCount(listOf(crossing), study.id, day, zone))

        // 范围统计把两天各 10 分钟相加，且未追踪按 24h×2 口径。
        val range = TimeStats.computeRange(
            listOf(crossing),
            categories,
            LocalDateRange(startDay, day),
            zone,
            at(2),
        )
        assertEquals("跨天总累计 20 分钟", 20L, minutes(range.totalAccumulated))
        assertEquals("两日去重覆盖 20 分钟", 20L, minutes(range.dedupCovered))
        assertEquals("重复计时 0（单条记录）", 0L, minutes(range.duplicateDuration))
        assertEquals("分母 24h×2=48h", 2880L, minutes(range.denominator))
        assertEquals("未追踪 48h-20m", 2860L, minutes(range.untracked))
    }

    // ---------------- AC-L-15 运行中 / 未追踪 ----------------

    @Test
    fun AC_L_15_runningLogIsCountedLiveAndStaysAfterStop() {
        val study = category(1, "学习")
        val categories = listOf(study)
        val startAt = at(9)
        val running = log(1, study.id, startAt, null)

        val at10 = TimeStats.computeDay(listOf(running), categories, day, zone, at(10))
        assertEquals("10:00 时已用 1h", 60L, minutes(at10.totalAccumulated))
        assertEquals("未追踪 23h", 1380L, minutes(at10.untracked))

        val at11 = TimeStats.computeDay(listOf(running), categories, day, zone, at(11))
        assertEquals("11:00 时已用 2h", 120L, minutes(at11.totalAccumulated))
        assertEquals("分母仍 24h（无重叠）", 1440L, minutes(at11.denominator))

        val stopped = running.copy(endAt = at(11))
        val later = TimeStats.computeDay(listOf(stopped), categories, day, zone, at(23))
        assertEquals("结束后保持 2h，不因 now 增大而回退", 120L, minutes(later.totalAccumulated))
        assertEquals("结束后未追踪 22h", 1320L, minutes(later.untracked))
    }

    @Test
    fun AC_L_15_clockRollbackYieldsZeroNotNegative() {
        val study = category(1, "学习")
        val running = log(1, study.id, at(9), null)

        val stats = TimeStats.computeDay(listOf(running), listOf(study), day, zone, at(8))

        assertEquals("时钟早于开始时刻，累计为 0", 0L, minutes(stats.totalAccumulated))
        assertEquals("未追踪保持 24h", 1440L, minutes(stats.untracked))
        assertFalse("不产生负数段", stats.segments.any { it.end.isBefore(it.start) })
    }

    @Test
    fun AC_L_15_parallelOverlapCountsTwiceInTotalButOnceInCoverage() {
        val a = category(1, "并行A")
        val b = category(2, "并行B")
        val c = category(3, "并行C")
        val categories = listOf(a, b, c)
        val logs = listOf(
            log(1, a.id, at(8), at(20)),
            log(2, b.id, at(8), at(20)),
            log(3, c.id, at(8), at(20)),
        )

        val stats = TimeStats.computeDay(logs, categories, day, zone, at(23))

        assertEquals("总累计 3×12h=36h（含重叠累计）", 2160L, minutes(stats.totalAccumulated))
        assertEquals("去重覆盖只算一次 12h", 720L, minutes(stats.dedupCovered))
        assertEquals("重复计时 24h", 1440L, minutes(stats.duplicateDuration))
        assertEquals("分母 24h+24h=48h", 2880L, minutes(stats.denominator))
        assertEquals("未追踪 12h", 720L, minutes(stats.untracked))
        assertTrue("未追踪非负", !stats.untracked.isNegative)

        val names = categories.associate { it.id to it.name }
        val slices = TimeStats.chartSlices(stats.parents, names, stats.denominator, stats.untracked)
        assertEquals("饼图合计 = 分母", 2880L, slices.sumOf { minutes(it.duration) })
        assertTrue("未追踪单独成片", slices.any { it.untracked && it.label == "未追踪" })
    }

    @Test
    fun AC_L_15_untrackedAndDuplicateAreClampedAtZero() {
        assertEquals("覆盖 30h 时未追踪钳制为 0", Duration.ZERO, TimeStats.untracked(Duration.ofHours(30)))
        assertEquals(
            "总累计小于覆盖时重复计时钳制为 0",
            Duration.ZERO,
            TimeStats.duplicateDuration(Duration.ofHours(1), Duration.ofHours(2)),
        )
    }
}
