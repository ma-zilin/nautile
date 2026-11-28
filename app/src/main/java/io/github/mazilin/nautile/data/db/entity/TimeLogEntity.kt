package io.github.mazilin.nautile.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 时间日志（架构 §1.3；本闸门只建表，G4 才写入）。
 *
 * [endAt] 为 null 表示运行中；跨天不拆行，统计时按本地 00:00 派生段。
 * 复合索引名按架构固定为 `index_time_logs_category_end`。
 */
@Entity(
    tableName = "time_logs",
    foreignKeys = [
        ForeignKey(
            entity = TimeCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("start_at"),
        Index("end_at"),
        Index(
            name = "index_time_logs_category_end",
            value = ["category_id", "end_at"],
        ),
    ],
)
data class TimeLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "start_at")
    val startAt: Long,
    @ColumnInfo(name = "end_at")
    val endAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "color_index")
    val colorIndex: Int? = null,
)
