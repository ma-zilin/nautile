package io.github.mazilin.nautile.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.mazilin.nautile.data.db.dao.ProjectDao
import io.github.mazilin.nautile.data.db.dao.ScheduleDao
import io.github.mazilin.nautile.data.db.dao.TaskDao
import io.github.mazilin.nautile.data.db.dao.TimeCategoryDao
import io.github.mazilin.nautile.data.db.dao.TimeLogDao
import io.github.mazilin.nautile.data.db.entity.ProjectEntity
import io.github.mazilin.nautile.data.db.entity.ScheduleEventEntity
import io.github.mazilin.nautile.data.db.entity.ScheduleExceptionEntity
import io.github.mazilin.nautile.data.db.entity.ScheduleSeriesEntity
import io.github.mazilin.nautile.data.db.entity.ScheduleTagEntity
import io.github.mazilin.nautile.data.db.entity.TaskEntity
import io.github.mazilin.nautile.data.db.entity.TimeCategoryEntity
import io.github.mazilin.nautile.data.db.entity.TimeLogEntity

/**
 * Nautile 数据库（v5，R7-G1）。
 *
 * v4 保留既有 tasks/projects 并建齐 6 张日程/日志表；
 * v5 为 `schedule_events` 与 `time_logs` 各增量增加可空色槽列 `color_index`。
 * v1→v2、v2→v3、v3→v4、v4→v5 均由增量迁移完成；schema 导出到 `app/schemas`，
 * 不关闭导出、不使用 destructive fallback。
 */
@Database(
    entities = [
        TaskEntity::class,
        ProjectEntity::class,
        ScheduleSeriesEntity::class,
        ScheduleEventEntity::class,
        ScheduleExceptionEntity::class,
        ScheduleTagEntity::class,
        TimeCategoryEntity::class,
        TimeLogEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class NautileDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun projectDao(): ProjectDao

    abstract fun scheduleDao(): ScheduleDao

    abstract fun timeLogDao(): TimeLogDao

    abstract fun timeCategoryDao(): TimeCategoryDao
}
