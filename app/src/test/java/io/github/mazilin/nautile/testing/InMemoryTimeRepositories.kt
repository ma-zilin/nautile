package io.github.mazilin.nautile.testing

import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.domain.model.TimeRange
import io.github.mazilin.nautile.domain.repository.DeleteCategoryOutcome
import io.github.mazilin.nautile.domain.repository.StartTimerOutcome
import io.github.mazilin.nautile.domain.repository.StopTimerOutcome
import io.github.mazilin.nautile.domain.repository.TimeCategoryRepository
import io.github.mazilin.nautile.domain.repository.TimeLogRepository
import io.github.mazilin.nautile.domain.rule.TimeLogRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 日志仓库内存替身（R6-G4-V1 契约测试）。
 *
 * 查询/排序/运行中判定与 Room 实现保持一致：
 * - 全部记录 `start_at DESC, id DESC`；
 * - 运行中 `end_at IS NULL`、`start_at ASC`；
 * - 开始/结束计时在锁内完成“查同分类运行中 + 写入”，保证每分类最多一段；
 * - 结束时间 `max(now, start)`，与 [TimeLogRules.stopEndAt] 一致。
 */
class InMemoryTimeLogRepository(
    initial: List<TimeLog> = emptyList(),
) : TimeLogRepository {

    private val logs = MutableStateFlow(initial.associateBy { it.id.value })
    private var nextId = (initial.maxOfOrNull { it.id.value } ?: 0L) + 1
    private val timerMutex = Mutex()

    /** 写入次数（insert/update/delete），用于断言“不产生重复记录”。 */
    var writeCount: Int = 0
        private set

    val snapshot: List<TimeLog>
        get() = logs.value.values.sortedBy { it.id.value }

    private fun Collection<TimeLog>.byStartDesc(): List<TimeLog> =
        sortedWith(compareByDescending<TimeLog> { it.startAt }.thenByDescending { it.id.value })

    override fun observeAllDescending(): Flow<List<TimeLog>> =
        logs.map { stored -> stored.values.byStartDesc() }

    override fun observeForDay(day: LocalDate, zoneId: ZoneId): Flow<List<TimeLog>> {
        val dayStart = day.atStartOfDay(zoneId).toInstant()
        val dayEnd = day.plusDays(1).atStartOfDay(zoneId).toInstant()
        return logs.map { stored ->
            stored.values
                .filter { it.startAt.isBefore(dayEnd) }
                .filter { it.endAt == null || it.endAt.isAfter(dayStart) }
                .byStartDesc()
        }
    }

    override suspend fun listForDay(day: LocalDate, zoneId: ZoneId): List<TimeLog> {
        val dayStart = day.atStartOfDay(zoneId).toInstant()
        val dayEnd = day.plusDays(1).atStartOfDay(zoneId).toInstant()
        return logs.value.values
            .filter { it.startAt.isBefore(dayEnd) }
            .filter { it.endAt == null || it.endAt.isAfter(dayStart) }
            .byStartDesc()
    }

    override fun observeInRange(range: TimeRange): Flow<List<TimeLog>> =
        logs.map { stored ->
            stored.values
                .filter { it.startAt.isBefore(range.endExclusive) }
                .filter { it.endAt == null || it.endAt.isAfter(range.startInclusive) }
        }

    override fun observeRunning(): Flow<List<TimeLog>> =
        logs.map { stored ->
            stored.values
                .filter { it.endAt == null }
                .sortedWith(compareBy<TimeLog> { it.startAt }.thenBy { it.id.value })
        }

    override fun observeRecentCategoryIds(limit: Int): Flow<List<TimeCategoryId>> =
        logs.map { stored ->
            stored.values
                .groupBy { it.categoryId }
                .map { (categoryId, list) -> categoryId to list.maxOf { it.startAt } }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
        }

    override suspend fun get(id: TimeLogId): TimeLog? = logs.value[id.value]

    override suspend fun insert(log: TimeLog): TimeLogId {
        val id = if (log.id.value == 0L) TimeLogId(nextId++) else log.id
        if (id.value >= nextId) nextId = id.value + 1
        logs.value = logs.value + (id.value to log.copy(id = id))
        writeCount++
        return id
    }

    override suspend fun update(log: TimeLog) {
        if (!logs.value.containsKey(log.id.value)) return
        logs.value = logs.value + (log.id.value to log)
        writeCount++
    }

    override suspend fun delete(id: TimeLogId) {
        if (!logs.value.containsKey(id.value)) return
        logs.value = logs.value - id.value
        writeCount++
    }

    override suspend fun startTimer(
        categoryId: TimeCategoryId,
        draft: TimeLog,
    ): StartTimerOutcome = timerMutex.withLock {
        val running = logs.value.values.firstOrNull {
            it.categoryId == categoryId && it.endAt == null
        }
        if (running != null) {
            return@withLock StartTimerOutcome.AlreadyRunning(running.id)
        }
        val id = if (draft.id.value == 0L) TimeLogId(nextId++) else draft.id
        if (id.value >= nextId) nextId = id.value + 1
        val stored = draft.copy(id = id)
        logs.value = logs.value + (id.value to stored)
        writeCount++
        StartTimerOutcome.Started(stored)
    }

    override suspend fun stopTimer(
        categoryId: TimeCategoryId,
        endAt: Instant,
    ): StopTimerOutcome = timerMutex.withLock {
        val running = logs.value.values.firstOrNull {
            it.categoryId == categoryId && it.endAt == null
        } ?: return@withLock StopTimerOutcome.NotRunning
        val effectiveEnd = TimeLogRules.stopEndAt(running.startAt, endAt)
        val stopped = running.copy(endAt = effectiveEnd, updatedAt = effectiveEnd)
        logs.value = logs.value + (stopped.id.value to stopped)
        writeCount++
        StopTimerOutcome.Stopped(stopped)
    }
}

/**
 * 分类仓库内存替身（R6-G4-V1 契约测试）。
 *
 * 排序 `parent_id IS NOT NULL, sort_order, id` 与 Room DAO 一致；删除守卫检查子级与日志，
 * 与 Room 实现同语义（本替身不提供批量清空/迁移）。
 */
class InMemoryTimeCategoryRepository(
    private val logStore: InMemoryTimeLogRepository = InMemoryTimeLogRepository(),
    initial: List<TimeCategory> = emptyList(),
) : TimeCategoryRepository {

    private val categories = MutableStateFlow(initial.associateBy { it.id.value })
    private var nextId = (initial.maxOfOrNull { it.id.value } ?: 0L) + 1
    private val mutex = Mutex()

    val snapshot: List<TimeCategory>
        get() = categories.value.values.sortedBy { it.id.value }

    override fun observeAll(): Flow<List<TimeCategory>> =
        categories.map { stored ->
            stored.values.sortedWith(
                compareBy<TimeCategory> { it.parentId != null }
                    .thenBy { it.sortOrder }
                    .thenBy { it.id.value },
            )
        }

    override suspend fun get(id: TimeCategoryId): TimeCategory? = categories.value[id.value]

    override suspend fun insert(category: TimeCategory): TimeCategoryId = mutex.withLock {
        val id = if (category.id.value == 0L) TimeCategoryId(nextId++) else category.id
        if (id.value >= nextId) nextId = id.value + 1
        categories.value = categories.value + (id.value to category.copy(id = id))
        id
    }

    override suspend fun update(category: TimeCategory) {
        if (!categories.value.containsKey(category.id.value)) return
        categories.value = categories.value + (category.id.value to category)
    }

    override suspend fun deleteIfUnused(id: TimeCategoryId): DeleteCategoryOutcome = mutex.withLock {
        categories.value[id.value] ?: return@withLock DeleteCategoryOutcome.NotFound
        val childCount = countChildren(id)
        val logCount = countLogs(id)
        if (childCount > 0 || logCount > 0) {
            return@withLock DeleteCategoryOutcome.Blocked(childCount, logCount)
        }
        categories.value = categories.value - id.value
        DeleteCategoryOutcome.Deleted
    }

    override suspend fun countChildren(id: TimeCategoryId): Int =
        categories.value.values.count { it.parentId == id }

    override suspend fun countLogs(id: TimeCategoryId): Int =
        logStore.snapshot.count { it.categoryId == id }

    override suspend fun nextSortOrder(parentId: TimeCategoryId?): Int =
        categories.value.values
            .filter { it.parentId == parentId }
            .maxOfOrNull { it.sortOrder + 1 }
            ?: 0
}
