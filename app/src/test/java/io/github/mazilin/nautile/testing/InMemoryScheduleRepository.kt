package io.github.mazilin.nautile.testing

import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleSourceFilter
import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.model.TimeRange
import io.github.mazilin.nautile.domain.repository.ScheduleRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 日程仓库内存替身（R6-G2-V1、R7-G1 同步）。
 *
 * 查询语义与 Room 实现一致：`startAt < rangeEnd && endAt > rangeStart`（半开区间相交），
 * 来源筛选只按 `source`，不涉及标签（D-new1）；排序 `startAt ASC, id ASC`。
 * 写入计数用于 AC-S-13 的“点日期/切视图不改数据”断言。
 */
class InMemoryScheduleRepository(
    initialEvents: List<ScheduleEvent> = emptyList(),
    initialTags: List<ScheduleTag> = emptyList(),
) : ScheduleRepository {

    private val events = MutableStateFlow(initialEvents.associateBy { it.id.value })
    private val tags = MutableStateFlow(initialTags.associateBy { it.id.value })
    private var nextId = (initialEvents.maxOfOrNull { it.id.value } ?: 0L) + 1
    private var nextTagId = (initialTags.maxOfOrNull { it.id.value } ?: 0L) + 1

    /** 已发生的写操作次数（insert/update/delete）；只读查询不应增加。 */
    var writeCount: Int = 0
        private set

    /** 当前全部日程快照，按 id 升序；供测试断言数据未被修改。 */
    val snapshot: List<ScheduleEvent>
        get() = events.value.values.sortedBy { it.id.value }

    override fun observeTags(): Flow<List<ScheduleTag>> =
        tags.map { stored -> stored.values.sortedWith(compareBy({ it.sortOrder }, { it.id.value })) }

    override fun observeTagEventCounts(): Flow<Map<ScheduleTagId, Int>> =
        events.map { stored ->
            stored.values
                .mapNotNull { it.tagId }
                .groupingBy { it }
                .eachCount()
        }

    override fun observeInRange(
        range: TimeRange,
        source: ScheduleSourceFilter,
    ): Flow<List<ScheduleEvent>> = events.map { stored ->
        stored.values
            .filter { it.startAt.isBefore(range.endExclusive) && it.endAt.isAfter(range.startInclusive) }
            .filter { event ->
                when (source) {
                    ScheduleSourceFilter.MERGED -> true
                    ScheduleSourceFilter.COURSE_ONLY -> event.source == ScheduleSource.IMPORTED
                    ScheduleSourceFilter.REGULAR_ONLY -> event.source == ScheduleSource.MANUAL
                }
            }
            .sortedWith(compareBy({ it.startAt }, { it.id.value }))
    }

    override suspend fun listInRange(range: TimeRange): List<ScheduleEvent> =
        events.value.values
            .filter { it.startAt.isBefore(range.endExclusive) && it.endAt.isAfter(range.startInclusive) }
            .sortedWith(compareBy({ it.startAt }, { it.id.value }))

    override fun observeEvent(id: ScheduleId): Flow<ScheduleEvent?> =
        events.map { stored -> stored[id.value] }

    override suspend fun get(id: ScheduleId): ScheduleEvent? = events.value[id.value]

    override suspend fun insert(event: ScheduleEvent): ScheduleId {
        val id = if (event.id.value == 0L) ScheduleId(nextId++) else event.id
        if (id.value >= nextId) nextId = id.value + 1
        events.value = events.value + (id.value to event.copy(id = id))
        writeCount++
        return id
    }

    override suspend fun update(event: ScheduleEvent) {
        if (!events.value.containsKey(event.id.value)) return
        events.value = events.value + (event.id.value to event)
        writeCount++
    }

    override suspend fun delete(id: ScheduleId) {
        events.value = events.value - id.value
        writeCount++
    }

    override suspend fun listTags(): List<ScheduleTag> =
        tags.value.values.sortedWith(compareBy({ it.sortOrder }, { it.id.value }))

    override suspend fun getTag(id: ScheduleTagId): ScheduleTag? = tags.value[id.value]

    override suspend fun findSystemTag(systemKey: String): ScheduleTag? =
        tags.value.values.firstOrNull { it.systemKey == systemKey }

    override suspend fun nextTagSortOrder(): Int =
        tags.value.values.maxOfOrNull { it.sortOrder + 1 } ?: 0

    override suspend fun insertTag(tag: ScheduleTag, now: Instant): ScheduleTagId {
        val id = if (tag.id.value == 0L) ScheduleTagId(nextTagId++) else tag.id
        if (id.value >= nextTagId) nextTagId = id.value + 1
        tags.value = tags.value + (id.value to tag.copy(id = id))
        writeCount++
        return id
    }

    override suspend fun updateTagName(id: ScheduleTagId, name: String, updatedAt: Instant) {
        val existing = tags.value[id.value] ?: return
        tags.value = tags.value + (id.value to existing.copy(name = name))
        writeCount++
    }

    override suspend fun deleteTagAndDetachEvents(id: ScheduleTagId, updatedAt: Instant): Boolean {
        if (!tags.value.containsKey(id.value)) return false
        events.value = events.value.mapValues { (_, event) ->
            if (event.tagId == id) event.copy(tagId = null, updatedAt = updatedAt) else event
        }
        tags.value = tags.value - id.value
        writeCount++
        return true
    }
}
