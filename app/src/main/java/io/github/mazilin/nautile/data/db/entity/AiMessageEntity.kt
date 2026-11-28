package io.github.mazilin.nautile.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 消息表（架构 §3.1）。
 *
 * 角色/状态以枚举名存 TEXT；`content` 不为 null（占位行为空串）；
 * `error_code` 只存稳定短码，可空。
 * 外键 RESTRICT：删除会话由仓库用例显式先删消息，不让数据库静默级联。
 */
@Entity(
    tableName = "ai_messages",
    foreignKeys = [
        ForeignKey(
            entity = AiSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("session_id")],
)
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    val role: String,
    val content: String,
    val status: String,
    @ColumnInfo(name = "error_code")
    val errorCode: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
