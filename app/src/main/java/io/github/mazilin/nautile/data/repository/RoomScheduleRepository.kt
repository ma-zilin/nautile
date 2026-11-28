package io.github.mazilin.nautile.data.repository

import androidx.room.withTransaction
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.db.entity.ScheduleTagEntity
import io.github.mazilin.nautile.data.mapper.ScheduleMapper
import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSourceFilter
import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.model.TimeRange
import io.github.mazilin.nautile.domain.repository.ScheduleRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room 日程仓库（R6-G2-T1、R7 §2.3 / §3.1）；只暴露领域模型，转换集中在 [ScheduleMapper]。
 *
 * - 历史来源兼容：`COURSE_ONLY` 查询 `('COURSE', 'IMPORTED')`，`REGULAR_ONLY` 查询 `('REGULAR', 'MANUAL')`；
 * - 标签删除在 `withTransaction` 内先解绑 `schedule_events.tag_id = NULL` 再删 `schedule_tags`，
 *   适配 `ON DELETE RESTRICT` 外键。
 */
class RoomScheduleRepository(private val database: NautileDatabase) : ScheduleRepository {

    private val scheduleDao = database.scheduleDao()

    override fun observeTags(): Flow<List<ScheduleTag>> =
        scheduleDao.observeTags().map { entities -> entities.map(ScheduleMapper::toDomain) }

    override fun observeTagEventCounts(): Flow<Map<ScheduleTagId, Int>> =
        scheduleDao.observeTagEventCounts().map { rows ->
            rows.associate { row -> ScheduleTagId(row.tagId) to row.count }
        }

    override fun observeInRange(range: TimeRange): Flow<List<ScheduleEvent>> =
        scheduleDao.observeInRange(
            rangeStart = range.startInclusive.toEpochMilli(),
            rangeEnd = range.endExclusive.toEpochMilli(),
        ).map { list -> list.map(ScheduleMapper::toDomain) }

    override fun observeInRange(
        range: TimeRange,
        source: ScheduleSourceFilter,
    ): Flow<List<ScheduleEvent>> {
        val start = range.startInclusive.toEpochMilli()
        val end = range.endExclusive.toEpochMilli()
        val entities = when (source) {
            ScheduleSourceFilter.MERGED -> scheduleDao.observeInRange(start, end)
            ScheduleSourceFilter.COURSE_ONLY ->
                scheduleDao.observeInRangeBySources(start, end, IMPORTED_SOURCE_VALUES)
            ScheduleSourceFilter.REGULAR_ONLY ->
                scheduleDao.observeInRangeBySources(start, end, MANUAL_SOURCE_VALUES)
        }
        return entities.map { list -> list.map(ScheduleMapper::toDomain) }
    }

    override suspend fun listInRange(range: TimeRange): List<ScheduleEvent> =
        scheduleDao.listInRange(
            rangeStart = range.startInclusive.toEpochMilli(),
            rangeEnd = range.endExclusive.toEpochMilli(),
        ).map(ScheduleMapper::toDomain)

    override fun observeEvent(id: ScheduleId): Flow<ScheduleEvent?> =
        scheduleDao.observeEvent(id.value).map { entity -> entity?.let(ScheduleMapper::toDomain) }

    override suspend fun get(id: ScheduleId): ScheduleEvent? =
        scheduleDao.get(id.value)?.let(ScheduleMapper::toDomain)

    override suspend fun insert(event: ScheduleEvent): ScheduleId =
        ScheduleId(scheduleDao.insert(ScheduleMapper.toEntity(event)))

    override suspend fun update(event: ScheduleEvent) {
        scheduleDao.update(ScheduleMapper.toEntity(event))
    }

    override suspend fun delete(id: ScheduleId) {
        scheduleDao.delete(id.value)
    }

    override suspend fun listTags(): List<ScheduleTag> =
        scheduleDao.listTags().map(ScheduleMapper::toDomain)

    override suspend fun getTag(id: ScheduleTagId): ScheduleTag? =
        scheduleDao.getTag(id.value)?.let(ScheduleMapper::toDomain)

    override suspend fun findSystemTag(systemKey: String): ScheduleTag? =
        scheduleDao.findSystemTag(systemKey)?.let(ScheduleMapper::toDomain)

    override suspend fun nextTagSortOrder(): Int = scheduleDao.nextTagSortOrder()

    override suspend fun insertTag(tag: ScheduleTag, now: Instant): ScheduleTagId {
        val millis = now.toEpochMilli()
        val id = scheduleDao.insertTag(
            ScheduleTagEntity(
                id = tag.id.value,
                name = tag.name,
                systemKey = tag.systemKey,
                sortOrder = tag.sortOrder,
                createdAt = millis,
                updatedAt = millis,
            ),
        )
        return ScheduleTagId(id)
    }

    override suspend fun updateTagName(id: ScheduleTagId, name: String, updatedAt: Instant) {
        scheduleDao.updateTagName(id.value, name, updatedAt.toEpochMilli())
    }

    override suspend fun deleteTagAndDetachEvents(
        id: ScheduleTagId,
        updatedAt: Instant,
    ): Boolean = database.withTransaction {
        val existing = scheduleDao.getTag(id.value) ?: return@withTransaction false
        // 先将关联日程解绑为无标签，再删除标签行，避免触发 ON DELETE RESTRICT 约束异常。
        scheduleDao.detachEventsFromTag(existing.id, updatedAt.toEpochMilli())
        scheduleDao.deleteTag(existing.id) > 0
    }

    private companion object {
        val IMPORTED_SOURCE_VALUES = listOf("COURSE", "IMPORTED")
        val MANUAL_SOURCE_VALUES = listOf("REGULAR", "MANUAL")
    }
}
