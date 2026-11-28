package io.github.mazilin.nautile.verification

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R6-G2-V1 · 导出 schema v4 静态核对（JVM，不启动 Room）。
 *
 * 预期来自冻结架构 §1.2/§2.4 与任务书：`4.json` version=4、8 张表、6 张新表与关键列/索引；
 * `time_logs.end_at`、`schedule_events.tag_id/series_id`、`time_categories.parent_id` 可空；
 * 1/2/3.json 保留。真实迁移由 androidTest 的 MigrationV4Test 负责。
 */
class SchemaV4ArtifactG2Test {

    private fun schemaFile(name: String): File = listOf(
        File("schemas/io.github.mazilin.nautile.data.db.NautileDatabase/$name"),
        File("app/schemas/io.github.mazilin.nautile.data.db.NautileDatabase/$name"),
    ).firstOrNull { it.isFile }
        ?: error("找不到 $name；当前工作目录=${File(".").absolutePath}")

    private fun entitySection(text: String, table: String): String {
        val start = text.indexOf("\"tableName\": \"$table\"")
        assertTrue("4.json 缺少表 $table", start >= 0)
        val next = text.indexOf("\"tableName\":", start + 1)
        return if (next >= 0) text.substring(start, next) else text.substring(start)
    }

    private fun columnBlock(section: String, column: String): String {
        val index = section.indexOf("\"columnName\": \"$column\"")
        assertTrue("缺少列 $column", index >= 0)
        return section.substring(index, section.indexOf("}", index) + 1)
    }

    @Test
    fun g2_schema_artifact_exists_and_is_version4_with_eight_tables() {
        val text = schemaFile("4.json").readText()

        assertTrue("4.json 版本不是 4", text.contains("\"version\": 4"))
        listOf(
            "tasks",
            "projects",
            "schedule_series",
            "schedule_events",
            "schedule_exceptions",
            "schedule_tags",
            "time_categories",
            "time_logs",
        ).forEach { table ->
            assertTrue("4.json 缺少表 $table", text.contains("\"tableName\": \"$table\""))
        }
    }

    @Test
    fun g2_schema_nullable_columns_match_frozen_design() {
        val text = schemaFile("4.json").readText()

        columnBlock(entitySection(text, "time_logs"), "end_at").let { block ->
            assertTrue("time_logs.end_at 应为 INTEGER", block.contains("\"affinity\": \"INTEGER\""))
            assertFalse("time_logs.end_at 不得 NOT NULL", block.contains("\"notNull\": true"))
            assertFalse("time_logs.end_at 不得带默认值", block.contains("defaultValue"))
        }
        columnBlock(entitySection(text, "schedule_events"), "tag_id").let { block ->
            assertFalse("schedule_events.tag_id 必须可空", block.contains("\"notNull\": true"))
        }
        columnBlock(entitySection(text, "schedule_events"), "series_id").let { block ->
            assertFalse("schedule_events.series_id 必须可空", block.contains("\"notNull\": true"))
        }
        columnBlock(entitySection(text, "time_categories"), "parent_id").let { block ->
            assertFalse("time_categories.parent_id 必须可空", block.contains("\"notNull\": true"))
        }
    }

    @Test
    fun g2_schema_time_logs_create_sql_has_no_default_and_nullable_end_at() {
        val section = entitySection(schemaFile("4.json").readText(), "time_logs")
        val createSql = section.substringAfter("\"createSql\": \"", "").substringBefore("\"")

        assertTrue("建表 SQL 缺少 end_at", createSql.contains("`end_at` INTEGER"))
        assertFalse("time_logs.end_at 不得 NOT NULL", createSql.contains("`end_at` INTEGER NOT NULL"))
        assertFalse("time_logs 建表 SQL 不应出现 DEFAULT", createSql.contains("DEFAULT"))
    }

    @Test
    fun g2_schema_declares_required_indices() {
        val text = schemaFile("4.json").readText()

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
        ).forEach { index ->
            assertTrue("4.json 缺少索引 $index", text.contains(index))
        }

        // system_key 必须唯一；series_id + original_start_at 必须唯一。
        val tags = entitySection(text, "schedule_tags")
        val systemKeyIndex = tags.indexOf("index_schedule_tags_system_key")
        assertTrue(
            "schedule_tags.system_key 索引必须 unique",
            tags.substring(systemKeyIndex, tags.indexOf("}", systemKeyIndex)).contains("\"unique\": true"),
        )
        val exceptions = entitySection(text, "schedule_exceptions")
        val occurrence = exceptions.indexOf("index_schedule_exceptions_occurrence")
        assertTrue(
            "schedule_exceptions occurrence 索引必须 unique",
            exceptions.substring(occurrence, exceptions.indexOf("}", occurrence))
                .contains("\"unique\": true"),
        )
    }

    @Test
    fun g2_schema_foreign_keys_use_restrict() {
        val text = schemaFile("4.json").readText()

        listOf("schedule_events", "schedule_exceptions", "time_categories", "time_logs")
            .forEach { table ->
                val section = entitySection(text, table)
                assertTrue("$table 应声明外键", section.contains("\"foreignKeys\""))
                assertTrue("$table 外键应为 RESTRICT", section.contains("\"onDelete\": \"RESTRICT\""))
            }
        assertTrue("schedule_events 应引用 schedule_tags", text.contains("\"table\": \"schedule_tags\""))
        assertTrue("schedule_events 应引用 schedule_series", text.contains("\"table\": \"schedule_series\""))
    }

    @Test
    fun g2_previous_schema_artifacts_are_retained() {
        assertEquals(true, schemaFile("1.json").isFile)
        assertEquals(true, schemaFile("2.json").isFile)
        assertEquals(true, schemaFile("3.json").isFile)
        assertTrue("1.json 不应升级到 v4", schemaFile("1.json").readText().contains("\"version\": 1"))
        assertTrue("2.json 不应升级到 v4", schemaFile("2.json").readText().contains("\"version\": 2"))
        assertTrue("3.json 不应升级到 v4", schemaFile("3.json").readText().contains("\"version\": 3"))
        assertFalse(
            "3.json 不应提前包含 v4 的 time_logs",
            schemaFile("3.json").readText().contains("\"tableName\": \"time_logs\""),
        )
    }
}
