package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import java.time.Instant
import java.time.LocalTime

/**
 * 日程基础规则（架构 §3.5）。
 *
 * 纯 Kotlin + java.time，无 Android import；标题/时间合法性与默认标签在此集中，
 * 供用例与 UI 复用，避免两处各写一套判断。
 */
object ScheduleRules {

    /** 严格 24 小时 HH:mm：小时 00–23、分钟 00–59，不接受单位数小时。 */
    private val TIME_TEXT_PATTERN = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")

    /** 时间输入非法时的统一内联提示；不静默修正用户输入（AC-M-01 辅助核对）。 */
    const val TIME_FORMAT_ERROR = "时间需为 24 小时制 HH:mm（00:00–23:59）"

    /** 标题必须非空白（去除首尾后非空）。 */
    fun isValidTitle(title: String): Boolean = title.isNotBlank()

    /** 结束必须严格晚于开始；允许跨天。 */
    fun isValidRange(startAt: Instant, endAt: Instant): Boolean = endAt.isAfter(startAt)

    /**
     * 解析严格 24h `HH:mm`；不合法返回 null。
     *
     * 只接受两位小时与两位分钟（`09:05` 合法，`9:05`、`25:00`、`12:60`、非数字均非法）。
     */
    fun parseTimeText(text: String): LocalTime? {
        val match = TIME_TEXT_PATTERN.matchEntire(text.trim()) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        return LocalTime.of(hour, minute)
    }

    /** 严格 24h 文本校验；合法返回 null，非法返回统一提示。 */
    fun timeTextError(text: String): String? =
        if (parseTimeText(text) == null) TIME_FORMAT_ERROR else null

    /**
     * 默认标签：导入来源默认挂系统“课程表”，手动/AI 来源为无标签（R7 §2.3）。
     *
     * 手动日程修改标签不改变来源。
     */
    fun defaultTag(source: ScheduleSource, systemCourseTag: ScheduleTag?): Long? =
        if (source == ScheduleSource.IMPORTED) systemCourseTag?.id?.value else null

    /**
     * 按多选标签筛选日程（纯函数，委托至 [io.github.mazilin.nautile.domain.rule.filterByTags]）。
     */
    fun filterByTags(
        events: List<ScheduleEvent>,
        filter: ScheduleTagFilter,
        courseTagId: ScheduleTagId? = null,
    ): List<ScheduleEvent> = io.github.mazilin.nautile.domain.rule.filterByTags(
        events = events,
        filter = filter,
        courseTagId = courseTagId,
    )
}
