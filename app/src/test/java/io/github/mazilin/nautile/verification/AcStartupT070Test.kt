package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import io.github.mazilin.nautile.interaction.AppTab
import io.github.mazilin.nautile.interaction.FullScreenPage
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.ui.screen.settings.SettingsViewModel
import io.github.mazilin.nautile.ui.screen.settings.StartupState
import io.github.mazilin.nautile.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T-070/AC-N-14 启动映射与 T-071/P-03 启动状态的 JVM 验证（T-072）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AcStartupT070Test {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun AC_N_14_initial_tab_uses_last_then_default_only_once() {
        val navigation = NavigationCoordinator()
        navigation.applyInitialTab(AppTab.LOG)
        assertEquals(AppTab.LOG, navigation.activeTab.value)

        navigation.selectTab(AppTab.AI)
        navigation.applyInitialTab(AppTab.TODO)
        assertEquals("只应用一次，不得覆盖用户选择", AppTab.AI, navigation.activeTab.value)
    }

    @Test
    fun AC_N_14_initial_tab_does_not_change_when_full_screen_is_open() {
        val navigation = NavigationCoordinator()
        navigation.navigateToSettings()
        navigation.applyInitialTab(AppTab.LOG)

        assertEquals(FullScreenPage.Settings, navigation.currentFullScreenPage)
        assertEquals(AppTab.TODO, navigation.activeTab.value)
    }

    @Test
    fun AC_N_14_set_default_page_writes_default_and_last() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.setDefaultPage(AppPage.LOG)

        val settings = repository.settings.first()
        assertEquals(AppPage.LOG, settings.defaultPage)
        assertEquals(AppPage.LOG, settings.lastPage)
    }

    @Test
    fun AC_N_14_startup_reaches_ready_with_persisted_last_page() = runTest {
        val repository = FakeSettingsRepository(
            UserSettings(defaultPage = AppPage.LOG, lastPage = AppPage.AI),
        )
        val viewModel = SettingsViewModel(repository)

        val state = viewModel.startup.first { it is StartupState.Ready }
        assertEquals(AppPage.LOG, (state as StartupState.Ready).settings.defaultPage)
        assertEquals(AppPage.AI, state.settings.lastPage)
    }

    @Test
    fun P_03_startup_failure_reports_failed_and_can_apply_todo() = runTest {
        val failing = object : SettingsRepository {
            override val settings: Flow<UserSettings> = flow { error("DataStore failure") }
            override suspend fun setDefaultPage(page: AppPage) = Unit
            override suspend fun setLastPage(page: AppPage) = Unit
            override suspend fun setLastGtdPosition(position: GtdPosition) = Unit
            override suspend fun setPredictiveBackEnabled(enabled: Boolean) = Unit
            override suspend fun setViewOptions(position: GtdPosition, options: String) = Unit
        }
        val viewModel = SettingsViewModel(failing)

        assertTrue(viewModel.startup.first { it is StartupState.Failed } is StartupState.Failed)

        val navigation = NavigationCoordinator()
        navigation.applyInitialTab(AppTab.TODO)
        assertEquals(AppTab.TODO, navigation.activeTab.value)
    }

    @Test
    fun AC_N_14_record_last_page_is_forwarded() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.recordLastPage(AppPage.SCHEDULE)

        assertEquals(AppPage.SCHEDULE, repository.settings.first().lastPage)
    }
}
