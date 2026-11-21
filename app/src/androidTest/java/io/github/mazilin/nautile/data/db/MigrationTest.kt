package io.github.mazilin.nautile.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S1-T04/T-051 迁移测试（06-STO-02）。
 *
 * S1 只有 version 1：用 MigrationTestHelper 打开导出的 schema 并核对两张表/索引。
 * 真机无人到场时本测试不执行，报告中标注“未执行”。
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

    private fun SupportSQLiteDatabase.objectNames(type: String): Set<String> {
        val names = mutableSetOf<String>()
        query("SELECT name FROM sqlite_master WHERE type = '$type'").use { cursor ->
            while (cursor.moveToNext()) {
                names += cursor.getString(0)
            }
        }
        return names
    }

    private companion object {
        const val TEST_DATABASE_OPEN = "migration-test-open.db"
        const val TEST_DATABASE_TABLES = "migration-test-tables.db"
    }
}
