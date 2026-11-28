package io.github.mazilin.nautile.ui.screen.log

import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 日志页模式；本轮只实现记录模式，统计属 G5。 */
enum class LogMode { RECORD, STATS }

/** 运行中计时（架构 §5.2）：[elapsed] 由 `max(0, now - startAt)` 计算。 */
data class RunningTimerUi(
    val logId: TimeLogId,
    val categoryId: TimeCategoryId,
    val categoryName: String,
    val parentName: String?,
    val startedAt: Instant,
    val elapsed: Duration,
)

/** 记录行（架构 §5.2）：分类路径、时长与运行中标记由状态层算好。 */
data class TimeLogRow(
    val log: TimeLog,
    val categoryPath: String,
    val duration: Duration,
    val running: Boolean,
)

/**
 * 日志记录页状态（架构 §5.2；05 §2.4/§3.3）。
 *
 * 只承载记录模式数据；统计属 G5，不在此伪造。
 */
data class LogUiState(
    val mode: LogMode = LogMode.RECORD,
    val today: LocalDate = LocalDate.now(),
    val logs: List<TimeLogRow> = emptyList(),
    val categories: List<TimeCategory> = emptyList(),
    val recentCategoryIds: List<TimeCategoryId> = emptyList(),
    val running: List<RunningTimerUi> = emptyList(),
    /** 当前时刻：有运行中时每秒更新；无运行中不启动 tick（架构 §7.3）。 */
    val now: Instant = Instant.now(),
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val isLoading: Boolean = false,
    val message: String? = null,
) {

    val isEmpty: Boolean get() = logs.isEmpty() && running.isEmpty()

    /** 最近使用优先的快捷入口；未使用过时为空，只显示“全部分类”。 */
    val recentCategories: List<TimeCategory>
        get() {
            val byId = categories.associateBy { it.id }
            return recentCategoryIds.mapNotNull { byId[it] }
        }

    fun categoryPath(categoryId: TimeCategoryId?): String = categoryPath(categories, categoryId)

    fun categoryName(categoryId: TimeCategoryId?): String =
        categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name } ?: ""
}

/**
 * 日志补录/新建草稿（05 §3.3.1 A）。
 *
 * 分类、日期、起止时间必填；结束必须严格晚于开始；非法时留在面板并提示，不写库。
 * [endDate] 默认与 [startDate] 相同；补录面板只展示单一行“日期”，跨天仅由计时产生。
 */
data class LogEditorUiState(
    val categoryId: TimeCategoryId? = null,
    val startDate: LocalDate,
    val endDate: LocalDate = startDate,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val isSaving: Boolean = false,
    val saveError: String? = null,
) {

    val categoryError: String? get() = if (categoryId == null) "请选择分类" else null

    fun startInstant(zoneId: ZoneId): Instant? =
        startTime?.let { startDate.atTime(it).atZone(zoneId).toInstant() }

    fun endInstant(zoneId: ZoneId): Instant? =
        endTime?.let { endDate.atTime(it).atZone(zoneId).toInstant() }

    val rangeError: String?
        get() {
            val start = startTime ?: return "请设置开始时间"
            val end = endTime ?: return "请设置结束时间"
            val startDateTime = startDate.atTime(start)
            val endDateTime = endDate.atTime(end)
            return if (endDateTime.isAfter(startDateTime)) null else "结束时间必须晚于开始时间"
        }

    val validationMessage: String? get() = categoryError ?: rangeError

    val isValid: Boolean get() = validationMessage == null

    val canDismiss: Boolean get() = isValid && !isSaving
}

/**
 * 日志详情草稿（05 §3.3.1 C）：字段直接编辑、编辑即保存；无效阻止离开。
 *
 * [running] 为 true 时字段只提示“请先结束计时再编辑”，不静默结束。
 */
data class LogDetailUiState(
    val logId: TimeLogId,
    val categoryId: TimeCategoryId,
    val startDate: LocalDate,
    val startTime: LocalTime?,
    val endDate: LocalDate,
    val endTime: LocalTime?,
    val running: Boolean,
    val isSaving: Boolean = false,
    val saveError: String? = null,
) {

    fun startInstant(zoneId: ZoneId): Instant? =
        startTime?.let { startDate.atTime(it).atZone(zoneId).toInstant() }

    fun endInstant(zoneId: ZoneId): Instant? =
        endTime?.let { endDate.atTime(it).atZone(zoneId).toInstant() }

    val rangeError: String?
        get() {
            if (running) return null
            val start = startTime ?: return "请设置开始时间"
            val end = endTime ?: return "请设置结束时间"
            val startDateTime = startDate.atTime(start)
            val endDateTime = endDate.atTime(end)
            return if (endDateTime.isAfter(startDateTime)) null else "结束时间必须晚于开始时间"
        }

    val isValid: Boolean get() = running || rangeError == null

    /** 运行中可关闭查看；编辑中必须字段有效才可离开。 */
    val canDismiss: Boolean get() = !isSaving && isValid
}

/** 分类路径：“父 / 子”或“父级”；找不到返回空串，不伪造名称。 */
internal fun categoryPath(categories: List<TimeCategory>, categoryId: TimeCategoryId?): String {
    if (categoryId == null) return ""
    val category = categories.firstOrNull { it.id == categoryId } ?: return ""
    val parent = category.parentId?.let { parentId -> categories.firstOrNull { it.id == parentId } }
    return if (parent == null) category.name else "${parent.name} / ${category.name}"
}

/** 本地时刻 `HH:mm`。 */
internal fun formatLogClock(instant: Instant, zoneId: ZoneId): String =
    instant.atZone(zoneId).toLocalTime().format(LOG_CLOCK_FORMATTER)

/** 日期分组标题：今天/昨天/`M月d日`。 */
internal fun formatLogDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "今天"
    today.minusDays(1) -> "昨天"
    else -> "${date.monthValue}月${date.dayOfMonth}日"
}

/** 记录时长：`1h 00m` / `30m`；不足 1 分钟显示 `0m`。 */
internal fun formatDurationText(duration: Duration): String {
    val totalMinutes = duration.toMinutes().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h %02dm".format(minutes) else "${minutes}m"
}

/** 运行中已用：`HH:mm:ss`（小时可超过 24）。 */
internal fun formatElapsedText(duration: Duration): String {
    val totalSeconds = duration.seconds.coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

/** 记录行副标题：`14:00–15:00`；运行中为 `14:00–计时中` 由列表另行标示。 */
internal fun formatLogRange(row: TimeLogRow, zoneId: ZoneId): String {
    val start = formatLogClock(row.log.startAt, zoneId)
    val end = row.log.endAt?.let { formatLogClock(it, zoneId) } ?: "计时中"
    return "$start–$end"
}

private val LOG_CLOCK_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
