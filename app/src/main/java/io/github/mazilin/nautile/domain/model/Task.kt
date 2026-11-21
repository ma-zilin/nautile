package io.github.mazilin.nautile.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** 任务 ID 值类；以 Long 承载。 */
@JvmInline
value class TaskId(val value: Long)

/**
 * 任务业务事实（AC-T-01、AC-T-29）。
 *
 * 不含 Android/Room 注解；时间用 [Instant]、日期用 [LocalDate]。
 * 位置与结项字段的合法性由 domain 规则校验（T-03），模型本身不做守卫。
 *
 * @property positionBeforeClose 结项前位置，仅用于重新打开；未结项时为 null（BR-80）
 * @property closedOn 结项发生的本地日期，统计按它归档（D-05a）
 */
data class Task(
    val id: TaskId,
    val title: String,
    val projectId: ProjectId? = null,
    val position: GtdPosition,
    val positionBeforeClose: GtdPosition? = null,
    val priority: Priority = Priority.NONE,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val closedOutcome: ClosedOutcome? = null,
    val closedAt: Instant? = null,
    val closedOn: LocalDate? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
