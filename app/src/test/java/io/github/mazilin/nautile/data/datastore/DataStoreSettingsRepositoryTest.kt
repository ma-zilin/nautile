package io.github.mazilin.nautile.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * S1-T05 步骤 5：JVM 临时文件验证 DataStore 默认值、写入后可读、重建实例后保持。
 *
 * 使用 `PreferenceDataStoreFactory`（preferences-core，纯 JVM），不需要 Android 运行时。
 */
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var settingsFile: File
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun setUp() {
        settingsFile = File(temporaryFolder.root, "user_settings.preferences_pb")
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { settingsFile })
        repository = DataStoreSettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun acN14_defaults_are_todo_and_predictive_back_enabled() = runTest {
        val settings = repository.settings.first()

        assertEquals(AppPage.TODO, settings.defaultPage)
        assertEquals(null, settings.lastPage)
        assertEquals(null, settings.lastGtdPosition)
        assertTrue(settings.predictiveBackEnabled)
        assertTrue(settings.perModuleViewOptions.isEmpty())
    }

    @Test
    fun acN14_writes_are_visible_through_flow() = runTest {
        repository.setDefaultPage(AppPage.LOG)
        repository.setLastPage(AppPage.AI)
        repository.setLastGtdPosition(GtdPosition.WAITING)
        repository.setPredictiveBackEnabled(false)
        repository.setViewOptions(GtdPosition.INBOX, "group=PROJECT")

        val settings = repository.settings.first()
        assertEquals(AppPage.LOG, settings.defaultPage)
        assertEquals(AppPage.AI, settings.lastPage)
        assertEquals(GtdPosition.WAITING, settings.lastGtdPosition)
        assertFalse(settings.predictiveBackEnabled)
        assertEquals(mapOf(GtdPosition.INBOX to "group=PROJECT"), settings.perModuleViewOptions)
    }

    @Test
    fun acN21_settings_survive_store_recreation() = runTest {
        repository.setDefaultPage(AppPage.AI)
        repository.setPredictiveBackEnabled(false)
        scope.cancel()

        val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val secondStore = PreferenceDataStoreFactory.create(
                scope = secondScope,
                produceFile = { settingsFile },
            )
            val secondRepository = DataStoreSettingsRepository(secondStore)

            val settings = secondRepository.settings.first()
            assertEquals(AppPage.AI, settings.defaultPage)
            assertFalse(settings.predictiveBackEnabled)
        } finally {
            secondScope.cancel()
        }
    }
}
