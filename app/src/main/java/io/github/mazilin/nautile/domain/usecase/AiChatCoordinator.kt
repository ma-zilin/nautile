package io.github.mazilin.nautile.domain.usecase

import io.github.mazilin.nautile.domain.ai.AiChatState
import io.github.mazilin.nautile.domain.ai.AiPhase
import io.github.mazilin.nautile.domain.ai.AiSystemPrompt
import io.github.mazilin.nautile.domain.ai.SendOutcome
import io.github.mazilin.nautile.domain.model.AiError
import io.github.mazilin.nautile.domain.model.AiErrorCode
import io.github.mazilin.nautile.domain.model.AiGatewayException
import io.github.mazilin.nautile.domain.model.AiMessage
import io.github.mazilin.nautile.domain.model.AiMessageId
import io.github.mazilin.nautile.domain.model.AiMessageStatus
import io.github.mazilin.nautile.domain.model.AiRequest
import io.github.mazilin.nautile.domain.model.AiRole
import io.github.mazilin.nautile.domain.model.AiTurn
import io.github.mazilin.nautile.domain.model.toErrorCode
import io.github.mazilin.nautile.domain.port.AiGateway
import io.github.mazilin.nautile.domain.port.AiStreamEvent
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.AiConversationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 进程级 AI 对话协调器（架构 §2.4）。
 *
 * 确定性职责：
 * 1. 单请求互斥：`sendGate` 从发送准备持有到终态写入完成，重复发送返回 [SendOutcome.Busy] 且不排队；
 * 2. 先查 Key：缺失直接返回 [SendOutcome.KeyMissing]，不写 Room、不清草稿、不发网络；
 * 3. 只有 `beginTurn` 成功才返回 [SendOutcome.Started]；
 * 4. 流式增量只更新内存 `streamingText`，终态一次性 `finishAssistant`；
 * 5. 停止取消 job，`NonCancellable` 写入 STOPPED；门闩在终态写入后才释放，已 COMPLETE 不被覆盖；
 * 6. 清除先停止并等待 job 结束，再事务删除；清除期间拒绝新发送；
 * 7. 启动先 `markInterruptedAsStopped()` 再观察消息，避免长期停留 STREAMING；
 * 8. 错误映射为 FAILED + 短码；`CancellationException` 不映射为业务错误；不自动重试。
 *
 * 生命周期：切页、打开设置/Key 页、收起键盘都不取消处理中请求；只有 [stop]、
 * [clearConversation] 或进程死亡终止。
 */
class AiChatCoordinator(
    private val repository: AiConversationRepository,
    private val gateway: AiGateway,
    private val keyCommands: AiKeyCommands,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(AiChatState())
    val state: StateFlow<AiChatState> = _state.asStateFlow()

    /** 单请求门闩：发送准备时获取，终态落库后由流处理 job 释放。 */
    private val sendGate = Mutex()

    /** 串行化“发送准备”和“清除会话”，避免清除与 `beginTurn` 交叉。 */
    private val lifecycleGate = Mutex()

    /** 清除互斥；同一时刻只执行一次清除（重复调用串行后为无操作删除）。 */
    private val clearGate = Mutex()

    /** 清除进行中标记；发送入口先看它，避免清除期间启动新请求。 */
    private val clearing = AtomicBoolean(false)

    @Volatile
    private var currentJob: Job? = null

    /** 当前轮是否已写入终态；用于防止停止覆盖 COMPLETE，并保证只落库一次。 */
    @Volatile
    private var terminalWritten = false

    /** 当前轮的内存增量文本；只有流处理 job 写入。 */
    private var streamingText = ""

    init {
        scope.launch {
            // 崩溃恢复先于观察：把仍为 STREAMING 的助手行改为 STOPPED，再开始收集消息。
            try {
                repository.markInterruptedAsStopped()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 恢复失败不阻塞消息观察；下次启动再试，避免整页不可用。
            }
            repository.observeMessages().collect { messages ->
                _state.update { it.copy(messages = messages) }
            }
        }
    }

    /**
     * 尝试发送一条消息。
     *
     * 返回 [SendOutcome.Started] 前已完成 `beginTurn`；UI 只有看到它才能清草稿、收键盘。
     */
    suspend fun trySend(text: String): SendOutcome {
        if (clearing.get()) return SendOutcome.Busy
        return lifecycleGate.withLock {
            if (clearing.get()) return@withLock SendOutcome.Busy
            // 单请求门闩：已有请求（含停止收尾）时直接 Busy，不排队。
            if (!sendGate.tryLock()) return@withLock SendOutcome.Busy

            var handedToJob = false
            try {
                if (clearing.get()) return@withLock SendOutcome.Busy

                if (!keyCommands.hasKey()) {
                    _state.update { it.copy(keyMissing = true) }
                    return@withLock SendOutcome.KeyMissing
                }
                _state.update { it.copy(keyMissing = false) }

                val ids = try {
                    repository.beginTurn(text, clock.now())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // 请求开始前落库失败：不创建失败消息，保持当前阶段，草稿由 UI 保留。
                    return@withLock SendOutcome.Failed(AiErrorCode.UNKNOWN)
                }

                // 请求体只含已持久化的 USER 与 COMPLETE ASSISTANT；新用户消息在此追加。
                val request = buildRequest(text, ids.userMessageId)

                streamingText = ""
                terminalWritten = false
                _state.update {
                    it.copy(
                        phase = AiPhase.Streaming,
                        streamingMessageId = ids.assistantMessageId,
                        streamingText = "",
                    )
                }

                // UNDISPATCHED 保证 job 体立即进入 try/finally，避免“启动前被停止”导致
                // 终态不写、门闩不释放；真实网关在首次 collect 时挂起，不阻塞调用方。
                val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    runTurn(ids.assistantMessageId, request)
                }
                // 无论正常完成、取消还是未执行，都在 job 完成（含终态落库）后释放门闩；
                // Mutex 的 unlock 不绑定线程，可与 job 体在不同线程完成。
                job.invokeOnCompletion { sendGate.unlock() }
                currentJob = job
                handedToJob = true
                SendOutcome.Started
            } finally {
                // 交给 job 后由 job 在终态写入完成后释放；其余路径在此释放。
                if (!handedToJob) sendGate.unlock()
            }
        }
    }

    /**
     * 停止当前请求：取消流处理 job，由 job 在 `NonCancellable` 中把部分文本写为 STOPPED。
     *
     * 停止完成前门闩不释放，期间新发送返回 Busy；已 COMPLETE 的终态不会被覆盖。
     */
    fun stop() {
        currentJob?.cancel()
    }

    /**
     * 清除会话：先停止并等待当前 job 结束，再在同一事务内删除消息与会话。
     *
     * 清除期间拒绝新发送；失败向调用方抛出，由调用方给出失败反馈，不假成功。
     */
    suspend fun clearConversation() {
        clearGate.withLock {
            clearing.set(true)
            try {
                lifecycleGate.withLock {
                    // 等待终态写入与门闩释放；随后删除不会与终态写回竞态。
                    currentJob?.cancelAndJoin()
                    currentJob = null
                    repository.clearConversation()
                    streamingText = ""
                    terminalWritten = false
                    _state.update {
                        it.copy(
                            messages = emptyList(),
                            phase = AiPhase.Idle,
                            streamingMessageId = null,
                            streamingText = "",
                            // 自增后由 AI 页观察并清空本地草稿（不改变请求与落库语义）。
                            clearGeneration = it.clearGeneration + 1,
                        )
                    }
                }
            } finally {
                clearing.set(false)
            }
        }
    }

    /** 重新读取 Key 是否存在；设置页保存/清除后由界面调用。 */
    suspend fun refreshKeyPresence() {
        val present = keyCommands.hasKey()
        _state.update { it.copy(keyMissing = !present) }
    }

    /** 界面可见时触发 Key 存在性刷新（不取消处理中请求）。 */
    fun markScreenVisible() {
        scope.launch { refreshKeyPresence() }
    }

    /**
     * 流处理主循环：增量只更新内存，终态只写一次。
     *
     * 门闩由 `trySend` 注册的完成回调在 job 结束（终态写入完成）后释放，
     * 因此停止收尾完成前始终不能被新发送抢占。
     */
    private suspend fun runTurn(assistantId: AiMessageId, request: AiRequest) {
        var nextPhase: AiPhase = AiPhase.Streaming
        try {
            gateway.stream(request).collect { event ->
                when (event) {
                    is AiStreamEvent.Delta -> {
                        if (!terminalWritten) {
                            streamingText += event.text
                            val snapshot = streamingText
                            _state.update { current ->
                                if (current.streamingMessageId == assistantId) {
                                    current.copy(streamingText = snapshot)
                                } else {
                                    current
                                }
                            }
                        }
                    }

                    AiStreamEvent.Completed -> {
                        if (!terminalWritten) {
                            // 终态写入不可被取消打断：一旦收到 [DONE]，内容就应保留为 COMPLETE。
                            withContext(NonCancellable) {
                                finishOnce(
                                    assistantId = assistantId,
                                    content = streamingText,
                                    status = AiMessageStatus.COMPLETE,
                                    errorCode = null,
                                )
                            }
                            nextPhase = AiPhase.Idle
                        }
                    }
                }
            }
            // 流正常结束但没有 COMPLETE 标记：按断流失败处理，不伪造完成。
            if (!terminalWritten) {
                throw AiGatewayException(AiError.StreamInterrupted)
            }
        } catch (cancelled: CancellationException) {
            if (!terminalWritten) {
                // 停止：用户停止或清除触发取消；部分文本以 STOPPED 落库后再向上传播取消。
                withContext(NonCancellable) {
                    finishOnce(
                        assistantId = assistantId,
                        content = streamingText,
                        status = AiMessageStatus.STOPPED,
                        errorCode = null,
                    )
                }
                nextPhase = AiPhase.Stopped
            }
            throw cancelled
        } catch (failure: AiGatewayException) {
            if (!terminalWritten) {
                val code = failure.error.toErrorCode()
                withContext(NonCancellable) {
                    finishOnce(
                        assistantId = assistantId,
                        content = streamingText,
                        status = AiMessageStatus.FAILED,
                        errorCode = code,
                    )
                }
                nextPhase = AiPhase.Failed(code)
            }
        } catch (_: Exception) {
            // 未预期异常也按失败处理，保留已收到的部分文本；不自动重试。
            if (!terminalWritten) {
                withContext(NonCancellable) {
                    finishOnce(
                        assistantId = assistantId,
                        content = streamingText,
                        status = AiMessageStatus.FAILED,
                        errorCode = AiErrorCode.UNKNOWN,
                    )
                }
                nextPhase = AiPhase.Failed(AiErrorCode.UNKNOWN)
            }
        } finally {
            _state.update {
                it.copy(
                    phase = nextPhase,
                    streamingMessageId = null,
                    streamingText = "",
                )
            }
            // 门闩由 trySend 中注册的 invokeOnCompletion 统一释放，覆盖“未执行即取消”的情形。
        }
    }

    /**
     * 写入助手终态；只允许一次。
     *
     * 成功或非取消失败后置位 [terminalWritten]，避免重复写入；调用方用 `NonCancellable`
     * 保证终态写入不被停止/清除打断。若写入被取消打断（未置位），停止分支会以 STOPPED 兜底。
     */
    private suspend fun finishOnce(
        assistantId: AiMessageId,
        content: String,
        status: AiMessageStatus,
        errorCode: AiErrorCode?,
    ) {
        if (terminalWritten) return
        try {
            repository.finishAssistant(
                messageId = assistantId,
                content = content,
                status = status,
                errorCode = errorCode,
                at = clock.now(),
            )
            terminalWritten = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 终态落库失败：保留内存终态展示，重启后由 markInterruptedAsStopped 兜底，
            // 不因写库异常重复尝试或卡住门闩释放。
            terminalWritten = true
        }
    }

    /**
     * 构造请求：固定系统提示 + 本次对话中全部 USER 消息与仅 COMPLETE 的 ASSISTANT 消息。
     *
     * 不含 STOPPED/FAILED 助手的部分文本，也不含页面、筛选、日期或可见内容。
     */
    private fun buildRequest(userText: String, userMessageId: AiMessageId): AiRequest {
        val historyTurns = _state.value.messages
            // 新用户消息与助手占位行的 id 更大；只取本轮的之前消息，避免重复。
            .filter { it.id.value < userMessageId.value }
            .filter { it.isSendableHistory() }
            .sortedBy { it.id.value }
            .map { AiTurn(role = it.role, content = it.content) }

        return AiRequest(
            systemPrompt = AiSystemPrompt.TEXT,
            turns = historyTurns + AiTurn(role = AiRole.USER, content = userText),
        )
    }

    private fun AiMessage.isSendableHistory(): Boolean = when (role) {
        // 用户消息只写 COMPLETE；仍按角色纳入。
        AiRole.USER -> true
        // 助手只发送完整回复，停止/失败的部分文本不作为上下文。
        AiRole.ASSISTANT -> status == AiMessageStatus.COMPLETE
    }
}
