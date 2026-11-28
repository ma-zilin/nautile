package io.github.mazilin.nautile.ui.screen.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mazilin.nautile.domain.ai.AiChatState
import io.github.mazilin.nautile.domain.ai.AiPhase
import io.github.mazilin.nautile.domain.ai.SendOutcome
import io.github.mazilin.nautile.domain.usecase.AiChatCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * AI 页视图模型（架构 §7.2）。
 *
 * - 只持有页面本地状态：草稿、停止收尾、清除中；业务状态全部来自 [AiChatCoordinator]；
 * - 不直接访问网络或 Key 存储，发送/停止/清除都经协调器；
 * - 草稿仅内存，不跨进程/重启持久化（06 §5.4）。
 */
class AiViewModel(
    private val coordinator: AiChatCoordinator,
) : ViewModel() {

    private val draft = MutableStateFlow("")
    private val stopping = MutableStateFlow(false)
    private val clearing = MutableStateFlow(false)

    /** 直接暴露进程级业务状态，供需要消息/阶段的界面使用。 */
    val chatState: StateFlow<AiChatState> = coordinator.state

    val uiState: StateFlow<AiUiState> = combine(
        coordinator.state,
        draft,
        stopping,
        clearing,
    ) { chat, draftValue, stoppingValue, clearingValue ->
        chat.toUiState(
            draft = draftValue,
            stopping = stoppingValue,
            clearing = clearingValue,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = coordinator.state.value.toUiState(),
    )

    init {
        refreshKeyPresence()
        // 停止收尾结束时复位本地 stopping；阶段仍为 Streaming 时保持禁用。
        viewModelScope.launch {
            coordinator.state.collect { chat ->
                if (chat.phase !is AiPhase.Streaming && stopping.value) {
                    stopping.value = false
                }
            }
        }
    }

    /** 草稿只在内存中更新，不触发网络与落库。 */
    fun onDraftChange(text: String) {
        draft.value = text
    }

    /**
     * 发送当前草稿。
     *
     * 只有协调器返回 [SendOutcome.Started] 才清空草稿；若等待期间用户又输入了新内容，
     * 保留新内容不覆盖（架构 §7.3）。其余结果保留草稿、不自动重发。
     */
    fun send() {
        if (clearing.value) return
        val text = draft.value
        if (text.isBlank()) return
        viewModelScope.launch {
            when (coordinator.trySend(text)) {
                SendOutcome.Started -> {
                    if (draft.value == text) draft.value = ""
                }

                SendOutcome.Busy,
                SendOutcome.KeyMissing,
                is SendOutcome.Failed,
                -> Unit
            }
        }
    }

    /** 停止当前请求；终态写入完成前协调器保持门闩，新的发送会返回 Busy。 */
    fun stop() {
        if (stopping.value) return
        // 无流式请求时不进入停止态，避免本地状态卡在“等待终态”。
        if (coordinator.state.value.phase !is AiPhase.Streaming) return
        stopping.value = true
        coordinator.stop()
    }

    /**
     * 清除 AI 会话：经协调器先停止并等待终态写入，再事务删除。
     *
     * 成功清空草稿；失败保留草稿与消息，由界面给出失败提示（不假成功）。
     */
    fun clearConversation() {
        if (clearing.value) return
        clearing.value = true
        viewModelScope.launch {
            try {
                coordinator.clearConversation()
                draft.value = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 删除事务失败整体回滚；草稿与消息保持原样。
            } finally {
                clearing.value = false
                stopping.value = false
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
private fun AiChatState.toUiState(
    draft: String = "",
    stopping: Boolean = false,
    clearing: Boolean = false,
): AiUiState = AiUiState(
    messages = messages,
    phase = phase,
    streamingMessageId = streamingMessageId,
    streamingText = streamingText,
    keyMissing = keyMissing,
    draft = draft,
    stopping = stopping,
    clearing = clearing,
)
