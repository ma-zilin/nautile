package io.github.mazilin.nautile.verification

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R6-G2-V1 · 升级安全静态核对：禁止 destructive fallback 作为迁移失败兜底。
 *
 * 依据冻结架构 §2.4 与任务书：不得出现 `fallbackToDestructiveMigration`、
 * `fallbackToDestructiveMigrationOnDowngrade`、`dropAllTables`、`clearAllTables`。
 * 这是源码文本检查，不替代 MigrationV4Test 的真实迁移断言。
 */
class MigrationGuardG2Test {

    private val mainRoot: File = listOf(
        File("src/main/java"),
        File("app/src/main/java"),
    ).firstOrNull { it.isDirectory }
        ?: error("找不到 src/main/java；当前工作目录=${File(".").absolutePath}")

    private val forbidden = listOf(
        "fallbackToDestructiveMigration",
        "fallbackToDestructiveMigrationOnDowngrade",
        "dropAllTables",
        "clearAllTables",
    )

    @Test
    fun g2_no_destructive_fallback_in_main_sources() {
        val violations = mainRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().withIndex().mapNotNull { (index, line) ->
                    if (forbidden.any { line.contains(it) }) {
                        "${file.relativeTo(mainRoot).path}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertTrue(
            "发现禁止的 destructive fallback：\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun g2_app_container_registers_all_migrations_through_v5() {
        val container = File(mainRoot, "io/github/mazilin/nautile/di/AppContainer.kt")
        assertTrue("缺少 AppContainer.kt", container.isFile)
        val text = container.readText()

        assertTrue("未注册 MIGRATION_1_2", text.contains("MIGRATION_1_2"))
        assertTrue("未注册 MIGRATION_2_3", text.contains("MIGRATION_2_3"))
        assertTrue("未注册 MIGRATION_3_4", text.contains("MIGRATION_3_4"))
        assertTrue("未注册 MIGRATION_4_5", text.contains("MIGRATION_4_5"))
        assertTrue("数据库构建未调用 addMigrations", text.contains("addMigrations"))

        val dbFile = File(mainRoot, "io/github/mazilin/nautile/data/db/NautileDatabase.kt")
        assertTrue("缺少 NautileDatabase.kt", dbFile.isFile)
        val dbText = dbFile.readText()
        assertTrue("NautileDatabase 版本应已升级至 v5", dbText.contains("version = 5"))
    }
}
