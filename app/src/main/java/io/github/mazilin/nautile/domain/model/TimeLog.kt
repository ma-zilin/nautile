package io.github.mazilin.nautile.domain.model

import java.time.Instant

/** 时间日志 ID（架构 §3.1）。 */
@JvmInline
value class TimeLogId(val value: Long)

/** 时间分类 ID（架构 §3.1）。 */
@JvmInline
value class TimeCategoryId(val value: Long)

/**
 * 时间分类（两级；架构 §3.1）。
 *
 * [parentId] 为 null 表示顶级；第三级由用例拒绝，不在模型层隐藏。
 * 不增加 icon 字段（L5）：分类图标如需显示仅为装饰，不落库、不参与统计。
 */
data class TimeCategory(
    val id: TimeCategoryId,
    val name: String,
    val parentId: TimeCategoryId?,
    val sortOrder: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isTopLevel: Boolean get() = parentId == null
}

/**
 * 时间日志（架构 §3.1）。
 *
 * [endAt] 为 null 表示运行中；跨天不拆行，统计时按本地 00:00 派生段。
 * [colorIndex] 为事件色板槽位（0..7）；历史旧记录为 null，展示时按确定性回退解析（R7 #22）。
 */
data class TimeLog(
    val id: TimeLogId,
    val categoryId: TimeCategoryId,
    val startAt: Instant,
    val endAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val colorIndex: Int? = null,
) {
    val isRunning: Boolean get() = endAt == null
}

/** 计时模式；本轮只用默认并行，单活动与切换延后（FR-T-08 Phase A 口径）。 */
enum class TimerMode { PARALLEL, SINGLE }
