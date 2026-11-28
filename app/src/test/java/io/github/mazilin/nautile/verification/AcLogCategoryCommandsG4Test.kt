package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.usecase.TimeCategoryCommandResult
import io.github.mazilin.nautile.domain.usecase.TimeCategoryCommands
import io.github.mazilin.nautile.domain.usecase.TimeCategoryError
import io.github.mazilin.nautile.domain.usecase.TimeLogCommandResult
import io.github.mazilin.nautile.domain.usecase.TimeLogCommands
import io.github.mazilin.nautile.testing.FakeClock
import io.github.mazilin.nautile.testing.InMemoryTimeCategoryRepository
import io.github.mazilin.nautile.testing.InMemoryTimeLogRepository
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R6-G4-V1 · 分类写入命令（AC-L-10、AC-L-18）。
 *
 * 预期来自基线 v2.16 与 D-new2：最多两级；有子级或日志时删除被拒并给出两项计数；
 * 用户逐条处理后可删；改名只改名称，历史日志按 id 归属。
 */
class AcLogCategoryCommandsG4Test {

    private val now: Instant = Instant.parse("2026-11-28T02:00:00Z")

    private lateinit var logStore: InMemoryTimeLogRepository
    private lateinit var categoryStore: InMemoryTimeCategoryRepository
    private lateinit var categoryCommands: TimeCategoryCommands
    private lateinit var logCommands: TimeLogCommands

    @Before
    fun setUp() {
        logStore = InMemoryTimeLogRepository()
        categoryStore = InMemoryTimeCategoryRepository(logStore)
        val clock = FakeClock(now)
        categoryCommands = TimeCategoryCommands(categoryStore, clock)
        logCommands = TimeLogCommands(logStore, categoryStore, clock)
    }

    private suspend fun create(name: String, parentId: TimeCategoryId? = null): TimeCategoryId =
        (categoryCommands.create(name, parentId) as TimeCategoryCommandResult.Success).category.id

    @Test
    fun AC_L_10_third_level_is_rejected_with_error_and_not_stored() = runTest {
        val study = create("学习")
        val math = create("数学", study)

        val result = categoryCommands.create("高等数学", math)

        assertEquals(
            "不能在子级下创建第三级",
            TimeCategoryCommandResult.Invalid(TimeCategoryError.THIRD_LEVEL_FORBIDDEN),
            result,
        )
        assertEquals("被拒后不得写入", 2, categoryStore.snapshot.size)
        assertEquals(0, categoryStore.countChildren(math))
    }

    @Test
    fun AC_L_10_blank_name_and_missing_parent_are_rejected() = runTest {
        assertEquals(
            TimeCategoryCommandResult.Invalid(TimeCategoryError.BLANK_NAME),
            categoryCommands.create("   ", null),
        )
        assertEquals(
            TimeCategoryCommandResult.Invalid(TimeCategoryError.PARENT_NOT_FOUND),
            categoryCommands.create("数学", TimeCategoryId(404)),
        )
        assertEquals(0, categoryStore.snapshot.size)
    }

    @Test
    fun AC_L_10_two_levels_can_be_created_renamed_and_deleted() = runTest {
        val study = create("学习")
        val math = create("数学", study)
        val work = create("工作")

        assertEquals(
            "顶级在前、子级随后",
            listOf(study, work, math),
            categoryStore.observeAll().first().map { it.id },
        )

        val renamed = categoryCommands.rename(math, "高等数学")
        assertTrue(renamed is TimeCategoryCommandResult.Success)
        assertEquals("高等数学", categoryStore.get(math)?.name)
        assertEquals("改名不得改变父级", study, categoryStore.get(math)?.parentId)

        assertEquals(TimeCategoryCommandResult.Deleted(math), categoryCommands.delete(math))
        assertEquals(TimeCategoryCommandResult.Deleted(study), categoryCommands.delete(study))
        assertEquals(listOf(work), categoryStore.snapshot.map { it.id })
    }

    @Test
    fun AC_L_18_delete_parent_with_child_is_blocked_with_counts() = runTest {
        val study = create("学习")
        create("数学", study)

        val result = categoryCommands.delete(study)

        assertTrue(result is TimeCategoryCommandResult.Blocked)
        result as TimeCategoryCommandResult.Blocked
        assertEquals(1, result.childCount)
        assertEquals(0, result.logCount)
        assertEquals("被拒后分类仍在", 2, categoryStore.snapshot.size)
    }

    @Test
    fun AC_L_18_delete_with_log_is_blocked_then_allowed_after_log_removed() = runTest {
        val study = create("学习")
        val log = (logCommands.createManual(
            study,
            now.minusSeconds(3600),
            now.minusSeconds(1800),
        ) as TimeLogCommandResult.Success).log

        val blocked = categoryCommands.delete(study) as TimeCategoryCommandResult.Blocked
        assertEquals(1, blocked.logCount)
        assertEquals(0, blocked.childCount)

        assertEquals("逐条删除日志后仍可再试", TimeLogCommandResult.Deleted(log.id), logCommands.delete(log.id))
        assertEquals(TimeCategoryCommandResult.Deleted(study), categoryCommands.delete(study))
        assertEquals(0, categoryStore.snapshot.size)
    }

    @Test
    fun AC_L_18_rename_keeps_history_under_same_category() = runTest {
        val study = create("学习")
        val log = (logCommands.createManual(
            study,
            now.minusSeconds(3600),
            now.minusSeconds(1800),
        ) as TimeLogCommandResult.Success).log

        categoryCommands.rename(study, "学习（新名）")

        val stored = logStore.get(log.id)
        assertEquals("历史记录仍指向同一分类 id", study, stored?.categoryId)
        assertEquals("学习（新名）", categoryStore.get(stored!!.categoryId)?.name)
        assertEquals(1, categoryStore.countLogs(study))
        assertEquals(
            "列表路径随分类名更新",
            "学习（新名）",
            categoryStore.observeAll().first().single { it.id == study }.name,
        )
    }

    @Test
    fun AC_L_18_rename_blank_rejected_and_unknown_not_found() = runTest {
        val study = create("学习")

        assertEquals(
            TimeCategoryCommandResult.Invalid(TimeCategoryError.BLANK_NAME),
            categoryCommands.rename(study, " "),
        )
        assertEquals("学习", categoryStore.get(study)?.name)
        assertEquals(
            TimeCategoryCommandResult.NotFound(TimeCategoryId(404)),
            categoryCommands.rename(TimeCategoryId(404), "不存在"),
        )
        assertEquals(
            TimeCategoryCommandResult.NotFound(TimeCategoryId(404)),
            categoryCommands.delete(TimeCategoryId(404)),
        )
    }

    @Test
    fun AC_L_18_no_bulk_clear_or_migration_api() {
        // D-new2：分类命令不提供批量清空/迁移；只有增、改名、删除。
        val names = TimeCategoryCommands::class.java.declaredMethods.map { it.name.lowercase() }
        assertTrue("不得出现批量清空：$names", names.none { "clearall" in it || "deleteall" in it })
        assertTrue("不得出现迁移：$names", names.none { "migrat" in it })
        assertTrue(
            "只暴露增、改名、删除三个写命令：$names",
            names.filterNot { it.contains("$") }.all { name ->
                name.startsWith("create") || name.startsWith("rename") || name.startsWith("delete")
            },
        )
    }
}
