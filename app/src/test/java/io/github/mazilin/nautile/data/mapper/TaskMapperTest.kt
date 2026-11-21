package io.github.mazilin.nautile.data.mapper

import io.github.mazilin.nautile.data.db.entity.TaskEntity
import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S1-T04 映射测试：枚举文本名往返、可空字段、closedOn 日期往返（08 T04 步骤 7）。 */
class TaskMapperTest {

    private val now: Instant = Instant.parse("2026-11-19T02:00:00Z")

    @Test
    fun acT01_entity_round_trip_keeps_enums_and_closed_fields() {
        val task = Task(
            id = TaskId(5L),
            title = "写周报",
            projectId = ProjectId(3L),
            position = GtdPosition.ENDED,
            positionBeforeClose = GtdPosition.WAITING,
            priority = Priority.HIGH,
            dueDate = LocalDate.of(2026, 11, 20),
            dueTime = LocalTime.of(15, 30),
            closedOutcome = ClosedOutcome.COMPLETED,
            closedAt = now,
            closedOn = LocalDate.of(2026, 11, 19),
            createdAt = now,
            updatedAt = now,
        )

        val entity = TaskMapper.toEntity(task)
        val restored = TaskMapper.toDomain(entity)

        assertEquals("ENDED", entity.position)
        assertEquals("WAITING", entity.positionBeforeClose)
        assertEquals("HIGH", entity.priority)
        assertEquals("COMPLETED", entity.closedOutcome)
        assertEquals("2026-11-19", entity.closedOn)
        assertEquals(task, restored)
    }

    @Test
    fun acT29_entity_round_trip_keeps_optional_fields_null() {
        val task = Task(
            id = TaskId(6L),
            title = "收件箱任务",
            position = GtdPosition.INBOX,
            createdAt = now,
            updatedAt = now,
        )

        val entity = TaskMapper.toEntity(task)

        assertNull(entity.projectId)
        assertNull(entity.positionBeforeClose)
        assertNull(entity.dueDate)
        assertNull(entity.dueTime)
        assertNull(entity.closedOutcome)
        assertNull(entity.closedAt)
        assertNull(entity.closedOn)
        assertEquals(task, TaskMapper.toDomain(entity))
    }

    @Test
    fun acT29_domain_from_entity_reads_text_names_and_iso_dates() {
        val entity = TaskEntity(
            id = 9L,
            title = "读回",
            projectId = 2L,
            position = "SOMEDAY",
            positionBeforeClose = null,
            priority = "LOW",
            dueDate = "2026-12-01",
            dueTime = "09:05",
            closedOutcome = null,
            closedAt = null,
            closedOn = "2026-11-19",
            createdAt = now.toEpochMilli(),
            updatedAt = now.toEpochMilli(),
        )

        val task = TaskMapper.toDomain(entity)

        assertEquals(GtdPosition.SOMEDAY, task.position)
        assertEquals(Priority.LOW, task.priority)
        assertEquals(LocalDate.of(2026, 12, 1), task.dueDate)
        assertEquals(LocalTime.of(9, 5), task.dueTime)
        assertEquals(LocalDate.of(2026, 11, 19), task.closedOn)
    }
}
