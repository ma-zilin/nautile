package io.github.mazilin.nautile.verification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import io.github.mazilin.nautile.data.datastore.DataStoreSettingsRepository
import io.github.mazilin.nautile.data.datastore.UserSettingsStore
import io.github.mazilin.nautile.domain.model.ScheduleViewMode
import io.github.mazilin.nautile.domain.model.TimerMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * R6-G4-V1 · 日志偏好设置（G4-T2）。
 *
 * 预期来自任务书与架构 §4.2：`timer_mode` 默认 PARALLEL、未知值回落并行；
 * `schedule_view_mode` 默认 DAY、未知值回落日视图；写入后重建实例仍保持。
 */
class AcLogSettingsG4Test {

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
    fun G4_defaults_are_parallel_timer_and_day_view() = runTest {
        val settings = repository.settings.first()
        assertEquals(TimerMode.PARALLEL, settings.timerMode)
        assertEquals(ScheduleViewMode.DAY, settings.scheduleViewMode)
    }

    @Test
    fun G4_timer_and_view_mode_persist_across_recreation() = runTest {
        repository.setTimerMode(TimerMode.SINGLE)
        repository.setScheduleViewMode(ScheduleViewMode.WEEK)
        scope.cancel()

        val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val secondStore = PreferenceDataStoreFactory.create(
                scope = secondScope,
                produceFile = { settingsFile },
            )
            val settings = DataStoreSettingsRepository(secondStore).settings.first()
            assertEquals(TimerMode.SINGLE, settings.timerMode)
            assertEquals(ScheduleViewMode.WEEK, settings.scheduleViewMode)
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun G4_unknown_stored_values_fall_back_to_defaults_without_throwing() = runTest {
        dataStore.edit { preferences ->
            preferences[UserSettingsStore.timerMode] = "NOT_A_MODE"
            preferences[UserSettingsStore.scheduleViewMode] = "NOT_A_VIEW"
        }

        val settings = repository.settings.first()
        assertEquals("未知计时模式回落并行", TimerMode.PARALLEL, settings.timerMode)
        assertEquals("未知视图回落日视图", ScheduleViewMode.DAY, settings.scheduleViewMode)
    }
}
