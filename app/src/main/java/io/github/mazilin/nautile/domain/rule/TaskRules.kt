package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Task
import java.time.Instant
import java.time.ZoneId

/** 任务规则违规原因；由用例转成明确结果，不向 UI 抛异常。 */
enum class TaskRuleViolation {
    BLANK_TITLE,
    TARGET_ENDED,
    ALREADY_CLOSED,
    NOT_CLOSED,
}

/** S1 任务纯规则（FR-D-01、FR-G-19、BR-80；AC-T-01、AC-T-29）。 */
object TaskRules {

    /** 标题不能为空或全空白（AC-T-01）。 */
    fun validateTitle(title: String): TaskRuleViolation? =
        if (title.isBlank()) TaskRuleViolation.BLANK_TITLE else null

    /** 未结项任务不能直接被移到“已结束”（AC-T-01）。 */
    fun canMoveTo(position: GtdPosition): Boolean = position != GtdPosition.ENDED

    fun isClosed(task: Task): Boolean = task.closedOutcome != null

    /**
     * 完成/放弃：位置原子置为“已结束”，并记录结项前位置与结项日期（AC-T-01、D-11）。
     * 调用前须由用例保证任务尚未结项。
     */
    fun close(task: Task, outcome: ClosedOutcome, now: Instant, zoneId: ZoneId): Task {
        val previousPosition = if (task.position != GtdPosition.ENDED) {
            task.position
        } else {
            task.positionBeforeClose
        }
        return task.copy(
            position = GtdPosition.ENDED,
            positionBeforeClose = previousPosition,
            closedOutcome = outcome,
            closedAt = now,
            closedOn = now.atZone(zoneId).toLocalDate(),
            updatedAt = now,
        )
    }

    /**
     * 重新打开：清除结项字段，位置恢复到结项前位置；缺失时回到收件箱（AC-T-29、BR-80）。
     */
    fun reopen(task: Task, now: Instant): Task = task.copy(
        position = task.positionBeforeClose?.takeIf { it != GtdPosition.ENDED } ?: GtdPosition.INBOX,
        positionBeforeClose = null,
        closedOutcome = null,
        closedAt = null,
        closedOn = null,
        updatedAt = now,
    )
}
