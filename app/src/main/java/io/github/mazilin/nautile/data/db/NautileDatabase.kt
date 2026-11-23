package io.github.mazilin.nautile.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.mazilin.nautile.data.db.dao.ProjectDao
import io.github.mazilin.nautile.data.db.dao.TaskDao
import io.github.mazilin.nautile.data.db.entity.ProjectEntity
import io.github.mazilin.nautile.data.db.entity.TaskEntity

/**
 * S1 数据库（version 2，08 S1-T04 步骤 4；T-080 增加任务详情）。
 *
 * 只含 Task/Project 两张表；v1→v2 由 [MIGRATION_1_2] 增量迁移。
 * schema 导出到 `app/schemas`，不关闭导出、不使用 destructive fallback。
 */
@Database(
    entities = [TaskEntity::class, ProjectEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class NautileDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun projectDao(): ProjectDao
}
