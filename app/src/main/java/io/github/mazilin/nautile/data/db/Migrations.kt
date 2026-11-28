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
 * schema v3 → v4：一次建齐 6 张日程/日志表与种子标签（架构 §2.1）。
 *
 * 只做增量建表、索引与种子，不 DROP、不改 tasks/projects、不重写旧数据；
 * 不使用 destructive fallback。任一语句失败由 Room 在升级事务中整体回滚并上抛。
 * DDL 的列顺序、类型、可空性与索引名必须与 Entity 生成的 `4.json` 一致。
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. 日程系列（本轮零写入）。
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS schedule_series (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "recurrence_rule TEXT NOT NULL, " +
                "start_date TEXT NOT NULL, " +
                "until_date TEXT, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL)",
        )

        // 2. 日程标签 + system_key 唯一索引。
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS schedule_tags (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "system_key TEXT, " +
                "sort_order INTEGER NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_schedule_tags_system_key " +
                "ON schedule_tags(system_key)",
        )

        // 3. 日程事件 + 5 个索引；外键 RESTRICT，不静默级联。
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS schedule_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "title TEXT NOT NULL, " +
                "note TEXT, " +
                "start_at INTEGER NOT NULL, " +
                "end_at INTEGER NOT NULL, " +
                "source TEXT NOT NULL, " +
                "tag_id INTEGER, " +
                "series_id INTEGER, " +
                "original_start_at INTEGER, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL, " +
                "FOREIGN KEY(tag_id) REFERENCES schedule_tags(id) " +
                "ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                "FOREIGN KEY(series_id) REFERENCES schedule_series(id) " +
                "ON UPDATE NO ACTION ON DELETE RESTRICT)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_events_start_at ON schedule_events(start_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_events_end_at ON schedule_events(end_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_events_tag_id ON schedule_events(tag_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_events_series_id ON schedule_events(series_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_events_source ON schedule_events(source)")

        // 4. 系列例外 + occurrence 唯一索引（本轮零写入）。
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS schedule_exceptions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "series_id INTEGER NOT NULL, " +
                "original_start_at INTEGER NOT NULL, " +
                "kind TEXT NOT NULL, " +
                "override_event_id INTEGER, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL, " +
                "FOREIGN KEY(series_id) REFERENCES schedule_series(id) " +
                "ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                "FOREIGN KEY(override_event_id) REFERENCES schedule_events(id) " +
                "ON UPDATE NO ACTION ON DELETE RESTRICT)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_schedule_exceptions_occurrence " +
                "ON schedule_exceptions(series_id, original_start_at)",
        )

        // 5. 两级时间分类 + parent 索引。
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS time_categories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "parent_id INTEGER, " +
                "sort_order INTEGER NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL, " +
                "FOREIGN KEY(parent_id) REFERENCES time_categories(id) " +
                "ON UPDATE NO ACTION ON DELETE RESTRICT)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_time_categories_parent_id " +
                "ON time_categories(parent_id)",
        )

        // 6. 时间日志 + 3 个索引（end_at 可空表示运行中，无默认值）。
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS time_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "category_id INTEGER NOT NULL, " +
                "start_at INTEGER NOT NULL, " +
                "end_at INTEGER, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL, " +
                "FOREIGN KEY(category_id) REFERENCES time_categories(id) " +
                "ON UPDATE NO ACTION ON DELETE RESTRICT)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_time_logs_start_at ON time_logs(start_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_time_logs_end_at ON time_logs(end_at)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_time_logs_category_end " +
                "ON time_logs(category_id, end_at)",
        )

        // 7. 种子：系统“课程表”标签恰一行，时间戳用 0 保证迁移确定性（架构 §2.2）。
        db.execSQL(
            "INSERT INTO schedule_tags " +
                "(id, name, system_key, sort_order, created_at, updated_at) " +
                "VALUES (1, '课程表', 'COURSE', 0, 0, 0)",
        )
    }
}

/**
 * schema v4 → v5：日程事件与时间日志各增加可空色槽列 `color_index`（R7 Q4-A、#22）。
 *
 * 只做增量 ALTER 且列可空、无默认值；旧行保持 NULL，展示时按确定性回退色槽渲染；
 * 不重建表、不修改外键、不使用 destructive fallback。
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_events ADD COLUMN color_index INTEGER")
        db.execSQL("ALTER TABLE time_logs ADD COLUMN color_index INTEGER")
    }
}
