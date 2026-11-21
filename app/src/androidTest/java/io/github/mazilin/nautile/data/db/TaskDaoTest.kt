package io.github.mazilin.nautile.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.dao.TaskDao
import io.github.mazilin.nautile.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** S1-T04 TaskDao 测试（AC-T-01 支撑）；真机未执行时按报告标注“未执行”。 */
@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    private lateinit var database: NautileDatabase
    private lateinit var tasks: TaskDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NautileDatabase::class.java,
        ).allowMainThreadQueries().build()
        tasks = database.taskDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun task(
        title: String,
        position: String = "INBOX",
    ) = TaskEntity(
        title = title,
        position = position,
        priority = "NONE",
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun AC_T_01_insert_and_observe_by_position() = runBlocking {
        tasks.insert(task("收件箱任务", "INBOX"))
        tasks.insert(task("等待任务", "WAITING"))

        assertEquals(
            listOf("收件箱任务"),
            tasks.observeByPosition("INBOX").first().map { it.title },
        )
        assertEquals(2, tasks.observeAll().first().size)
    }

    @Test
    fun AC_T_01_update_title_and_close_fields() = runBlocking {
        val id = tasks.insert(task("写周报"))
        val stored = requireNotNull(tasks.get(id))

        tasks.update(
            stored.copy(
                title = "写季报",
                position = "ENDED",
                positionBeforeClose = "INBOX",
                closedOutcome = "COMPLETED",
                closedAt = 1_000L,
                closedOn = "2026-11-19",
            ),
        )

        val updated = requireNotNull(tasks.get(id))
        assertEquals("写季报", updated.title)
        assertEquals("ENDED", updated.position)
        assertEquals("INBOX", updated.positionBeforeClose)
        assertEquals("COMPLETED", updated.closedOutcome)
        assertEquals("2026-11-19", updated.closedOn)
    }

    @Test
    fun AC_T_01_delete_single_and_by_ids() = runBlocking {
        val first = tasks.insert(task("任务一"))
        val second = tasks.insert(task("任务二"))
        val third = tasks.insert(task("任务三"))

        tasks.delete(first)
        assertNull(tasks.get(first))

        tasks.deleteByIds(listOf(second, third))
        assertEquals(0, tasks.observeAll().first().size)
    }
}
