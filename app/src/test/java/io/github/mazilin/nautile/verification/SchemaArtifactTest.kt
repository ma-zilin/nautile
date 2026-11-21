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
}
