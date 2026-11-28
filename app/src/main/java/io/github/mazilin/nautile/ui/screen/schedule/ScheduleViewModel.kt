package io.github.mazilin.nautile.ui.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mazilin.nautile.domain.model.LocalDateRange
import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleSourceFilter
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.model.ScheduleViewMode
import io.github.mazilin.nautile.domain.model.TimeRange
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.ScheduleRepository
import io.github.mazilin.nautile.domain.rule.ScheduleLayout
import io.github.mazilin.nautile.domain.rule.ScheduleRangeRules
import io.github.mazilin.nautile.domain.rule.ScheduleRules
import io.github.mazilin.nautile.domain.usecase.ScheduleCommandResult
import io.github.mazilin.nautile.domain.usecase.ScheduleCommands
import io.github.mazilin.nautile.domain.usecase.ScheduleError
import io.github.mazilin.nautile.domain.usecase.ScheduleFieldUpdate
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 日程页状态与事件入口（R6-G2-T3）。
 *
 * 可见范围、来源筛选与日期选择只在内存：来源筛选默认“合并”、返回页面不保留（架构 §1.1）。
 * 标签只随事件读取用于显示，不做标签筛选（D-new1）。切换视图不改变数据，只改变可见范围。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val clock: Clock = Clock { Instant.now() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val commands: ScheduleCommands = ScheduleCommands(repository, clock),
) : ViewModel() {

    private val selectedDate = MutableStateFlow(clock.now().atZone(zoneId).toLocalDate())
    private val viewMode = MutableStateFlow(ScheduleViewMode.DAY)
    private val sourceFilter = MutableStateFlow(ScheduleSourceFilter.MERGED)
    private val message = MutableStateFlow<String?>(null)

    // ---- 新建/详情编辑器（G3）：状态与写库解耦，UI 只读状态并发事件 ----
    private val editorState = MutableStateFlow<ScheduleEditorUiState?>(null)
    val editor: StateFlow<ScheduleEditorUiState?> = editorState.asStateFlow()

    /** 系统“课程表”标签；种子在迁移中写入，未加载到时标签选择等待查找。 */
    private var courseTagId: ScheduleTagId? = null

    /** 写库串行化：避免快速改期时多次写入乱序，产生非法中间状态。 */
    private val saveMutex = Mutex()

    /** 打开请求代号：关闭/取消后，未完成的异步加载不得再写回编辑器。 */
    private var editorRequest = 0L

    init {
        viewModelScope.launch {
            courseTagId = repository.findSystemTag(SYSTEM_COURSE_TAG)?.id
            editorState.update { it?.copy(courseTagId = courseTagId) }
        }
    }

    private val visibleRange = combine(selectedDate, viewMode) { date, mode ->
        ScheduleRangeRules.visibleRange(date, mode)
    }

    private val events = combine(visibleRange, sourceFilter) { range, filter ->
        range.toTimeRange() to filter
    }.flatMapLatest { (range, filter) ->
        repository.observeInRange(range, filter)
    }

    private val tags = repository.observeTags()

    /** 每分钟发射当前时刻，仅驱动“今天”的当前时间线；不写库（ANIM-S11）。 */
    private val ticker = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_INTERVAL_MILLIS)
        }
    }

    private data class Base(
        val range: LocalDateRange,
        val events: List<io.github.mazilin.nautile.domain.model.ScheduleEvent>,
        val tags: List<io.github.mazilin.nautile.domain.model.ScheduleTag>,
        val now: Instant,
    )

    private val base = combine(visibleRange, events, tags, ticker) { range, list, tags, now ->
        Base(range = range, events = list, tags = tags, now = now)
    }

    val uiState: StateFlow<ScheduleUiState> = combine(
        base,
        selectedDate,
        viewMode,
        sourceFilter,
        message,
    ) { base, date, mode, filter, message ->
        val blocks = ScheduleLayout.layoutRange(base.events, base.range, zoneId).map { layout ->
            ScheduleEventBlock(
                event = layout.event,
                dayIndex = layout.dayIndex,
                topFraction = layout.topFraction,
                heightFraction = layout.heightFraction,
                column = layout.column,
                columnCount = layout.columnCount,
            )
        }
        ScheduleUiState(
            today = clock.now().atZone(zoneId).toLocalDate(),
            selectedDate = date,
            viewMode = mode,
            visibleRange = base.range,
            rangeTitle = rangeTitle(base.range, mode),
            sourceFilter = filter,
            tags = base.tags,
            events = blocks,
            now = base.now,
            zoneId = zoneId,
            isLoading = false,
            message = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = initialState(),
    )

    /** 点击日期条选日期；只改变选中日，不改变来源筛选与数据。 */
    fun onDateSelected(date: LocalDate) {
        selectedDate.value = date
    }

    /** 左右日期按钮：按视图步进 1/3/7 天并选中目标日；边界不循环。 */
    fun onRangeStep(delta: Int) {
        selectedDate.value = ScheduleRangeRules.step(selectedDate.value, viewMode.value, delta)
    }

    /** 回到今天并选中今天。 */
    fun onToday() {
        selectedDate.value = clock.now().atZone(zoneId).toLocalDate()
    }

    /** 视图菜单三选一：立即生效，不轮换、无滑动手势。 */
    fun onViewModeSelected(mode: ScheduleViewMode) {
        viewMode.value = mode
        message.value = null
    }

    /** 来源筛选三态：只影响查询条件，不修改任何日程数据。 */
    fun onSourceFilterSelected(filter: ScheduleSourceFilter) {
        sourceFilter.value = filter
    }

    fun onDismissMessage() {
        message.value = null
    }

    // ---- 新建（G3-T1/T2）：默认取当前选中日期；今天从当前时刻起一小时 ----

    /** 打开新建面板；只有“创建”落库，取消不生成半成品（AC-D-07）。 */
    fun onOpenNew() {
        editorRequest++
        val now = clock.now().atZone(zoneId)
        val date = selectedDate.value
        val startTime = if (date == now.toLocalDate()) {
            now.toLocalTime().withSecond(0).withNano(0)
        } else {
            DEFAULT_START_TIME
        }
        val end = date.atTime(startTime).plusHours(1)
        editorState.value = ScheduleEditorUiState(
            mode = ScheduleEditorMode.CREATE,
            title = "",
            note = "",
            startDate = date,
            startTime = startTime,
            endDate = end.toLocalDate(),
            endTime = end.toLocalTime(),
            source = ScheduleSource.MANUAL,
            tagId = null,
            courseTagId = courseTagId,
        )
    }

    /** 打开详情：读取真实日程；读取完成前关闭/取消会作废本次请求。 */
    fun onOpenDetail(id: ScheduleId) {
        editorRequest++
        val request = editorRequest
        viewModelScope.launch {
            val event = repository.get(id) ?: return@launch
            if (request != editorRequest) return@launch
            editorState.value = event.toEditorState(
                mode = ScheduleEditorMode.EDIT,
                courseTagId = courseTagId,
            )
        }
    }

    /** 关闭编辑器（取消/创建成功/删除成功）；作废未完成的加载。 */
    fun closeEditor() {
        editorRequest++
        editorState.value = null
    }

    /**
     * 尝试关闭（遮罩/系统返回/关闭图标）。
     *
     * 无效字段留在面板并提示，返回 false；有效才清空状态并返回 true。
     */
    fun dismissEditor(): Boolean {
        editorRequest++
        val state = editorState.value ?: return true
        // 提交中不允许关闭，避免“创建/删除已发出但面板被关”的歧义。
        if (state.isSaving) return false
        if (!state.isValid) {
            editorState.value = state.copy(saveError = state.validationMessage)
            return false
        }
        // 关闭前补提交仍在输入框内的标题/备注草稿，避免“编辑即保存”漏掉最后一次输入。
        if (state.mode == ScheduleEditorMode.EDIT) {
            commitEditorTitle()
            commitEditorNote()
        }
        editorState.value = null
        return true
    }

    fun onEditorTitleChanged(value: String) {
        editorState.update { it?.copy(title = value, saveError = null) }
    }

    /** 标题字段结束（失焦/IME Done）：详情编辑即保存；非法只提示，不写库。 */
    fun commitEditorTitle() {
        val state = editorState.value ?: return
        if (!ScheduleRules.isValidTitle(state.title)) {
            editorState.update { it?.copy(saveError = state.titleError) }
            return
        }
        val id = state.scheduleId ?: return
        if (state.mode == ScheduleEditorMode.EDIT) {
            persist(id, ScheduleFieldUpdate.Title(state.title))
        }
    }

    fun onEditorNoteChanged(value: String) {
        editorState.update { it?.copy(note = value, saveError = null) }
    }

    fun commitEditorNote() {
        val state = editorState.value ?: return
        val id = state.scheduleId ?: return
        if (state.mode == ScheduleEditorMode.EDIT) {
            persist(id, ScheduleFieldUpdate.Note(state.note.ifBlank { null }))
        }
    }

    fun onEditorStartDateSelected(date: LocalDate) = updateDate(ScheduleEditorField.START, date)

    fun onEditorEndDateSelected(date: LocalDate) = updateDate(ScheduleEditorField.END, date)

    fun onEditorTimeConfirmed(field: ScheduleEditorField, time: LocalTime) =
        updateTime(field, time)

    /** 清除时间：回到“未设置”，不写非法值；起止必填，因此清除后阻止提交/关闭。 */
    fun onEditorTimeCleared(field: ScheduleEditorField) = updateTime(field, null)

    /** 选择“课程表”标签；来源仍为普通（AC-S-04）。 */
    fun onCourseTagSelected() {
        if (editorState.value == null) return
        val cached = courseTagId
        if (cached != null) {
            applyTag(cached)
            return
        }
        // 种子未加载时再查一次；查不到只提示，不伪造标签 id。
        viewModelScope.launch {
            courseTagId = repository.findSystemTag(SYSTEM_COURSE_TAG)?.id
            val resolved = courseTagId
            if (resolved == null) {
                editorState.update { it?.copy(saveError = "课程表标签不可用") }
            } else {
                editorState.update { it?.copy(courseTagId = resolved) }
                applyTag(resolved)
            }
        }
    }

    /** 选择“无标签”；来源不变，不参与筛选（D-new1）。 */
    fun onNoTagSelected() = applyTag(null)

    /** 创建：校验失败留在面板；成功回调关闭宿主，原位插入不跳页（AC-D-07）。 */
    fun createNew(onCreated: () -> Unit) {
        val state = editorState.value ?: return
        if (!state.isValid) {
            editorState.update { it?.copy(saveError = state.validationMessage) }
            return
        }
        val startAt = state.startInstant() ?: return
        val endAt = state.endInstant() ?: return
        viewModelScope.launch {
            editorState.update { it?.copy(isSaving = true, saveError = null) }
            try {
                when (
                    val result = commands.create(
                        title = state.title,
                        note = state.note.ifBlank { null },
                        startAt = startAt,
                        endAt = endAt,
                        tagId = state.tagId,
                        source = ScheduleSource.MANUAL,
                    )
                ) {
                    is ScheduleCommandResult.Success -> {
                        editorRequest++
                        editorState.value = null
                        onCreated()
                    }

                    is ScheduleCommandResult.Invalid -> editorState.update {
                        it?.copy(isSaving = false, saveError = result.error.message())
                    }

                    else -> editorState.update {
                        it?.copy(isSaving = false, saveError = "创建失败，请重试")
                    }
                }
            } catch (error: Exception) {
                // G4-T0：仓库异常转为错误态提示，不崩溃、不显示成功。
                editorState.update { it?.copy(isSaving = false, saveError = "创建失败，请重试") }
            }
        }
    }

    /** 删除：确认对话框之后调用；失败保留面板并报错。 */
    fun deleteSchedule(onDeleted: () -> Unit) {
        val state = editorState.value ?: return
        val id = state.scheduleId ?: return
        viewModelScope.launch {
            editorState.update { it?.copy(isSaving = true, saveError = null) }
            try {
                when (val result = commands.delete(id)) {
                    is ScheduleCommandResult.Deleted -> {
                        editorRequest++
                        editorState.value = null
                        onDeleted()
                    }

                    is ScheduleCommandResult.NotFound -> editorState.update {
                        it?.copy(isSaving = false, saveError = "日程不存在")
                    }

                    else -> editorState.update {
                        it?.copy(isSaving = false, saveError = "删除失败，请重试")
                    }
                }
            } catch (error: Exception) {
                // G4-T0：仓库异常转为错误态提示，不崩溃、不显示成功。
                editorState.update { it?.copy(isSaving = false, saveError = "删除失败，请重试") }
            }
        }
    }

    private fun updateDate(field: ScheduleEditorField, date: LocalDate) {
        editorState.update {
            when (field) {
                ScheduleEditorField.START -> it?.copy(startDate = date, saveError = null)
                ScheduleEditorField.END -> it?.copy(endDate = date, saveError = null)
            }
        }
        persistRangeIfEditing()
    }

    private fun updateTime(field: ScheduleEditorField, time: LocalTime?) {
        editorState.update {
            when (field) {
                ScheduleEditorField.START -> it?.copy(startTime = time, saveError = null)
                ScheduleEditorField.END -> it?.copy(endTime = time, saveError = null)
            }
        }
        persistRangeIfEditing()
    }

    /** 详情中改日期/时间：起止同时提交，先校验后写库；无效不写。 */
    private fun persistRangeIfEditing() {
        val state = editorState.value ?: return
        val id = state.scheduleId ?: return
        if (state.mode != ScheduleEditorMode.EDIT) return
        if (!state.isValid) return
        val startAt = state.startInstant() ?: return
        val endAt = state.endInstant() ?: return
        persist(id, ScheduleFieldUpdate.Range(startAt, endAt))
    }

    private fun applyTag(tagId: ScheduleTagId?) {
        val state = editorState.value ?: return
        if (tagId != null && tagId != courseTagId) return
        editorState.update { it?.copy(tagId = tagId, saveError = null) }
        val id = state.scheduleId ?: return
        if (state.mode == ScheduleEditorMode.EDIT) {
            persist(id, ScheduleFieldUpdate.Tag(tagId))
        }
    }

    private fun persist(id: ScheduleId, update: ScheduleFieldUpdate) {
        viewModelScope.launch {
            saveMutex.withLock {
                // 串行写入时用最新草稿覆盖同字段，避免快速连续输入被旧值回写。
                val effective = latestFieldValue(update)
                try {
                    when (val result = commands.updateField(id, effective)) {
                        is ScheduleCommandResult.Success -> editorState.update {
                            it?.copy(isSaving = false, saveError = null)
                        }

                        is ScheduleCommandResult.Invalid -> editorState.update {
                            it?.copy(isSaving = false, saveError = result.error.message())
                        }

                        is ScheduleCommandResult.NotFound -> editorState.update {
                            it?.copy(isSaving = false, saveError = "日程不存在")
                        }

                        else -> editorState.update {
                            it?.copy(isSaving = false, saveError = "保存失败，请重试")
                        }
                    }
                } catch (error: Exception) {
                    // G4-T0：仓库异常转为错误态提示，不崩溃、不显示成功。
                    editorState.update {
                        it?.copy(isSaving = false, saveError = "保存失败，请重试")
                    }
                }
            }
        }
    }

    /** 同字段取当前草稿的最新合法值；编辑器已关闭时沿用提交时的快照。 */
    private fun latestFieldValue(update: ScheduleFieldUpdate): ScheduleFieldUpdate {
        val latest = editorState.value ?: return update
        return when (update) {
            is ScheduleFieldUpdate.Title -> if (ScheduleRules.isValidTitle(latest.title)) {
                ScheduleFieldUpdate.Title(latest.title)
            } else {
                update
            }

            is ScheduleFieldUpdate.Note -> ScheduleFieldUpdate.Note(latest.note.ifBlank { null })

            is ScheduleFieldUpdate.Range -> {
                val start = latest.startInstant()
                val end = latest.endInstant()
                if (latest.isValid && start != null && end != null) {
                    ScheduleFieldUpdate.Range(start, end)
                } else {
                    update
                }
            }

            is ScheduleFieldUpdate.Tag -> ScheduleFieldUpdate.Tag(latest.tagId)

            is ScheduleFieldUpdate.Start,
            is ScheduleFieldUpdate.End,
            -> update
        }
    }

    private fun ScheduleEditorUiState.startInstant(): Instant? =
        startDateTime?.atZone(zoneId)?.toInstant()

    private fun ScheduleEditorUiState.endInstant(): Instant? =
        endDateTime?.atZone(zoneId)?.toInstant()

    private fun ScheduleEvent.toEditorState(
        mode: ScheduleEditorMode,
        courseTagId: ScheduleTagId?,
    ): ScheduleEditorUiState {
        val start = startAt.atZone(zoneId).toLocalDateTime()
        val end = endAt.atZone(zoneId).toLocalDateTime()
        return ScheduleEditorUiState(
            mode = mode,
            scheduleId = id,
            title = title,
            note = note.orEmpty(),
            startDate = start.toLocalDate(),
            startTime = start.toLocalTime(),
            endDate = end.toLocalDate(),
            endTime = end.toLocalTime(),
            // R7 #11：详情只读展示日程真实来源，编辑其他字段绝不篡改来源。
            source = this.source,
            tagId = tagId,
            courseTagId = courseTagId,
        )
    }

    private fun ScheduleError.message(): String = when (this) {
        ScheduleError.BLANK_TITLE -> "标题不能为空"
        ScheduleError.END_NOT_AFTER_START -> "结束时间必须晚于开始时间"
        ScheduleError.UNSUPPORTED_SOURCE -> "本轮仅支持普通来源"
        ScheduleError.TAG_NOT_FOUND -> "标签不可用"
    }

    private fun initialState(): ScheduleUiState {
        val now = clock.now()
        val today = now.atZone(zoneId).toLocalDate()
        val range = ScheduleRangeRules.visibleRange(today, ScheduleViewMode.DAY)
        return ScheduleUiState(
            today = today,
            selectedDate = today,
            viewMode = ScheduleViewMode.DAY,
            visibleRange = range,
            rangeTitle = rangeTitle(range, ScheduleViewMode.DAY),
            sourceFilter = ScheduleSourceFilter.MERGED,
            now = now,
            zoneId = zoneId,
            isLoading = true,
        )
    }

    private fun LocalDateRange.toTimeRange(): TimeRange = TimeRange(
        startInclusive = startInclusive.atStartOfDay(zoneId).toInstant(),
        endExclusive = endInclusive.plusDays(1).atStartOfDay(zoneId).toInstant(),
    )

    private fun rangeTitle(range: LocalDateRange, mode: ScheduleViewMode): String {
        val start = range.startInclusive
        val end = range.endInclusive
        return when (mode) {
            ScheduleViewMode.DAY -> {
                val weekday = start.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
                "${start.year}年${start.monthValue}月${start.dayOfMonth}日 $weekday"
            }

            ScheduleViewMode.THREE_DAY,
            ScheduleViewMode.WEEK,
            -> "${start.monthValue}月${start.dayOfMonth}日 – " +
                "${end.monthValue}月${end.dayOfMonth}日"
        }
    }

    private companion object {
        const val TICK_INTERVAL_MILLIS = 60_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SYSTEM_COURSE_TAG = "COURSE"
        val DEFAULT_START_TIME: LocalTime = LocalTime.of(9, 0)
    }
}

/** 供 UI 显示的视图名称。 */
fun ScheduleViewMode.displayLabel(): String = when (this) {
    ScheduleViewMode.DAY -> "日"
    ScheduleViewMode.THREE_DAY -> "三天"
    ScheduleViewMode.WEEK -> "周"
}

/** 供 UI 显示的来源筛选名称。 */
fun ScheduleSourceFilter.displayLabel(): String = when (this) {
    ScheduleSourceFilter.COURSE_ONLY -> "课程表"
    ScheduleSourceFilter.MERGED -> "合并"
    ScheduleSourceFilter.REGULAR_ONLY -> "仅普通"
}

/** 星期中文简称（日期条与列头使用）。 */
internal fun DayOfWeek.shortChinese(): String = when (this) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}
