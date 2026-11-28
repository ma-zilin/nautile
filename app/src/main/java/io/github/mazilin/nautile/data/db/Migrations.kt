package io.github.mazilin.nautile.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * schema v1 → v2：任务增加可空详情列（FR-D-03、AC-D-09）。
 *
 * 只做增量 ALTER，不重写旧数据；旧任务 `detail` 为 NULL，不使用 destructive fallback。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN detail TEXT")
    }
}

/**
 * schema v2 → v3：任务增加手动排序列（CMP-40、CMP-40.1）。
 *
 * 只做增量 ALTER 且列可空、无默认值，旧行保持 NULL，语义为“未手动排序”；
 * 不重写旧数据，不使用 destructive fallback。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN manual_order INTEGER")
    }
}

/**
 * schema v3 → v4：新增 AI 会话/消息两张表（架构 §3.1、§4.1）。
 *
 * 只新增表与索引，不改 tasks/projects 的列、索引与外键；消息外键 RESTRICT。
 * 不使用 destructive fallback，不删除既有数据。
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ai_sessions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ai_messages` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`session_id` INTEGER NOT NULL, " +
                "`role` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`error_code` TEXT, " +
                "`created_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`session_id`) REFERENCES `ai_sessions`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE RESTRICT)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ai_messages_session_id` " +
                "ON `ai_messages` (`session_id`)",
        )
    }
}
