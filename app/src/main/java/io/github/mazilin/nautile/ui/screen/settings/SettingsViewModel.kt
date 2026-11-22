package io.github.mazilin.nautile.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 设置页状态（S1-T10）：只用既有 SettingsRepository，不新增设置键。 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    /** DataStore 首次发射前为 false；界面据此等待，避免先渲染默认待办再跳页（AC-N-14）。 */
    val loaded: StateFlow<Boolean> = settingsRepository.settings
        .map { true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * 写入默认页，同时把 lastPage 对齐到新默认页。
     *
     * AC-N-14 要求“设置改默认页后重启按新默认进入”；既有 SettingsRepository 没有清除
     * lastPage 的接口，这里以同一值覆盖达到等价效果，且不新增设置键。
     */
    fun setDefaultPage(page: AppPage) {
        viewModelScope.launch {
            settingsRepository.setDefaultPage(page)
            settingsRepository.setLastPage(page)
        }
    }

    /** 一级页面切换时记录 lastPage，供下次冷启动恢复（AC-N-14）。 */
    fun recordLastPage(page: AppPage) {
        viewModelScope.launch { settingsRepository.setLastPage(page) }
    }

    fun setPredictiveBackEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPredictiveBackEnabled(enabled) }
    }
}
