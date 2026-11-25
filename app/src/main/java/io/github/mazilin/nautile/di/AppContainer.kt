package io.github.mazilin.nautile.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import io.github.mazilin.nautile.data.datastore.DataStoreSettingsRepository
import io.github.mazilin.nautile.data.db.MIGRATION_1_2
import io.github.mazilin.nautile.data.db.MIGRATION_2_3
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.repository.RoomProjectRepository
import io.github.mazilin.nautile.data.repository.RoomTaskRepository
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.ProjectRepository
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import io.github.mazilin.nautile.domain.repository.TaskRepository
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.interaction.BackDispatcher
import io.github.mazilin.nautile.interaction.KeyboardCoordinator
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.OverlayBackStack
import io.github.mazilin.nautile.platform.clock.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 手工装配容器（T-06 起负责）：唯一 new 具体实现的地方。
 *
 * 不承载业务规则；只创建数据库、DataStore、仓库、时钟与交互协调器。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: NautileDatabase = Room.databaseBuilder(
        appContext,
        NautileDatabase::class.java,
        DATABASE_NAME,
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = ioScope,
        produceFile = { appContext.preferencesDataStoreFile(SETTINGS_FILE_NAME) },
    )

    val taskRepository: TaskRepository = RoomTaskRepository(database)
    val projectRepository: ProjectRepository = RoomProjectRepository(database)
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(dataStore)
    val clock: Clock = SystemClock()

    val taskCommands = TaskCommands(taskRepository, projectRepository, clock)
    val projectCommands = ProjectCommands(projectRepository, taskRepository, clock)

    val navigationCoordinator = NavigationCoordinator()
    val overlayBackStack = OverlayBackStack()
    val keyboardCoordinator = KeyboardCoordinator()
    val backDispatcher = BackDispatcher(keyboardCoordinator, overlayBackStack, navigationCoordinator)

    private companion object {
        const val DATABASE_NAME = "nautile.db"
        const val SETTINGS_FILE_NAME = "user_settings"
    }
}
