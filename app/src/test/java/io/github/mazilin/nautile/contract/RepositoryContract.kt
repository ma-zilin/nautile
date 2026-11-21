package io.github.mazilin.nautile.contract

import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.repository.ProjectRepository
import io.github.mazilin.nautile.domain.repository.TaskRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务仓库契约（T-051；AC-T-01、AC-T-29）。
 *
 * 同一组断言由内存替身与 Room 实现共同继承；本类不依赖 Android。
 * 具体实现通过 [tasks] 与 [close] 注入。
 */
abstract class TaskRepositoryContract {

    protected abstract val tasks: TaskRepository

    protected open fun close() = Unit

    private val now: Instant = Instant.parse("2026-11-21T00:00:00Z")

    @After
    fun tearDownContract() {
        close()
    }

    protected fun task(
        title: String,
        position: GtdPosition = GtdPosition.INBOX,
        id: TaskId = TaskId(0L),
        projectId: ProjectId? = null,
        positionBeforeClose: GtdPosition? = null,
        closedOutcome: ClosedOutcome? = null,
    ): Task = Task(
        id = id,
        title = title,
        projectId = projectId,
        position = position,
        positionBeforeClose = positionBeforeClose,
        closedOutcome = closedOutcome,
        closedAt = if (closedOutcome != null) now else null,
        closedOn = if (closedOutcome != null) LocalDate.of(2026, 11, 21) else null,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun AC_T_01_insert_assigns_id_and_reads_back() = runBlocking {
        val id = tasks.insert(task("收件箱任务"))

        assertTrue("新任务 id 应大于 0", id.value > 0L)
        assertEquals("收件箱任务", tasks.get(id)?.title)
    }

    @Test
    fun AC_T_01_observe_by_position_queries_all_five_positions() = runBlocking {
        GtdPosition.entries.forEach { position ->
            tasks.insert(
                task(
                    title = "位置-$position",
                    position = position,
                    closedOutcome = if (position == GtdPosition.ENDED) ClosedOutcome.COMPLETED else null,
                ),
            )
        }

        GtdPosition.entries.forEach { position ->
            assertEquals(
                "位置 $position 的查询结果不一致",
                setOf("位置-$position"),
                tasks.observeByPosition(position).first().map { it.title }.toSet(),
            )
        }
        assertEquals(5, tasks.observeAll().first().size)
    }

    @Test
    fun AC_T_01_update_persists_title_and_closed_fields() = runBlocking {
        val id = tasks.insert(task("写周报", position = GtdPosition.WAITING))

        tasks.update(
            task(
                title = "写季报",
                position = GtdPosition.ENDED,
                id = id,
                positionBeforeClose = GtdPosition.WAITING,
                closedOutcome = ClosedOutcome.COMPLETED,
            ),
        )

        val stored = tasks.get(id)
        assertEquals("写季报", stored?.title)
        assertEquals(GtdPosition.ENDED, stored?.position)
        assertEquals(GtdPosition.WAITING, stored?.positionBeforeClose)
        assertEquals(ClosedOutcome.COMPLETED, stored?.closedOutcome)
        assertEquals(LocalDate.of(2026, 11, 21), stored?.closedOn)
    }

    @Test
    fun AC_T_01_delete_and_delete_all_remove_tasks() = runBlocking {
        val first = tasks.insert(task("任务一"))
        val second = tasks.insert(task("任务二"))
        val third = tasks.insert(task("任务三"))

        tasks.delete(first)
        assertNull(tasks.get(first))

        tasks.deleteAll(listOf(second, third))
        assertTrue(tasks.observeAll().first().isEmpty())
    }

    @Test
    fun AC_T_29_reopen_update_clears_closed_fields_and_restores_position() = runBlocking {
        val id = tasks.insert(
            task(
                title = "重开任务",
                position = GtdPosition.ENDED,
                positionBeforeClose = GtdPosition.SOMEDAY,
                closedOutcome = ClosedOutcome.ABANDONED,
            ),
        )

        tasks.update(
            task(
                title = "重开任务",
                position = GtdPosition.SOMEDAY,
                id = id,
            ),
        )

        val stored = tasks.get(id)
        assertEquals(GtdPosition.SOMEDAY, stored?.position)
        assertNull(stored?.positionBeforeClose)
        assertNull(stored?.closedOutcome)
        assertNull(stored?.closedAt)
        assertNull(stored?.closedOn)
    }
}

/**
 * 项目仓库契约（T-051；AC-T-01、AC-T-17）。
 *
 * 同一组断言由内存替身与 Room 实现共同继承；本类不依赖 Android。
 */
abstract class ProjectRepositoryContract {

    protected abstract val projects: ProjectRepository

    protected abstract val tasks: TaskRepository

    protected open fun close() = Unit

    private val now: Instant = Instant.parse("2026-11-21T00:00:00Z")

    @After
    fun tearDownContract() {
        close()
    }

    protected fun project(name: String, id: ProjectId = ProjectId(0L)): Project = Project(
        id = id,
        name = name,
        status = ProjectStatus.ACTIVE,
        createdAt = now,
        updatedAt = now,
    )

    protected fun task(
        title: String,
        projectId: ProjectId?,
        position: GtdPosition = GtdPosition.INBOX,
        id: TaskId = TaskId(0L),
        closedOutcome: ClosedOutcome? = null,
    ): Task = Task(
        id = id,
        title = title,
        projectId = projectId,
        position = position,
        positionBeforeClose = if (closedOutcome != null) GtdPosition.INBOX else null,
        closedOutcome = closedOutcome,
        closedAt = if (closedOutcome != null) now else null,
        closedOn = if (closedOutcome != null) LocalDate.of(2026, 11, 21) else null,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun AC_T_17_insert_get_and_rename_project() = runBlocking {
        val id = projects.insert(project("课程项目"))

        assertTrue("新项目 id 应大于 0", id.value > 0L)
        assertEquals("课程项目", projects.get(id)?.name)

        projects.update(project("课程项目二", id = id))
        assertEquals("课程项目二", projects.get(id)?.name)
    }

    @Test
    fun AC_T_17_update_with_tasks_persists_project_and_children() = runBlocking {
        val projectId = projects.insert(project("课程项目"))
        val taskId = tasks.insert(task("子任务一", projectId))

        projects.updateWithTasks(
            project("课程项目", id = projectId).copy(status = ProjectStatus.COMPLETED),
            listOf(
                task(
                    title = "子任务一",
                    projectId = projectId,
                    position = GtdPosition.ENDED,
                    id = taskId,
                    closedOutcome = ClosedOutcome.COMPLETED,
                ),
            ),
        )

        assertEquals(ProjectStatus.COMPLETED, projects.get(projectId)?.status)
        assertEquals(GtdPosition.ENDED, tasks.get(taskId)?.position)
        assertEquals(ClosedOutcome.COMPLETED, tasks.get(taskId)?.closedOutcome)
    }

    @Test
    fun AC_T_17_delete_with_tasks_removes_project_and_children() = runBlocking {
        val projectId = projects.insert(project("课程项目"))
        val first = tasks.insert(task("子任务一", projectId))
        val second = tasks.insert(task("子任务二", projectId))

        projects.deleteWithTasks(projectId, listOf(first, second))

        assertNull(projects.get(projectId))
        assertNull(tasks.get(first))
        assertNull(tasks.get(second))
    }

    @Test
    fun AC_T_17_delete_empty_project() = runBlocking {
        val projectId = projects.insert(project("空项目"))

        projects.delete(projectId)

        assertNull(projects.get(projectId))
    }

    @Test
    fun AC_T_01_task_can_belong_to_project_and_position() = runBlocking {
        val projectId = projects.insert(project("课程项目"))
        val taskId = tasks.insert(task("项目任务", projectId, GtdPosition.NEXT_ACTION))

        val stored = tasks.get(taskId)
        assertEquals(projectId, stored?.projectId)
        assertEquals(GtdPosition.NEXT_ACTION, stored?.position)
        assertEquals(
            listOf("项目任务"),
            tasks.observeByPosition(GtdPosition.NEXT_ACTION).first().map { it.title },
        )
    }
}
