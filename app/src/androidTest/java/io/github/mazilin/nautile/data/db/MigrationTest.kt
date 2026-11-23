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
 * S1-T04/T-080 迁移测试：v1 夹具 → MIGRATION_1_2 → v2 校验。
 *
 * 覆盖旧行 detail 为 null、其他列/行数不变；真机未到场时由报告标注“未执行”。
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
        const val TEST_DATABASE_MIGRATION = "migration-test-v1-v2.db"
    }
}
