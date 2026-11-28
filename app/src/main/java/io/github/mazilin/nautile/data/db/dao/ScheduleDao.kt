package io.github.mazilin.nautile.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.mazilin.nautile.data.db.entity.ScheduleEventEntity
import io.github.mazilin.nautile.data.db.entity.ScheduleTagEntity
import kotlinx.coroutines.flow.Flow

/**
 * 日程 DAO（架构 §4.1）。
 *
 * 范围查询语义为 `start_at < :rangeEnd AND end_at > :rangeStart`，按来源可选过滤；
 * 不接收标签条件（D-new1：标签筛选本轮不做）。
 */
@Dao
interface ScheduleDao {

    @Query(
        "SELECT * FROM schedule_events " +
            "WHERE start_at < :rangeEnd AND end_at > :rangeStart " +
            "ORDER BY start_at ASC, id ASC",
    )
    fun observeInRange(rangeStart: Long, rangeEnd: Long): Flow<List<ScheduleEventEntity>>

    @Query(
        "SELECT * FROM schedule_events " +
            "WHERE source = :source AND start_at < :rangeEnd AND end_at > :rangeStart " +
            "ORDER BY start_at ASC, id ASC",
    )
    fun observeInRangeBySource(
        rangeStart: Long,
        rangeEnd: Long,
        source: String,
    ): Flow<List<ScheduleEventEntity>>

    @Query("SELECT * FROM schedule_events WHERE id = :id")
    fun observeEvent(id: Long): Flow<ScheduleEventEntity?>

    @Query("SELECT * FROM schedule_events WHERE id = :id")
    suspend fun get(id: Long): ScheduleEventEntity?

    @Insert
    suspend fun insert(event: ScheduleEventEntity): Long

    @Update
    suspend fun update(event: ScheduleEventEntity)

    @Query("DELETE FROM schedule_events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM schedule_tags ORDER BY sort_order ASC, id ASC")
    fun observeTags(): Flow<List<ScheduleTagEntity>>

    @Query("SELECT * FROM schedule_tags WHERE id = :id")
    suspend fun getTag(id: Long): ScheduleTagEntity?

    @Query("SELECT * FROM schedule_tags WHERE system_key = :systemKey LIMIT 1")
    suspend fun findSystemTag(systemKey: String): ScheduleTagEntity?
}
