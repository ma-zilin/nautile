package io.github.mazilin.nautile.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 系列单次例外/取消（架构 §1.3；v4 建表、本轮零写入）。
 *
 * 唯一索引 (series_id, original_start_at) 保证同一 occurrence 至多一条例外。
 */
@Entity(
    tableName = "schedule_exceptions",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleSeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["series_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ScheduleEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["override_event_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(
            name = "index_schedule_exceptions_occurrence",
            value = ["series_id", "original_start_at"],
            unique = true,
        ),
    ],
)
data class ScheduleExceptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "series_id")
    val seriesId: Long,
    @ColumnInfo(name = "original_start_at")
    val originalStartAt: Long,
    /** `CANCELLED` / `MODIFIED`。 */
    val kind: String,
    @ColumnInfo(name = "override_event_id")
    val overrideEventId: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
