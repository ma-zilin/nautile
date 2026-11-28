package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R6-G5-V1 · AC-L-16 / AC-L-19 固定夹具精确数值（判决性 JVM 纯函数）。
 *
 * 夹具（用户 2026-11-28 确认口径）：
 * - 单活动 08:00—10:00（2h）；并行 A 09:00—10:00（1h）、B 09:30—10:30（1h）；
 * - 去重覆盖 2.5h、重复计时 1.5h、总累计 4h、分母 25.5h、未追踪 21.5h；
 * - 饼图 2 + 1 + 1 + 21.5 = 25.5h，百分比合计 100%；
 * - 重叠分钟不单独归属分类；切换顺序/重开（读回持久列表）口径不变。
 *
 * 时钟与时区注入固定值，不使用系统时间。
 */
class AcStatsExactNumbersG5Test {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val day: LocalDate = LocalDate.of(2026, 11, 28)
    private val createdAt: Instant = day.atTime(7, 0).atZone(zone).toInstant()

    private fun at(hour: Int, minute: Int = 0): Instant =
        day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun category(id: Long, name: String): TimeCategory = TimeCategory(
        id = TimeCategoryId(id),
        name = name,
        parentId = null,
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

    private val single = category(1, "学习")
    private val parallelA = category(2, "并行A")
    private val parallelB = category(3, "并行B")
    private val categories = listOf(single, parallelA, parallelB)

    private fun fixtureLogs(): List<TimeLog> = listOf(
        log(1, single.id, at(8), at(10)),
        log(2, parallelA.id, at(9), at(10)),
        log(3, parallelB.id, at(9, 30), at(10, 30)),
    )

    private fun minutes(duration: Duration): Long = duration.toMinutes()

    @Test
    fun AC_L_16_exactFixture_coveredDuplicateDenominatorAndUntracked() {
        val stats = TimeStats.computeDay(fixtureLogs(), categories, day, zone, at(23))

        assertEquals("总累计 2+1+1=4h", 240L, minutes(stats.totalAccumulated))
        assertEquals("去重覆盖 08:00—10:30=2.5h", 150L, minutes(stats.dedupCovered))
        assertEquals("重复计时 4h-2.5h=1.5h", 90L, minutes(stats.duplicateDuration))
        assertEquals("分母 24h+1.5h=25.5h", 1530L, minutes(stats.denominator))
        assertEquals("未追踪 24h-2.5h=21.5h", 1290L, minutes(stats.untracked))

        val leaf = stats.categories.associate { it.categoryId to minutes(it.duration) }
        assertEquals("单活动 2h", 120L, leaf[single.id])
        assertEquals("并行 A 1h", 60L, leaf[parallelA.id])
        assertEquals("并行 B 1h", 60L, leaf[parallelB.id])
        assertEquals("重叠分钟不新增分类", 3, stats.categories.size)
    }

    @Test
    fun AC_L_16_pieSlicesSumToDenominator_andPercentSumIs100() {
        val stats = TimeStats.computeDay(fixtureLogs(), categories, day, zone, at(23))
        val names = categories.associate { it.id to it.name }
        val slices = TimeStats.chartSlices(stats.parents, names, stats.denominator, stats.untracked)

        assertEquals("父级分类 3 片 + 未追踪 1 片", 4, slices.size)
        assertEquals("饼图时长合计 = 分母", minutes(stats.denominator), slices.sumOf { minutes(it.duration) })
        assertEquals("2 + 1 + 1 + 21.5 = 25.5h", 1530L, slices.sumOf { minutes(it.duration) })
        assertTrue("无分类被合并或遗漏", slices.none { it.label == "重叠" })

        val untracked = slices.single { it.untracked }
        assertEquals("未追踪单独成片", 1290L, minutes(untracked.duration))
        assertEquals("未追踪标注", "未追踪", untracked.label)

        val fractions = slices.map { it.fraction }
        assertEquals("比例合计 100%", 1.0, fractions.sum(), 1e-9)
        val labels = TimeStats.percentLabels(fractions)
        assertEquals("整数百分比合计恒为 100", 100, labels.sum())
        labels.forEachIndexed { index, label ->
            val exact = fractions[index] * 100.0
            assertTrue(
                "百分比 ${label}% 与真值 ${exact}% 偏差应 ≤1（最大余数法）",
                abs(label - exact) <= 1.0,
            )
        }
    }

    @Test
    fun AC_L_19_restartAndOrderDoNotChangeNumbers() {
        val first = TimeStats.computeDay(fixtureLogs(), categories, day, zone, at(23))
        val reversed = TimeStats.computeDay(fixtureLogs().reversed(), categories.reversed(), day, zone, at(23))

        // 模拟进程重启：从持久层（内存仓库等价 Room 的读回顺序）重新计算。
        val store = InMemoryTimeLogRepository()
        runBlocking { fixtureLogs().forEach { store.insert(it) } }
        val persisted = store.snapshot
        val restarted = TimeStats.computeDay(persisted, categories, day, zone, at(23))

        listOf(reversed, restarted).forEach { stats ->
            assertEquals("重复计时不随重启变化", 90L, minutes(stats.duplicateDuration))
            assertEquals("分母不随重启变化", 1530L, minutes(stats.denominator))
            assertEquals("未追踪不随重启变化", 1290L, minutes(stats.untracked))
            assertEquals("总累计不随重启变化", 240L, minutes(stats.totalAccumulated))
        }

        val names = categories.associate { it.id to it.name }
        val slices = TimeStats.chartSlices(first.parents, names, first.denominator, first.untracked)
        assertEquals("饼图合计 25.5h", 1530L, slices.sumOf { minutes(it.duration) })
    }

    @Test
    fun AC_L_19_percentageIsNotReportedAsDayShare() {
        val stats = TimeStats.computeDay(fixtureLogs(), categories, day, zone, at(23))
        val names = categories.associate { it.id to it.name }
        val slices = TimeStats.chartSlices(stats.parents, names, stats.denominator, stats.untracked)
        val labels = TimeStats.percentLabels(slices.map { it.fraction })

        // 2h 活动在 25.5h 分母下应约 8%，而不是“2/24≈8.3% 的一天占比”或“100% 占满一天”。
        val studyIndex = slices.indexOfFirst { it.categoryId == single.id }
        assertTrue("单活动比例约 8%", abs(labels[studyIndex] - 8) <= 1)
        val untrackedIndex = slices.indexOfFirst { it.untracked }
        assertTrue("未追踪比例约 84%", abs(labels[untrackedIndex] - 84) <= 1)
    }
}
