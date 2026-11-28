package io.github.mazilin.nautile.data.repository

import androidx.room.withTransaction
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.mapper.TimeLogMapper
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.domain.model.TimeRange
import io.github.mazilin.nautile.domain.repository.StartTimerOutcome
import io.github.mazilin.nautile.domain.repository.StopTimerOutcome
import io.github.mazilin.nautile.domain.repository.TimeLogRepository
import io.github.mazilin.nautile.domain.rule.TimeLogRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room 日志仓库（R6-G4-T2）；只暴露领域模型，转换集中在 TimeLogMapper。
 *
 * 开始/结束计时在 `withTransaction` 内完成“查同分类运行中 + 写入”，
 * 是“每分类最多一段运行中”的原子保障（AC-T-22）。
 */
class RoomTimeLogRepository(private val database: NautileDatabase) : TimeLogRepository {

    private val logDao = database.timeLogDao()

    override fun observeAllDescending(): Flow<List<TimeLog>> =
        logDao.observeAllDescending().map { entities -> entities.map(TimeLogMapper::toDomain) }

    override fun observeForDay(day: LocalDate, zoneId: ZoneId): Flow<List<TimeLog>> {
        val dayStart = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return logDao.observeForDay(dayStart, dayEnd)
            .map { entities -> entities.map(TimeLogMapper::toDomain) }
    }

    override suspend fun listForDay(day: LocalDate, zoneId: ZoneId): List<TimeLog> {
        val dayStart = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return logDao.listForDay(dayStart, dayEnd).map(TimeLogMapper::toDomain)
    }

    override fun observeInRange(range: TimeRange): Flow<List<TimeLog>> =
        logDao.observeInRange(
            range.startInclusive.toEpochMilli(),
            range.endExclusive.toEpochMilli(),
        ).map { entities -> entities.map(TimeLogMapper::toDomain) }

    override fun observeRunning(): Flow<List<TimeLog>> =
        logDao.observeRunning().map { entities -> entities.map(TimeLogMapper::toDomain) }

    override fun observeRecentCategoryIds(limit: Int): Flow<List<TimeCategoryId>> =
        logDao.observeRecentCategoryIds(limit)
            .map { ids -> ids.map { TimeCategoryId(it) } }

    override suspend fun get(id: TimeLogId): TimeLog? =
        logDao.get(id.value)?.let(TimeLogMapper::toDomain)

    override suspend fun insert(log: TimeLog): TimeLogId =
        TimeLogId(logDao.insert(TimeLogMapper.toEntity(log)))

    override suspend fun update(log: TimeLog) {
        logDao.update(TimeLogMapper.toEntity(log))
    }

    override suspend fun delete(id: TimeLogId) {
        logDao.delete(id.value)
    }

    override suspend fun startTimer(
        categoryId: TimeCategoryId,
        draft: TimeLog,
    ): StartTimerOutcome = database.withTransaction {
        val running = logDao.getRunningForCategory(categoryId.value)
        if (running != null) {
            return@withTransaction StartTimerOutcome.AlreadyRunning(TimeLogId(running.id))
        }
        val id = logDao.insert(TimeLogMapper.toEntity(draft))
        StartTimerOutcome.Started(draft.copy(id = TimeLogId(id)))
    }

    override suspend fun stopTimer(
        categoryId: TimeCategoryId,
        endAt: Instant,
    ): StopTimerOutcome = database.withTransaction {
        val running = logDao.getRunningForCategory(categoryId.value)
            ?: return@withTransaction StopTimerOutcome.NotRunning
        // 结束时间取 max(now, start)，设备时间回拨时不产生负数与补时（架构 §7.1）。
        val effectiveEnd = TimeLogRules.stopEndAt(Instant.ofEpochMilli(running.startAt), endAt)
        // 已用时钟由调用方给出；updatedAt 取结束时刻，不额外读系统时钟。
        val updated = running.copy(
            endAt = effectiveEnd.toEpochMilli(),
            updatedAt = effectiveEnd.toEpochMilli(),
        )
        logDao.update(updated)
        StopTimerOutcome.Stopped(TimeLogMapper.toDomain(updated))
    }
}
