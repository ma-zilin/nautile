package io.github.mazilin.nautile.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.rule.ProjectRules
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
 * S1-T05 RoomProjectRepository 测试（AC-T-17 批量结项事务）。
 * 需要真机/模拟器；无人到场时不执行，报告中标注“未执行”。
 */
@RunWith(AndroidJUnit4::class)
class RoomProjectRepositoryTest {

    private val now: Instant = Instant.parse("2026-11-20T02:00:00Z")
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private lateinit var database: NautileDatabase
    private lateinit var projectRepository: RoomProjectRepository
    private lateinit var taskRepository: RoomTaskRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NautileDatabase::class.java,
        ).allowMainThreadQueries().build()
        projectRepository = RoomProjectRepository(database)
        taskRepository = RoomTaskRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertProject(name: String): ProjectId = projectRepository.insert(
        Project(
            id = ProjectId(0L),
            name = name,
            createdAt = now,
            updatedAt = now,
        ),
    )

    private suspend fun insertTask(title: String, projectId: ProjectId): TaskId = taskRepository.insert(
        Task(
            id = TaskId(0L),
            title = title,
            projectId = projectId,
            position = GtdPosition.INBOX,
            createdAt = now,
            updatedAt = now,
        ),
    )

    @Test
    fun acT17_batch_close_updates_project_and_children_in_one_call() = runBlocking {
        val projectId = insertProject("课程项目")
        val firstId = insertTask("子任务一", projectId)
        val secondId = insertTask("子任务二", projectId)
        val project = requireNotNull(projectRepository.get(projectId))
        val children = listOf(firstId, secondId).map { requireNotNull(taskRepository.get(it)) }

        val closedProject = ProjectRules.close(project, ClosedOutcome.COMPLETED, now)
        val closedChildren = children.map { TaskRules.close(it, ClosedOutcome.COMPLETED, now, zone) }
        projectRepository.updateWithTasks(closedProject, closedChildren)

        assertEquals(ProjectStatus.COMPLETED, projectRepository.get(projectId)?.status)
        assertEquals(listOf(GtdPosition.ENDED, GtdPosition.ENDED), children.map { taskRepository.get(it.id)?.position })
        assertEquals(
            listOf(ClosedOutcome.COMPLETED, ClosedOutcome.COMPLETED),
            children.map { taskRepository.get(it.id)?.closedOutcome },
        )
        assertEquals(1, projectRepository.observeAll().first().size)
    }

    @Test
    fun acT17_delete_with_tasks_removes_children_and_project() = runBlocking {
        val projectId = insertProject("课程项目")
        val firstId = insertTask("子任务一", projectId)
        val secondId = insertTask("子任务二", projectId)

        projectRepository.deleteWithTasks(projectId, listOf(firstId, secondId))

        assertNull(projectRepository.get(projectId))
        assertNull(taskRepository.get(firstId))
        assertNull(taskRepository.get(secondId))
        assertEquals(0, taskRepository.observeAll().first().size)
    }
}
