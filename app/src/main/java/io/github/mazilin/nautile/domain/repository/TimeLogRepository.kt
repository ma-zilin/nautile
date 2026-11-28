package io.github.mazilin.nautile.domain.repository

import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.domain.model.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** 开始计时结果；同分类已有运行中时返回 [AlreadyRunning]，不重置、不结束（AC-T-21）。 */
sealed interface StartTimerOutcome {
    data class Started(val log: TimeLog) : StartTimerOutcome

    data class AlreadyRunning(val logId: TimeLogId) : StartTimerOutcome
}

/** 结束计时结果；无运行中记录时返回 [NotRunning]，不写入任何数据。 */
sealed interface StopTimerOutcome {
    data class Stopped(val log: TimeLog) : StopTimerOutcome

    data object NotRunning : StopTimerOutcome
}

/**
 * 时间日志仓库（架构 §3.2）。
 *
 * 运行中判定统一为 `endAt == null`；开始/结束必须在事务内完成“查同分类运行中 + 写入”，
 * 保证每分类最多一段运行中（AC-T-22）。
 */
interface TimeLogRepository {

    fun observeAllDescending(): Flow<List<TimeLog>>

    fun observeForDay(day: LocalDate, zoneId: ZoneId): Flow<List<TimeLog>>

    /** 一次性查询与指定本地日相交的全部日志（含运行中；供开始计时/补录计算同日相邻异色）。 */
    suspend fun listForDay(day: LocalDate, zoneId: ZoneId): List<TimeLog>

    fun observeInRange(range: TimeRange): Flow<List<TimeLog>>

    /** 运行中记录（`end_at IS NULL`），按开始时间升序；用于重启后墙钟恢复。 */
    fun observeRunning(): Flow<List<TimeLog>>

    /** 最近使用的分类 ID（按最近开始时间倒序），用于快捷入口排序。 */
    fun observeRecentCategoryIds(limit: Int): Flow<List<TimeCategoryId>>

    suspend fun get(id: TimeLogId): TimeLog?

    suspend fun insert(log: TimeLog): TimeLogId

    suspend fun update(log: TimeLog)

    suspend fun delete(id: TimeLogId)

    /** 事务内“查同分类运行中 + 插入”；已存在时返回 [StartTimerOutcome.AlreadyRunning]。 */
    suspend fun startTimer(categoryId: TimeCategoryId, draft: TimeLog): StartTimerOutcome

    /** 事务内结束指定分类的运行中记录；不存在返回 [StopTimerOutcome.NotRunning]。 */
    suspend fun stopTimer(categoryId: TimeCategoryId, endAt: Instant): StopTimerOutcome
}
