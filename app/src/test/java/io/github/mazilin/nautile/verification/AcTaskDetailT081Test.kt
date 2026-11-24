package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommandResult
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.domain.usecase.TaskFieldUpdate
import io.github.mazilin.nautile.testing.FakeClock
import io.github.mazilin.nautile.testing.FakeSettingsRepository
import io.github.mazilin.nautile.testing.InMemoryProjectRepository
import io.github.mazilin.nautile.testing.InMemoryTaskRepository
import io.github.mazilin.nautile.ui.screen.todo.TodoViewModel
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T-081/FR-D-03、AC-D-09 的任务详情字段与草稿保存验证。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AcTaskDetailT081Test {

    private val now: Instant = Instant.parse("2026-11-23T12:00:00Z")
    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var taskRepository: InMemoryTaskRepository
    private lateinit var projectRepository: InMemoryProjectRepository
    private lateinit var taskCommands: TaskCommands
    private lateinit var viewModel: TodoViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        taskRepository = InMemoryTaskRepository()
        projectRepository = InMemoryProjectRepository(taskRepository)
        taskCommands = TaskCommands(taskRepository, projectRepository, FakeClock(now))
        viewModel = TodoViewModel(
            taskRepository = taskRepository,
            projectRepository = projectRepository,
            settingsRepository = FakeSettingsRepository(),
            taskCommands = taskCommands,
            projectCommands = ProjectCommands(projectRepository, taskRepository, FakeClock(now)),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun insertTask(detail: String? = null): TaskId =
        taskRepository.insert(
            Task(
                id = TaskId(0L),
                title = "任务",
                position = GtdPosition.INBOX,
                detail = detail,
                createdAt = now,
                updatedAt = now,
            ),
        )

    @Test
    fun AC_D_09_blank_detail_is_normalized_to_null_on_create() = runTest {
        val result = taskCommands.create("任务", GtdPosition.INBOX, detail = "   ")

        val task = (result as TaskCommandResult.Success).task
        assertNull(task.detail)
        assertNull(taskRepository.get(task.id)?.detail)
    }

    @Test
    fun AC_D_09_detail_is_saved_and_blank_update_becomes_null() = runTest {
        val id = insertTask()

        taskCommands.updateField(id, TaskFieldUpdate.Detail("  第一行\n第二行  "))
        assertEquals("  第一行\n第二行  ", taskRepository.get(id)?.detail)

        taskCommands.updateField(id, TaskFieldUpdate.Detail("   "))
        assertNull(taskRepository.get(id)?.detail)
    }

    @Test
    fun AC_D_09_detail_draft_and_title_require_two_leave_steps_then_close() = runTest {
        val id = insertTask(detail = "旧详情")
        viewModel.openTaskDetail(id)
        viewModel.onTaskTitleChanged("新标题")
        viewModel.onTaskDetailChanged("新详情")

        assertTrue("标题草稿应先阻止离开", !viewModel.requestDetailLeave())
        assertTrue("详情草稿应继续阻止离开", !viewModel.requestDetailLeave())
        assertEquals("新标题", taskRepository.get(id)?.title)
        assertEquals("新详情", taskRepository.get(id)?.detail)
        assertTrue("两个草稿保存后允许关闭", viewModel.requestDetailLeave())
    }

    @Test
    fun AC_D_09_old_task_with_null_detail_opens_with_empty_display() = runTest {
        val id = insertTask(detail = null)
        viewModel.openTaskDetail(id)

        val detail = viewModel.taskDetail.first()

        assertEquals("", detail?.detailValue)
        assertNull(taskRepository.get(id)?.detail)
    }

    @Test
    fun T_088_invalid_field_blocks_leave_and_prompts() = runTest {
        val id = insertTask(detail = "旧详情")
        viewModel.openTaskDetail(id)
        viewModel.onTaskTitleChanged("   ")

        val allowed = viewModel.requestDetailLeave()
        val detail = viewModel.taskDetail.first()

        assertFalse("无效标题应阻止离开", allowed)
        assertTrue("应标记离开被阻止", detail?.leaveBlocked == true)
        assertEquals("请先修正无效标题", detail?.error)
        assertEquals("旧详情", taskRepository.get(id)?.detail)
    }
}
