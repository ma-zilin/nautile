package io.github.mazilin.nautile.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R6-G2-V1 · 迁移测试 v3 → v4（AC-S-03/04 数据前提；架构 §2.3）。
 *
 * 覆盖：v3 旧数据保留；6 张新表存在且为空（schedule_tags 仅种子 1 行）；
 * time_logs.end_at 可空无默认；关键索引存在；v1→v4 串联保留旧行。
 * 使用 MigrationTestHelper 对照导出的 4.json 校验 schema；不依赖 destructive fallback。
 */
@RunWith(AndroidJUnit4::class)
class MigrationV4Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NautileDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun g2_migration_v3_to_v4_createsTablesIndicesAndKeepsOldRows() {
        val v3 = helper.createDatabase(TEST_DATABASE_V3, 3)
        try {
            v3.execSQL(
                "INSERT INTO projects (id, name, status, created_at, updated_at) " +
                    "VALUES (1, '旧项目', 'ACTIVE', 1000, 2000)",
            )
            v3.execSQL(
                "INSERT INTO tasks (id, title, detail, project_id, position, priority, created_at, updated_at, manual_order) " +
                    "VALUES (1, '旧任务', '旧详情', 1, 'INBOX', 'NONE', 1000, 2000, 3)",
            )
        } finally {
            v3.close()
        }

        val v4 = helper.runMigrationsAndValidate(
            TEST_DATABASE_V3,
            4,
            true,
            MIGRATION_3_4,
        )
        try {
            assertEquals(4, v4.version)

            val tables = v4.objectNames("table")
            listOf(
                "tasks",
                "projects",
                "schedule_series",
                "schedule_events",
                "schedule_exceptions",
                "schedule_tags",
                "time_categories",
                "time_logs",
            ).forEach { table -> assertTrue("迁移后缺少表 $table", table in tables) }

            // 旧数据保留且未被重写。
            assertEquals(1, v4.countRows("tasks"))
            assertEquals(1, v4.countRows("projects"))
            v4.query(
                "SELECT title, detail, position, manual_order FROM tasks WHERE id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("旧任务", cursor.getString(0))
                assertEquals("旧详情", cursor.getString(1))
                assertEquals("INBOX", cursor.getString(2))
                assertEquals(3L, cursor.getLong(3))
            }

            // 新表为空（防半成品）；schedule_tags 只有迁移种子。
            assertEquals(0, v4.countRows("schedule_series"))
            assertEquals(0, v4.countRows("schedule_events"))
            assertEquals(0, v4.countRows("schedule_exceptions"))
            assertEquals(0, v4.countRows("time_categories"))
            assertEquals(0, v4.countRows("time_logs"))
            assertEquals(1, v4.countRows("schedule_tags"))

            // 索引存在。
            val indices = v4.objectNames("index")
            listOf(
                "index_schedule_tags_system_key",
                "index_schedule_events_start_at",
                "index_schedule_events_end_at",
                "index_schedule_events_tag_id",
                "index_schedule_events_series_id",
                "index_schedule_events_source",
                "index_schedule_exceptions_occurrence",
                "index_time_categories_parent_id",
                "index_time_logs_start_at",
                "index_time_logs_end_at",
                "index_time_logs_category_end",
            ).forEach { index -> assertTrue("迁移后缺少索引 $index", index in indices) }
        } finally {
            v4.close()
        }
    }

    @Test
    fun g2_migration_v3_to_v4_seeds_course_tag_once() {
        helper.createDatabase(TEST_DATABASE_SEED, 3).close()

        val v4 = helper.runMigrationsAndValidate(TEST_DATABASE_SEED, 4, true, MIGRATION_3_4)
        try {
            assertEquals(1, v4.countRows("schedule_tags"))
            v4.query(
                "SELECT id, name, system_key, sort_order, created_at, updated_at FROM schedule_tags",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
                assertEquals("课程表", cursor.getString(1))
                assertEquals("COURSE", cursor.getString(2))
                assertEquals(0L, cursor.getLong(3))
                assertEquals(0L, cursor.getLong(4))
                assertEquals(0L, cursor.getLong(5))
            }
        } finally {
            v4.close()
        }
    }

    @Test
    fun g2_migration_v3_to_v4_time_logs_end_at_is_nullable_without_default() {
        helper.createDatabase(TEST_DATABASE_NULLABLE, 3).close()

        val v4 = helper.runMigrationsAndValidate(TEST_DATABASE_NULLABLE, 4, true, MIGRATION_3_4)
        try {
            var found = false
            v4.query("PRAGMA table_info(time_logs)").use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndex("name")) == "end_at") {
                        found = true
                        assertEquals(0, cursor.getInt(cursor.getColumnIndex("notnull")))
                        assertTrue(
                            "end_at 不应有默认值",
                            cursor.isNull(cursor.getColumnIndex("dflt_value")),
                        )
                    }
                }
            }
            assertTrue("time_logs 缺少 end_at 列", found)

            // 运行中记录（end_at NULL）可写入，且不产生半截时间。
            v4.execSQL(
                "INSERT INTO time_categories (id, name, parent_id, sort_order, created_at, updated_at) " +
                    "VALUES (10, '学习', NULL, 0, 0, 0)",
            )
            v4.execSQL(
                "INSERT INTO time_logs (category_id, start_at, end_at, created_at, updated_at) " +
                    "VALUES (10, 5000, NULL, 5000, 5000)",
            )
            assertEquals(1, v4.countRows("time_logs"))
            v4.query("SELECT end_at FROM time_logs").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue("运行中记录 end_at 应为 NULL", cursor.isNull(0))
            }
        } finally {
            v4.close()
        }
    }

    @Test
    fun g2_migration_v1_to_v4_chain_keeps_old_task_and_project_rows() {
        val v1 = helper.createDatabase(TEST_DATABASE_CHAIN, 1)
        try {
            v1.execSQL(
                "INSERT INTO projects (id, name, status, created_at, updated_at) " +
                    "VALUES (9, '链式项目', 'ACTIVE', 1000, 2000)",
            )
            v1.execSQL(
                "INSERT INTO tasks (id, title, project_id, position, priority, created_at, updated_at) " +
                    "VALUES (9, '链式任务', 9, 'WAITING', 'HIGH', 1000, 2000)",
            )
        } finally {
            v1.close()
        }

        val v4 = helper.runMigrationsAndValidate(
            TEST_DATABASE_CHAIN,
            4,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
        )
        try {
            assertEquals(4, v4.version)
            assertEquals(1, v4.countRows("tasks"))
            assertEquals(1, v4.countRows("projects"))
            v4.query(
                "SELECT title, project_id, position, priority, detail, manual_order FROM tasks WHERE id = 9",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("链式任务", cursor.getString(0))
                assertEquals(9L, cursor.getLong(1))
                assertEquals("WAITING", cursor.getString(2))
                assertEquals("HIGH", cursor.getString(3))
                assertNull("旧任务 detail 应为 NULL", cursor.getString(4))
                assertTrue("旧任务 manual_order 应为 NULL", cursor.isNull(5))
            }
            assertEquals(0, v4.countRows("schedule_events"))
            assertEquals(1, v4.countRows("schedule_tags"))
        } finally {
            v4.close()
        }
    }

    private fun SupportSQLiteDatabase.objectNames(type: String): Set<String> {
        val names = mutableSetOf<String>()
        query("SELECT name FROM sqlite_master WHERE type = '$type'").use { cursor ->
            while (cursor.moveToNext()) {
                names += cursor.getString(0)
            }
        }
        return names
    }

    private fun SupportSQLiteDatabase.countRows(table: String): Int {
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private companion object {
        const val TEST_DATABASE_V3 = "g2-migration-v3-v4.db"
        const val TEST_DATABASE_SEED = "g2-migration-seed.db"
        const val TEST_DATABASE_NULLABLE = "g2-migration-nullable.db"
        const val TEST_DATABASE_CHAIN = "g2-migration-chain-v1-v4.db"
    }
}
