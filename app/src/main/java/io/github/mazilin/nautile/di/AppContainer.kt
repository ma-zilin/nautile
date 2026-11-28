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
import io.github.mazilin.nautile.data.db.MIGRATION_3_4
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.repository.RoomAiConversationRepository
import io.github.mazilin.nautile.data.repository.RoomProjectRepository
import io.github.mazilin.nautile.data.repository.RoomTaskRepository
import io.github.mazilin.nautile.domain.port.AiGateway
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.port.SecretStore
import io.github.mazilin.nautile.domain.repository.AiConversationRepository
import io.github.mazilin.nautile.domain.repository.ProjectRepository
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import io.github.mazilin.nautile.domain.repository.TaskRepository
import io.github.mazilin.nautile.domain.usecase.AiChatCoordinator
import io.github.mazilin.nautile.domain.usecase.AiKeyCommands
import io.github.mazilin.nautile.domain.usecase.ProjectCommands
import io.github.mazilin.nautile.domain.usecase.TaskCommands
import io.github.mazilin.nautile.interaction.BackDispatcher
import io.github.mazilin.nautile.interaction.KeyboardCoordinator
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.OverlayBackStack
import io.github.mazilin.nautile.platform.clock.SystemClock
import io.github.mazilin.nautile.platform.keystore.AndroidKeystoreSecretStore
import io.github.mazilin.nautile.platform.network.DeepSeekAiGateway
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
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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

    // AI Phase A：Key 存 Android Keystore；网关自行读取 Key；会话落 Room（v4）。
    private val secretStore: SecretStore = AndroidKeystoreSecretStore(appContext)
    val aiKeyCommands = AiKeyCommands(secretStore)
    val aiGateway: AiGateway = DeepSeekAiGateway(secretStore)
    val aiConversationRepository: AiConversationRepository =
        RoomAiConversationRepository(database)

    // 进程级 AI 协调器：单请求门闩与清除串行化都依赖同一个应用级 scope（架构 §2.4）。
    private val aiScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val aiChatCoordinator = AiChatCoordinator(
        repository = aiConversationRepository,
        gateway = aiGateway,
        keyCommands = aiKeyCommands,
        clock = clock,
        scope = aiScope,
    )

    val navigationCoordinator = NavigationCoordinator()
    val overlayBackStack = OverlayBackStack()
    val keyboardCoordinator = KeyboardCoordinator()
    val backDispatcher = BackDispatcher(keyboardCoordinator, overlayBackStack, navigationCoordinator)

    private companion object {
        const val DATABASE_NAME = "nautile.db"
        const val SETTINGS_FILE_NAME = "user_settings"
    }
}
