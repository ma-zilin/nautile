package io.github.mazilin.nautile.ui.screen.schedule

import io.github.mazilin.nautile.domain.model.LocalDateRange
import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleSourceFilter
import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.model.ScheduleViewMode
import io.github.mazilin.nautile.domain.rule.ScheduleRangeRules
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 日程页状态（R6-G2-T3；架构 §5.1）。
 *
 * 只承载可见范围、来源筛选与布局结果；标签仅显示/录入，不参与筛选（D-new1）。
 * [events] 已按天拆块并计算比例与重叠列，UI 只做像素换算。
 */
data class ScheduleUiState(
    val today: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = today,
    val viewMode: ScheduleViewMode = ScheduleViewMode.DAY,
    val visibleRange: LocalDateRange = ScheduleRangeRules.visibleRange(selectedDate, viewMode),
    /** 可见范围标题（如“11月24日 – 11月30日”），用于“标题与范围一致”。 */
    val rangeTitle: String = "",
    val sourceFilter: ScheduleSourceFilter = ScheduleSourceFilter.MERGED,
    val tags: List<ScheduleTag> = emptyList(),
    val events: List<ScheduleEventBlock> = emptyList(),
    /** 当前时刻，用于“仅今天”的当前时间线；每分钟更新，不写库。 */
    val now: Instant = Instant.now(),
    /** 注入的时区，保证网格与日期换算一致。 */
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val isLoading: Boolean = false,
    val message: String? = null,
) {

    /** 可见范围内的日期列（日 1 / 三天 3 / 周 7）。 */
    val columnDates: List<LocalDate> get() = visibleRange.dates()

    val isEmpty: Boolean get() = events.isEmpty()

    /** 按 ID 取标签名；不存在返回 null（不伪造“无标签”文字）。 */
    fun tagName(tagId: ScheduleTagId?): String? =
        tagId?.let { id -> tags.firstOrNull { it.id == id }?.name }
}

/** 编辑面板模式：新建只在“创建”时落库；详情字段编辑即保存。 */
enum class ScheduleEditorMode { CREATE, EDIT }

/** 面板内日期/时间字段；用于区分开始与结束。 */
enum class ScheduleEditorField { START, END }

/**
 * 日程新建/详情编辑器状态（架构 §5.1、05 §2.3/§3.2）。
 *
 * - 来源本轮固定普通：新建不提供课程选项；详情只读展示，不提供修改入口（设计默认，待确认）。
 * - [tagId] 为 null 表示“无标签”；`课程表` 由系统标签 id 承载，不改变来源（AC-S-04）。
 * - 时间允许暂时为 null（行内可清除），但为空时不可提交/关闭；非法文本只在面板内本地报错，
 *   不写回状态、不静默修正（AC-M-01 辅助核对）。
 */
data class ScheduleEditorUiState(
    val mode: ScheduleEditorMode,
    val scheduleId: ScheduleId? = null,
    val title: String = "",
    val note: String = "",
    val startDate: LocalDate,
    val startTime: LocalTime?,
    val endDate: LocalDate,
    val endTime: LocalTime?,
    val source: ScheduleSource = ScheduleSource.MANUAL,
    val tagId: ScheduleTagId? = null,
    /** 系统“课程表”标签 id；未加载到时为 null，标签选择暂时不可用。 */
    val courseTagId: ScheduleTagId? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
) {

    val titleError: String? get() = if (title.isBlank()) "标题不能为空" else null

    val startDateTime: LocalDateTime? get() = startTime?.let { startDate.atTime(it) }

    val endDateTime: LocalDateTime? get() = endTime?.let { endDate.atTime(it) }

    /** 起止校验：必填且结束严格晚于开始（允许跨天）。 */
    val rangeError: String?
        get() {
            val start = startDateTime ?: return "请设置开始时间"
            val end = endDateTime ?: return "请设置结束时间"
            return if (end.isAfter(start)) null else "结束时间必须晚于开始时间"
        }

    /** 架构 §5.1 字段名：时间/起止错误（含未设置与结束不晚于开始）。 */
    val timeError: String? get() = rangeError

    /** 首次提示与阻止离开共用同一条信息。 */
    val validationMessage: String? get() = titleError ?: rangeError

    val isValid: Boolean get() = validationMessage == null

    /** 关闭条件：字段有效且没有正在进行的创建/删除，避免提交中被关闭。 */
    val canDismiss: Boolean get() = isValid && !isSaving

    val isCourseTagSelected: Boolean
        get() = courseTagId != null && tagId == courseTagId

    val sourceLabel: String
        get() = when (source) {
            ScheduleSource.MANUAL -> "手动创建"
            ScheduleSource.AI -> "AI创建"
            ScheduleSource.IMPORTED -> "导入"
        }
}

/** 日内事件块：布局比例与重叠列由 `ScheduleLayout` 计算（架构 §3.5、§5.1）。 */
data class ScheduleEventBlock(
    val event: ScheduleEvent,
    val dayIndex: Int,
    val topFraction: Float,
    val heightFraction: Float,
    val column: Int,
    val columnCount: Int,
)
