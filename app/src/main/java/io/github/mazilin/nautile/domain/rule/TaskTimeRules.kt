package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Task
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 严格 24 小时制 `HH:mm` 时间文本解析结果（BR-70、R5-3）。
 *
 * - [Empty]：去首尾空白后为空字符串；
 * - [Valid]：精确匹配两位时（00–23）+ 冒号 + 两位分（00–59）；
 * - [Invalid]：格式不符或数值越界，携带中文错误提示，绝不静默修正。
 */
sealed interface StrictTimeParseResult {
    data object Empty : StrictTimeParseResult
    data class Valid(val time: LocalTime) : StrictTimeParseResult
    data class Invalid(val message: String) : StrictTimeParseResult
}

/**
 * 任务截止时间与逾期判定规则（FR-G-09、BR-38、BR-70、R5-3、R5-5）。
 *
 * 纯 Kotlin 实现，不依赖 Android/Compose；仅负责只读判定、严格 24h 时间校验与展示文案格式化，
 * 不修改任务状态、不自动结项。
 */
object TaskTimeRules {

    private val TIME_24H_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val STRICT_24H_REGEX: Regex = Regex("""^([01][0-9]|2[0-3]):[0-5][0-9]$""")

    /** 24 小时制时间格式或范围非法时的统一中文提示。 */
    const val INVALID_TIME_MESSAGE: String = "请输入 00:00–23:59 的 24 小时制时间"

    /**
     * 严格校验 24 小时制 `HH:mm` 时间文本（BR-70、R5-3）。
     *
     * - 首尾空白 trim 后为空返回 [StrictTimeParseResult.Empty]；
     * - 仅接受精确两位小时（00–23）+ 半角冒号 + 精确两位分钟（00–59）；
     * - 任何越界或格式不符（如 `24:00`、`25:00`、`12:60`、`9:30`、`09:30 AM`、`abc`、`1230`、`-1:00`）
     *   均返回 [StrictTimeParseResult.Invalid]，绝不静默修正。
     */
    fun parseStrict24hTime(raw: String): StrictTimeParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return StrictTimeParseResult.Empty
        if (!STRICT_24H_REGEX.matches(trimmed)) {
            return StrictTimeParseResult.Invalid(INVALID_TIME_MESSAGE)
        }
        val hour = trimmed.substring(0, 2).toInt()
        val minute = trimmed.substring(3, 5).toInt()
        return StrictTimeParseResult.Valid(LocalTime.of(hour, minute))
    }

    /** 将 [LocalTime] 格式化为 24 小时制 `HH:mm`。 */
    fun format24hTime(time: LocalTime): String = time.format(TIME_24H_FORMATTER)

    /**
     * 判断任务在 [now] 时刻是否已逾期（BR-38）。
     *
     * - 已结项（[Task.position] 为 [GtdPosition.ENDED] 或 [Task.closedOutcome] 非空）恒返回 false；
     * - 未设置 [Task.dueDate] 恒返回 false；
     * - 仅设置 [Task.dueDate] 时，按 [zoneId] 换算本地日期，`dueDate < today` 视为逾期；
     * - 同时设置 [Task.dueDate] 与 [Task.dueTime] 时，按 [zoneId] 换算为时刻，严格早于 [now] 视为逾期。
     */
    fun isOverdue(
        task: Task,
        now: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (task.position == GtdPosition.ENDED || task.closedOutcome != null) return false
        val dueDate = task.dueDate ?: return false
        val dueTime = task.dueTime
        return if (dueTime != null) {
            LocalDateTime.of(dueDate, dueTime).atZone(zoneId).toInstant().isBefore(now)
        } else {
            dueDate.isBefore(now.atZone(zoneId).toLocalDate())
        }
    }

    /**
     * 格式化任务行截止时间展示文案（03_交互与动效规格 §2.2、R5-5）。
     *
     * - 无 [dueDate] 返回 null；
     * - 传入参考日期 [today] 时支持“今天”/“明天”简写，其余格式化为“M月d日”；
     * - 有 [dueTime] 时附 24 小时制“ HH:mm”。
     */
    fun formatTaskDueLabel(
        dueDate: LocalDate?,
        dueTime: LocalTime?,
        today: LocalDate? = null,
    ): String? {
        val date = dueDate ?: return null
        val dateText = when {
            today != null && date == today -> "今天"
            today != null && date == today.plusDays(1) -> "明天"
            else -> "${date.monthValue}月${date.dayOfMonth}日"
        }
        val timeText = dueTime?.format(TIME_24H_FORMATTER)
        return if (timeText == null) dateText else "$dateText $timeText"
    }
}
