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
 * 迁移测试：v1 → v2（detail）→ v3（manual_order）。
 *
 * 覆盖：旧行 detail/manual_order 为 null、其他列与行数不变、v3 可读写；
 * schema 校验由 MigrationTestHelper.runMigrationsAndValidate 对照导出的 3.json 执行。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NautileDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun AC_T_01_schema_v1_opens_without_migration() {
        helper.createDatabase(TEST_DATABASE_OPEN, 1).close()
    }

    @Test
    fun AC_T_01_schema_v1_contains_tasks_and_projects() {
        val database = helper.createDatabase(TEST_DATABASE_TABLES, 1)
        try {
            assertEquals(1, database.version)
            val tables = database.objectNames("table")
            assertTrue("schema v1 缺少 tasks 表", "tasks" in tables)
            assertTrue("schema v1 缺少 projects 表", "projects" in tables)

            val indices = database.objectNames("index")
            assertTrue("缺少 index_tasks_position", "index_tasks_position" in indices)
            assertTrue("缺少 index_tasks_project_id", "index_tasks_project_id" in indices)
        } finally {
            database.close()
        }
    }

    @Test
    fun AC_D_09_migration_v1_to_v2_adds_nullable_detail_and_keeps_old_row() {
        val v1 = helper.createDatabase(TEST_DATABASE_MIGRATION, 1)
        try {
            v1.execSQL(
                "INSERT INTO projects (id, name, status, created_at, updated_at) " +
                    "VALUES (1, '旧项目', 'ACTIVE', 1000, 2000)",
            )
            v1.execSQL(
                "INSERT INTO tasks (id, title, project_id, position, priority, created_at, updated_at) " +
                    "VALUES (1, '旧任务', 1, 'INBOX', 'NONE', 1000, 2000)",
            )
        } finally {
            v1.close()
        }

        val v2 = helper.runMigrationsAndValidate(
            TEST_DATABASE_MIGRATION,
            2,
            true,
            MIGRATION_1_2,
        )
        try {
            assertEquals(2, v2.version)
            assertTrue("v2 缺少 detail 列", v2.hasColumn("tasks", "detail"))
            assertEquals(1, v2.countRows("tasks"))
            assertEquals(1, v2.countRows("projects"))

            v2.query(
                "SELECT title, project_id, position, created_at, updated_at, detail " +
                    "FROM tasks WHERE id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("旧任务", cursor.getString(0))
                assertEquals(1L, cursor.getLong(1))
                assertEquals("INBOX", cursor.getString(2))
                assertEquals(1000L, cursor.getLong(3))
                assertEquals(2000L, cursor.getLong(4))
                assertNull("旧任务 detail 应为 null", cursor.getString(5))
            }
        } finally {
            v2.close()
        }
    }

    @Test
    fun cmp40_1_migration_v1_to_v3_keeps_old_row_with_null_manual_order() {
        val v1 = helper.createDatabase(TEST_DATABASE_MIGRATION, 1)
        try {
            v1.execSQL(
                "INSERT INTO projects (id, name, status, created_at, updated_at) " +
                    "VALUES (1, '旧项目', 'ACTIVE', 1000, 2000)",
            )
            v1.execSQL(
                "INSERT INTO tasks (id, title, project_id, position, priority, created_at, updated_at) " +
                    "VALUES (1, '旧任务', 1, 'INBOX', 'NONE', 1000, 2000)",
            )
        } finally {
            v1.close()
        }

        val v3 = helper.runMigrationsAndValidate(
            TEST_DATABASE_MIGRATION,
            3,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
        )
        try {
            assertEquals(3, v3.version)
            assertTrue("v3 缺少 detail 列", v3.hasColumn("tasks", "detail"))
            assertTrue("v3 缺少 manual_order 列", v3.hasColumn("tasks", "manual_order"))
            assertEquals(1, v3.countRows("tasks"))
            assertEquals(1, v3.countRows("projects"))

            v3.query(
                "SELECT title, project_id, position, priority, created_at, updated_at, detail, manual_order " +
                    "FROM tasks WHERE id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("旧任务", cursor.getString(0))
                assertEquals(1L, cursor.getLong(1))
                assertEquals("INBOX", cursor.getString(2))
                assertEquals("NONE", cursor.getString(3))
                assertEquals(1000L, cursor.getLong(4))
                assertEquals(2000L, cursor.getLong(5))
                assertNull("旧任务 detail 应为 null", cursor.getString(6))
                assertTrue("旧任务 manual_order 应为 null", cursor.isNull(7))
            }
        } finally {
            v3.close()
        }
    }

    @Test
    fun cmp40_1_migration_v2_to_v3_keeps_data_and_allows_manual_order_write() {
        val v2 = helper.createDatabase(TEST_DATABASE_V2_V3, 2)
        try {
            v2.execSQL(
                "INSERT INTO projects (id, name, status, created_at, updated_at) " +
                    "VALUES (2, '旧项目', 'ACTIVE', 1000, 2000)",
            )
            v2.execSQL(
                "INSERT INTO tasks (id, title, detail, project_id, position, priority, created_at, updated_at) " +
                    "VALUES (7, '旧任务', '旧详情', 2, 'WAITING', 'HIGH', 1000, 2000)",
            )
        } finally {
            v2.close()
        }

        val v3 = helper.runMigrationsAndValidate(
            TEST_DATABASE_V2_V3,
            3,
            true,
            MIGRATION_2_3,
        )
        try {
            assertEquals(3, v3.version)

            v3.query(
                "SELECT title, detail, project_id, position, priority, created_at, updated_at, manual_order " +
                    "FROM tasks WHERE id = 7",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("旧任务", cursor.getString(0))
                assertEquals("旧详情", cursor.getString(1))
                assertEquals(2L, cursor.getLong(2))
                assertEquals("WAITING", cursor.getString(3))
                assertEquals("HIGH", cursor.getString(4))
                assertEquals(1000L, cursor.getLong(5))
                assertEquals(2000L, cursor.getLong(6))
                assertTrue("升级后 manual_order 应为 null", cursor.isNull(7))
            }

            v3.execSQL("UPDATE tasks SET manual_order = 4 WHERE id = 7")
            v3.query("SELECT manual_order, detail FROM tasks WHERE id = 7").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(4L, cursor.getLong(0))
                assertEquals("写 manual_order 不得改动 detail", "旧详情", cursor.getString(1))
            }

            v3.execSQL(
                "INSERT INTO tasks (title, position, priority, created_at, updated_at) " +
                    "VALUES ('新任务', 'INBOX', 'NONE', 3000, 4000)",
            )
            v3.query("SELECT manual_order FROM tasks WHERE title = '新任务'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue("v3 新行未指定 manual_order 应为 null", cursor.isNull(0))
            }
            assertEquals(2, v3.countRows("tasks"))
        } finally {
            v3.close()
        }
    }

    @Test
    fun cmp40_1_schema_v3_declares_manual_order_nullable_without_default() {
        val v3 = helper.createDatabase(TEST_DATABASE_OPEN_V3, 3)
        try {
            assertEquals(3, v3.version)
            var found = false
            v3.query("PRAGMA table_info(tasks)").use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndex("name")) == "manual_order") {
                        found = true
                        assertEquals(0, cursor.getInt(cursor.getColumnIndex("notnull")))
                        assertTrue(
                            "manual_order 不应有默认值",
                            cursor.isNull(cursor.getColumnIndex("dflt_value")),
                        )
                    }
                }
            }
            assertTrue("v3 缺少 manual_order 列", found)
        } finally {
            v3.close()
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

    private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean {
        query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) return true
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
        const val TEST_DATABASE_OPEN = "migration-test-open.db"
        const val TEST_DATABASE_TABLES = "migration-test-tables.db"
        const val TEST_DATABASE_MIGRATION = "migration-test-v1-v3.db"
        const val TEST_DATABASE_V2_V3 = "migration-test-v2-v3.db"
        const val TEST_DATABASE_OPEN_V3 = "migration-test-v3.db"
    }
}
