package io.github.mazilin.nautile.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 日程标签（架构 §1.3）。
 *
 * [systemKey] 为稳定系统键（目前只有 `COURSE`），唯一索引防重复；普通用户标签为 null。
 */
@Entity(
    tableName = "schedule_tags",
    indices = [Index(value = ["system_key"], unique = true)],
)
data class ScheduleTagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "system_key")
    val systemKey: String? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
