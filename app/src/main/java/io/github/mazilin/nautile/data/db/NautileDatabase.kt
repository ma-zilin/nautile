package io.github.mazilin.nautile.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.mazilin.nautile.data.db.dao.AiConversationDao
import io.github.mazilin.nautile.data.db.dao.ProjectDao
import io.github.mazilin.nautile.data.db.dao.TaskDao
import io.github.mazilin.nautile.data.db.entity.AiMessageEntity
import io.github.mazilin.nautile.data.db.entity.AiSessionEntity
import io.github.mazilin.nautile.data.db.entity.ProjectEntity
import io.github.mazilin.nautile.data.db.entity.TaskEntity

/**
 * 数据库（version 4，AI Phase A G2：新增 AI 会话/消息两张表）。
 *
 * v1→v2 由 [MIGRATION_1_2]、v2→v3 由 [MIGRATION_2_3]、v3→v4 由 [MIGRATION_3_4] 增量迁移。
 * schema 导出到 `app/schemas`，不关闭导出、不使用 destructive fallback。
 */
@Database(
    entities = [
        TaskEntity::class,
        ProjectEntity::class,
        AiSessionEntity::class,
        AiMessageEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class NautileDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun projectDao(): ProjectDao

    abstract fun aiConversationDao(): AiConversationDao
}
