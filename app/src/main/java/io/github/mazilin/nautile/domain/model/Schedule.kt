package io.github.mazilin.nautile.domain.model

import java.time.Instant
import java.time.LocalDate

/** 日程 ID 值类；以 Long 承载（架构 §3.1）。 */
@JvmInline
value class ScheduleId(val value: Long)

/** 日程标签 ID 值类；以 Long 承载（架构 §3.1）。 */
@JvmInline
value class ScheduleTagId(val value: Long)

/**
 * 日程来源（架构 §1.3 第 7 条、AC-S-04）。
 *
 * 本轮新建只允许 [REGULAR]；[COURSE] 仅由未来课程表导入产生，“合并”只是页面筛选，不入库。
 */
enum class ScheduleSource { COURSE, REGULAR }

/** 日程视图模式：日 / 三天 / 周（AC-S-03/13）。 */
enum class ScheduleViewMode { DAY, THREE_DAY, WEEK }

/** 来源筛选三态：课程表 / 合并 / 仅普通；只在 ViewModel 内存，不持久化。 */
enum class ScheduleSourceFilter { COURSE_ONLY, MERGED, REGULAR_ONLY }

/**
 * 日程标签（架构 §1.3）。
 *
 * [systemKey] 为稳定系统键，目前只有“课程表”用 `COURSE`；普通用户标签为 null。
 */
data class ScheduleTag(
    val id: ScheduleTagId,
    val name: String,
    val systemKey: String?,
    val sortOrder: Int,
)

/**
 * 日程业务事实（架构 §3.1）。
 *
 * 不含 Android/Room 注解；时间用 [Instant]。系列字段本轮恒为 null，仅预留结构。
 *
 * @property tagId 标签 ID；null 表示无标签（不伪造“无标签”行）
 * @property seriesId 重复系列 ID；本轮恒 null
 * @property originalStartAt 系列实例原 occurrence；本轮恒 null
 */
data class ScheduleEvent(
    val id: ScheduleId,
    val title: String,
    val note: String?,
    val startAt: Instant,
    val endAt: Instant,
    val source: ScheduleSource,
    val tagId: ScheduleTagId?,
    val seriesId: Long?,
    val originalStartAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** 闭区间本地日期范围；[dayCount] 为含首尾的天数（架构 §3.1）。 */
data class LocalDateRange(val startInclusive: LocalDate, val endInclusive: LocalDate) {

    /** 含首尾的天数；至少 1（调用方保证 start <= end）。 */
    val dayCount: Int
        get() = (endInclusive.toEpochDay() - startInclusive.toEpochDay() + 1).toInt()

    /** 按天展开的日期列表；仅用于 UI 列与规则计算，最多 7 天量级。 */
    fun dates(): List<LocalDate> = List(dayCount) { startInclusive.plusDays(it.toLong()) }
}

/** 半开时间区间 [startInclusive, endExclusive)；用于仓库范围查询（架构 §3.1）。 */
data class TimeRange(val startInclusive: Instant, val endExclusive: Instant)
