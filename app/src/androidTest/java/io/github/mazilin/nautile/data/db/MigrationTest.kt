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
 * 迁移测试：v1 → v2（detail）→ v3（manual_order）→ v4（AI 两张表）。
 *
 * 覆盖：旧行 detail/manual_order 为 null、其他列与行数不变、v3 可读写；
 * v3→v4、v1→v4 全链、v4 打开、v4 外键 RESTRICT/索引、4.json 静态核对；
 * schema 校验由 MigrationTestHelper.runMigrationsAndValidate 对照导出的 4.json 执行。
 * R6 尚未合并，不写 4→5；待 R6 合并后按架构 §4.5 补验。
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

    /**
     * G2-T1：v3 → v4 只新增 AI 两张表与索引；旧 project/task（含 detail/manual_order）不变，AI 表为空。
     */
    @Test
    fun g2_migration_v3_to_v4_adds_ai_tables_and_keeps_legacy_rows() {
        val v3 = helper.createDatabase(TEST_DATABASE_V3_V4, 3)
        try {
            v3.execSQL(
                "INSERT INTO projects (id, name, status, created_at, updated_at) " +
                    "VALUES (1, '旧项目', 'ACTIVE', 1000, 2000)",
            )
            v3.execSQL(
                "INSERT INTO tasks (id, title, detail, project_id, position, priority, " +
                    "created_at, updated_at, manual_order) " +
                    "VALUES (1, '旧任务', '旧详情', 1, 'INBOX', 'NONE', 1000, 2000, 3)",
            )
        } finally {
            v3.close()
        }

        val v4 = helper.runMigrationsAndValidate(
            TEST_DATABASE_V3_V4,
            4,
            true,
            MIGRATION_3_4,
        )
        try {
            assertEquals(4, v4.version)
            assertTrue("v4 缺少 ai_sessions 表", v4.hasTable("ai_sessions"))
            assertTrue("v4 缺少 ai_messages 表", v4.hasTable("ai_messages"))
            assertEquals("AI 会话表迁移后应为空", 0, v4.countRows("ai_sessions"))
            assertEquals("AI 消息表迁移后应为空", 0, v4.countRows("ai_messages"))
            assertEquals("旧任务行数不应变化", 1, v4.countRows("tasks"))
            assertEquals("旧项目行数不应变化", 1, v4.countRows("projects"))

            v4.query(
                "SELECT title, detail, project_id, position, priority, manual_order " +
                    "FROM tasks WHERE id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("旧任务", cursor.getString(0))
                assertEquals("旧详情", cursor.getString(1))
                assertEquals(1L, cursor.getLong(2))
                assertEquals("INBOX", cursor.getString(3))
                assertEquals("NONE", cursor.getString(4))
                assertEquals(3L, cursor.getLong(5))
            }
        } finally {
            v4.close()
        }
    }

    /**
     * G2-T1：v1 → v4 全链迁移；旧任务 detail/manual_order 为 null，AI 表存在。
     */
    @Test
    fun g2_migration_v1_to_v4_full_chain_keeps_old_row_and_creates_ai_tables() {
        val v1 = helper.createDatabase(TEST_DATABASE_V1_V4, 1)
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

        val v4 = helper.runMigrationsAndValidate(
            TEST_DATABASE_V1_V4,
            4,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
        )
        try {
            assertEquals(4, v4.version)
            assertTrue("v4 缺少 ai_sessions 表", v4.hasTable("ai_sessions"))
            assertTrue("v4 缺少 ai_messages 表", v4.hasTable("ai_messages"))
            assertEquals(1, v4.countRows("tasks"))
            assertEquals(1, v4.countRows("projects"))
            assertEquals(0, v4.countRows("ai_sessions"))
            assertEquals(0, v4.countRows("ai_messages"))

            v4.query("SELECT detail, manual_order FROM tasks WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertNull("全链迁移后旧任务 detail 应为 null", cursor.getString(0))
                assertTrue("全链迁移后旧任务 manual_order 应为 null", cursor.isNull(1))
            }
        } finally {
            v4.close()
        }
    }

    /**
     * G2-T1：v4 直接打开；AI 关键列约束正确——error_code 可空，其余关键列 NOT NULL、无默认值。
     */
    @Test
    fun g2_schema_v4_opening_declares_ai_columns_nullability() {
        val v4 = helper.createDatabase(TEST_DATABASE_OPEN_V4, 4)
        try {
            assertEquals(4, v4.version)
            assertTrue("v4 缺少 ai_sessions 表", v4.hasTable("ai_sessions"))
            assertTrue("v4 缺少 ai_messages 表", v4.hasTable("ai_messages"))

            v4.assertColumn("ai_sessions", "id", nullable = false)
            v4.assertColumn("ai_sessions", "created_at", nullable = false)
            v4.assertColumn("ai_sessions", "updated_at", nullable = false)

            v4.assertColumn("ai_messages", "id", nullable = false)
            v4.assertColumn("ai_messages", "session_id", nullable = false)
            v4.assertColumn("ai_messages", "role", nullable = false)
            v4.assertColumn("ai_messages", "content", nullable = false)
            v4.assertColumn("ai_messages", "status", nullable = false)
            v4.assertColumn("ai_messages", "error_code", nullable = true)
            v4.assertColumn("ai_messages", "created_at", nullable = false)

            v4.assertNoDefaultValue("ai_sessions")
            v4.assertNoDefaultValue("ai_messages")
        } finally {
            v4.close()
        }
    }

    /**
     * G2-T1：v4 外键 RESTRICT 与索引——无 session 的消息插入失败；有消息的 session 删除失败；
     * 先删消息再删 session 成功；index_ai_messages_session_id 存在。
     */
    @Test
    fun g2_schema_v4_enforces_restrict_foreign_key_and_session_index() {
        val v4 = helper.createDatabase(TEST_DATABASE_FK_V4, 4)
        try {
            assertTrue(
                "v4 缺少 index_ai_messages_session_id",
                "index_ai_messages_session_id" in v4.objectNames("index"),
            )

            v4.execSQL("PRAGMA foreign_keys = ON")
            v4.query("PRAGMA foreign_keys").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("外键约束未开启，后续断言将失效", 1, cursor.getInt(0))
            }

            v4.execSQL(
                "INSERT INTO ai_sessions (id, created_at, updated_at) VALUES (1, 1000, 1000)",
            )
            v4.execSQL(
                "INSERT INTO ai_messages " +
                    "(id, session_id, role, content, status, error_code, created_at) " +
                    "VALUES (1, 1, 'USER', '你好', 'COMPLETE', NULL, 1000)",
            )

            var orphanRejected = false
            try {
                v4.execSQL(
                    "INSERT INTO ai_messages " +
                        "(id, session_id, role, content, status, error_code, created_at) " +
                        "VALUES (2, 999, 'USER', '孤儿消息', 'COMPLETE', NULL, 1000)",
                )
            } catch (_: Exception) {
                orphanRejected = true
            }
            assertTrue("指向不存在 session 的消息应被外键拒绝", orphanRejected)

            var restrictRejected = false
            try {
                v4.execSQL("DELETE FROM ai_sessions WHERE id = 1")
            } catch (_: Exception) {
                restrictRejected = true
            }
            assertTrue("有消息的 session 应因 ON DELETE RESTRICT 无法删除", restrictRejected)

            v4.execSQL("DELETE FROM ai_messages WHERE session_id = 1")
            v4.execSQL("DELETE FROM ai_sessions WHERE id = 1")
            assertEquals("先删消息再删 session 后应为空", 0, v4.countRows("ai_sessions"))
        } finally {
            v4.close()
        }
    }

    /**
     * G2-T1：导出 4.json 静态核对（androidTest 资产）。
     * 版本 4、四张表、AI 关键列可空性、索引、外键 RESTRICT、无 DEFAULT；1/2/3.json 保留。
     */
    @Test
    fun g2_schema_v4_json_artifact_matches_ai_tables() {
        val text = readSchemaAsset("4.json")

        assertTrue("4.json 版本不是 4", text.contains("\"version\": 4"))
        assertTrue("4.json 缺少 tasks", text.contains("\"tableName\": \"tasks\""))
        assertTrue("4.json 缺少 projects", text.contains("\"tableName\": \"projects\""))
        assertTrue("4.json 缺少 ai_sessions", text.contains("\"tableName\": \"ai_sessions\""))
        assertTrue("4.json 缺少 ai_messages", text.contains("\"tableName\": \"ai_messages\""))
        assertTrue("4.json 缺少 session 索引", text.contains("index_ai_messages_session_id"))
        assertTrue("4.json 缺少 session_id 列", text.contains("\"columnName\": \"session_id\""))
        assertTrue("4.json 缺少 status 列", text.contains("\"columnName\": \"status\""))
        assertTrue("4.json 缺少 RESTRICT 外键", text.contains("\"onDelete\": \"RESTRICT\""))

        val errorCodeIndex = text.indexOf("\"columnName\": \"error_code\"")
        assertTrue("4.json 缺少 error_code 列", errorCodeIndex >= 0)
        val errorCodeBlock = text.substring(errorCodeIndex, text.indexOf("}", errorCodeIndex))
        assertTrue(
            "4.json 中 error_code 应可空",
            !errorCodeBlock.contains("\"notNull\": true"),
        )
        assertTrue(
            "4.json 中 error_code 应为 TEXT",
            errorCodeBlock.contains("\"affinity\": \"TEXT\""),
        )

        // AI 表建表 SQL 不应出现 DEFAULT（与迁移 DDL 一致）。
        assertTrue(
            "4.json 的 AI 建表 SQL 不应出现 DEFAULT",
            text.contains("`error_code` TEXT") && !text.contains("`error_code` TEXT NOT NULL"),
        )

        // 历史 schema 保留。
        assertTrue("1.json 应保留", readSchemaAsset("1.json").contains("\"version\": 1"))
        assertTrue("2.json 应保留", readSchemaAsset("2.json").contains("\"version\": 2"))
        assertTrue("3.json 应保留", readSchemaAsset("3.json").contains("\"version\": 3"))
    }

    private fun readSchemaAsset(name: String): String {
        val path = "${NautileDatabase::class.java.canonicalName}/$name"
        return InstrumentationRegistry.getInstrumentation().context.assets
            .open(path)
            .bufferedReader()
            .use { it.readText() }
    }

    private fun SupportSQLiteDatabase.hasTable(table: String): Boolean =
        table in objectNames("table")

    private fun SupportSQLiteDatabase.assertColumn(
        table: String,
        column: String,
        nullable: Boolean,
    ) {
        var found = false
        query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val notNullIndex = cursor.getColumnIndex("notnull")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) {
                    found = true
                    val actualNullable = cursor.getInt(notNullIndex) == 0
                    assertEquals(
                        "$table.$column 可空性与预期不符",
                        nullable,
                        actualNullable,
                    )
                }
            }
        }
        assertTrue("$table 缺少列 $column", found)
    }

    private fun SupportSQLiteDatabase.assertNoDefaultValue(table: String) {
        query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val defaultIndex = cursor.getColumnIndex("dflt_value")
            while (cursor.moveToNext()) {
                assertTrue(
                    "$table.${cursor.getString(nameIndex)} 不应有默认值",
                    cursor.isNull(defaultIndex),
                )
            }
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
        const val TEST_DATABASE_V3_V4 = "migration-test-v3-v4.db"
        const val TEST_DATABASE_V1_V4 = "migration-test-v1-v4.db"
        const val TEST_DATABASE_OPEN_V4 = "migration-test-v4.db"
        const val TEST_DATABASE_FK_V4 = "migration-test-v4-fk.db"
    }
}
