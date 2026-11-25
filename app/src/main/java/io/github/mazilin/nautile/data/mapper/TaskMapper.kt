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

/**
 * entity ↔ domain 纯函数（07 §2.3）；枚举按文本名往返，日期/时刻用 ISO 文本。
 */
object TaskMapper {

    fun toDomain(entity: TaskEntity): Task = Task(
        id = TaskId(entity.id),
        title = entity.title,
        projectId = entity.projectId?.let { ProjectId(it) },
        position = GtdPosition.valueOf(entity.position),
        positionBeforeClose = entity.positionBeforeClose?.let { GtdPosition.valueOf(it) },
        priority = Priority.valueOf(entity.priority),
        dueDate = entity.dueDate?.let { LocalDate.parse(it) },
        dueTime = entity.dueTime?.let { LocalTime.parse(it) },
        closedOutcome = entity.closedOutcome?.let { ClosedOutcome.valueOf(it) },
        closedAt = entity.closedAt?.let { Instant.ofEpochMilli(it) },
        closedOn = entity.closedOn?.let { LocalDate.parse(it) },
        createdAt = Instant.ofEpochMilli(entity.createdAt),
        updatedAt = Instant.ofEpochMilli(entity.updatedAt),
        detail = entity.detail,
        manualOrder = entity.manualOrder,
    )

    fun toEntity(task: Task): TaskEntity = TaskEntity(
        id = task.id.value,
        title = task.title,
        projectId = task.projectId?.value,
        position = task.position.name,
        positionBeforeClose = task.positionBeforeClose?.name,
        priority = task.priority.name,
        dueDate = task.dueDate?.toString(),
        dueTime = task.dueTime?.toString(),
        closedOutcome = task.closedOutcome?.name,
        closedAt = task.closedAt?.toEpochMilli(),
        closedOn = task.closedOn?.toString(),
        createdAt = task.createdAt.toEpochMilli(),
        updatedAt = task.updatedAt.toEpochMilli(),
        detail = task.detail,
        manualOrder = task.manualOrder,
    )
}
