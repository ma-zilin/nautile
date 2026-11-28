package io.github.mazilin.nautile.data.mapper

import io.github.mazilin.nautile.data.db.entity.TimeCategoryEntity
import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import java.time.Instant

/**
 * 分类 entity ↔ domain 纯函数（架构 §4.1；沿用 ScheduleMapper 约定）。
 *
 * 时间戳按 UTC epoch 毫秒往返；parentId 可空原样映射。
 */
object TimeCategoryMapper {

    fun toDomain(entity: TimeCategoryEntity): TimeCategory = TimeCategory(
        id = TimeCategoryId(entity.id),
        name = entity.name,
        parentId = entity.parentId?.let { TimeCategoryId(it) },
        sortOrder = entity.sortOrder,
        createdAt = Instant.ofEpochMilli(entity.createdAt),
        updatedAt = Instant.ofEpochMilli(entity.updatedAt),
    )

    fun toEntity(category: TimeCategory): TimeCategoryEntity = TimeCategoryEntity(
        id = category.id.value,
        name = category.name,
        parentId = category.parentId?.value,
        sortOrder = category.sortOrder,
        createdAt = category.createdAt.toEpochMilli(),
        updatedAt = category.updatedAt.toEpochMilli(),
    )
}
