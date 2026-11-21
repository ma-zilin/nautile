package io.github.mazilin.nautile.contract

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.repository.RoomProjectRepository
import io.github.mazilin.nautile.data.repository.RoomTaskRepository
import io.github.mazilin.nautile.domain.repository.ProjectRepository
import io.github.mazilin.nautile.domain.repository.TaskRepository
import org.junit.Before
import org.junit.runner.RunWith

/**
 * Room 任务仓库契约实现（T-051；AC-T-01、AC-T-29）。
 *
 * 与内存替身共享 [TaskRepositoryContract] 的同一组断言；本类需要 instrumentation，
 * 未连真机时由门禁标“未执行”，不得写成已通过。
 */
@RunWith(AndroidJUnit4::class)
class RoomTaskRepositoryContractTest : TaskRepositoryContract() {

    private lateinit var database: NautileDatabase

    override lateinit var tasks: TaskRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NautileDatabase::class.java,
        ).allowMainThreadQueries().build()
        tasks = RoomTaskRepository(database)
    }

    override fun close() {
        database.close()
    }
}

/**
 * Room 项目仓库契约实现（T-051；AC-T-01、AC-T-17）。
 *
 * 与内存替身共享 [ProjectRepositoryContract] 的同一组断言；未连真机时标“未执行”。
 */
@RunWith(AndroidJUnit4::class)
class RoomProjectRepositoryContractTest : ProjectRepositoryContract() {

    private lateinit var database: NautileDatabase

    override lateinit var tasks: TaskRepository

    override lateinit var projects: ProjectRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NautileDatabase::class.java,
        ).allowMainThreadQueries().build()
        tasks = RoomTaskRepository(database)
        projects = RoomProjectRepository(database)
    }

    override fun close() {
        database.close()
    }
}
