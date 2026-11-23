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
