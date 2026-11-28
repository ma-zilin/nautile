package io.github.mazilin.nautile.ui.screen.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mazilin.nautile.domain.ai.AiChatState
import io.github.mazilin.nautile.domain.ai.AiPhase
import io.github.mazilin.nautile.domain.ai.SendOutcome
import io.github.mazilin.nautile.domain.model.AiErrorCode
import io.github.mazilin.nautile.domain.usecase.AiChatCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * AI 页视图模型（架构 §7.2）。
 *
 * - 只持有页面本地状态：草稿、停止收尾、清除中、发送前失败；业务状态全部来自 [AiChatCoordinator]；
 * - 不直接访问网络或 Key 存储，发送/停止/清除都经协调器；
 * - 草稿仅内存，不跨进程/重启持久化（06 §5.4）；清除会话成功后由协调器的
 *   [AiChatState.clearGeneration] 变化触发清空。
 */
class AiViewModel(
    private val coordinator: AiChatCoordinator,
) : ViewModel() {

    /** 页面本地状态；不与 domain 混用。 */
    internal data class LocalState(
        val draft: String = "",
        val stopping: Boolean = false,
        val clearing: Boolean = false,
        val sendError: AiErrorCode? = null,
    )

    private val local = MutableStateFlow(LocalState())

    /** 直接暴露进程级业务状态，供需要消息/阶段的界面使用。 */
    val chatState: StateFlow<AiChatState> = coordinator.state

    val uiState: StateFlow<AiUiState> = combine(
        coordinator.state,
        local,
    ) { chat, localState ->
        chat.toUiState(localState)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = coordinator.state.value.toUiState(LocalState()),
    )

    init {
        refreshKeyPresence()
        viewModelScope.launch {
            var lastClearGeneration = coordinator.state.value.clearGeneration
            coordinator.state.collect { chat ->
                // 清除会话成功（含从设置页触发）：清空本地草稿，不改变业务状态。
                if (chat.clearGeneration != lastClearGeneration) {
                    lastClearGeneration = chat.clearGeneration
                    local.update { it.copy(draft = "") }
                }
                // 停止收尾结束时复位本地 stopping；阶段仍为 Streaming 时保持禁用。
                if (chat.phase !is AiPhase.Streaming && local.value.stopping) {
                    local.update { it.copy(stopping = false) }
                }
            }
        }
    }

    /** 草稿只在内存中更新，不触发网络与落库。 */
    fun onDraftChange(text: String) {
        local.update { it.copy(draft = text) }
    }

    /**
     * 发送当前草稿。
     *
     * 只有协调器返回 [SendOutcome.Started] 才清空草稿；若等待期间用户又输入了新内容，
     * 保留新内容不覆盖（架构 §7.3）。其余结果保留草稿、不自动重发。
     */
    fun send() {
        val current = local.value
        if (current.clearing) return
        val text = current.draft
        if (text.isBlank() || coordinator.state.value.keyMissing) return
        viewModelScope.launch {
            // 新的发送尝试先清掉上一次的发送前失败提示。
            local.update { it.copy(sendError = null) }
            when (val outcome = coordinator.trySend(text)) {
                SendOutcome.Started -> {
                    if (local.value.draft == text) local.update { it.copy(draft = "") }
                }

                SendOutcome.Busy,
                SendOutcome.KeyMissing,
                -> Unit

                // 请求开始前失败：不创建失败消息，用提示条说明。
                is SendOutcome.Failed -> local.update { it.copy(sendError = outcome.code) }
            }
        }
    }

    /** 停止当前请求；终态写入完成前协调器保持门闩，新的发送会返回 Busy。 */
    fun stop() {
        if (local.value.stopping) return
        // 无流式请求时不进入停止态，避免本地状态卡在“等待终态”。
        if (coordinator.state.value.phase !is AiPhase.Streaming) return
        local.update { it.copy(stopping = true) }
        coordinator.stop()
    }

    /**
     * 清除 AI 会话：经协调器先停止并等待终态写入，再事务删除。
     *
     * 成功清空草稿；失败保留草稿与消息，由界面给出失败提示（不假成功）。
     */
    fun clearConversation() {
        if (local.value.clearing) return
        local.update { it.copy(clearing = true, sendError = null) }
        viewModelScope.launch {
            try {
                coordinator.clearConversation()
                local.update { it.copy(draft = "") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 删除事务失败整体回滚；草稿与消息保持原样。
            } finally {
                local.update { it.copy(clearing = false, stopping = false) }
            }
        }
    }

    /** 重新读取 Key 是否存在（进入页面或从设置返回时调用）；读取统一在协调器内。 */
    fun refreshKeyPresence() {
        viewModelScope.launch {
            coordinator.refreshKeyPresence()
        }
    }
}

/** 业务状态 + 页面本地状态合并为 UI 状态。 */
private fun AiChatState.toUiState(local: AiViewModel.LocalState): AiUiState = AiUiState(
    messages = messages,
    phase = phase,
    streamingMessageId = streamingMessageId,
    streamingText = streamingText,
    keyMissing = keyMissing,
    draft = local.draft,
    stopping = local.stopping,
    clearing = local.clearing,
    sendError = local.sendError,
)
