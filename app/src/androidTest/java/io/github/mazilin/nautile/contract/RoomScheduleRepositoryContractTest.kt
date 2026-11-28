package io.github.mazilin.nautile.contract

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.repository.RoomScheduleRepository
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.repository.ScheduleRepository
import org.junit.Before
import org.junit.runner.RunWith

/**
 * Room 日程仓库契约实现（R6-G2-V1；AC-S-03/AC-S-04 数据层）。
 *
 * 与内存替身共享 [ScheduleRepositoryContract] 的同一组断言；
 * 使用内存库，不污染实际数据库。模拟器执行，不等同真机结论。
 */
@RunWith(AndroidJUnit4::class)
class RoomScheduleRepositoryContractTest : ScheduleRepositoryContract() {

    private lateinit var database: NautileDatabase

    override lateinit var schedules: ScheduleRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NautileDatabase::class.java,
        ).allowMainThreadQueries().build()
        schedules = RoomScheduleRepository(database)
    }

    /** 与 MIGRATION_3_4 的种子一致：系统“课程表”标签 id=1。 */
    override suspend fun ensureCourseTag(): ScheduleTagId {
        database.openHelper.writableDatabase.execSQL(
            "INSERT OR IGNORE INTO schedule_tags " +
                "(id, name, system_key, sort_order, created_at, updated_at) " +
                "VALUES (1, '课程表', 'COURSE', 0, 0, 0)",
        )
        return ScheduleTagId(1L)
    }

    override fun close() {
        database.close()
    }
}
