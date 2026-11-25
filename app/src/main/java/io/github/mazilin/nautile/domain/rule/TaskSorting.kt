package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Task
import java.time.LocalDate
import java.time.LocalTime

/**
 * G9 视图排序纯函数（CMP-40）。
 *
 * 只改展示顺序，不写任何字段；输入列表不被修改，统一返回新列表。
 * 每种排序都给定稳定次序：主键相同时依次用创建时间、任务 ID 兜底，避免同值随机抖动。
 */
object TaskSorting {

    /** 截止日期排序：有日期在前、近→远；无日期末位；同日按时刻（无时刻末位）；再按创建时间升序。 */
    fun byDue(tasks: List<Task>): List<Task> = tasks.sortedWith(
        byDueDateAscNullLast
            .thenComparing(byDueTimeAscNullLast)
            .thenComparing(byCreatedAtAsc)
            .thenComparing(byIdAsc),
    )

    /** 优先级排序：高→中→低→无；同级按截止日期/时刻，再按创建时间升序。 */
    fun byPriority(tasks: List<Task>): List<Task> = tasks.sortedWith(
        byPriorityRank
            .thenComparing(byDueDateAscNullLast)
            .thenComparing(byDueTimeAscNullLast)
            .thenComparing(byCreatedAtAsc)
            .thenComparing(byIdAsc),
    )

    /** 创建时间排序：新→旧；同一时刻按任务 ID 升序保证稳定。 */
    fun byCreated(tasks: List<Task>): List<Task> = tasks.sortedWith(
        byCreatedAtDesc
            .thenComparing(byIdAsc),
    )

    /** 手动排序：null（未排序）置末；非 null 升序；同值/null 内按创建时间倒序，再按 ID 倒序。 */
    fun byManual(tasks: List<Task>): List<Task> = tasks.sortedWith(
        byManualOrderAsc
            .thenComparing(byCreatedAtDesc)
            .thenComparing(byIdDesc),
    )

    private val byDueDateAscNullLast: Comparator<Task> = Comparator { a, b ->
        compareNullsLast(a.dueDate, b.dueDate)
    }

    private val byDueTimeAscNullLast: Comparator<Task> = Comparator { a, b ->
        compareNullsLast(a.dueTime, b.dueTime)
    }

    private val byCreatedAtAsc: Comparator<Task> = Comparator { a, b ->
        a.createdAt.compareTo(b.createdAt)
    }

    private val byCreatedAtDesc: Comparator<Task> = Comparator { a, b ->
        b.createdAt.compareTo(a.createdAt)
    }

    private val byIdAsc: Comparator<Task> = Comparator { a, b ->
        a.id.value.compareTo(b.id.value)
    }

    private val byIdDesc: Comparator<Task> = Comparator { a, b ->
        b.id.value.compareTo(a.id.value)
    }

    private val byManualOrderAsc: Comparator<Task> = Comparator { a, b ->
        compareNullsLast(a.manualOrder, b.manualOrder)
    }

    /** 高→中→低→无：用固定名次而非枚举声明顺序，避免枚举调整改变排序语义。 */
    private val byPriorityRank: Comparator<Task> = Comparator { a, b ->
        rank(a.priority).compareTo(rank(b.priority))
    }

    private fun rank(priority: Priority): Int = when (priority) {
        Priority.HIGH -> 0
        Priority.MEDIUM -> 1
        Priority.LOW -> 2
        Priority.NONE -> 3
    }

    private fun <T : Comparable<T>> compareNullsLast(a: T?, b: T?): Int = when {
        a == null && b == null -> 0
        a == null -> 1
        b == null -> -1
        else -> a.compareTo(b)
    }
}
