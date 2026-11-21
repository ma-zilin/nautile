package io.github.mazilin.nautile.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 设置页状态（S1-T10）：只用既有 SettingsRepository，不新增设置键。 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun setDefaultPage(page: AppPage) {
        viewModelScope.launch { settingsRepository.setDefaultPage(page) }
    }

    fun setPredictiveBackEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPredictiveBackEnabled(enabled) }
    }
}
