package io.github.mazilin.nautile.testing

import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** 设置仓库内存替身，供 T06/T07/T10 使用；默认值与 [UserSettings] 一致。 */
class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    override val settings: Flow<UserSettings> = state

    override suspend fun setDefaultPage(page: AppPage) {
        state.update { it.copy(defaultPage = page) }
    }

    override suspend fun setLastPage(page: AppPage) {
        state.update { it.copy(lastPage = page) }
    }

    override suspend fun setLastGtdPosition(position: GtdPosition) {
        state.update { it.copy(lastGtdPosition = position) }
    }

    override suspend fun setPredictiveBackEnabled(enabled: Boolean) {
        state.update { it.copy(predictiveBackEnabled = enabled) }
    }

    override suspend fun setViewOptions(position: GtdPosition, options: String) {
        state.update { it.copy(perModuleViewOptions = it.perModuleViewOptions + (position to options)) }
    }
}
