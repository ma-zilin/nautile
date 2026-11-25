package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.data.mapper.TaskMapper
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.repository.TaskRepository
import io.github.mazilin.nautile.domain.usecase.TaskCommandError
import io.github.mazilin.nautile.domain.usecase.TaskCommandResult
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.testing.FakeClock
import io.github.mazilin.nautile.testing.InMemoryProjectRepository
import io.github.mazilin.nautile.testing.InMemoryTaskRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * G5 门禁 · 新建任务字段写入（JVM，替身仓库）。
 *
 * 覆盖：priority/dueDate/dueTime/manualOrder 在一次 insert 内写入；默认值保持 NONE/null；
 * 既有校验顺序（标题 → 目标位置 → 项目）不变，校验失败不写库；映射往返保留 manualOrder。
 */
class G5CreateFieldsTest {

    private val now: Instant = Instant.parse("2026-11-25T02:00:00Z")

    private lateinit var delegate: InMemoryTaskRepository
    private lateinit var taskRepository: CountingTaskRepository
    private lateinit var projectRepository: InMemoryProjectRepository
    private lateinit var commands: TaskCommands

    @Before
    fun setUp() {
        delegate = InMemoryTaskRepository()
        taskRepository = CountingTaskRepository(delegate)
        projectRepository = InMemoryProjectRepository()
        commands = TaskCommands(taskRepository, projectRepository, FakeClock(now))
    }

    private suspend fun insertProject(status: ProjectStatus = ProjectStatus.ACTIVE): ProjectId =
        projectRepository.insert(
            Project(
                id = ProjectId(0L),
                name = "课程项目",
                status = status,
                createdAt = now,
                updatedAt = now,
            ),
        )

    @Test
    fun g5_01_create_writes_priority_due_and_manual_order_in_single_insert() = runTest {
        val dueDate = LocalDate.parse("2026-11-30")
        val dueTime = LocalTime.of(18, 30)

        val result = commands.create(
            title = "写报告",
            position = GtdPosition.NEXT_ACTION,
            priority = Priority.HIGH,
            dueDate = dueDate,
            dueTime = dueTime,
            manualOrder = 7L,
        )

        val created = (result as TaskCommandResult.Success).task
        assertEquals(Priority.HIGH, created.priority)
        assertEquals(dueDate, created.dueDate)
        assertEquals(dueTime, created.dueTime)
        assertEquals(7L, created.manualOrder)
        assertEquals("一次写入：insert 只应调用一次", 1, taskRepository.insertCount)
        assertEquals("入库任务应等于返回值", created, requireNotNull(taskRepository.get(created.id)))
    }

    @Test
    fun g5_02_create_defaults_priority_none_and_null_due_and_manual_order() = runTest {
        val result = commands.create("默认任务", GtdPosition.INBOX)

        val created = (result as TaskCommandResult.Success).task
        assertEquals(Priority.NONE, created.priority)
        assertNull(created.dueDate)
        assertNull(created.dueTime)
        assertNull(created.manualOrder)
        assertEquals(1, taskRepository.insertCount)

        val stored = requireNotNull(taskRepository.get(created.id))
        assertNull("未手动排序的任务入库 manualOrder 必须为 null", stored.manualOrder)
    }

    @Test
    fun g5_03_create_keeps_validation_order_and_writes_nothing_on_failure() = runTest {
        val closedProject = insertProject(ProjectStatus.COMPLETED)

        val blank = commands.create("   ", GtdPosition.ENDED, projectId = closedProject)
        assertEquals(TaskCommandResult.Invalid(TaskCommandError.BLANK_TITLE), blank)

        val ended = commands.create("任务", GtdPosition.ENDED, projectId = closedProject)
        assertEquals(
            "目标位置校验应先于项目状态提示",
            TaskCommandResult.Invalid(TaskCommandError.TARGET_ENDED),
            ended,
        )

        val missingProject = commands.create("任务", GtdPosition.INBOX, projectId = ProjectId(999L))
        assertEquals(TaskCommandResult.Invalid(TaskCommandError.PROJECT_NOT_FOUND), missingProject)

        assertEquals("校验失败不得写库", 0, taskRepository.insertCount)
        assertTrue(delegate.observeAll().first().isEmpty())
    }

    @Test
    fun g5_04_mapper_roundtrip_keeps_manual_order() {
        val dueDate = LocalDate.parse("2026-11-30")
        val dueTime = LocalTime.of(8, 15)
        val ordered = Task(
            id = TaskId(3L),
            title = "任务",
            position = GtdPosition.WAITING,
            priority = Priority.MEDIUM,
            dueDate = dueDate,
            dueTime = dueTime,
            createdAt = now,
            updatedAt = now,
            manualOrder = 12L,
        )

        assertEquals(ordered, TaskMapper.toDomain(TaskMapper.toEntity(ordered)))
        assertNull(
            "manualOrder 为 null 时往返仍为 null",
            TaskMapper.toDomain(TaskMapper.toEntity(ordered.copy(manualOrder = null))).manualOrder,
        )
    }

    /** 只统计 insert 次数的委托仓库；其余行为沿用内存替身。 */
    private class CountingTaskRepository(private val delegate: TaskRepository) : TaskRepository {

        var insertCount: Int = 0
            private set

        override fun observeByPosition(position: GtdPosition): Flow<List<Task>> =
            delegate.observeByPosition(position)

        override fun observeAll(): Flow<List<Task>> = delegate.observeAll()

        override suspend fun get(id: TaskId): Task? = delegate.get(id)

        override suspend fun insert(task: Task): TaskId {
            insertCount++
            return delegate.insert(task)
        }

        override suspend fun update(task: Task) = delegate.update(task)

        override suspend fun delete(id: TaskId) = delegate.delete(id)

        override suspend fun deleteAll(ids: List<TaskId>) = delegate.deleteAll(ids)
    }
}
