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
 * 任务截止时间与逾期判定规则（FR-G-09、BR-38、R5-5）。
 *
 * 纯 Kotlin 实现，不依赖 Android/Compose；仅负责只读判定与展示文案格式化，
 * 不修改任务状态、不自动结项。
 */
object TaskTimeRules {

    private val TIME_24H_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
