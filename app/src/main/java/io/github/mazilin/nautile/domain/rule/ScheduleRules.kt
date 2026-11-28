package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleTag
import java.time.Instant

/**
 * 日程基础规则（架构 §3.5）。
 *
 * 纯 Kotlin + java.time，无 Android import；标题/时间合法性与默认标签在此集中，
 * 供用例与 UI 复用，避免两处各写一套判断。
 */
object ScheduleRules {

    /** 标题必须非空白（去除首尾后非空）。 */
    fun isValidTitle(title: String): Boolean = title.isNotBlank()

    /** 结束必须严格晚于开始；允许跨天。 */
    fun isValidRange(startAt: Instant, endAt: Instant): Boolean = endAt.isAfter(startAt)

    /**
     * 默认标签：课程来源挂系统“课程表”，普通来源为无标签（架构 §3.5）。
     *
     * 本轮新建只允许普通来源，本函数为未来导入与显示保留；普通日程手动选择标签不改来源。
     */
    fun defaultTag(source: ScheduleSource, systemCourseTag: ScheduleTag?): Long? =
        if (source == ScheduleSource.COURSE) systemCourseTag?.id?.value else null
}
