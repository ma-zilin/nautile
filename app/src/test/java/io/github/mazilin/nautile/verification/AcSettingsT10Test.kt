package io.github.mazilin.nautile.verification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.mazilin.nautile.data.datastore.DataStoreSettingsRepository
import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.interaction.FullScreenPage
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.PredictiveBackCoordinator
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * S1-T10 设置壳、默认页持久化与预测返回范围的 JVM 验证（T-066）。
 *
 * 预期取自 AC-N-14、AC-N-21、FR-N-19/20/30；DataStore 用临时文件实际写入/重建。
 */
class AcSettingsT10Test {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @After
    fun tearDown() {
        // DataStore 的 scope 在各用例内单独取消；这里只保留临时目录清理。
    }

    @Test
    fun AC_N_14_four_tabs_share_same_settings_root_and_back_returns_to_tab() {
        io.github.mazilin.nautile.interaction.AppTab.entries.forEach { tab ->
            val navigation = NavigationCoordinator()
            navigation.selectTab(tab)
            navigation.navigateToSettings()

            assertEquals(FullScreenPage.Settings, navigation.currentFullScreenPage)
            assertEquals(1, navigation.fullScreenStack.value.size)

            assertTrue(navigation.back())
            assertNull(navigation.currentFullScreenPage)
            assertEquals(tab, navigation.activeTab.value)
        }
    }

    @Test
    fun AC_N_14_settings_root_and_default_page_are_full_screen_and_back_stepwise() {
        val navigation = NavigationCoordinator()
        navigation.selectTab(io.github.mazilin.nautile.interaction.AppTab.LOG)

        navigation.navigateToSettings()
        navigation.navigateToDefaultPage()
        assertEquals(
            listOf(FullScreenPage.Settings, FullScreenPage.DefaultPage),
            navigation.fullScreenStack.value,
        )

        navigation.selectTab(io.github.mazilin.nautile.interaction.AppTab.AI)
        assertEquals("全屏子页打开时不应切一级页", io.github.mazilin.nautile.interaction.AppTab.LOG, navigation.activeTab.value)

        assertTrue(navigation.back())
        assertEquals(FullScreenPage.Settings, navigation.currentFullScreenPage)
        assertTrue(navigation.back())
        assertNull(navigation.currentFullScreenPage)
    }

    @Test
    fun AC_N_21_predictive_scope_is_full_screen_with_can_leave_only() {
        val predictive = PredictiveBackCoordinator()

        assertTrue(predictive.shouldPreview(FullScreenPage.Settings, canLeave = true))
        assertTrue(predictive.shouldPreview(FullScreenPage.DefaultPage, canLeave = true))
        assertFalse(predictive.shouldPreview(FullScreenPage.Settings, canLeave = false))
        assertFalse(predictive.shouldPreview(null, canLeave = true))
    }

    @Test
    fun AC_N_21_progress_is_clamped_and_reset() {
        val predictive = PredictiveBackCoordinator()

        predictive.onProgress(-0.5f)
        assertEquals(0f, predictive.progress.value, 0.0001f)
        predictive.onProgress(0.4f)
        assertEquals(0.4f, predictive.progress.value, 0.0001f)
        predictive.onProgress(1.7f)
        assertEquals(1f, predictive.progress.value, 0.0001f)
        predictive.reset()
        assertEquals(0f, predictive.progress.value, 0.0001f)
    }

    @Test
    fun AC_N_14_default_page_and_predictive_toggle_survive_store_recreation() = runTest {
        val file = File(temporaryFolder.root, "settings.preferences_pb")
        val firstScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val firstStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = firstScope,
            produceFile = { file },
        )
        val first = DataStoreSettingsRepository(firstStore)

        first.setDefaultPage(AppPage.LOG)
        first.setLastPage(AppPage.AI)
        first.setPredictiveBackEnabled(false)
        firstScope.cancel()

        val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val secondStore = PreferenceDataStoreFactory.create(
                scope = secondScope,
                produceFile = { file },
            )
            val second = DataStoreSettingsRepository(secondStore)
            val settings = second.settings.first()

            assertEquals(AppPage.LOG, settings.defaultPage)
            assertEquals(AppPage.AI, settings.lastPage)
            assertFalse(settings.predictiveBackEnabled)
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun AC_N_14_first_start_defaults_are_todo_and_predictive_enabled() {
        val defaults = UserSettings()

        assertEquals(AppPage.TODO, defaults.defaultPage)
        assertNull(defaults.lastPage)
        assertTrue(defaults.predictiveBackEnabled)
    }
}
