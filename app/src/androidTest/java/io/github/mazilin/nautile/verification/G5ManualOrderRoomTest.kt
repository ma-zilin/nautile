package io.github.mazilin.nautile.verification

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.repository.RoomTaskRepository
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G5 门禁 · Room 手动排序写入（instrumentation，内存库）。
 *
 * 覆盖 CMP-40.2 的数据部分：批量写入一个事务内完成（调用方只调一次）、
 * 只改 manual_order、不改其他列；空映射不写库；重复提交幂等。
 * 事务失败回滚需要故障注入，本轮未自动化，报告中单列。
 */
@RunWith(AndroidJUnit4::class)
class G5ManualOrderRoomTest {

    private val now: Instant = Instant.parse("2026-11-25T02:00:00Z")

    private lateinit var database: NautileDatabase
    private lateinit var repository: RoomTaskRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NautileDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomTaskRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun task(
        title: String,
        manualOrder: Long? = null,
        priority: Priority = Priority.MEDIUM,
        dueDate: LocalDate? = LocalDate.parse("2026-12-01"),
        dueTime: LocalTime? = LocalTime.of(9, 0),
    ) = Task(
        id = TaskId(0L),
        title = title,
        position = GtdPosition.NEXT_ACTION,
        priority = priority,
        dueDate = dueDate,
        dueTime = dueTime,
        createdAt = now,
        updatedAt = now,
        manualOrder = manualOrder,
    )

    @Test
    fun cmp40_2_update_manual_orders_writes_all_rows_and_only_that_column() = runBlocking {
        val first = repository.insert(task("任务一"))
        val second = repository.insert(task("任务二"))
        val third = repository.insert(task("任务三"))
        val before = listOf(first, second, third).associateWith { requireNotNull(repository.get(it)) }

        repository.updateManualOrders(
            mapOf(
                third to 0L,
                first to 1L,
                second to 2L,
            ),
        )

        assertEquals(0L, repository.get(third)?.manualOrder)
        assertEquals(1L, repository.get(first)?.manualOrder)
        assertEquals(2L, repository.get(second)?.manualOrder)

        listOf(first, second, third).forEach { id ->
            val after = requireNotNull(repository.get(id))
            assertEquals("只改 manual_order", requireNotNull(before[id]).copy(manualOrder = after.manualOrder), after)
        }
        // observeAll 不保证按 manual_order 排序，按 id 核对写入值，避免把查询顺序当成排序结果。
        assertEquals(
            mapOf(first to 1L, second to 2L, third to 0L),
            repository.observeAll().first().associate { it.id to it.manualOrder },
        )
    }

    @Test
    fun cmp40_2_update_manual_orders_is_idempotent_and_empty_is_noop() = runBlocking {
        val first = repository.insert(task("任务一", manualOrder = 5L))
        val second = repository.insert(task("任务二"))

        repository.updateManualOrders(emptyMap())
        assertEquals(5L, repository.get(first)?.manualOrder)
        assertNull(repository.get(second)?.manualOrder)

        val orders = mapOf(first to 1L, second to 0L)
        repository.updateManualOrders(orders)
        repository.updateManualOrders(orders)

        assertEquals(1L, repository.get(first)?.manualOrder)
        assertEquals(0L, repository.get(second)?.manualOrder)
    }

    @Test
    fun cmp40_1_insert_keeps_manual_order_null_unless_given() = runBlocking {
        val unsorted = repository.insert(task("未排序"))
        val sorted = repository.insert(task("已排序", manualOrder = 9L))

        assertNull("未指定 manualOrder 入库应为 null", repository.get(unsorted)?.manualOrder)
        assertEquals(9L, repository.get(sorted)?.manualOrder)
    }
}
