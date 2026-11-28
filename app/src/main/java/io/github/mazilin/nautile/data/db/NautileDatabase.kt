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
 * Nautile 数据库（v4，R6-G2）。
 *
 * v4 保留既有 tasks/projects，并一次建齐 6 张日程/日志表：
 * schedule_series、schedule_events、schedule_exceptions、schedule_tags、time_categories、time_logs。
 * v1→v2、v2→v3、v3→v4 均由增量迁移完成；schema 导出到 `app/schemas`，
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
    version = 4,
    exportSchema = true,
)
abstract class NautileDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun projectDao(): ProjectDao

    abstract fun scheduleDao(): ScheduleDao

    abstract fun timeLogDao(): TimeLogDao

    abstract fun timeCategoryDao(): TimeCategoryDao
}
