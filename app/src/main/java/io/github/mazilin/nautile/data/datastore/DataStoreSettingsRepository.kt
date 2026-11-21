package io.github.mazilin.nautile.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * DataStore 设置仓库（S1-T05 步骤 3）。
 *
 * 所有写入经 [DataStore.updateData]，避免读改写竞态；每个键独立更新，设置之间不要求跨键原子。
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<UserSettings> = dataStore.data
        .catch { error ->
            // 读取失败（如文件损坏）时回落到默认值，不把异常抛给 UI；损坏修复不在 S1 范围。
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(UserSettingsStore::toSettings)

    override suspend fun setDefaultPage(page: AppPage) {
        dataStore.updateData { current ->
            current.toMutablePreferences().apply { this[UserSettingsStore.defaultPage] = page.name }
        }
    }

    override suspend fun setLastPage(page: AppPage) {
        dataStore.updateData { current ->
            current.toMutablePreferences().apply { this[UserSettingsStore.lastPage] = page.name }
        }
    }

    override suspend fun setLastGtdPosition(position: GtdPosition) {
        dataStore.updateData { current ->
            current.toMutablePreferences().apply { this[UserSettingsStore.lastGtdPosition] = position.name }
        }
    }

    override suspend fun setPredictiveBackEnabled(enabled: Boolean) {
        dataStore.updateData { current ->
            current.toMutablePreferences().apply { this[UserSettingsStore.predictiveBackEnabled] = enabled }
        }
    }

    override suspend fun setViewOptions(position: GtdPosition, options: String) {
        dataStore.updateData { current ->
            current.toMutablePreferences().apply { this[UserSettingsStore.viewOptionsKey(position)] = options }
        }
    }
}
