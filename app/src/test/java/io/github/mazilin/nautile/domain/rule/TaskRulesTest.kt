package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S1-T03 任务规则；纯 JVM，无 Android 依赖。 */
class TaskRulesTest {

    private val now: Instant = Instant.parse("2026-11-19T02:00:00Z")
    private val later: Instant = Instant.parse("2026-11-19T03:00:00Z")
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun task(
        position: GtdPosition = GtdPosition.INBOX,
        positionBeforeClose: GtdPosition? = null,
        closedOutcome: ClosedOutcome? = null,
    ) = Task(
        id = TaskId(1L),
        title = "写周报",
        position = position,
        positionBeforeClose = positionBeforeClose,
        closedOutcome = closedOutcome,
        closedAt = if (closedOutcome != null) now else null,
        closedOn = if (closedOutcome != null) LocalDate.of(2026, 11, 19) else null,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun acT01_blank_title_is_rejected() {
        assertEquals(TaskRuleViolation.BLANK_TITLE, TaskRules.validateTitle("   "))
        assertEquals(TaskRuleViolation.BLANK_TITLE, TaskRules.validateTitle(""))
        assertNull(TaskRules.validateTitle("写周报"))
    }

    @Test
    fun acT01_moving_to_ended_is_rejected() {
        assertFalse(TaskRules.canMoveTo(GtdPosition.ENDED))
        assertTrue(TaskRules.canMoveTo(GtdPosition.NEXT_ACTION))
    }

    @Test
    fun acT01_close_sets_ended_and_records_previous_position() {
        val closed = TaskRules.close(
            task = task(position = GtdPosition.NEXT_ACTION),
            outcome = ClosedOutcome.COMPLETED,
            now = now,
            zoneId = zone,
        )

        assertEquals(GtdPosition.ENDED, closed.position)
        assertEquals(GtdPosition.NEXT_ACTION, closed.positionBeforeClose)
        assertEquals(ClosedOutcome.COMPLETED, closed.closedOutcome)
        assertEquals(now, closed.closedAt)
        assertEquals(LocalDate.of(2026, 11, 19), closed.closedOn)
        assertEquals(now, closed.updatedAt)
    }

    @Test
    fun acT29_reopen_clears_closed_fields_and_restores_position() {
        val closed = task(
            position = GtdPosition.ENDED,
            positionBeforeClose = GtdPosition.WAITING,
            closedOutcome = ClosedOutcome.ABANDONED,
        )

        val reopened = TaskRules.reopen(closed, later)

        assertEquals(GtdPosition.WAITING, reopened.position)
        assertNull(reopened.positionBeforeClose)
        assertNull(reopened.closedOutcome)
        assertNull(reopened.closedAt)
        assertNull(reopened.closedOn)
        assertEquals(later, reopened.updatedAt)
    }

    @Test
    fun acT29_reopen_without_previous_position_falls_back_to_inbox() {
        val closed = task(position = GtdPosition.ENDED, closedOutcome = ClosedOutcome.COMPLETED)

        val reopened = TaskRules.reopen(closed, later)

        assertEquals(GtdPosition.INBOX, reopened.position)
        assertNull(reopened.positionBeforeClose)
    }
}
