package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.interaction.GestureArbiter
import io.github.mazilin.nautile.testing.FakeClock
import io.github.mazilin.nautile.testing.InMemoryProjectRepository
import io.github.mazilin.nautile.testing.InMemoryTaskRepository
import io.github.mazilin.nautile.ui.screen.todo.TodoViewModel
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * R4 G3：GTD 左侧抽屉与主页面边界切换修复的 JVM 验证。
 *
 * 预期来自需求基线 v2.15（FR-G-15/16/18、AC-G-04、AC-G-05、BR-61、CMP-14）：
 * - `adjacentPosition` 首尾不循环、方向正确；
 * - `selectPosition` 先同步更新 UI 清单、再异步持久化；连续快速选择不读旧值；
 * - 持久化失败保留新清单并记录错误，不回滚。
 *
 * 只调用公开 API，不复制开发自测；不覆盖 Compose 手势，UI 侧由 androidTest 负责。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AcGtdDrawerR4Test {

    private val now: Instant = Instant.parse("2026-11-26T04:00:00Z")

    private lateinit var settings: GatedSettingsRepository
    private lateinit var viewModel: TodoViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val taskRepository = InMemoryTaskRepository()
        val projectRepository = InMemoryProjectRepository(taskRepository)
        settings = GatedSettingsRepository()
        viewModel = TodoViewModel(
            taskRepository = taskRepository,
            projectRepository = projectRepository,
            settingsRepository = settings,
            taskCommands = TaskCommands(taskRepository, projectRepository, FakeClock(now)),
            projectCommands = ProjectCommands(projectRepository, taskRepository, FakeClock(now)),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** AC-G-05 / BR-61：相邻清单按枚举顺序，首尾返回 null（不循环）。 */
    @Test
    fun AC_G_05_adjacent_position_direction_and_edges() {
        assertEquals(
            GtdPosition.NEXT_ACTION,
            GestureArbiter.adjacentPosition(GtdPosition.INBOX, 1),
        )
        assertEquals(
            GtdPosition.WAITING,
            GestureArbiter.adjacentPosition(GtdPosition.NEXT_ACTION, 1),
        )
        assertEquals(
            GtdPosition.SOMEDAY,
            GestureArbiter.adjacentPosition(GtdPosition.WAITING, 1),
        )
        assertEquals(
            GtdPosition.ENDED,
            GestureArbiter.adjacentPosition(GtdPosition.SOMEDAY, 1),
        )
        assertEquals(
            GtdPosition.NEXT_ACTION,
            GestureArbiter.adjacentPosition(GtdPosition.WAITING, -1),
        )
        assertEquals(
            GtdPosition.SOMEDAY,
            GestureArbiter.adjacentPosition(GtdPosition.ENDED, -1),
        )

        // 首位向下、末位向上都不循环。
        assertNull("首位下拉不应回绕", GestureArbiter.adjacentPosition(GtdPosition.INBOX, -1))
        assertNull("末位上拉不应回绕", GestureArbiter.adjacentPosition(GtdPosition.ENDED, 1))
    }

    /**
     * CMP-14：切换先同步更新 UI 清单，再异步持久化。
     *
     * 通过阻塞持久化替身证明：持久化未完成时 UI 已是新清单，且连续两次快速选择读到的是
     * 本地新值而不是异步回流的旧值。
     */
    @Test
    fun AC_G_04_select_position_updates_ui_before_persistence() = runBlocking {
        settings.blockPersistence = true

        viewModel.selectPosition(GtdPosition.NEXT_ACTION)
        assertEquals(
            "持久化未完成时 UI 也应同步为新清单",
            GtdPosition.NEXT_ACTION,
            viewModel.uiState.first().position,
        )

        viewModel.selectPosition(GtdPosition.WAITING)
        assertEquals(
            "连续快速选择不应读旧值",
            GtdPosition.WAITING,
            viewModel.uiState.first().position,
        )

        assertEquals(
            "两次选择都应发起持久化",
            listOf(GtdPosition.NEXT_ACTION, GtdPosition.WAITING),
            settings.persisted,
        )

        settings.gate.complete(Unit)
        assertEquals(
            "最终持久化为最后一次选择",
            GtdPosition.WAITING,
            settings.lastGtdPosition,
        )
        assertNull("成功持久化不显示错误", viewModel.uiState.first().positionError)
    }

    /** CMP-14：连续两次快速选择后最终清单是第二次目标，且两次均已持久化。 */
    @Test
    fun AC_G_04_two_rapid_selections_end_on_second_target() = runBlocking {
        viewModel.selectPosition(GtdPosition.WAITING)
        viewModel.selectPosition(GtdPosition.SOMEDAY)

        assertEquals(GtdPosition.SOMEDAY, viewModel.uiState.first().position)
        assertEquals(
            listOf(GtdPosition.WAITING, GtdPosition.SOMEDAY),
            settings.persisted,
        )
        assertEquals(GtdPosition.SOMEDAY, settings.lastGtdPosition)
    }

    /** 同一清单重复选择幂等：不重复写设置。 */
    @Test
    fun AC_G_04_same_position_is_idempotent() = runBlocking {
        viewModel.selectPosition(GtdPosition.INBOX)

        assertEquals("与当前清单相同不应持久化", emptyList<GtdPosition>(), settings.persisted)
        assertEquals(GtdPosition.INBOX, viewModel.uiState.first().position)
    }

    /** CMP-14：持久化失败保留本地新清单、显示错误、不回滚、不显示成功。 */
    @Test
    fun AC_G_04_persistence_failure_keeps_new_position_and_reports_error() = runBlocking {
        settings.failPersistence = true

        viewModel.selectPosition(GtdPosition.SOMEDAY)

        // 加超时避免实现异常时用例悬挂（而不是失败），便于门禁给出明确红绿。
        val state = withTimeout(5_000) { viewModel.uiState.first { it.positionError != null } }
        assertEquals("失败不回滚本地清单", GtdPosition.SOMEDAY, state.position)
        assertEquals(
            "清单切换未能保存，本次仍按新清单显示",
            state.positionError,
        )
    }

    /**
     * 设置仓库替身：可阻塞或抛出持久化，用于区分“同步生效”与“异步落库”。
     */
    private class GatedSettingsRepository(
        initial: UserSettings = UserSettings(),
    ) : SettingsRepository {

        private val state = MutableStateFlow(initial)
        val gate = CompletableDeferred<Unit>()
        var blockPersistence = false
        var failPersistence = false
        val persisted = mutableListOf<GtdPosition>()

        val lastGtdPosition: GtdPosition?
            get() = state.value.lastGtdPosition

        override val settings: Flow<UserSettings> = state

        override suspend fun setLastGtdPosition(position: GtdPosition) {
            persisted += position
            if (blockPersistence) gate.await()
            if (failPersistence) error("模拟持久化失败")
            state.update { it.copy(lastGtdPosition = position) }
        }

        override suspend fun setDefaultPage(page: AppPage) {
            state.update { it.copy(defaultPage = page) }
        }

        override suspend fun setLastPage(page: AppPage) {
            state.update { it.copy(lastPage = page) }
        }

        override suspend fun setPredictiveBackEnabled(enabled: Boolean) {
            state.update { it.copy(predictiveBackEnabled = enabled) }
        }

        override suspend fun setViewOptions(position: GtdPosition, options: String) {
            state.update { it.copy(perModuleViewOptions = it.perModuleViewOptions + (position to options)) }
        }
    }
}
