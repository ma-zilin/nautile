package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G9 四种排序（CMP-40、AC-T-08、AC-T-13）。
 *
 * 预期依据 `docs/需求基线.md` v2.2 与用户 2026-11-25 的 G9 口径：
 * - 截止日期：有日期在前、近→远；无日期末位；同日按时刻，无时刻末位；
 * - 优先级：高→中→低→无，同级再按截止日期；
 * - 创建时间：新→旧；
 * - 手动：manualOrder 升序、null 末位；
 * - 主键相同时用创建时间与任务 ID 兜底，保证稳定；排序不修改输入列表。
 */
class TaskSortingTest {

    private val base: Instant = Instant.parse("2026-11-25T00:00:00Z")

    private fun created(seconds: Long): Instant = base.plusSeconds(seconds)

    private fun task(
        id: Long,
        createdAt: Instant = created(id),
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
        priority: Priority = Priority.NONE,
        manualOrder: Long? = null,
    ) = Task(
        id = TaskId(id),
        title = "任务 $id",
        position = GtdPosition.INBOX,
        priority = priority,
        dueDate = dueDate,
        dueTime = dueTime,
        createdAt = createdAt,
        updatedAt = createdAt,
        manualOrder = manualOrder,
    )

    private fun ids(tasks: List<Task>): List<Long> = tasks.map { it.id.value }

    @Test
    fun due_sorts_nearest_first_and_undated_last() {
        val tasks = listOf(
            task(1L, dueDate = LocalDate.parse("2026-12-03")),
            task(2L, dueDate = null),
            task(3L, dueDate = LocalDate.parse("2026-12-01")),
            task(4L, dueDate = LocalDate.parse("2026-12-02")),
        )

        assertEquals(listOf(3L, 4L, 1L, 2L), ids(TaskSorting.byDue(tasks)))
    }

    @Test
    fun due_same_day_orders_by_time_with_null_time_last() {
        val day = LocalDate.parse("2026-12-01")
        val tasks = listOf(
            task(1L, dueDate = day, dueTime = null),
            task(2L, dueDate = day, dueTime = LocalTime.of(18, 0)),
            task(3L, dueDate = day, dueTime = LocalTime.of(9, 0)),
        )

        assertEquals(listOf(3L, 2L, 1L), ids(TaskSorting.byDue(tasks)))
    }

    @Test
    fun due_equal_keys_are_deterministic_and_keep_all_tasks() {
        val day = LocalDate.parse("2026-12-01")
        val tasks = listOf(
            task(30L, createdAt = created(20), dueDate = day),
            task(10L, createdAt = created(5), dueDate = day),
            task(20L, createdAt = created(5), dueDate = day),
        )

        val first = TaskSorting.byDue(tasks)
        assertEquals("重复调用结果一致", ids(first), ids(TaskSorting.byDue(tasks)))
        assertEquals("不得丢失任务", setOf(10L, 20L, 30L), ids(first).toSet())
        assertEquals("同日任务仍在同组", tasks.map { it.dueDate }.toSet(), first.map { it.dueDate }.toSet())
    }

    @Test
    fun priority_sorts_high_to_none_and_never_drops_tasks() {
        val tasks = listOf(
            task(1L, priority = Priority.NONE),
            task(2L, priority = Priority.LOW),
            task(3L, priority = Priority.HIGH),
            task(4L, priority = Priority.MEDIUM),
        )

        assertEquals(listOf(3L, 4L, 2L, 1L), ids(TaskSorting.byPriority(tasks)))
    }

    @Test
    fun priority_same_level_orders_by_due_date() {
        val tasks = listOf(
            task(1L, priority = Priority.HIGH, dueDate = null),
            task(2L, priority = Priority.HIGH, dueDate = LocalDate.parse("2026-12-02")),
            task(3L, priority = Priority.HIGH, dueDate = LocalDate.parse("2026-12-01")),
        )

        assertEquals(listOf(3L, 2L, 1L), ids(TaskSorting.byPriority(tasks)))
    }

    @Test
    fun created_sorts_newest_first_and_is_deterministic() {
        val tasks = listOf(
            task(1L, createdAt = created(1)),
            task(2L, createdAt = created(3)),
            task(3L, createdAt = created(3)),
            task(4L, createdAt = created(2)),
        )

        val first = TaskSorting.byCreated(tasks)
        assertTrue(
            "不同创建时间新→旧",
            ids(first).indexOf(4L) < ids(first).indexOf(1L),
        )
        assertEquals("重复调用结果一致", ids(first), ids(TaskSorting.byCreated(tasks)))
        assertEquals("不得丢失任务", setOf(1L, 2L, 3L, 4L), ids(first).toSet())
    }

    @Test
    fun manual_sorts_by_order_ascending_and_null_last() {
        val tasks = listOf(
            task(1L, manualOrder = null),
            task(2L, manualOrder = 2L),
            task(3L, manualOrder = 0L),
            task(4L, manualOrder = 1L),
        )

        assertEquals(listOf(3L, 4L, 2L, 1L), ids(TaskSorting.byManual(tasks)))
    }

    @Test
    fun manual_null_tasks_fall_back_to_created_desc() {
        val tasks = listOf(
            task(10L, createdAt = created(5), manualOrder = null),
            task(20L, createdAt = created(9), manualOrder = null),
            task(30L, createdAt = created(7), manualOrder = null),
        )

        // 无手动序：创建时间倒序（G9 用户口径“null 按 created 倒序”）。
        assertEquals(listOf(20L, 30L, 10L), ids(TaskSorting.byManual(tasks)))
    }

    @Test
    fun sorting_does_not_mutate_input_list() {
        val tasks = mutableListOf(
            task(1L, priority = Priority.NONE),
            task(2L, priority = Priority.HIGH),
        )
        val original = tasks.toList()

        TaskSorting.byDue(tasks)
        TaskSorting.byPriority(tasks)
        TaskSorting.byCreated(tasks)
        TaskSorting.byManual(tasks)

        assertEquals(original, tasks)
    }
}
