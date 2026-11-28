package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * 设备层设置替身（R6-G4-V2）：默认并行计时；只用于 UI 计时用例，不写 DataStore。
 * 独立于 JVM 测试源集，避免跨源集依赖。
 */
internal class G4FakeSettingsRepository(
    initial: UserSettings = UserSettings(),
) : SettingsRepository {

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
