package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.data.db.entity.ScheduleEventEntity
import io.github.mazilin.nautile.data.mapper.ScheduleMapper
import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.rule.ScheduleRules
import io.github.mazilin.nautile.domain.rule.ScheduleTagFilter
import io.github.mazilin.nautile.domain.rule.filterByTags
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R7-G1 · 日程多选标签筛选与 ScheduleMapper 来源兼容验证（#5、#11、Q6、架构 §2.2 / §3.3）。
 *
 * 覆盖：
 * - ScheduleTagFilter 默认全选、切换“无标签”、切换系统“课程表”与用户标签；
 * - IMPORTED 且 tagId == null 兜底归入 courseTagId；
 * - ScheduleMapper 历史 "REGULAR"→MANUAL、"COURSE"→IMPORTED 读兼容与规范写、colorIndex 透传。
 */
class ScheduleTagFilterTest {

    private val now: Instant = Instant.parse("2026-11-28T08:00:00Z")
    private val courseTag = ScheduleTag(ScheduleTagId(1L), "课程表", "COURSE", 0)
    private val workTag = ScheduleTag(ScheduleTagId(2L), "工作", null, 1)
    private val studyTag = ScheduleTag(ScheduleTagId(3L), "学习", null, 2)
    private val allTags = listOf(courseTag, workTag, studyTag)

    private fun event(
        id: Long,
        title: String,
        source: ScheduleSource = ScheduleSource.MANUAL,
        tagId: ScheduleTagId? = null,
    ): ScheduleEvent = ScheduleEvent(
        id = ScheduleId(id),
        title = title,
        note = null,
        startAt = now,
        endAt = now.plusSeconds(3600),
        source = source,
        tagId = tagId,
        seriesId = null,
        originalStartAt = null,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun r7_filterByTags_defaultAllSelectedIncludesAllEventsAndNewlyAddedTags() {
        val events = listOf(
            event(1L, "无标签手动", ScheduleSource.MANUAL, null),
            event(2L, "工作日程", ScheduleSource.MANUAL, workTag.id),
            event(3L, "导入课程", ScheduleSource.IMPORTED, courseTag.id),
            event(4L, "无标签导入兜底", ScheduleSource.IMPORTED, null),
            event(5L, "AI 日程", ScheduleSource.AI, null),
        )
        val filter = ScheduleTagFilter()

        assertTrue(filter.isAllSelected)
        assertFalse(filter.hasActiveFilter(allTags))
        assertEquals(setOf(courseTag.id, workTag.id, studyTag.id), filter.selectedTagIds(allTags))
        assertEquals(
            listOf("无标签手动", "工作日程", "导入课程", "无标签导入兜底", "AI 日程"),
            filterByTags(events, filter, courseTag.id).map { it.title },
        )

        // 新增标签自动处于勾选态（排除集驱动）。
        val newTag = ScheduleTag(ScheduleTagId(4L), "健身", null, 3)
        assertTrue(filter.isTagSelected(newTag.id))
        assertTrue(newTag.id in filter.selectedTagIds(allTags + newTag))
    }

    @Test
    fun r7_filterByTags_toggleUntaggedExcludesOnlyTrueUntaggedKeepingImportedFallback() {
        val events = listOf(
            event(1L, "无标签手动", ScheduleSource.MANUAL, null),
            event(2L, "无标签 AI", ScheduleSource.AI, null),
            event(3L, "无标签导入兜底", ScheduleSource.IMPORTED, null),
            event(4L, "工作日程", ScheduleSource.MANUAL, workTag.id),
        )
        val filter = ScheduleTagFilter().toggleUntagged()

        assertFalse(filter.includeUntagged)
        assertFalse(filter.isAllSelected)
        assertTrue(filter.hasActiveFilter(allTags))
        assertEquals(
            "关闭无标签后仅排除真正无标签的 MANUAL/AI，IMPORTED 且 tagId==null 兜底归入课程表仍应保留",
            listOf("无标签导入兜底", "工作日程"),
            filterByTags(events, filter, courseTag.id).map { it.title },
        )

        // 当系统课程表标签不存在（courseTagId == null）时，无标签导入日程才按 includeUntagged 判定。
        assertEquals(
            listOf("工作日程"),
            filterByTags(events, filter, courseTagId = null).map { it.title },
        )
    }

    @Test
    fun r7_filterByTags_toggleCourseTagHidesBothExplicitCourseTagAndImportedFallback() {
        val events = listOf(
            event(1L, "无标签手动", ScheduleSource.MANUAL, null),
            event(2L, "显式课程表手动", ScheduleSource.MANUAL, courseTag.id),
            event(3L, "导入课程", ScheduleSource.IMPORTED, courseTag.id),
            event(4L, "无标签导入兜底", ScheduleSource.IMPORTED, null),
            event(5L, "工作日程", ScheduleSource.MANUAL, workTag.id),
        )
        val filter = ScheduleTagFilter().toggleTag(courseTag.id)

        assertFalse(filter.isTagSelected(courseTag.id))
        assertTrue(filter.hasActiveFilter(allTags))
        assertEquals(
            listOf("无标签手动", "工作日程"),
            filterByTags(events, filter, courseTag.id).map { it.title },
        )

        // 再次点击恢复勾选。
        val restored = filter.toggleTag(courseTag.id)
        assertTrue(restored.isAllSelected)
        assertEquals(5, filterByTags(events, restored, courseTag.id).size)
    }

    @Test
    fun r7_hasActiveFilter_ignoresDeletedTagResidualExclusion() {
        val deletedTagId = ScheduleTagId(99L)
        val filter = ScheduleTagFilter(excludedTagIds = setOf(deletedTagId), includeUntagged = true)

        assertFalse("排除集中仅残留已删除标签时不应高亮激活态", filter.hasActiveFilter(allTags))
    }

    @Test
    fun r7_scheduleMapper_readsLegacyRegularAndCourseAndWritesCanonicalNames() {
        assertEquals(ScheduleSource.MANUAL, ScheduleMapper.parseScheduleSource("REGULAR"))
        assertEquals(ScheduleSource.MANUAL, ScheduleMapper.parseScheduleSource("MANUAL"))
        assertEquals(ScheduleSource.AI, ScheduleMapper.parseScheduleSource("AI"))
        assertEquals(ScheduleSource.IMPORTED, ScheduleMapper.parseScheduleSource("COURSE"))
        assertEquals(ScheduleSource.IMPORTED, ScheduleMapper.parseScheduleSource("IMPORTED"))

        val invalidThrown = runCatching { ScheduleMapper.parseScheduleSource("UNKNOWN") }.exceptionOrNull()
        assertTrue("未知来源必须抛 IllegalArgumentException", invalidThrown is IllegalArgumentException)

        val legacyCourseEntity = ScheduleEventEntity(
            id = 7L,
            title = "高等数学",
            note = "旧记录",
            startAt = now.toEpochMilli(),
            endAt = now.plusSeconds(3600).toEpochMilli(),
            source = "COURSE",
            tagId = 1L,
            seriesId = null,
            originalStartAt = null,
            createdAt = now.toEpochMilli(),
            updatedAt = now.toEpochMilli(),
            colorIndex = 5,
        )
        val domain = ScheduleMapper.toDomain(legacyCourseEntity)
        assertEquals(ScheduleSource.IMPORTED, domain.source)
        assertEquals(5, domain.colorIndex)

        val writtenBack = ScheduleMapper.toEntity(domain)
        assertEquals("写路径必须写入规范名 IMPORTED", "IMPORTED", writtenBack.source)
        assertEquals(5, writtenBack.colorIndex)

        val legacyRegularEntity = legacyCourseEntity.copy(source = "REGULAR", colorIndex = null)
        val manualDomain = ScheduleMapper.toDomain(legacyRegularEntity)
        assertEquals(ScheduleSource.MANUAL, manualDomain.source)
        assertNull(manualDomain.colorIndex)
        assertEquals("写路径必须写入规范名 MANUAL", "MANUAL", ScheduleMapper.toEntity(manualDomain).source)
    }

    @Test
    fun r7_scheduleRules_defaultTag_mapsImportedToCourseTagOnly() {
        assertEquals(courseTag.id.value, ScheduleRules.defaultTag(ScheduleSource.IMPORTED, courseTag))
        assertNull(ScheduleRules.defaultTag(ScheduleSource.MANUAL, courseTag))
        assertNull(ScheduleRules.defaultTag(ScheduleSource.AI, courseTag))
        assertNull(ScheduleRules.defaultTag(ScheduleSource.IMPORTED, null))
    }
}
