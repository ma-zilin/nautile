package io.github.mazilin.nautile.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.rule.TaskRules
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S1-T05 RoomTaskRepository 测试（AC-T-01、AC-G-03 的持久化路径）。
 * 需要真机/模拟器；无人到场时不执行，报告中标注“未执行”。
 */
@RunWith(AndroidJUnit4::class)
class RoomTaskRepositoryTest {

    private val now: Instant = Instant.parse("2026-11-20T02:00:00Z")
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

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

    private fun task(title: String, position: GtdPosition = GtdPosition.INBOX) = Task(
        id = TaskId(0L),
        title = title,
        position = position,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun acT01_insert_and_observe_by_position() = runBlocking {
        val id = repository.insert(task("收件箱任务"))
        repository.insert(task("等待任务", GtdPosition.WAITING))

        assertEquals(listOf("收件箱任务"), repository.observeByPosition(GtdPosition.INBOX).first().map { it.title })
        assertEquals(id, repository.get(id)?.id)
        assertEquals(2, repository.observeAll().first().size)
    }

    @Test
    fun acT01_complete_and_reopen_persist_through_repository() = runBlocking {
        val id = repository.insert(task("写周报", GtdPosition.WAITING))

        val closed = TaskRules.close(requireNotNull(repository.get(id)), ClosedOutcome.COMPLETED, now, zone)
        repository.update(closed)
        assertEquals(GtdPosition.ENDED, repository.get(id)?.position)
        assertEquals(ClosedOutcome.COMPLETED, repository.get(id)?.closedOutcome)

        val reopened = TaskRules.reopen(requireNotNull(repository.get(id)), now)
        repository.update(reopened)
        assertEquals(GtdPosition.WAITING, repository.get(id)?.position)
        assertNull(repository.get(id)?.closedOutcome)
    }

    @Test
    fun acT01_delete_and_delete_all_remove_tasks() = runBlocking {
        val first = repository.insert(task("任务一"))
        val second = repository.insert(task("任务二"))

        repository.delete(first)
        assertNull(repository.get(first))

        repository.deleteAll(listOf(second))
        assertEquals(0, repository.observeAll().first().size)
    }
}
