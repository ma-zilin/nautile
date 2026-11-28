package io.github.mazilin.nautile.contract

import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleSourceFilter
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.model.TimeRange
import io.github.mazilin.nautile.domain.repository.ScheduleRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日程仓库契约（R6-G2-V1；AC-S-03/AC-S-04 数据层）。
 *
 * 同一组断言由内存替身与 Room 实现共同继承；本类不依赖 Android。
 * 范围语义固定为半开区间相交：`startAt < rangeEnd && endAt > rangeStart`；
 * 来源筛选只按 `source`，标签不参与查询（D-new1）。
 */
abstract class ScheduleRepositoryContract {

    protected abstract val schedules: ScheduleRepository

    /** 保证“课程表”标签存在并返回其 ID；内存替身预置、Room 实现写种子行。 */
    protected abstract suspend fun ensureCourseTag(): ScheduleTagId

    protected open fun close() = Unit

    @After
    fun tearDownContract() {
        close()
    }

    private val today: LocalDate = LocalDate.of(2026, 11, 25)

    private fun instant(day: LocalDate, hour: Int, minute: Int = 0): Instant =
        day.atTime(hour, minute).toInstant(ZoneOffset.UTC)

    protected fun event(
        title: String,
        startAt: Instant,
        endAt: Instant,
        source: ScheduleSource = ScheduleSource.MANUAL,
        id: Long = 0L,
    ): ScheduleEvent = ScheduleEvent(
        id = ScheduleId(id),
        title = title,
        note = null,
        startAt = startAt,
        endAt = endAt,
        source = source,
        tagId = null,
        seriesId = null,
        originalStartAt = null,
        createdAt = startAt,
        updatedAt = startAt,
    )

    private fun range(startHour: Int, endHour: Int): TimeRange =
        TimeRange(instant(today, startHour), instant(today, endHour))

    private fun wholeDay(): TimeRange =
        TimeRange(instant(today, 0), instant(today.plusDays(1), 0))

    @Test
    fun g2_insert_assignsId_and_readBackByGetAndObserve() = runBlocking {
        val id = schedules.insert(
            event("新建日程", instant(today, 9), instant(today, 10)),
        )

        assertTrue("新日程 id 应大于 0", id.value > 0L)
        val stored = schedules.get(id)
        assertEquals("新建日程", stored?.title)
        assertEquals(ScheduleSource.MANUAL, stored?.source)
        assertNull("本轮默认无标签", stored?.tagId)
        assertEquals("新建日程", schedules.observeEvent(id).first()?.title)
    }

    @Test
    fun g2_update_persistsTitleAndTimeFields() = runBlocking {
        val id = schedules.insert(event("原标题", instant(today, 9), instant(today, 10)))

        schedules.update(
            event("新标题", instant(today, 11), instant(today, 12, 30)).copy(id = id),
        )

        val stored = schedules.get(id)
        assertEquals("新标题", stored?.title)
        assertEquals(instant(today, 11), stored?.startAt)
        assertEquals(instant(today, 12, 30), stored?.endAt)
    }

    @Test
    fun g2_delete_removesEventFromAllReads() = runBlocking {
        val id = schedules.insert(event("待删除", instant(today, 9), instant(today, 10)))

        schedules.delete(id)

        assertNull(schedules.get(id))
        assertNull(schedules.observeEvent(id).first())
        assertTrue(schedules.observeInRange(wholeDay(), ScheduleSourceFilter.MERGED).first().isEmpty())
    }

    @Test
    fun acS04_sourceFilter_separatesRegularAndCourseWithoutTouchingData() = runBlocking {
        schedules.insert(event("普通日程", instant(today, 9), instant(today, 10)))
        schedules.insert(
            event("课程日程", instant(today, 10), instant(today, 11), source = ScheduleSource.IMPORTED),
        )

        val merged = schedules.observeInRange(wholeDay(), ScheduleSourceFilter.MERGED).first()
        val courseOnly = schedules.observeInRange(wholeDay(), ScheduleSourceFilter.COURSE_ONLY).first()
        val regularOnly = schedules.observeInRange(wholeDay(), ScheduleSourceFilter.REGULAR_ONLY).first()

        assertEquals(listOf("普通日程", "课程日程"), merged.map { it.title })
        assertEquals("课程表来源只应含课程", listOf("课程日程"), courseOnly.map { it.title })
        assertEquals("仅普通只应含普通日程", listOf("普通日程"), regularOnly.map { it.title })
        // 拒绝：仅普通筛选不得出现课程来源。
        assertTrue(regularOnly.all { it.source == ScheduleSource.MANUAL })
    }

    @Test
    fun acS04_courseTagIsStoredButNeverFiltersOrChangesSource() = runBlocking {
        val courseTag = ensureCourseTag()
        schedules.insert(
            event("标课程表标签的普通日程", instant(today, 9), instant(today, 10))
                .copy(tagId = courseTag),
        )
        schedules.insert(event("无标签的普通日程", instant(today, 10), instant(today, 11)))

        val regularOnly = schedules.observeInRange(wholeDay(), ScheduleSourceFilter.REGULAR_ONLY).first()
        val courseOnly = schedules.observeInRange(wholeDay(), ScheduleSourceFilter.COURSE_ONLY).first()

        // 标签只存储：两条普通日程都返回，标签不是查询条件。
        assertEquals(
            setOf("标课程表标签的普通日程", "无标签的普通日程"),
            regularOnly.map { it.title }.toSet(),
        )
        val tagged = regularOnly.first { it.title == "标课程表标签的普通日程" }
        assertEquals("标签不得改变来源（AC-S-04）", ScheduleSource.MANUAL, tagged.source)
        assertEquals("标签应原样保存", courseTag, tagged.tagId)
        // 拒绝：课程表来源筛选不得因标签而包含普通日程。
        assertTrue("课程表来源筛选不得出现普通日程", courseOnly.none { it.source == ScheduleSource.MANUAL })
        assertTrue(courseOnly.isEmpty())
    }

    @Test
    fun g2_rangeQuery_includesOverlapsAndExcludesTouchingBoundaries() = runBlocking {
        val target = range(10, 12)
        schedules.insert(event("区间内", instant(today, 10, 30), instant(today, 11)))
        schedules.insert(event("左重叠", instant(today, 9), instant(today, 10, 30)))
        schedules.insert(event("右重叠", instant(today, 11, 30), instant(today, 13)))
        schedules.insert(event("刚好在起点结束", instant(today, 8), instant(today, 10)))
        schedules.insert(event("刚好在终点开始", instant(today, 12), instant(today, 13)))
        schedules.insert(event("区间前", instant(today, 8), instant(today, 9)))
        schedules.insert(event("区间后", instant(today, 13), instant(today, 14)))

        val titles = schedules.observeInRange(target, ScheduleSourceFilter.MERGED).first().map { it.title }

        assertEquals(
            "范围查询应为半开区间相交",
            setOf("区间内", "左重叠", "右重叠"),
            titles.toSet(),
        )
    }

    @Test
    fun g2_rangeQuery_ordersByStartThenId() = runBlocking {
        val laterFirst = schedules.insert(event("同起点A", instant(today, 10), instant(today, 11)))
        val laterSecond = schedules.insert(event("同起点B", instant(today, 10), instant(today, 11)))
        schedules.insert(event("更早", instant(today, 9), instant(today, 10)))

        val ordered = schedules.observeInRange(range(8, 12), ScheduleSourceFilter.MERGED).first()

        assertEquals(listOf("更早", "同起点A", "同起点B"), ordered.map { it.title })
        assertTrue("同起点应按 id 升序", laterFirst.value < laterSecond.value)
    }
}
