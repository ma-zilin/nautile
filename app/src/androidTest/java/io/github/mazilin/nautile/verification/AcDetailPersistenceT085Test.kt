package io.github.mazilin.nautile.verification

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.repository.RoomTaskRepository
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T-085 详情编辑补测：文件型 Room 关闭重开后详情仍保持（AC-D-09、FR-D-03）。
 *
 * 说明：本用例验证设备上真实数据库文件的持久化，不等同于整进程重启的 UI 恢复；
 * 整进程重启后的界面恢复仍列入未自动化人工清单。
 */
@RunWith(AndroidJUnit4::class)
class AcDetailPersistenceT085Test {

    private val now: Instant = Instant.parse("2026-11-24T00:00:00Z")

    @Test
    fun AC_D_09_detail_survives_database_reopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "t085-detail-persistence.db"
        context.deleteDatabase(name)
        try {
            var database = Room.databaseBuilder(
                context,
                NautileDatabase::class.java,
                name,
            ).build()
            var repository = RoomTaskRepository(database)
            val detailId = repository.insert(
                Task(
                    id = TaskId(0L),
                    title = "持久任务",
                    position = GtdPosition.INBOX,
                    detail = "第一行\n第二行",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            val blankId = repository.insert(
                Task(
                    id = TaskId(0L),
                    title = "空白详情",
                    position = GtdPosition.INBOX,
                    detail = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.close()

            database = Room.databaseBuilder(
                context,
                NautileDatabase::class.java,
                name,
            ).build()
            repository = RoomTaskRepository(database)

            assertEquals("关闭重开后非空详情应保持", "第一行\n第二行", repository.get(detailId)?.detail)
            assertNull("关闭重开后空详情仍为 null", repository.get(blankId)?.detail)
            database.close()
        } finally {
            context.deleteDatabase(name)
        }
    }
}
