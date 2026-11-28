package io.github.mazilin.nautile.contract

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.repository.RoomTimeCategoryRepository
import io.github.mazilin.nautile.data.repository.RoomTimeLogRepository
import io.github.mazilin.nautile.domain.repository.TimeCategoryRepository
import io.github.mazilin.nautile.domain.repository.TimeLogRepository
import org.junit.Before
import org.junit.runner.RunWith

/**
 * Room 日志仓库契约实现（R6-G4-V2；AC-L-02、AC-T-21/22）。
 *
 * 与内存替身共享同一组断言；使用内存库，不污染实际数据库。
 * 开始/结束计时的 `withTransaction` 原子性由真实 Room 实现验证。
 */
@RunWith(AndroidJUnit4::class)
class RoomTimeLogRepositoryContractTest : TimeLogRepositoryContract() {

    private lateinit var database: NautileDatabase

    override lateinit var logs: TimeLogRepository

    override lateinit var categories: TimeCategoryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NautileDatabase::class.java,
        ).allowMainThreadQueries().build()
        logs = RoomTimeLogRepository(database)
        categories = RoomTimeCategoryRepository(database)
    }

    override fun close() {
        database.close()
    }
}

/**
 * Room 分类仓库契约实现（R6-G4-V2；AC-L-10、AC-L-18）。
 *
 * 删除守卫在同一事务内查子级/日志；与内存替身共享同一组断言。
 */
@RunWith(AndroidJUnit4::class)
class RoomTimeCategoryRepositoryContractTest : TimeCategoryRepositoryContract() {

    private lateinit var database: NautileDatabase

    override lateinit var logs: TimeLogRepository

    override lateinit var categories: TimeCategoryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NautileDatabase::class.java,
        ).allowMainThreadQueries().build()
        logs = RoomTimeLogRepository(database)
        categories = RoomTimeCategoryRepository(database)
    }

    override fun close() {
        database.close()
    }
}
