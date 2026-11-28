package io.github.mazilin.nautile.verification

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R7-G1 · 导出 schema v5 静态核对（JVM，不启动 Room；架构 §1.3）。
 *
 * 校验：
 * - `5.json` version == 5、8 张表齐全；
 * - `schedule_events.color_index` 与 `time_logs.color_index` 均存在、INTEGER、可空无默认；
 * - `schedule_tags` 与 `time_categories` 不含 `color_index`；
 * - 既有外键仍为 `ON DELETE RESTRICT`，`time_logs.end_at` 仍可空无默认；
 * - 历史 `1.json`–`4.json` 完好保留且未被修改；
 * - `AppContainer.kt` 注册 `MIGRATION_1_2 .. MIGRATION_4_5` 且零 destructive fallback。
 */
class SchemaV5ArtifactR7Test {

    private fun schemaFile(name: String): File = listOf(
        File("schemas/io.github.mazilin.nautile.data.db.NautileDatabase/$name"),
        File("app/schemas/io.github.mazilin.nautile.data.db.NautileDatabase/$name"),
    ).firstOrNull { it.isFile }
        ?: error("找不到 $name；当前工作目录=${File(".").absolutePath}")

    private fun mainRoot(): File = listOf(
        File("src/main/java"),
        File("app/src/main/java"),
    ).firstOrNull { it.isDirectory }
        ?: error("找不到 src/main/java；当前工作目录=${File(".").absolutePath}")

    private fun entitySection(text: String, table: String): String {
        val start = text.indexOf("\"tableName\": \"$table\"")
        assertTrue("5.json 缺少表 $table", start >= 0)
        val next = text.indexOf("\"tableName\":", start + 1)
        return if (next >= 0) text.substring(start, next) else text.substring(start)
    }

    private fun columnBlock(section: String, column: String): String {
        val index = section.indexOf("\"columnName\": \"$column\"")
        assertTrue("缺少列 $column", index >= 0)
        return section.substring(index, section.indexOf("}", index) + 1)
    }

    @Test
    fun r7_schemaV5_hasVersion5AndAllEightTables() {
        val text = schemaFile("5.json").readText()

        assertTrue("5.json 版本不是 5", text.contains("\"version\": 5"))
        val expectedTables = listOf(
            "tasks",
            "projects",
            "schedule_series",
            "schedule_events",
            "schedule_exceptions",
            "schedule_tags",
            "time_categories",
            "time_logs",
        )
        expectedTables.forEach { table ->
            assertTrue("5.json 缺少表 $table", text.contains("\"tableName\": \"$table\""))
        }
        val tableOccurrences = Regex("\"tableName\":\\s*\"").findAll(text).count()
        assertTrue("5.json 表数量应恰好为 8，实际=$tableOccurrences", tableOccurrences == 8)
    }

    @Test
    fun r7_schemaV5_colorIndexExistsOnlyOnEventsAndTimeLogsAsNullableIntegerWithoutDefault() {
        val text = schemaFile("5.json").readText()

        listOf("schedule_events", "time_logs").forEach { table ->
            val section = entitySection(text, table)
            val block = columnBlock(section, "color_index")
            assertTrue("$table.color_index 应为 INTEGER", block.contains("\"affinity\": \"INTEGER\""))
            assertFalse("$table.color_index 不得 NOT NULL", block.contains("\"notNull\": true"))
            assertFalse("$table.color_index 不得有默认值", block.contains("defaultValue"))

            val createSql = section.substringAfter("\"createSql\": \"", "").substringBefore("\"")
            assertTrue("$table 建表 SQL 缺少 color_index", createSql.contains("`color_index` INTEGER"))
            assertFalse("$table.color_index 不得 NOT NULL", createSql.contains("`color_index` INTEGER NOT NULL"))
            assertFalse("$table 建表 SQL 不得含 DEFAULT", createSql.contains("DEFAULT"))
        }

        listOf("schedule_tags", "time_categories").forEach { table ->
            val section = entitySection(text, table)
            assertFalse("$table 不应包含 color_index 列", section.contains("\"columnName\": \"color_index\""))
        }
    }

    @Test
    fun r7_schemaV5_preservesNullableColumnsAndRestrictForeignKeys() {
        val text = schemaFile("5.json").readText()

        columnBlock(entitySection(text, "time_logs"), "end_at").let { block ->
            assertFalse("time_logs.end_at 不得 NOT NULL", block.contains("\"notNull\": true"))
            assertFalse("time_logs.end_at 不得有默认值", block.contains("defaultValue"))
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

        // schedule_events.tag_id 外键仍保持 RESTRICT，未做破坏性重建。
        val scheduleEvents = entitySection(text, "schedule_events")
        assertTrue("schedule_events 外键应保持 RESTRICT", scheduleEvents.contains("\"onDelete\": \"RESTRICT\""))
        assertFalse("schedule_events 外键不得改为 SET NULL", scheduleEvents.contains("SET_NULL"))
    }

    @Test
    fun r7_previousSchemas1To4_remainIntactAndUnmodified() {
        val v1 = schemaFile("1.json").readText()
        val v2 = schemaFile("2.json").readText()
        val v3 = schemaFile("3.json").readText()
        val v4 = schemaFile("4.json").readText()

        assertTrue("1.json 必须保持 version 1", v1.contains("\"version\": 1"))
        assertTrue("2.json 必须保持 version 2", v2.contains("\"version\": 2"))
        assertTrue("3.json 必须保持 version 3", v3.contains("\"version\": 3"))
        assertTrue("4.json 必须保持 version 4", v4.contains("\"version\": 4"))
        assertFalse("4.json 不应提前包含 color_index", v4.contains("color_index"))
    }

    @Test
    fun r7_appContainerRegistersMigration4To5_withoutDestructiveFallback() {
        val container = File(mainRoot(), "io/github/mazilin/nautile/di/AppContainer.kt").readText()
        assertTrue("AppContainer 必须注册 MIGRATION_1_2", container.contains("MIGRATION_1_2"))
        assertTrue("AppContainer 必须注册 MIGRATION_2_3", container.contains("MIGRATION_2_3"))
        assertTrue("AppContainer 必须注册 MIGRATION_3_4", container.contains("MIGRATION_3_4"))
        assertTrue("AppContainer 必须注册 MIGRATION_4_5", container.contains("MIGRATION_4_5"))
        assertFalse("禁止 fallbackToDestructiveMigration", container.contains("fallbackToDestructiveMigration"))
    }
}
