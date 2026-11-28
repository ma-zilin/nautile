package io.github.mazilin.nautile.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.repository.RoomScheduleRepository
import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R7-G1 · 数据库 v4 → v5 / v1 → v5 真实迁移与 ON DELETE RESTRICT 事务解绑测试（模拟器非真机）。
 *
 * 覆盖：
 * 1. v4 → v5：既有全部表数据无损保留，迁移后旧行 `color_index IS NULL`，迁移后可写入带 `color_index` 与 `NULL` 新行；
 * 2. v1 → v5 全链迁移：旧任务/项目无损保留，系统 COURSE 种子完好，可写入带 `color_index` 的新日程与日志；
 * 3. 真实 SQLite `ON DELETE RESTRICT` 下验证：直接删有关联日程的标签必抛 `SQLiteConstraintException`，
 *    而调用 `RoomScheduleRepository.deleteTagAndDetachEvents` 不触发外键崩溃、关联日程 `tag_id` 变为 `NULL` 且日程本体保留。
 */
@RunWith(AndroidJUnit4::class)
class MigrationV5Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NautileDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun r7_migration_v4_to_v5_preservesAllRows_setsOldColorIndexNull_andAllowsWritingColorIndex() {
        val v4 = helper.createDatabase(TEST_DB_V4_TO_V5, 4)
        try {
            v4.execSQL(
                "INSERT INTO projects (id, name, status, created_at, updated_at) " +
                    "VALUES (1, '旧项目', 'ACTIVE', 1000, 2000)",
            )
            v4.execSQL(
                "INSERT INTO tasks (id, title, detail, project_id, position, priority, created_at, updated_at, manual_order) " +
                    "VALUES (1, '旧任务', '旧详情', 1, 'NEXT_ACTION', 'HIGH', 1000, 2000, 0)",
            )
            v4.execSQL(
                "INSERT INTO schedule_tags (id, name, system_key, sort_order, created_at, updated_at) " +
                    "VALUES (1, '课程表', 'COURSE', 0, 0, 0)",
            )
            v4.execSQL(
                "INSERT INTO schedule_tags (id, name, system_key, sort_order, created_at, updated_at) " +
                    "VALUES (2, '工作', NULL, 1, 1000, 1000)",
            )
            // 历史来源字面量 "REGULAR" 与 "COURSE"
            v4.execSQL(
                "INSERT INTO schedule_events " +
                    "(id, title, note, start_at, end_at, source, tag_id, series_id, original_start_at, created_at, updated_at) " +
                    "VALUES (10, '旧普通日程', '备注', 10000, 20000, 'REGULAR', 2, NULL, NULL, 1000, 2000)",
            )
            v4.execSQL(
                "INSERT INTO schedule_events " +
                    "(id, title, note, start_at, end_at, source, tag_id, series_id, original_start_at, created_at, updated_at) " +
                    "VALUES (11, '旧课程日程', NULL, 30000, 40000, 'COURSE', 1, NULL, NULL, 1000, 2000)",
            )
            v4.execSQL(
                "INSERT INTO time_categories (id, name, parent_id, sort_order, created_at, updated_at) " +
                    "VALUES (5, '学习', NULL, 0, 1000, 1000)",
            )
            v4.execSQL(
                "INSERT INTO time_logs (id, category_id, start_at, end_at, created_at, updated_at) " +
                    "VALUES (20, 5, 50000, 60000, 50000, 60000)",
            )
            v4.execSQL(
                "INSERT INTO time_logs (id, category_id, start_at, end_at, created_at, updated_at) " +
                    "VALUES (21, 5, 70000, NULL, 70000, 70000)",
            )
        } finally {
            v4.close()
        }

        val v5 = helper.runMigrationsAndValidate(
            TEST_DB_V4_TO_V5,
            5,
            true,
            MIGRATION_4_5,
        )
        try {
            assertEquals(5, v5.version)
            assertEquals(1, v5.countRows("projects"))
            assertEquals(1, v5.countRows("tasks"))
            assertEquals(2, v5.countRows("schedule_tags"))
            assertEquals(2, v5.countRows("schedule_events"))
            assertEquals(1, v5.countRows("time_categories"))
            assertEquals(2, v5.countRows("time_logs"))

            // 校验 PRAGMA table_info：color_index 仅在 schedule_events/time_logs 上且可空无默认。
            assertNullableNoDefaultColumn(v5, "schedule_events", "color_index")
            assertNullableNoDefaultColumn(v5, "time_logs", "color_index")
            assertFalse("schedule_tags 不应含 color_index", v5.hasColumn("schedule_tags", "color_index"))
            assertFalse("time_categories 不应含 color_index", v5.hasColumn("time_categories", "color_index"))

            // 旧行内容完整且 color_index IS NULL。
            v5.query("SELECT id, title, source, tag_id, color_index FROM schedule_events ORDER BY id").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(10L, cursor.getLong(0))
                assertEquals("旧普通日程", cursor.getString(1))
                assertEquals("REGULAR", cursor.getString(2))
                assertEquals(2L, cursor.getLong(3))
                assertTrue("迁移前旧日程 color_index 必须为 NULL", cursor.isNull(4))

                assertTrue(cursor.moveToNext())
                assertEquals(11L, cursor.getLong(0))
                assertEquals("旧课程日程", cursor.getString(1))
                assertEquals("COURSE", cursor.getString(2))
                assertEquals(1L, cursor.getLong(3))
                assertTrue("迁移前旧课程 color_index 必须为 NULL", cursor.isNull(4))
            }

            v5.query("SELECT id, end_at, color_index FROM time_logs ORDER BY id").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(20L, cursor.getLong(0))
                assertEquals(60000L, cursor.getLong(1))
                assertTrue("迁移前旧日志 color_index 必须为 NULL", cursor.isNull(2))

                assertTrue(cursor.moveToNext())
                assertEquals(21L, cursor.getLong(0))
                assertTrue("运行中日志 end_at 必须仍为 NULL", cursor.isNull(1))
                assertTrue("运行中旧日志 color_index 必须为 NULL", cursor.isNull(2))
            }

            // 迁移后可写入带 color_index（0..7）与 NULL 的新行。
            v5.execSQL(
                "INSERT INTO schedule_events " +
                    "(id, title, note, start_at, end_at, source, tag_id, series_id, original_start_at, created_at, updated_at, color_index) " +
                    "VALUES (12, '新彩色日程', NULL, 80000, 90000, 'MANUAL', NULL, NULL, NULL, 80000, 80000, 6)",
            )
            v5.execSQL(
                "INSERT INTO time_logs (id, category_id, start_at, end_at, created_at, updated_at, color_index) " +
                    "VALUES (22, 5, 80000, 85000, 80000, 85000, 3)",
            )
            v5.query("SELECT color_index FROM schedule_events WHERE id = 12").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(6, cursor.getInt(0))
            }
            v5.query("SELECT color_index FROM time_logs WHERE id = 22").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
            }
        } finally {
            v5.close()
        }
    }

    @Test
    fun r7_migration_v1_to_v5_fullChain_preservesLegacyDataAndSupportsColorIndex() {
        val v1 = helper.createDatabase(TEST_DB_V1_TO_V5, 1)
        try {
            v1.execSQL(
                "INSERT INTO projects (id, name, status, created_at, updated_at) " +
                    "VALUES (7, '初代项目', 'ACTIVE', 1000, 2000)",
            )
            v1.execSQL(
                "INSERT INTO tasks (id, title, project_id, position, priority, created_at, updated_at) " +
                    "VALUES (7, '初代任务', 7, 'INBOX', 'MEDIUM', 1000, 2000)",
            )
        } finally {
            v1.close()
        }

        val v5 = helper.runMigrationsAndValidate(
            TEST_DB_V1_TO_V5,
            5,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
        )
        try {
            assertEquals(5, v5.version)
            assertEquals(1, v5.countRows("projects"))
            assertEquals(1, v5.countRows("tasks"))
            assertEquals(1, v5.countRows("schedule_tags"))

            v5.query(
                "SELECT title, detail, manual_order FROM tasks WHERE id = 7",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("初代任务", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
            }

            // 链式迁移后可写入带 color_index 的日程与日志。
            v5.execSQL(
                "INSERT INTO schedule_events " +
                    "(title, note, start_at, end_at, source, tag_id, series_id, original_start_at, created_at, updated_at, color_index) " +
                    "VALUES ('v5 新日程', NULL, 1000, 2000, 'MANUAL', 1, NULL, NULL, 1000, 1000, 4)",
            )
            v5.execSQL(
                "INSERT INTO time_categories (id, name, parent_id, sort_order, created_at, updated_at) " +
                    "VALUES (1, '阅读', NULL, 0, 1000, 1000)",
            )
            v5.execSQL(
                "INSERT INTO time_logs (category_id, start_at, end_at, created_at, updated_at, color_index) " +
                    "VALUES (1, 1000, 2000, 1000, 2000, 7)",
            )
            assertEquals(1, v5.countRows("schedule_events"))
            assertEquals(1, v5.countRows("time_logs"))
        } finally {
            v5.close()
        }
    }

    @Test
    fun r7_realSqliteOnDeleteRestrict_deleteTagAndDetachEvents_avoidsFkCrashAndKeepsEvents() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, NautileDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = RoomScheduleRepository(database)
            val now = Instant.parse("2026-11-28T08:00:00Z")

            val workTagId = repository.insertTag(
                ScheduleTag(ScheduleTagId(0), "工作", null, 1),
                now,
            )
            val studyTagId = repository.insertTag(
                ScheduleTag(ScheduleTagId(0), "学习", null, 2),
                now,
            )

            val event1Id = repository.insert(
                ScheduleEvent(
                    id = ScheduleId(0),
                    title = "绑定工作标签 1",
                    note = null,
                    startAt = now,
                    endAt = now.plusSeconds(3600),
                    source = ScheduleSource.MANUAL,
                    tagId = workTagId,
                    seriesId = null,
                    originalStartAt = null,
                    createdAt = now,
                    updatedAt = now,
                    colorIndex = 1,
                ),
            )
            val event2Id = repository.insert(
                ScheduleEvent(
                    id = ScheduleId(0),
                    title = "绑定工作标签 2",
                    note = null,
                    startAt = now.plusSeconds(3600),
                    endAt = now.plusSeconds(7200),
                    source = ScheduleSource.MANUAL,
                    tagId = workTagId,
                    seriesId = null,
                    originalStartAt = null,
                    createdAt = now,
                    updatedAt = now,
                    colorIndex = 2,
                ),
            )
            val studyEventId = repository.insert(
                ScheduleEvent(
                    id = ScheduleId(0),
                    title = "绑定学习标签",
                    note = null,
                    startAt = now.plusSeconds(7200),
                    endAt = now.plusSeconds(10_800),
                    source = ScheduleSource.MANUAL,
                    tagId = studyTagId,
                    seriesId = null,
                    originalStartAt = null,
                    createdAt = now,
                    updatedAt = now,
                    colorIndex = 3,
                ),
            )

            // 前置验证：在真实 SQLite 外键开启下，直接 DELETE 含关联日程的标签必抛外键约束异常（证明 ON DELETE RESTRICT 真实生效）。
            try {
                database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM schedule_tags WHERE id = ${workTagId.value}",
                )
                fail("直接删除有关联日程的标签应触发 ON DELETE RESTRICT 异常")
            } catch (_: SQLiteConstraintException) {
                // 预期命中外键限制。
            }

            // 正向验证：通过事务安全删除方法删除“工作”标签，不触发外键崩溃、关联日程 tag_id 置 NULL 且日程保留。
            val detachTime = now.plusSeconds(300)
            val deleted = repository.deleteTagAndDetachEvents(workTagId, detachTime)
            assertTrue("事务删除用户标签应返回 true", deleted)
            assertNull("标签行应已删除", repository.getTag(workTagId))

            val detached1 = repository.get(event1Id)
            val detached2 = repository.get(event2Id)
            val untouched = repository.get(studyEventId)

            assertEquals("日程 1 本体必须保留", "绑定工作标签 1", detached1?.title)
            assertNull("日程 1 tagId 必须变为 NULL", detached1?.tagId)
            assertEquals("日程 1 updatedAt 应更新", detachTime, detached1?.updatedAt)
            assertEquals("日程 1 colorIndex 不应丢失", 1, detached1?.colorIndex)

            assertEquals("日程 2 本体必须保留", "绑定工作标签 2", detached2?.title)
            assertNull("日程 2 tagId 必须变为 NULL", detached2?.tagId)

            assertEquals("其他标签的日程不受影响", studyTagId, untouched?.tagId)

            // 边界：再次删除已不存在的标签返回 false，不崩溃。
            assertFalse(repository.deleteTagAndDetachEvents(workTagId, detachTime))
        } finally {
            database.close()
        }
    }

    private fun assertNullableNoDefaultColumn(
        db: SupportSQLiteDatabase,
        table: String,
        column: String,
    ) {
        var found = false
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            val notNullIdx = cursor.getColumnIndex("notnull")
            val dfltIdx = cursor.getColumnIndex("dflt_value")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIdx) == column) {
                    found = true
                    assertEquals("$table.$column 不得 NOT NULL", 0, cursor.getInt(notNullIdx))
                    assertTrue("$table.$column 不得有默认值", cursor.isNull(dfltIdx))
                }
            }
        }
        assertTrue("$table 缺少列 $column", found)
    }

    private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean {
        query("PRAGMA table_info($table)").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIdx) == column) return true
            }
        }
        return false
    }

    private fun SupportSQLiteDatabase.countRows(table: String): Int {
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private companion object {
        const val TEST_DB_V4_TO_V5 = "r7-migration-v4-to-v5.db"
        const val TEST_DB_V1_TO_V5 = "r7-migration-v1-to-v5.db"
    }
}
