package io.github.mazilin.nautile.verification

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导出 schema 静态核对（T-051；S1-T04）。
 *
 * 不依赖 Room 运行时，只检查 `app/schemas/.../1.json` 的版本与表/索引文本；
 * 真实打开与迁移由 androidTest 的 MigrationTestHelper 负责。
 */
class SchemaArtifactTest {

    private val schema: File = listOf(
        File("schemas/io.github.mazilin.nautile.data.db.NautileDatabase/1.json"),
        File("app/schemas/io.github.mazilin.nautile.data.db.NautileDatabase/1.json"),
    ).firstOrNull { it.isFile }
        ?: error("找不到导出的 schema；当前工作目录=${File(".").absolutePath}")

    @Test
    fun AC_T_01_schema_v1_contains_tasks_and_projects() {
        val text = schema.readText()

        assertTrue("schema 版本不是 1", text.contains("\"version\": 1"))
        assertTrue("缺少 tasks 表", text.contains("\"tableName\": \"tasks\""))
        assertTrue("缺少 projects 表", text.contains("\"tableName\": \"projects\""))
    }

    @Test
    fun AC_T_01_schema_v1_has_task_indices_and_project_foreign_key() {
        val text = schema.readText()

        assertTrue("缺少 position 索引", text.contains("index_tasks_position"))
        assertTrue("缺少 project_id 索引", text.contains("index_tasks_project_id"))
        assertTrue("缺少 projects 外键", text.contains("\"table\": \"projects\""))
        assertTrue("外键不是 RESTRICT", text.contains("\"onDelete\": \"RESTRICT\""))
    }

    @Test
    fun AC_D_09_schema_v2_has_detail_and_keeps_v1_artifact() {
        val v1 = schema
        val v2 = listOf(
            File("schemas/io.github.mazilin.nautile.data.db.NautileDatabase/2.json"),
            File("app/schemas/io.github.mazilin.nautile.data.db.NautileDatabase/2.json"),
        ).firstOrNull { it.isFile }
            ?: error("找不到 2.json；当前工作目录=${File(".").absolutePath}")

        assertTrue("1.json 应保留", v1.isFile)
        val text = v2.readText()
        assertTrue("v2 版本不是 2", text.contains("\"version\": 2"))
        assertTrue("v2 缺少 tasks", text.contains("\"tableName\": \"tasks\""))
        assertTrue("v2 缺少 projects", text.contains("\"tableName\": \"projects\""))
        assertTrue("v2 缺少 detail 列", text.contains("\"columnName\": \"detail\""))
        assertTrue("detail 不是 TEXT", text.contains("\"columnName\": \"detail\"") &&
            text.contains("\"affinity\": \"TEXT\""))
    }
}
