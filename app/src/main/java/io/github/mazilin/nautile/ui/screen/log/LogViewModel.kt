package io.github.mazilin.nautile.ui.screen.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.domain.model.TimerMode
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import io.github.mazilin.nautile.domain.repository.TimeCategoryRepository
import io.github.mazilin.nautile.domain.repository.TimeLogRepository
import io.github.mazilin.nautile.domain.rule.TimeLogRules
import io.github.mazilin.nautile.domain.usecase.TimeLogCommandResult
import io.github.mazilin.nautile.domain.usecase.TimeLogCommands
import io.github.mazilin.nautile.domain.usecase.TimeLogError
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 日志记录页状态与事件入口（R6-G4-T3/T4；架构 §5.2、§7.1、§7.2）。
 *
 * - 记录列表按开始时间倒序；运行中记录显示“计时中”和墙钟已用；
 * - 计时只用注入的 [Clock]；有运行中才每秒 tick，无运行中不空转；
 * - 计时状态来自 `end_at IS NULL`，进程被杀/重启后自动恢复，不出现暂停态；
 * - 开始/结束/补录/编辑/删除的仓库异常转为提示，不崩溃、不显示成功。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModel(
    private val logRepository: TimeLogRepository,
    private val categoryRepository: TimeCategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val commands: TimeLogCommands,
    private val clock: Clock = Clock { Instant.now() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    private val editorState = MutableStateFlow<LogEditorUiState?>(null)
    val editor: StateFlow<LogEditorUiState?> = editorState.asStateFlow()

    private val detailState = MutableStateFlow<LogDetailUiState?>(null)
    val detail: StateFlow<LogDetailUiState?> = detailState.asStateFlow()

    /** 详情异步加载代号：关闭/切换后旧请求不得回写。 */
    private var detailRequest = 0L

    /** 详情写库串行化：快速连续改字段时按最新草稿写入，避免旧值回写。 */
    private val detailSaveMutex = Mutex()

    private val logs = logRepository.observeAllDescending()
    private val categories = categoryRepository.observeAll()
    private val recentCategoryIds = logRepository.observeRecentCategoryIds(RECENT_LIMIT)
    private val runningLogs = logRepository.observeRunning()

    /** 有运行中每秒发射当前时刻；无运行中只发射一次，不启动空转 tick。 */
    private val tickerNow: Flow<Instant> = runningLogs.flatMapLatest { running ->
        if (running.isEmpty()) {
            flowOf(clock.now())
        } else {
            flow {
                while (true) {
                    emit(clock.now())
                    delay(TICK_INTERVAL_MILLIS)
                }
            }
        }
    }

    private data class Base(
        val logs: List<TimeLog>,
        val categories: List<io.github.mazilin.nautile.domain.model.TimeCategory>,
        val recentIds: List<TimeCategoryId>,
        val running: List<TimeLog>,
        val now: Instant,
    )

    private val base = combine(
        logs,
        categories,
        recentCategoryIds,
        runningLogs,
        tickerNow,
    ) { logList, categoryList, recent, running, now ->
        Base(logs = logList, categories = categoryList, recentIds = recent, running = running, now = now)
    }

    val uiState: StateFlow<LogUiState> = combine(base, message) { base, message ->
        val runningIds = base.running.map { it.id }.toSet()
        val rows = base.logs.map { log ->
            val duration = if (log.isRunning) {
                TimeLogRules.elapsed(log.startAt, base.now)
            } else {
                log.endAt?.let { TimeLogRules.duration(log.startAt, it) } ?: Duration.ZERO
            }
            TimeLogRow(
                log = log,
                categoryPath = categoryPath(base.categories, log.categoryId),
                duration = duration,
                running = log.id in runningIds,
            )
        }
        val running = base.running.map { log ->
            val category = base.categories.firstOrNull { it.id == log.categoryId }
            val parent = category?.parentId?.let { parentId ->
                base.categories.firstOrNull { it.id == parentId }
            }
            RunningTimerUi(
                logId = log.id,
                categoryId = log.categoryId,
                categoryName = category?.name.orEmpty(),
                parentName = parent?.name,
                startedAt = log.startAt,
                elapsed = TimeLogRules.elapsed(log.startAt, base.now),
            )
        }
        LogUiState(
            mode = LogMode.RECORD,
            today = base.now.atZone(zoneId).toLocalDate(),
            logs = rows,
            categories = base.categories,
            recentCategoryIds = base.recentIds,
            running = running,
            now = base.now,
            zoneId = zoneId,
            isLoading = false,
            message = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = LogUiState(
            today = clock.now().atZone(zoneId).toLocalDate(),
            now = clock.now(),
            zoneId = zoneId,
            isLoading = true,
        ),
    )

    fun onDismissMessage() {
        message.value = null
    }

    // ---- 计时（AC-T-21/22） ----

    /** 点分类快捷入口：开始计时；同分类运行中只提示，不重置、不结束。 */
    fun onCategoryQuickTap(categoryId: TimeCategoryId) {
        viewModelScope.launch {
            startTimer(categoryId)
        }
    }

    /** 点计时条对应分类：结束并保存；失败保留模块并提示。 */
    fun onRunningTimerTap(categoryId: TimeCategoryId) {
        viewModelScope.launch {
            stopTimer(categoryId)
        }
    }

    private suspend fun startTimer(categoryId: TimeCategoryId) {
        val mode = readTimerMode()
        try {
            when (val result = commands.startTimer(categoryId, mode)) {
                is TimeLogCommandResult.Success ->
                    message.value = "已开始计时：${categoryName(categoryId)}"

                is TimeLogCommandResult.AlreadyRunning -> message.value = "该分类已在计时"
                is TimeLogCommandResult.Invalid -> message.value = when (result.error) {
                    TimeLogError.CATEGORY_NOT_FOUND -> "分类不存在"
                    TimeLogError.UNSUPPORTED_MODE -> "单活动模式尚未开放"
                    TimeLogError.END_NOT_AFTER_START -> "开始失败，请重试"
                    TimeLogError.RUNNING_LOG -> "开始失败，请重试"
                }

                else -> message.value = "开始失败，请重试"
            }
        } catch (error: Exception) {
            message.value = "开始失败，请重试"
        }
    }

    private suspend fun stopTimer(categoryId: TimeCategoryId) {
        try {
            when (val result = commands.stopTimer(categoryId)) {
                is TimeLogCommandResult.Success -> {
                    val duration = result.log.endAt?.let {
                        TimeLogRules.duration(result.log.startAt, it)
                    } ?: Duration.ZERO
                    message.value =
                        "已结束并保存：${categoryName(categoryId)}，${formatDurationText(duration)}"
                }

                TimeLogCommandResult.NotRunning -> message.value = "该分类未在计时"
                else -> message.value = "结束失败，请重试"
            }
        } catch (error: Exception) {
            message.value = "结束失败，请重试"
        }
    }

    private suspend fun readTimerMode(): TimerMode = try {
        settingsRepository.settings.first().timerMode
    } catch (error: Exception) {
        // 设置读取失败回落默认并行；计时模式不是业务事实（架构 §4.2）。
        TimerMode.PARALLEL
    }

    private fun categoryName(categoryId: TimeCategoryId): String {
        val state = uiState.value
        return state.categories.firstOrNull { it.id == categoryId }?.name ?: ""
    }

    // ---- 补录/新建（AC-L-02） ----

    /** 打开补录面板；默认今天与当前时刻起 1 小时，保存前可改，不静默落库。 */
    fun onOpenManual() {
        val now = clock.now().atZone(zoneId)
        val startCandidate = now.toLocalTime().withSecond(0).withNano(0)
        val endCandidate = startCandidate.plusHours(1)
        // 补录面板使用单一行“日期”：跨午夜时回落到同日晚间，避免结束时间显示小于开始时间。
        val (start, end) = if (endCandidate.isAfter(LAST_MINUTE_OF_DAY)) {
            LocalTime.of(21, 0) to LocalTime.of(22, 0)
        } else {
            startCandidate to endCandidate
        }
        editorState.value = LogEditorUiState(
            categoryId = null,
            startDate = now.toLocalDate(),
            endDate = now.toLocalDate(),
            startTime = start,
            endTime = end,
        )
    }

    fun closeEditor() {
        editorState.value = null
    }

    fun dismissEditor(): Boolean {
        val state = editorState.value ?: return true
        if (state.isSaving) return false
        if (!state.isValid) {
            editorState.value = state.copy(saveError = state.validationMessage)
            return false
        }
        editorState.value = null
        return true
    }

    fun onEditorCategorySelected(categoryId: TimeCategoryId) {
        editorState.update { it?.copy(categoryId = categoryId, saveError = null) }
    }

    fun onEditorDateSelected(date: LocalDate) {
        editorState.update { it?.copy(startDate = date, endDate = date, saveError = null) }
    }

    fun onEditorTimeConfirmed(isStart: Boolean, time: LocalTime) {
        editorState.update {
            if (isStart) it?.copy(startTime = time, saveError = null)
            else it?.copy(endTime = time, saveError = null)
        }
    }

    fun onEditorTimeCleared(isStart: Boolean) {
        editorState.update {
            if (isStart) it?.copy(startTime = null, saveError = null)
            else it?.copy(endTime = null, saveError = null)
        }
    }

    fun createManual(onCreated: () -> Unit) {
        val state = editorState.value ?: return
        if (!state.isValid) {
            editorState.update { it?.copy(saveError = state.validationMessage) }
            return
        }
        val categoryId = state.categoryId ?: return
        val startAt = state.startInstant(zoneId) ?: return
        val endAt = state.endInstant(zoneId) ?: return
        viewModelScope.launch {
            editorState.update { it?.copy(isSaving = true, saveError = null) }
            try {
                when (val result = commands.createManual(categoryId, startAt, endAt)) {
                    is TimeLogCommandResult.Success -> {
                        editorState.value = null
                        onCreated()
                    }

                    is TimeLogCommandResult.Invalid -> editorState.update {
                        it?.copy(isSaving = false, saveError = result.error.message())
                    }

                    else -> editorState.update {
                        it?.copy(isSaving = false, saveError = "保存失败，请重试")
                    }
                }
            } catch (error: Exception) {
                editorState.update { it?.copy(isSaving = false, saveError = "保存失败，请重试") }
            }
        }
    }

    // ---- 详情（编辑即保存） ----

    fun onOpenLog(id: TimeLogId) {
        detailRequest++
        val request = detailRequest
        viewModelScope.launch {
            try {
                val log = logRepository.get(id) ?: run {
                    message.value = "记录不存在"
                    return@launch
                }
                if (request != detailRequest) return@launch
                detailState.value = log.toDetailState()
            } catch (error: Exception) {
                message.value = "读取记录失败，请重试"
            }
        }
    }

    fun closeDetail() {
        detailRequest++
        detailState.value = null
    }

    /** 无效字段阻止关闭并提示；运行中允许查看后关闭（不静默结束）。 */
    fun dismissDetail(): Boolean {
        detailRequest++
        val state = detailState.value ?: return true
        if (state.isSaving) return false
        if (!state.isValid) {
            detailState.value = state.copy(saveError = state.rangeError)
            return false
        }
        detailState.value = null
        return true
    }

    fun onDetailCategorySelected(categoryId: TimeCategoryId) {
        val state = detailState.value ?: return
        if (state.running) {
            detailState.value = state.copy(saveError = RUNNING_HINT)
            return
        }
        detailState.value = state.copy(categoryId = categoryId, saveError = null)
        persistDetail()
    }

    /** 改日期：起止日期整体平移，保留跨天关系；不静默改写已有结束日。 */
    fun onDetailDateSelected(date: LocalDate) {
        val state = detailState.value ?: return
        if (state.running) {
            detailState.value = state.copy(saveError = RUNNING_HINT)
            return
        }
        val delta = date.toEpochDay() - state.startDate.toEpochDay()
        detailState.value = state.copy(
            startDate = date,
            endDate = state.endDate.plusDays(delta),
            saveError = null,
        )
        persistDetail()
    }

    fun onDetailTimeConfirmed(isStart: Boolean, time: LocalTime) {
        val state = detailState.value ?: return
        if (state.running) {
            detailState.value = state.copy(saveError = RUNNING_HINT)
            return
        }
        detailState.value = if (isStart) {
            state.copy(startTime = time, saveError = null)
        } else {
            state.copy(endTime = time, saveError = null)
        }
        persistDetail()
    }

    fun onDetailTimeCleared(isStart: Boolean) {
        val state = detailState.value ?: return
        if (state.running) {
            detailState.value = state.copy(saveError = RUNNING_HINT)
            return
        }
        // 清空时间后字段无效：不写库，阻止离开，修正后才可保存。
        detailState.value = if (isStart) {
            state.copy(startTime = null, saveError = null)
        } else {
            state.copy(endTime = null, saveError = null)
        }
    }

    /** 运行中记录只提示，不静默结束、不写库。 */
    fun onDetailEditBlocked() {
        val state = detailState.value ?: return
        if (state.running) detailState.value = state.copy(saveError = RUNNING_HINT)
    }

    fun deleteLog(onDeleted: () -> Unit) {
        val state = detailState.value ?: return
        if (state.running) {
            detailState.value = state.copy(saveError = RUNNING_HINT)
            return
        }
        viewModelScope.launch {
            detailState.update { it?.copy(isSaving = true, saveError = null) }
            try {
                when (val result = commands.delete(state.logId)) {
                    is TimeLogCommandResult.Deleted -> {
                        detailRequest++
                        detailState.value = null
                        onDeleted()
                    }

                    is TimeLogCommandResult.NotFound -> detailState.update {
                        it?.copy(isSaving = false, saveError = "记录不存在")
                    }

                    else -> detailState.update {
                        it?.copy(isSaving = false, saveError = "删除失败，请重试")
                    }
                }
            } catch (error: Exception) {
                detailState.update { it?.copy(isSaving = false, saveError = "删除失败，请重试") }
            }
        }
    }

    /** 字段编辑结束即校验并保存；无效只提示，不写库（AC-D-06 记录分支）。 */
    private fun persistDetail() {
        val snapshot = detailState.value ?: return
        if (snapshot.running) return
        if (!snapshot.isValid) {
            detailState.value = snapshot.copy(saveError = snapshot.rangeError)
            return
        }
        viewModelScope.launch {
            detailSaveMutex.withLock {
                // 串行化后重新读取最新草稿，快速连续编辑以最后一次为准。
                val state = detailState.value ?: return@withLock
                if (state.running || !state.isValid) return@withLock
                if (state.isSaving) return@withLock
                val startAt = state.startInstant(zoneId) ?: return@withLock
                val endAt = state.endInstant(zoneId) ?: return@withLock
                detailState.update { it?.copy(isSaving = true, saveError = null) }
                try {
                    when (
                        val result = commands.update(
                            id = state.logId,
                            categoryId = state.categoryId,
                            startAt = startAt,
                            endAt = endAt,
                        )
                    ) {
                        is TimeLogCommandResult.Success -> detailState.update {
                            it?.copy(isSaving = false, saveError = null)
                        }

                        is TimeLogCommandResult.Invalid -> detailState.update {
                            it?.copy(isSaving = false, saveError = result.error.message())
                        }

                        is TimeLogCommandResult.NotFound -> detailState.update {
                            it?.copy(isSaving = false, saveError = "记录不存在")
                        }

                        else -> detailState.update {
                            it?.copy(isSaving = false, saveError = "保存失败，请重试")
                        }
                    }
                } catch (error: Exception) {
                    detailState.update { it?.copy(isSaving = false, saveError = "保存失败，请重试") }
                }
            }
        }
    }

    private fun TimeLog.toDetailState(): LogDetailUiState {
        val start = startAt.atZone(zoneId).toLocalDateTime()
        val end = endAt?.atZone(zoneId)?.toLocalDateTime()
        return LogDetailUiState(
            logId = id,
            categoryId = categoryId,
            startDate = start.toLocalDate(),
            startTime = start.toLocalTime(),
            endDate = end?.toLocalDate() ?: start.toLocalDate(),
            endTime = end?.toLocalTime(),
            running = isRunning,
        )
    }

    private fun TimeLogError.message(): String = when (this) {
        TimeLogError.CATEGORY_NOT_FOUND -> "分类不存在"
        TimeLogError.END_NOT_AFTER_START -> "结束时间必须晚于开始时间"
        TimeLogError.UNSUPPORTED_MODE -> "单活动模式尚未开放"
        TimeLogError.RUNNING_LOG -> RUNNING_HINT
    }

    private companion object {
        const val TICK_INTERVAL_MILLIS = 1_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val RECENT_LIMIT = 6
        const val RUNNING_HINT = "请先结束计时再编辑"
        val LAST_MINUTE_OF_DAY: LocalTime = LocalTime.of(23, 59)
    }
}
