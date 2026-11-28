package io.github.mazilin.nautile.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 单次日程；未来也承载系列实例（架构 §1.3、R7 §1.1）。
 *
 * 来源存 TEXT 枚举 `MANUAL` / `AI` / `IMPORTED`（读兼容历史 `REGULAR` / `COURSE`）；
 * `tag_id` 为 NULL 表示无标签；`color_index` 为可空色槽（旧行 NULL）；
 * 系列字段本轮恒 NULL。外键全部 RESTRICT，删除由用例/仓库事务显式处理。
 */
@Entity(
    tableName = "schedule_events",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ScheduleSeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["series_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("start_at"),
        Index("end_at"),
        Index("tag_id"),
        Index("series_id"),
        Index("source"),
    ],
)
data class ScheduleEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val note: String? = null,
    @ColumnInfo(name = "start_at")
    val startAt: Long,
    @ColumnInfo(name = "end_at")
    val endAt: Long,
    val source: String,
    @ColumnInfo(name = "tag_id")
    val tagId: Long? = null,
    @ColumnInfo(name = "series_id")
    val seriesId: Long? = null,
    @ColumnInfo(name = "original_start_at")
    val originalStartAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "color_index")
    val colorIndex: Int? = null,
)
