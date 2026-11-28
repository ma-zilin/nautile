package io.github.mazilin.nautile.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AI 会话表（架构 §3.1）；Phase A 只有一行，仍保留会话实体。
 *
 * 时间戳为 UTC epoch 毫秒；`updated_at` 在 beginTurn 与助手终态写入时更新。
 */
@Entity(tableName = "ai_sessions")
data class AiSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
