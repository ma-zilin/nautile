package io.github.mazilin.nautile.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.mazilin.nautile.data.db.entity.TimeLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * 时间日志 DAO（架构 §4.1；本闸门只建结构与查询，G4 才写入业务行）。
 *
 * 运行中记录 `end_at IS NULL`；范围查询把运行中视为与区间相交。
 */
@Dao
interface TimeLogDao {

    @Query("SELECT * FROM time_logs ORDER BY start_at DESC, id DESC")
    fun observeAllDescending(): Flow<List<TimeLogEntity>>

    @Query(
        "SELECT * FROM time_logs " +
            "WHERE start_at < :dayEnd AND (end_at IS NULL OR end_at > :dayStart) " +
            "ORDER BY start_at DESC, id DESC",
    )
    fun observeForDay(dayStart: Long, dayEnd: Long): Flow<List<TimeLogEntity>>

    @Query(
        "SELECT * FROM time_logs " +
            "WHERE start_at < :dayEnd AND (end_at IS NULL OR end_at > :dayStart) " +
            "ORDER BY start_at ASC, id ASC",
    )
    suspend fun listForDay(dayStart: Long, dayEnd: Long): List<TimeLogEntity>

    @Query(
        "SELECT * FROM time_logs " +
            "WHERE start_at < :rangeEnd AND (end_at IS NULL OR end_at > :rangeStart)",
    )
    fun observeInRange(rangeStart: Long, rangeEnd: Long): Flow<List<TimeLogEntity>>

    @Query("SELECT * FROM time_logs WHERE end_at IS NULL ORDER BY start_at ASC")
    fun observeRunning(): Flow<List<TimeLogEntity>>

    @Query("SELECT * FROM time_logs WHERE id = :id")
    suspend fun get(id: Long): TimeLogEntity?

    @Query("SELECT * FROM time_logs WHERE category_id = :categoryId AND end_at IS NULL LIMIT 1")
    suspend fun getRunningForCategory(categoryId: Long): TimeLogEntity?

    /**
     * 最近使用的分类 ID：按最近一次开始时间倒序。
     *
     * 用 `MAX(start_at)` 聚合以兼容部分 SQLite 版本对 GROUP BY 取值的宽松行为。
     */
    @Query(
        "SELECT category_id FROM time_logs " +
            "GROUP BY category_id ORDER BY MAX(start_at) DESC LIMIT :limit",
    )
    fun observeRecentCategoryIds(limit: Int): Flow<List<Long>>

    @Insert
    suspend fun insert(log: TimeLogEntity): Long

    @Update
    suspend fun update(log: TimeLogEntity)

    @Query("DELETE FROM time_logs WHERE id = :id")
    suspend fun delete(id: Long)
}
