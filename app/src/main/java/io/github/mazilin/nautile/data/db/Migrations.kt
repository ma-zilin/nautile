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
