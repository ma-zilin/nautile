package io.github.mazilin.nautile.contract

import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.domain.repository.DeleteCategoryOutcome
import io.github.mazilin.nautile.domain.repository.StartTimerOutcome
import io.github.mazilin.nautile.domain.repository.StopTimerOutcome
import io.github.mazilin.nautile.domain.repository.TimeCategoryRepository
import io.github.mazilin.nautile.domain.repository.TimeLogRepository
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日志仓库契约（R6-G4-V1；AC-L-02、AC-T-21/22）。
 *
 * 同一组断言由内存替身与 Room 实现共同继承；本类不依赖 Android 运行时。
 */
abstract class TimeLogRepositoryContract {

    protected abstract val logs: TimeLogRepository

    protected abstract val categories: TimeCategoryRepository

    protected open fun close() = Unit

    private val base: Instant = Instant.parse("2026-11-28T01:00:00Z")

    @After
    fun tearDownContract() {
        close()
    }

    private suspend fun category(name: String, parentId: TimeCategoryId? = null): TimeCategoryId {
        val now = base
        return categories.insert(
            TimeCategory(
                id = TimeCategoryId(0),
                name = name,
                parentId = parentId,
                sortOrder = categories.nextSortOrder(parentId),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun log(
        categoryId: TimeCategoryId,
        startAt: Instant,
        endAt: Instant?,
        id: TimeLogId = TimeLogId(0),
    ): TimeLog = TimeLog(
        id = id,
        categoryId = categoryId,
        startAt = startAt,
        endAt = endAt,
        createdAt = startAt,
        updatedAt = startAt,
    )

    @Test
    fun AC_L_02_insert_assigns_id_and_reads_back() = runBlocking {
        val categoryId = category("学习")
        val start = base.plusSeconds(3600)
        val end = start.plusSeconds(3600)

        val id = logs.insert(log(categoryId, start, end))

        assertTrue("新记录 id 应大于 0", id.value > 0L)
        val stored = logs.get(id)
        assertEquals(categoryId, stored?.categoryId)
        assertEquals(start, stored?.startAt)
        assertEquals(end, stored?.endAt)
    }

    @Test
    fun AC_L_02_observe_all_is_descending_and_update_keeps_single_row() = runBlocking {
        val categoryId = category("学习")
        val early = base.plusSeconds(600)
        val late = base.plusSeconds(7200)
        val earlyId = logs.insert(log(categoryId, early, early.plusSeconds(600)))
        val lateId = logs.insert(log(categoryId, late, late.plusSeconds(600)))

        assertEquals(
            "按开始时间倒序（最新在上）",
            listOf(lateId, earlyId),
            logs.observeAllDescending().first().map { it.id },
        )

        // 编辑起止/分类：整条更新，不新增行。
        val otherCategory = category("工作")
        val updated = log(
            categoryId = otherCategory,
            startAt = early.minusSeconds(600),
            endAt = early.plusSeconds(1200),
            id = earlyId,
        )
        logs.update(updated)

        val all = logs.observeAllDescending().first()
        assertEquals("更新不得产生重复记录", 2, all.size)
        assertEquals(otherCategory, logs.get(earlyId)?.categoryId)
        assertEquals(early.minusSeconds(600), logs.get(earlyId)?.startAt)
        assertEquals("修改后仍按新开始时间倒序", earlyId, all.last().id)
    }

    @Test
    fun AC_T_21_start_and_stop_running_record_persists_and_reappears() = runBlocking {
        val categoryId = category("学习")
        val start = base
        val started = logs.startTimer(categoryId, log(categoryId, start, null))
        assertTrue(started is StartTimerOutcome.Started)
        val runningId = (started as StartTimerOutcome.Started).log.id

        val running = logs.observeRunning().first()
        assertEquals(1, running.size)
        assertEquals(runningId, running.first().id)
        assertNull("运行中记录 end_at 为空", running.first().endAt)

        val stopped = logs.stopTimer(categoryId, start.plusSeconds(120))
        assertTrue(stopped is StopTimerOutcome.Stopped)
        val stoppedLog = (stopped as StopTimerOutcome.Stopped).log
        assertEquals(start.plusSeconds(120), stoppedLog.endAt)
        assertTrue("结束后不再处于运行中", logs.observeRunning().first().isEmpty())

        // 再次开始生成新记录，不复用旧记录。
        val restarted = logs.startTimer(categoryId, log(categoryId, start.plusSeconds(300), null))
        val newId = (restarted as StartTimerOutcome.Started).log.id
        assertTrue("再次开始应为新记录", newId != runningId)
        assertEquals(2, logs.observeAllDescending().first().size)
    }

    @Test
    fun AC_T_21_second_start_same_category_returns_already_running_without_new_row() = runBlocking {
        val categoryId = category("学习")
        val first = logs.startTimer(categoryId, log(categoryId, base, null))
        val firstId = (first as StartTimerOutcome.Started).log.id

        repeat(3) {
            val again = logs.startTimer(categoryId, log(categoryId, base.plusSeconds(60), null))
            assertTrue("同分类再次开始只提示", again is StartTimerOutcome.AlreadyRunning)
            assertEquals(firstId, (again as StartTimerOutcome.AlreadyRunning).logId)
        }

        assertEquals("同分类最多一段运行中", 1, logs.observeRunning().first().size)
        assertEquals("不得产生第二行", 1, logs.observeAllDescending().first().size)
        assertEquals("开始时刻不被重置", base, logs.get(firstId)?.startAt)
    }

    @Test
    fun AC_T_22_concurrent_start_same_category_keeps_single_running() = runBlocking {
        val categoryId = category("学习")

        val outcomes = coroutineScope {
            (1..8).map {
                async { logs.startTimer(categoryId, log(categoryId, base.plusSeconds(60L * it), null)) }
            }.awaitAll()
        }

        assertEquals("并发开始只有一个成功", 1, outcomes.count { it is StartTimerOutcome.Started })
        assertEquals(7, outcomes.count { it is StartTimerOutcome.AlreadyRunning })
        assertEquals(1, logs.observeRunning().first().size)
        assertEquals(1, logs.observeAllDescending().first().size)
    }

    @Test
    fun AC_T_22_stop_one_category_keeps_other_running() = runBlocking {
        val study = category("学习")
        val work = category("工作")
        logs.startTimer(study, log(study, base, null))
        logs.startTimer(work, log(work, base.plusSeconds(30), null))

        val stopped = logs.stopTimer(study, base.plusSeconds(600))
        assertTrue(stopped is StopTimerOutcome.Stopped)

        val running = logs.observeRunning().first()
        assertEquals("结束一个分类不得结束另一个", 1, running.size)
        assertEquals(work, running.first().categoryId)
    }

    @Test
    fun AC_T_21_stop_earlier_than_start_uses_start_time_and_never_negative() = runBlocking {
        val categoryId = category("学习")
        logs.startTimer(categoryId, log(categoryId, base, null))

        // 设备时间回拨：结束时间取 max(now, start)，不产生负数与补时。
        val stopped = logs.stopTimer(categoryId, base.minusSeconds(3600))
        assertEquals(base, (stopped as StopTimerOutcome.Stopped).log.endAt)
    }

    @Test
    fun AC_T_21_stop_without_running_returns_not_running() = runBlocking {
        val categoryId = category("学习")
        assertEquals(StopTimerOutcome.NotRunning, logs.stopTimer(categoryId, base))
        assertEquals(0, logs.observeAllDescending().first().size)
    }

    @Test
    fun AC_T_21_recent_category_ids_follow_latest_start_descending() = runBlocking {
        val study = category("学习")
        val work = category("工作")
        val rest = category("休息")
        logs.insert(log(study, base, base.plusSeconds(60)))
        logs.insert(log(work, base.plusSeconds(120), base.plusSeconds(180)))
        logs.insert(log(study, base.plusSeconds(240), base.plusSeconds(300)))

        assertEquals(
            listOf(study, work),
            logs.observeRecentCategoryIds(2).first(),
        )
        assertEquals(
            "最近使用按每分类最近一次开始时间倒序",
            listOf(rest, study, work),
            run {
                logs.insert(log(rest, base.plusSeconds(3600), base.plusSeconds(3660)))
                logs.observeRecentCategoryIds(3).first()
            },
        )
    }
}

/**
 * 分类仓库契约（R6-G4-V1；AC-L-10、AC-L-18）。
 *
 * 同一组断言由内存替身与 Room 实现共同继承。
 */
abstract class TimeCategoryRepositoryContract {

    protected abstract val categories: TimeCategoryRepository

    protected abstract val logs: TimeLogRepository

    protected open fun close() = Unit

    private val now: Instant = Instant.parse("2026-11-28T01:00:00Z")

    @After
    fun tearDownContract() {
        close()
    }

    private suspend fun insert(name: String, parentId: TimeCategoryId? = null): TimeCategoryId =
        categories.insert(
            TimeCategory(
                id = TimeCategoryId(0),
                name = name,
                parentId = parentId,
                sortOrder = categories.nextSortOrder(parentId),
                createdAt = now,
                updatedAt = now,
            ),
        )

    private suspend fun insertLog(categoryId: TimeCategoryId) {
        logs.insert(
            TimeLog(
                id = TimeLogId(0),
                categoryId = categoryId,
                startAt = now,
                endAt = now.plusSeconds(600),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    @Test
    fun AC_L_10_two_levels_listed_parent_first_then_children() = runBlocking {
        val study = insert("学习")
        val work = insert("工作")
        val math = insert("数学", study)

        val all = categories.observeAll().first()
        assertEquals("顶级在前，子级随后", listOf(study, work, math), all.map { it.id })
        // 同级下一个排序号：顶级已有 0、1，子级只有 0。
        assertEquals(2, categories.nextSortOrder(null))
        assertEquals(1, categories.nextSortOrder(study))
    }

    @Test
    fun AC_L_18_rename_keeps_id_and_log_attribution() = runBlocking {
        val study = insert("学习")
        insertLog(study)

        categories.update(categories.get(study)!!.copy(name = "学习（改名）", updatedAt = now.plusSeconds(1)))

        assertEquals("学习（改名）", categories.get(study)?.name)
        assertEquals("改名后历史日志仍归该分类", 1, categories.countLogs(study))
        assertEquals(study, logs.observeAllDescending().first().first().categoryId)
    }

    @Test
    fun AC_L_18_delete_blocked_by_children_then_allowed_after_child_removed() = runBlocking {
        val study = insert("学习")
        val math = insert("数学", study)

        val blocked = categories.deleteIfUnused(study)
        assertTrue("有子级时删除被拒", blocked is DeleteCategoryOutcome.Blocked)
        assertEquals(1, (blocked as DeleteCategoryOutcome.Blocked).childCount)
        assertEquals(0, blocked.logCount)
        assertEquals("被拒后分类仍在", "学习", categories.get(study)?.name)

        assertEquals(DeleteCategoryOutcome.Deleted, categories.deleteIfUnused(math))
        assertEquals(DeleteCategoryOutcome.Deleted, categories.deleteIfUnused(study))
        assertNull(categories.get(study))
    }

    @Test
    fun AC_L_18_delete_blocked_by_logs_then_allowed_after_log_removed() = runBlocking {
        val study = insert("学习")
        insertLog(study)

        val blocked = categories.deleteIfUnused(study)
        assertTrue("有日志时删除被拒", blocked is DeleteCategoryOutcome.Blocked)
        assertEquals(1, (blocked as DeleteCategoryOutcome.Blocked).logCount)
        assertEquals(0, blocked.childCount)
        assertEquals("被拒后分类仍在", "学习", categories.get(study)?.name)

        val logId = logs.observeAllDescending().first().first().id
        logs.delete(logId)
        assertEquals(DeleteCategoryOutcome.Deleted, categories.deleteIfUnused(study))
    }

    @Test
    fun AC_L_18_delete_unknown_returns_not_found() = runBlocking {
        assertEquals(DeleteCategoryOutcome.NotFound, categories.deleteIfUnused(TimeCategoryId(404)))
    }
}
