package io.github.mazilin.nautile.verification

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G5 门禁 · 导出 schema v3 静态核对（JVM，不启动 Room）。
 *
 * 预期依据：设计 §14.3 / CMP-40.1——v3 新增 manual_order INTEGER，可空、无默认值；
 * 1.json、2.json 保留，v3 仍含 tasks/projects/detail。
 * 真实打开与迁移由 androidTest 的 MigrationTest 负责。
 */
class SchemaV3ArtifactG5Test {

    private fun schemaFile(name: String): File = listOf(
        File("schemas/io.github.mazilin.nautile.data.db.NautileDatabase/$name"),
        File("app/schemas/io.github.mazilin.nautile.data.db.NautileDatabase/$name"),
    ).firstOrNull { it.isFile }
        ?: error("找不到 $name；当前工作目录=${File(".").absolutePath}")

    @Test
    fun cmp40_1_schema_v3_has_nullable_manual_order_column() {
        val text = schemaFile("3.json").readText()

        assertTrue("v3 版本不是 3", text.contains("\"version\": 3"))
        assertTrue("v3 缺少 tasks 表", text.contains("\"tableName\": \"tasks\""))
        assertTrue("v3 缺少 projects 表", text.contains("\"tableName\": \"projects\""))
        assertTrue("v3 缺少 detail 列", text.contains("\"columnName\": \"detail\""))

        val fieldStart = text.indexOf("\"columnName\": \"manual_order\"")
        assertTrue("v3 缺少 manual_order 列", fieldStart >= 0)
        val block = text.substring(fieldStart, text.indexOf("}", fieldStart))
        assertTrue("manual_order 应为 INTEGER", block.contains("\"affinity\": \"INTEGER\""))
        assertTrue("manual_order 不得为 NOT NULL", !block.contains("\"notNull\": true"))
        assertTrue("manual_order 不得带默认值", !block.contains("defaultValue"))
    }

    @Test
    fun cmp40_1_schema_v3_create_sql_keeps_manual_order_nullable_without_default() {
        val text = schemaFile("3.json").readText()
        val createSql = text.substringAfter("\"createSql\": \"", "").substringBefore("\"")

        assertTrue("建表 SQL 缺少 manual_order", createSql.contains("`manual_order` INTEGER"))
        assertTrue("建表 SQL 中 manual_order 不得 NOT NULL", !createSql.contains("`manual_order` INTEGER NOT NULL"))
        assertTrue("v3 建表 SQL 不应出现任何 DEFAULT", !createSql.contains("DEFAULT"))
    }

    @Test
    fun cmp40_1_schema_v1_and_v2_artifacts_are_retained() {
        val v1 = schemaFile("1.json").readText()
        val v2 = schemaFile("2.json").readText()

        assertTrue("1.json 版本不是 1", v1.contains("\"version\": 1"))
        assertTrue("1.json 缺少 tasks", v1.contains("\"tableName\": \"tasks\""))
        assertTrue("2.json 版本不是 2", v2.contains("\"version\": 2"))
        assertTrue("2.json 缺少 detail", v2.contains("\"columnName\": \"detail\""))
        assertTrue("1.json 不应含 manual_order", !v1.contains("\"columnName\": \"manual_order\""))
        assertTrue("2.json 不应含 manual_order", !v2.contains("\"columnName\": \"manual_order\""))
    }
}
