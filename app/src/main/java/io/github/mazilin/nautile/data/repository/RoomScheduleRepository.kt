package io.github.mazilin.nautile.data.repository

import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.mapper.ScheduleMapper
import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleSourceFilter
import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.model.TimeRange
import io.github.mazilin.nautile.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room 日程仓库（R6-G2-T1）；只暴露领域模型，转换集中在 ScheduleMapper。
 *
 * 来源筛选映射到 DAO 的两种查询；“合并”与“仅普通/课程表”都不改变底层数据。
 * 标签筛选本轮不做（D-new1），查询不接收标签条件。
 */
class RoomScheduleRepository(private val database: NautileDatabase) : ScheduleRepository {

    private val scheduleDao = database.scheduleDao()

    override fun observeTags(): Flow<List<ScheduleTag>> =
        scheduleDao.observeTags().map { entities -> entities.map(ScheduleMapper::toDomain) }

    override fun observeInRange(
        range: TimeRange,
        source: ScheduleSourceFilter,
    ): Flow<List<ScheduleEvent>> {
        val start = range.startInclusive.toEpochMilli()
        val end = range.endExclusive.toEpochMilli()
        val entities = when (source) {
            ScheduleSourceFilter.MERGED -> scheduleDao.observeInRange(start, end)
            ScheduleSourceFilter.COURSE_ONLY ->
                scheduleDao.observeInRangeBySource(start, end, ScheduleSource.COURSE.name)
            ScheduleSourceFilter.REGULAR_ONLY ->
                scheduleDao.observeInRangeBySource(start, end, ScheduleSource.REGULAR.name)
        }
        return entities.map { list -> list.map(ScheduleMapper::toDomain) }
    }

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

    override suspend fun getTag(id: ScheduleTagId): ScheduleTag? =
        scheduleDao.getTag(id.value)?.let(ScheduleMapper::toDomain)

    override suspend fun findSystemTag(systemKey: String): ScheduleTag? =
        scheduleDao.findSystemTag(systemKey)?.let(ScheduleMapper::toDomain)
}
