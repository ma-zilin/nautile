package io.github.mazilin.nautile.data.mapper

import io.github.mazilin.nautile.data.db.entity.ScheduleEventEntity
import io.github.mazilin.nautile.data.db.entity.ScheduleTagEntity
import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import java.time.Instant

/**
 * 日程 entity ↔ domain 纯函数（架构 §4.1；沿用 TaskMapper 约定）。
 *
 * 枚举按文本名往返，时间戳用 UTC epoch 毫秒；可空字段原样映射。
 */
object ScheduleMapper {

    fun toDomain(entity: ScheduleEventEntity): ScheduleEvent = ScheduleEvent(
        id = ScheduleId(entity.id),
        title = entity.title,
        note = entity.note,
        startAt = Instant.ofEpochMilli(entity.startAt),
        endAt = Instant.ofEpochMilli(entity.endAt),
        source = ScheduleSource.valueOf(entity.source),
        tagId = entity.tagId?.let { ScheduleTagId(it) },
        seriesId = entity.seriesId,
        originalStartAt = entity.originalStartAt?.let { Instant.ofEpochMilli(it) },
        createdAt = Instant.ofEpochMilli(entity.createdAt),
        updatedAt = Instant.ofEpochMilli(entity.updatedAt),
    )

    fun toEntity(event: ScheduleEvent): ScheduleEventEntity = ScheduleEventEntity(
        id = event.id.value,
        title = event.title,
        note = event.note,
        startAt = event.startAt.toEpochMilli(),
        endAt = event.endAt.toEpochMilli(),
        source = event.source.name,
        tagId = event.tagId?.value,
        seriesId = event.seriesId,
        originalStartAt = event.originalStartAt?.toEpochMilli(),
        createdAt = event.createdAt.toEpochMilli(),
        updatedAt = event.updatedAt.toEpochMilli(),
    )

    fun toDomain(entity: ScheduleTagEntity): ScheduleTag = ScheduleTag(
        id = ScheduleTagId(entity.id),
        name = entity.name,
        systemKey = entity.systemKey,
        sortOrder = entity.sortOrder,
    )
}
