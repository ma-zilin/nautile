package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.rule.TaskSorting
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G5 门禁 · CMP-40 展示排序纯函数（JVM）。
 *
 * 预期依据：设计 §14.3——排序只改变展示顺序、不写任务字段；空值排在有值时之后；
 * 同一层级的结果必须确定（稳定），输入列表不被修改。
 * 不相关注同级内部的次级次序用“截止时刻还是创建时间”这一设计草案细节，
 * 只断言层级和确定性，避免用实现反推预期。
 */
class G5TaskSortingTest {

    private val base: Instant = Instant.parse("2026-11-20T00:00:00Z")

    private fun task(
        id: Long,
        priority: Priority = Priority.NONE,
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
        createdAt: Instant = base.plusSeconds(id),
        manualOrder: Long? = null,
    ): Task = Task(
        id = TaskId(id),
        title = "任务$id",
        position = GtdPosition.INBOX,
        priority = priority,
        dueDate = dueDate,
        dueTime = dueTime,
        createdAt = createdAt,
        updatedAt = createdAt,
        manualOrder = manualOrder,
    )

    @Test
    fun cmp40_01_byDue_puts_undated_last_and_orders_dates_ascending() {
        val input = listOf(
            task(1, dueDate = LocalDate.parse("2026-11-25")),
            task(2),
            task(3, dueDate = LocalDate.parse("2026-11-22")),
            task(4, dueDate = LocalDate.parse("2026-11-25"), dueTime = LocalTime.of(9, 0)),
        )

        val result = TaskSorting.byDue(input)
        val dueDates = result.map { it.dueDate }

        assertEquals("无日期任务必须全部末位", 1, dueDates.count { it == null })
        assertEquals(TaskId(2), result.last().id)
        val dated = dueDates.filterNotNull()
        assertEquals(
            listOf(LocalDate.parse("2026-11-22"), LocalDate.parse("2026-11-25"), LocalDate.parse("2026-11-25")),
            dated,
        )
        assertEquals("不得丢失或新增任务", input.map { it.id }.toSet(), result.map { it.id }.toSet())
    }

    @Test
    fun cmp40_02_byDue_is_deterministic_and_keeps_input_unchanged() {
        val input = listOf(
            task(1, dueDate = LocalDate.parse("2026-11-24")),
            task(2),
            task(3, dueDate = LocalDate.parse("2026-11-24")),
            task(4, dueDate = LocalDate.parse("2026-11-23")),
        )
        val snapshot = input.toList()

        val first = TaskSorting.byDue(input)
        val second = TaskSorting.byDue(input)

        assertEquals("输入列表顺序与元素不得被改写", snapshot, input)
        assertNotSame("排序应返回新列表", input, first)
        assertEquals("重复调用结果必须一致", first.map { it.id }, second.map { it.id })
        assertEquals(input.map { it.id }.toSet(), first.map { it.id }.toSet())
    }

    @Test
    fun cmp40_03_byPriority_orders_high_medium_low_none() {
        val input = listOf(
            task(1, Priority.NONE),
            task(2, Priority.LOW),
            task(3, Priority.HIGH),
            task(4, Priority.MEDIUM),
        )

        val result = TaskSorting.byPriority(input)

        assertEquals(
            listOf(Priority.HIGH, Priority.MEDIUM, Priority.LOW, Priority.NONE),
            result.map { it.priority },
        )
        assertEquals(input.map { it.id }.toSet(), result.map { it.id }.toSet())
    }

    @Test
    fun cmp40_04_byPriority_keeps_same_level_stable_and_input_unchanged() {
        val input = listOf(
            task(1, Priority.HIGH),
            task(2, Priority.HIGH),
            task(3, Priority.LOW),
            task(4, Priority.HIGH),
        )
        val snapshot = input.toList()

        val first = TaskSorting.byPriority(input)
        val second = TaskSorting.byPriority(input)

        assertEquals("同级任务必须保持确定次序", first.map { it.id }, second.map { it.id })
        assertEquals(
            listOf(Priority.HIGH, Priority.HIGH, Priority.HIGH, Priority.LOW),
            first.map { it.priority },
        )
        assertEquals("输入列表顺序与元素不得被改写", snapshot, input)
    }

    @Test
    fun cmp40_05_byCreated_orders_newest_first_deterministically() {
        val input = listOf(
            task(1, createdAt = base.plusSeconds(10)),
            task(2, createdAt = base.plusSeconds(30)),
            task(3, createdAt = base.plusSeconds(20)),
        )
        val snapshot = input.toList()

        val result = TaskSorting.byCreated(input)

        assertEquals(listOf(TaskId(2), TaskId(3), TaskId(1)), result.map { it.id })
        assertEquals(snapshot, input)
        assertEquals(result.map { it.id }, TaskSorting.byCreated(input).map { it.id })
    }

    @Test
    fun cmp40_06_byManual_orders_ordered_tasks_ascending_and_keeps_unsorted_last() {
        val input = listOf(
            task(1),
            task(2, manualOrder = 5L),
            task(3, manualOrder = 0L),
            task(4),
            task(5, manualOrder = 2L),
        )

        val result = TaskSorting.byManual(input)

        assertEquals(
            "已排序任务必须按 manualOrder 升序",
            listOf(TaskId(3), TaskId(5), TaskId(2)),
            result.take(3).map { it.id },
        )
        assertTrue("未排序（null）任务必须全部末位", result.drop(3).all { it.manualOrder == null })
        assertEquals(2, result.drop(3).size)
        assertEquals(
            "manualOrder 序列必须非降",
            result.mapNotNull { it.manualOrder }.sorted(),
            result.mapNotNull { it.manualOrder },
        )
        assertEquals(input.map { it.id }.toSet(), result.map { it.id }.toSet())
    }

    @Test
    fun cmp40_07_byManual_is_deterministic_and_keeps_input_unchanged() {
        val input = listOf(
            task(1),
            task(2, manualOrder = 1L),
            task(3),
            task(4, manualOrder = 1L),
        )
        val snapshot = input.toList()

        val first = TaskSorting.byManual(input)
        val second = TaskSorting.byManual(input)

        assertEquals("同值 manualOrder 必须保持确定次序", first.map { it.id }, second.map { it.id })
        assertEquals("输入列表顺序与元素不得被改写", snapshot, input)
        assertNotSame("排序应返回新列表", input, first)
    }

    @Test
    fun cmp40_08_all_sorters_only_reorder_existing_task_objects() {
        val input = listOf(
            task(1, Priority.LOW, dueDate = LocalDate.parse("2026-11-26"), manualOrder = 30L),
            task(2, Priority.HIGH),
            task(3, Priority.NONE, dueDate = LocalDate.parse("2026-11-21")),
        )
        val snapshotById = input.associateBy { it.id }

        listOf(
            TaskSorting.byDue(input),
            TaskSorting.byPriority(input),
            TaskSorting.byCreated(input),
            TaskSorting.byManual(input),
        ).forEach { sorted ->
            assertEquals("排序不得丢失或新增任务", input.size, sorted.size)
            sorted.forEach { item ->
                assertEquals("排序不得改写任何任务字段", snapshotById[item.id], item)
            }
            assertEquals(input.map { it.id }.toSet(), sorted.map { it.id }.toSet())
        }
    }
}
