package io.github.mazilin.nautile.data.mapper

import io.github.mazilin.nautile.data.db.entity.TimeLogEntity
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
import java.time.Instant

/**
 * 日志 entity ↔ domain 纯函数（架构 §4.1；沿用 ScheduleMapper 约定）。
 *
 * `end_at` 为 null 原样映射为运行中；时间戳按 UTC epoch 毫秒往返。
 */
object TimeLogMapper {

    fun toDomain(entity: TimeLogEntity): TimeLog = TimeLog(
        id = TimeLogId(entity.id),
        categoryId = TimeCategoryId(entity.categoryId),
        startAt = Instant.ofEpochMilli(entity.startAt),
        endAt = entity.endAt?.let { Instant.ofEpochMilli(it) },
        createdAt = Instant.ofEpochMilli(entity.createdAt),
        updatedAt = Instant.ofEpochMilli(entity.updatedAt),
        colorIndex = entity.colorIndex,
    )

    fun toEntity(log: TimeLog): TimeLogEntity = TimeLogEntity(
        id = log.id.value,
        categoryId = log.categoryId.value,
        startAt = log.startAt.toEpochMilli(),
        endAt = log.endAt?.toEpochMilli(),
        createdAt = log.createdAt.toEpochMilli(),
        updatedAt = log.updatedAt.toEpochMilli(),
        colorIndex = log.colorIndex,
    )
}
