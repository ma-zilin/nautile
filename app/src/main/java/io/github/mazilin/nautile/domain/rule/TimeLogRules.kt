package io.github.mazilin.nautile.domain.rule

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日志/计时纯规则（架构 §7.1、§7.2 与任务书硬约束）。
 *
 * 全部使用注入的 `now` 与 `zoneId`，不读系统时钟；不产生负数与补时。
 */
object TimeLogRules {

    /** 运行中已用时长：`max(0, now - start)`；设备时间回拨时按 0 处理。 */
    fun elapsed(startAt: Instant, now: Instant): Duration =
        Duration.between(startAt, now).let { if (it.isNegative) Duration.ZERO else it }

    /** 结束时间：`max(now, start)`；不补时、不产生负数。 */
    fun stopEndAt(startAt: Instant, now: Instant): Instant =
        if (now.isBefore(startAt)) startAt else now

    /** 已完成记录时长；结束早于开始时按 0 处理（防御损坏数据）。 */
    fun duration(startAt: Instant, endAt: Instant): Duration =
        Duration.between(startAt, endAt).let { if (it.isNegative) Duration.ZERO else it }

    /** 记录所属本地日期（按开始时刻与注入时区）。 */
    fun startDate(logStartAt: Instant, zoneId: ZoneId): LocalDate =
        logStartAt.atZone(zoneId).toLocalDate()
}
