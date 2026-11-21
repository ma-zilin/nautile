package io.github.mazilin.nautile.verification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.mazilin.nautile.data.datastore.DataStoreSettingsRepository
import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.GtdPosition
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 设置仓库 AC 验证（T-052；AC-N-14、AC-N-21）。
 *
 * 断言来自基线 AC 文本，不复用开发自测断言；使用 DataStore 临时文件在 JVM 运行。
 */
class AcSettingsAcceptanceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var settingsFile: File
    private lateinit var scope: CoroutineScope
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun setUp() {
        settingsFile = File(temporaryFolder.root, "user_settings.preferences_pb")
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        repository = DataStoreSettingsRepository(createStore(scope))
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun AC_N_14_defaults_are_todo_with_no_last_page_or_gtd() = runTest {
        val settings = repository.settings.first()

        assertEquals(AppPage.TODO, settings.defaultPage)
        assertNull(settings.lastPage)
        assertNull(settings.lastGtdPosition)
        assertTrue(settings.perModuleViewOptions.isEmpty())
    }

    @Test
    fun AC_N_21_predictive_back_defaults_to_enabled() = runTest {
        assertTrue(repository.settings.first().predictiveBackEnabled)
    }

    @Test
    fun AC_N_14_writes_are_visible_and_module_view_options_are_independent() = runTest {
        repository.setDefaultPage(AppPage.LOG)
        repository.setLastPage(AppPage.AI)
        repository.setLastGtdPosition(GtdPosition.WAITING)
        repository.setViewOptions(GtdPosition.INBOX, "group=PROJECT")
        repository.setViewOptions(GtdPosition.WAITING, "sort=DUE")

        val settings = repository.settings.first()
        assertEquals(AppPage.LOG, settings.defaultPage)
        assertEquals(AppPage.AI, settings.lastPage)
        assertEquals(GtdPosition.WAITING, settings.lastGtdPosition)
        assertEquals("group=PROJECT", settings.perModuleViewOptions[GtdPosition.INBOX])
        assertEquals("sort=DUE", settings.perModuleViewOptions[GtdPosition.WAITING])

        repository.setViewOptions(GtdPosition.INBOX, "sort=DATE")
        val updated = repository.settings.first()
        assertEquals("sort=DATE", updated.perModuleViewOptions[GtdPosition.INBOX])
        assertEquals("sort=DUE", updated.perModuleViewOptions[GtdPosition.WAITING])
    }

    @Test
    fun AC_N_21_predictive_back_setting_survives_recreation() = runTest {
        repository.setDefaultPage(AppPage.AI)
        repository.setPredictiveBackEnabled(false)
        scope.cancel()

        val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val secondRepository = DataStoreSettingsRepository(createStore(secondScope))
            val settings = secondRepository.settings.first()

            assertEquals(AppPage.AI, settings.defaultPage)
            assertFalse(settings.predictiveBackEnabled)
        } finally {
            secondScope.cancel()
        }
    }

    private fun createStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { settingsFile },
        )
}
