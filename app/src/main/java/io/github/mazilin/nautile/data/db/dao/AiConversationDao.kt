package io.github.mazilin.nautile.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.mazilin.nautile.data.db.entity.AiMessageEntity
import io.github.mazilin.nautile.data.db.entity.AiSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * AI 会话 DAO（架构 §3.2）。
 *
 * 事务边界由 [io.github.mazilin.nautile.data.repository.RoomAiConversationRepository] 控制。
 */
@Dao
interface AiConversationDao {

    @Query("SELECT * FROM ai_messages ORDER BY id ASC")
    fun observeAll(): Flow<List<AiMessageEntity>>

    @Query("SELECT * FROM ai_sessions ORDER BY id ASC LIMIT 1")
    suspend fun firstSession(): AiSessionEntity?

    @Insert
    suspend fun insertSession(session: AiSessionEntity): Long

    @Insert
    suspend fun insertMessage(message: AiMessageEntity): Long

    @Query("UPDATE ai_sessions SET updated_at = :at WHERE id = :id")
    suspend fun touchSession(id: Long, at: Long)

    /** 终态写入时同步更新该消息所属会话的 updated_at；消息已删除时子查询无行，属安全无操作。 */
    @Query(
        "UPDATE ai_sessions SET updated_at = :at " +
            "WHERE id = (SELECT session_id FROM ai_messages WHERE id = :messageId)",
    )
    suspend fun touchSessionOfMessage(messageId: Long, at: Long)

    @Query(
        "UPDATE ai_messages SET content = :content, status = :status, " +
            "error_code = :errorCode WHERE id = :id",
    )
    suspend fun finishMessage(
        id: Long,
        content: String,
        status: String,
        errorCode: String?,
    )

    @Query("UPDATE ai_messages SET status = 'STOPPED' WHERE status = 'STREAMING'")
    suspend fun markStreamingAsStopped()

    @Query("DELETE FROM ai_messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM ai_sessions")
    suspend fun deleteAllSessions()
}
