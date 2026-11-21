package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.interaction.BackDispatcher
import io.github.mazilin.nautile.interaction.KeyboardCoordinator
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.Overlay
import io.github.mazilin.nautile.interaction.OverlayBackStack
import io.github.mazilin.nautile.testing.FakeClock
import io.github.mazilin.nautile.testing.FakeSettingsRepository
import io.github.mazilin.nautile.testing.InMemoryProjectRepository
import io.github.mazilin.nautile.testing.InMemoryTaskRepository
import io.github.mazilin.nautile.ui.screen.todo.TodoViewModel
import java.time.Instant
import java.time.LocalDate
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
 * S1-T08/T09 详情、离开阻止与新建面板的 JVM 验证（T-064）。
 *
 * 通过 TodoViewModel 的公开状态/命令断言；不复制开发自测，不用真机替身。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AcDetailNewTaskT09Test {

    private val now: Instant = Instant.parse("2026-11-21T04:00:00Z")
    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var taskRepository: InMemoryTaskRepository
    private lateinit var projectRepository: InMemoryProjectRepository
    private lateinit var settings: FakeSettingsRepository
    private lateinit var clock: FakeClock
    private lateinit var viewModel: TodoViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        taskRepository = InMemoryTaskRepository()
        projectRepository = InMemoryProjectRepository(taskRepository)
        settings = FakeSettingsRepository()
        clock = FakeClock(now)
        viewModel = TodoViewModel(
            taskRepository = taskRepository,
            projectRepository = projectRepository,
            settingsRepository = settings,
            taskCommands = TaskCommands(taskRepository, projectRepository, clock),
            projectCommands = ProjectCommands(projectRepository, taskRepository, clock),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun insertTask(
        title: String,
        projectId: ProjectId? = null,
        position: GtdPosition = GtdPosition.INBOX,
        closed: Boolean = false,
    ): TaskId = taskRepository.insert(
        Task(
            id = TaskId(0L),
            title = title,
            projectId = projectId,
            position = if (closed) GtdPosition.ENDED else position,
            positionBeforeClose = if (closed) position else null,
            closedOutcome = if (closed) ClosedOutcome.COMPLETED else null,
            closedAt = if (closed) now else null,
            closedOn = if (closed) LocalDate.of(2026, 11, 21) else null,
            createdAt = now,
            updatedAt = now,
        ),
    )

    private suspend fun insertProject(status: ProjectStatus): ProjectId =
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
    fun AC_D_08_invalid_task_title_blocks_leave_and_keeps_draft() = runTest {
        val taskId = insertTask("旧标题")
        viewModel.openTaskDetail(taskId)
        viewModel.onTaskTitleChanged("   ")

        val allowed = viewModel.requestDetailLeave()
        val detail = viewModel.taskDetail.first()

        assertFalse(allowed)
        assertTrue(detail?.titleInvalid == true)
        assertEquals("   ", detail?.titleDraft)
        assertEquals("旧标题", taskRepository.get(taskId)?.title)
    }

    @Test
    fun AC_D_06_valid_task_title_commits_on_leave_then_allows_close() = runTest {
        val taskId = insertTask("旧标题")
        viewModel.openTaskDetail(taskId)
        viewModel.onTaskTitleChanged("新标题")

        assertFalse("首次离开应先保存并留在详情", viewModel.requestDetailLeave())
        assertEquals("新标题", taskRepository.get(taskId)?.title)
        assertTrue(viewModel.requestDetailLeave())
    }

    @Test
    fun AC_D_06_project_name_rename_commits_and_blank_blocks() = runTest {
        val projectId = insertProject(ProjectStatus.ACTIVE)
        viewModel.openProjectDetail(projectId)
        viewModel.onProjectNameChanged("   ")

        assertFalse(viewModel.requestDetailLeave())
        val detail = viewModel.projectDetail.first()
        assertTrue(detail?.nameInvalid == true)
        assertEquals("课程项目", projectRepository.get(projectId)?.name)

        viewModel.onProjectNameChanged("概率论复习")
        assertFalse(viewModel.requestDetailLeave())
        assertEquals("概率论复习", projectRepository.get(projectId)?.name)
        assertTrue(viewModel.requestDetailLeave())
    }

    @Test
    fun AC_D_07_blank_create_stays_panel_and_writes_nothing() = runTest {
        viewModel.openNewTask()
        viewModel.onNewTaskTitleChanged("   ")
        var created = false

        viewModel.createNewTask { created = true }

        assertFalse(created)
        assertTrue(viewModel.newTask.value != null)
        assertTrue(viewModel.newTask.value?.titleInvalid == true)
        assertTrue(taskRepository.observeAll().first().isEmpty())
        assertFalse("无效创建后返回应被阻止", viewModel.requestNewTaskDismiss())
    }

    @Test
    fun AC_D_07_cancel_writes_nothing() = runTest {
        viewModel.openNewTask()
        viewModel.onNewTaskTitleChanged("不应保存")

        viewModel.closeNewTask()

        assertNull(viewModel.newTask.value)
        assertTrue(taskRepository.observeAll().first().isEmpty())
    }

    @Test
    fun AC_D_07_valid_create_closes_and_keeps_current_position() = runTest {
        viewModel.openNewTask()
        viewModel.onNewTaskTitleChanged("新任务")
        var created = false

        viewModel.createNewTask { created = true }

        assertTrue(created)
        assertNull(viewModel.newTask.value)
        val tasks = taskRepository.observeAll().first()
        assertEquals(1, tasks.size)
        assertEquals("新任务", tasks.single().title)
        assertEquals(GtdPosition.INBOX, tasks.single().position)
    }

    @Test
    fun AC_D_07_back_priority_hides_ime_before_closing_panel() {
        val keyboard = KeyboardCoordinator()
        val overlays = OverlayBackStack()
        val navigation = NavigationCoordinator()
        val dispatcher = BackDispatcher(keyboard, overlays, navigation)
        overlays.open(Overlay.NewTaskSheet)
        keyboard.updateImeVisible(true)

        assertTrue(dispatcher.dispatch {})
        assertFalse(keyboard.imeVisible.value)
        assertEquals(Overlay.NewTaskSheet, overlays.current?.overlay)

        assertTrue(dispatcher.dispatch {})
        assertNull(overlays.current)
    }

    @Test
    fun AC_T_28_project_reopen_prompt_cancel_then_confirm_retries_once() = runTest {
        val projectId = insertProject(ProjectStatus.COMPLETED)
        val taskId = insertTask("子任务", projectId, GtdPosition.WAITING, closed = true)

        viewModel.reopenTask(taskId)
        val prompt = viewModel.uiState.first { it.projectReopenPrompt != null }.projectReopenPrompt
        assertTrue(prompt != null)
        assertEquals(projectId, prompt?.projectId)

        viewModel.cancelProjectReopen()
        assertNull(
            viewModel.uiState.first { it.projectReopenPrompt == null }.projectReopenPrompt,
        )
        assertEquals(ClosedOutcome.COMPLETED, taskRepository.get(taskId)?.closedOutcome)

        viewModel.reopenTask(taskId)
        viewModel.uiState.first { it.projectReopenPrompt != null }
        viewModel.confirmProjectReopen()

        assertEquals(ProjectStatus.ACTIVE, projectRepository.get(projectId)?.status)
        assertNull(taskRepository.get(taskId)?.closedOutcome)
        assertEquals(GtdPosition.WAITING, taskRepository.get(taskId)?.position)
        assertEquals(1, taskRepository.observeAll().first().size)
    }
}
