package io.github.mazilin.nautile.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.mazilin.nautile.data.db.entity.ScheduleEventEntity
import io.github.mazilin.nautile.data.db.entity.ScheduleTagEntity
import kotlinx.coroutines.flow.Flow

/** 每个标签关联的日程计数聚合行（纯 DB 投影，不依赖 domain.model）。 */
data class ScheduleTagCountRow(
    @ColumnInfo(name = "tag_id")
    val tagId: Long,
    val count: Int,
)

/**
 * 日程 DAO（架构 §4.1、R7 §2.3 / §3.2）。
 *
 * 范围查询语义为 `start_at < :rangeEnd AND end_at > :rangeStart`；
 * 标签删除须配合 [detachEventsFromTag] 在同一 Room 事务内执行，以适配 `ON DELETE RESTRICT`。
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
            "WHERE source IN (:sources) AND start_at < :rangeEnd AND end_at > :rangeStart " +
            "ORDER BY start_at ASC, id ASC",
    )
    fun observeInRangeBySources(
        rangeStart: Long,
        rangeEnd: Long,
        sources: List<String>,
    ): Flow<List<ScheduleEventEntity>>

    @Query(
        "SELECT * FROM schedule_events " +
            "WHERE start_at < :rangeEnd AND end_at > :rangeStart " +
            "ORDER BY start_at ASC, id ASC",
    )
    suspend fun listInRange(rangeStart: Long, rangeEnd: Long): List<ScheduleEventEntity>

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

    @Query("SELECT * FROM schedule_tags ORDER BY sort_order ASC, id ASC")
    suspend fun listTags(): List<ScheduleTagEntity>

    @Query(
        "SELECT tag_id, COUNT(*) AS count FROM schedule_events " +
            "WHERE tag_id IS NOT NULL GROUP BY tag_id",
    )
    fun observeTagEventCounts(): Flow<List<ScheduleTagCountRow>>

    @Query("SELECT * FROM schedule_tags WHERE id = :id")
    suspend fun getTag(id: Long): ScheduleTagEntity?

    @Query("SELECT * FROM schedule_tags WHERE system_key = :systemKey LIMIT 1")
    suspend fun findSystemTag(systemKey: String): ScheduleTagEntity?

    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM schedule_tags")
    suspend fun nextTagSortOrder(): Int

    @Insert
    suspend fun insertTag(tag: ScheduleTagEntity): Long

    @Query("UPDATE schedule_tags SET name = :name, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateTagName(id: Long, name: String, updatedAt: Long): Int

    @Query("UPDATE schedule_events SET tag_id = NULL, updated_at = :updatedAt WHERE tag_id = :tagId")
    suspend fun detachEventsFromTag(tagId: Long, updatedAt: Long)

    @Query("DELETE FROM schedule_tags WHERE id = :id")
    suspend fun deleteTag(id: Long): Int
}
