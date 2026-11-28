package io.github.mazilin.nautile.domain.usecase

import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.ScheduleRepository

/** 日程标签命令失败原因；UI 按枚举映射提示，不捕获裸异常（R7 Q2、架构 §3.2.2）。 */
enum class ScheduleTagError {
    /** 名称为空或全空白。 */
    BLANK_NAME,

    /** 与已有标签重名（trim 后忽略大小写判重）。 */
    DUPLICATE_NAME,

    /** 系统内置标签（`systemKey != null`，如 COURSE“课程表”）禁止修改或删除。 */
    SYSTEM_TAG_PROTECTED,
}

/** 日程标签命令结果。 */
sealed interface ScheduleTagCommandResult {

    data class Success(val tag: ScheduleTag) : ScheduleTagCommandResult

    data class Invalid(val error: ScheduleTagError) : ScheduleTagCommandResult

    data class NotFound(val id: ScheduleTagId) : ScheduleTagCommandResult

    data class Deleted(val id: ScheduleTagId) : ScheduleTagCommandResult
}

/**
 * 日程标签管理用例（R7 Q2、架构 §3.2.2）。
 *
 * - 系统内置标签（`systemKey != null`）受保护，禁止改名与删除；
 * - 名称 trim 后判空且忽略大小写判重；
 * - 删除用户自定义标签时，在仓库事务内先解绑关联日程再删标签行，适配 `ON DELETE RESTRICT` 外键。
 */
class ScheduleTagCommands(
    private val repository: ScheduleRepository,
    private val clock: Clock,
) {

    /** 新建用户自定义日程标签；`systemKey` 恒为 null。 */
    suspend fun create(name: String): ScheduleTagCommandResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return ScheduleTagCommandResult.Invalid(ScheduleTagError.BLANK_NAME)
        }
        val existingTags = repository.listTags()
        if (existingTags.any { it.name.trim().equals(trimmed, ignoreCase = true) }) {
            return ScheduleTagCommandResult.Invalid(ScheduleTagError.DUPLICATE_NAME)
        }
        val draft = ScheduleTag(
            id = ScheduleTagId(0),
            name = trimmed,
            systemKey = null,
            sortOrder = repository.nextTagSortOrder(),
        )
        val id = repository.insertTag(draft, clock.now())
        return ScheduleTagCommandResult.Success(draft.copy(id = id))
    }

    /** 重命名用户自定义标签；系统标签拒绝修改。 */
    suspend fun rename(id: ScheduleTagId, name: String): ScheduleTagCommandResult {
        val existing = repository.getTag(id) ?: return ScheduleTagCommandResult.NotFound(id)
        if (existing.systemKey != null) {
            return ScheduleTagCommandResult.Invalid(ScheduleTagError.SYSTEM_TAG_PROTECTED)
        }
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return ScheduleTagCommandResult.Invalid(ScheduleTagError.BLANK_NAME)
        }
        val existingTags = repository.listTags()
        if (existingTags.any { it.id != id && it.name.trim().equals(trimmed, ignoreCase = true) }) {
            return ScheduleTagCommandResult.Invalid(ScheduleTagError.DUPLICATE_NAME)
        }
        repository.updateTagName(id, trimmed, clock.now())
        return ScheduleTagCommandResult.Success(existing.copy(name = trimmed))
    }

    /** 删除用户自定义标签并在同事务内将关联日程解绑为无标签；系统标签拒绝删除。 */
    suspend fun delete(id: ScheduleTagId): ScheduleTagCommandResult {
        val existing = repository.getTag(id) ?: return ScheduleTagCommandResult.NotFound(id)
        if (existing.systemKey != null) {
            return ScheduleTagCommandResult.Invalid(ScheduleTagError.SYSTEM_TAG_PROTECTED)
        }
        val deleted = repository.deleteTagAndDetachEvents(id, clock.now())
        return if (deleted) {
            ScheduleTagCommandResult.Deleted(id)
        } else {
            ScheduleTagCommandResult.NotFound(id)
        }
    }
}
