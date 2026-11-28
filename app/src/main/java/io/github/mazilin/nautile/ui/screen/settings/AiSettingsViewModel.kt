package io.github.mazilin.nautile.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mazilin.nautile.domain.repository.AiConversationRepository
import io.github.mazilin.nautile.domain.usecase.AiKeyCommands
import io.github.mazilin.nautile.domain.usecase.KeyClearResult
import io.github.mazilin.nautile.domain.usecase.KeySaveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 设置页一次性操作反馈；不进入持久状态，也不包含任何 Key 值（06 §6.2/§6.3）。 */
enum class AiSettingsFeedback {
    KeySaved,
    KeyCleared,
    KeySaveFailed,
    KeyClearFailed,
    ConversationCleared,
    ConversationClearFailed,
}

/**
 * 设置页 AI 状态（架构 §7.4）。
 *
 * 持久状态只有 [keyConfigured]（未配置/已保存）；保存/清除结果与清除会话结果为一次性反馈。
 * 不读取、不缓存 Key 明文：保存时只把输入值交给 [AiKeyCommands]。
 */
data class AiSettingsUiState(
    val keyConfigured: Boolean = false,
    val savingKey: Boolean = false,
    val clearingKey: Boolean = false,
    val keyFeedback: AiSettingsFeedback? = null,
    val clearingConversation: Boolean = false,
    val conversationFeedback: AiSettingsFeedback? = null,
)

/**
 * 设置页 AI 视图模型（独立于 SettingsViewModel，避免改动其构造器与既有测试依赖）。
 *
 * - Key 是否已保存每次从 [AiKeyCommands.hasKey] 读取真实存储，不做内存缓存；
 * - 清除会话只调用 [AiConversationRepository.clearConversation]，不触碰待办/项目/Key；
 * - 不打印 Key；异常只映射为失败反馈，不暴露内部文本。
 */
class AiSettingsViewModel(
    private val keyCommands: AiKeyCommands,
    private val conversationRepository: AiConversationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiSettingsUiState())
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()

    init {
        refreshKeyState()
    }

    /** 重新读取持久 Key 状态；进入 Key 页或返回设置根时可再调用。 */
    fun refreshKeyState() {
        viewModelScope.launch {
            val present = keyCommands.hasKey()
            _uiState.update { it.copy(keyConfigured = present) }
        }
    }

    /** 保存 Key：只做 trim 非空校验，不联网验证，不校验服务商格式（06 §6.2）。 */
    fun saveKey(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || _uiState.value.savingKey) return
        _uiState.update { it.copy(savingKey = true, keyFeedback = null) }
        viewModelScope.launch {
            when (keyCommands.save(trimmed)) {
                KeySaveResult.Saved -> _uiState.update {
                    it.copy(
                        savingKey = false,
                        keyConfigured = true,
                        keyFeedback = AiSettingsFeedback.KeySaved,
                    )
                }

                KeySaveResult.Failed -> _uiState.update {
                    it.copy(
                        savingKey = false,
                        keyFeedback = AiSettingsFeedback.KeySaveFailed,
                    )
                }
            }
        }
    }

    /** 清除 Key：不弹确认（可重新输入恢复，架构裁定 2026-11-28）。 */
    fun clearKey() {
        if (_uiState.value.clearingKey) return
        _uiState.update { it.copy(clearingKey = true, keyFeedback = null) }
        viewModelScope.launch {
            when (keyCommands.clear()) {
                KeyClearResult.Cleared -> _uiState.update {
                    it.copy(
                        clearingKey = false,
                        keyConfigured = false,
                        keyFeedback = AiSettingsFeedback.KeyCleared,
                    )
                }

                KeyClearResult.Failed -> _uiState.update {
                    it.copy(
                        clearingKey = false,
                        keyFeedback = AiSettingsFeedback.KeyClearFailed,
                    )
                }
            }
        }
    }

    /**
     * 清除 AI 会话：删除 ai_messages 与 ai_sessions，成功/失败都给一次性反馈。
     *
     * 删除后停留在设置页（由界面保证）；AI 页空态与草稿清空由 G3 的 AI 页状态负责。
     */
    fun clearConversation() {
        if (_uiState.value.clearingConversation) return
        _uiState.update { it.copy(clearingConversation = true, conversationFeedback = null) }
        viewModelScope.launch {
            val feedback = try {
                conversationRepository.clearConversation()
                AiSettingsFeedback.ConversationCleared
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                // 结构化并发：取消必须继续向上传播，不伪装成业务失败。
                throw cancelled
            } catch (_: Exception) {
                // 仓库事务失败整体回滚；这里只给失败反馈，不部分删除、不假成功。
                AiSettingsFeedback.ConversationClearFailed
            }
            _uiState.update {
                it.copy(clearingConversation = false, conversationFeedback = feedback)
            }
        }
    }
}
