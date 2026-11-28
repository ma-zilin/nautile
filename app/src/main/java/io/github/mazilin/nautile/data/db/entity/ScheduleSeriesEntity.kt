package io.github.mazilin.nautile.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 日程重复系列（架构 §1.3；v4 建表、本轮零写入）。
 *
 * [recurrenceRule] 预留给未来重复规则；日期存 ISO 文本，时间戳存 UTC epoch 毫秒。
 */
@Entity(tableName = "schedule_series")
data class ScheduleSeriesEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "recurrence_rule")
    val recurrenceRule: String,
    @ColumnInfo(name = "start_date")
    val startDate: String,
    @ColumnInfo(name = "until_date")
    val untilDate: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
