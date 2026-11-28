package io.github.mazilin.nautile.ui.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mazilin.nautile.domain.model.LocalDateRange
import io.github.mazilin.nautile.domain.model.ScheduleSourceFilter
import io.github.mazilin.nautile.domain.model.ScheduleViewMode
import io.github.mazilin.nautile.domain.model.TimeRange
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.ScheduleRepository
import io.github.mazilin.nautile.domain.rule.ScheduleLayout
import io.github.mazilin.nautile.domain.rule.ScheduleRangeRules
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

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
) : ViewModel() {

    private val selectedDate = MutableStateFlow(clock.now().atZone(zoneId).toLocalDate())
    private val viewMode = MutableStateFlow(ScheduleViewMode.DAY)
    private val sourceFilter = MutableStateFlow(ScheduleSourceFilter.MERGED)
    private val message = MutableStateFlow<String?>(null)

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
