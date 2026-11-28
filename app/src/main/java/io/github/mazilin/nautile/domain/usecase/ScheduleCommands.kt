package io.github.mazilin.nautile.domain.usecase

import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.model.TimeRange
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.ScheduleRepository
import io.github.mazilin.nautile.domain.rule.ColoredTimeSlot
import io.github.mazilin.nautile.domain.rule.EventColorRules
import io.github.mazilin.nautile.domain.rule.ScheduleRules
import java.time.Instant
import java.time.ZoneId

/** 日程命令失败原因；UI 按枚举输出内联提示，不捕获裸异常（架构 §3.3）。 */
enum class ScheduleError {
    /** 标题为空或全空白。 */
    BLANK_TITLE,

    /** 结束时间未晚于开始时间。 */
    END_NOT_AFTER_START,

    /** 本轮只允许普通来源；课程来源由未来导入产生。 */
    UNSUPPORTED_SOURCE,

    /** 指定的标签不存在。 */
    TAG_NOT_FOUND,
}

/** 日程命令结果；成功返回落库后的 [ScheduleEvent]，删除成功返回 [Deleted]。 */
sealed interface ScheduleCommandResult {

    data class Success(val event: ScheduleEvent) : ScheduleCommandResult

    data class Invalid(val error: ScheduleError) : ScheduleCommandResult

    data class Deleted(val id: ScheduleId) : ScheduleCommandResult

    data class NotFound(val id: ScheduleId) : ScheduleCommandResult
}

/**
 * 日程字段更新（架构 §3.3、D3）。
 *
 * 本轮不提供来源修改：新建固定 `REGULAR`，来源字段未启用/延后。
 */
sealed interface ScheduleFieldUpdate {

    data class Title(val title: String) : ScheduleFieldUpdate

    data class Note(val note: String?) : ScheduleFieldUpdate

    data class Start(val startAt: Instant) : ScheduleFieldUpdate

    data class End(val endAt: Instant) : ScheduleFieldUpdate

    /** 起止同时变更（日期/时间行编辑）：一次性校验，避免先写一端产生非法中间状态。 */
    data class Range(val startAt: Instant, val endAt: Instant) : ScheduleFieldUpdate

    data class Tag(val tagId: ScheduleTagId?) : ScheduleFieldUpdate
}

/**
 * 日程写入用例（R6-G2-T2、R7 §2.3 / §4.2）。
 *
 * 规则：标题非空白、`endAt > startAt`、标签存在或 null；新建默认来源 [ScheduleSource.MANUAL]，
 * 并按同日相邻异色规则自动分配持久化 `colorIndex`；编辑字段不重算 `colorIndex`。
 * 无效输入不写库，返回 [ScheduleError]；不产生半成品记录。
 */
class ScheduleCommands(
    private val repository: ScheduleRepository,
    private val clock: Clock,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    /** 新建单次日程；手工入口默认 [ScheduleSource.MANUAL]，系列字段恒 null。 */
    suspend fun create(
        title: String,
        note: String?,
        startAt: Instant,
        endAt: Instant,
        tagId: ScheduleTagId?,
        source: ScheduleSource = ScheduleSource.MANUAL,
    ): ScheduleCommandResult {
        if (!ScheduleRules.isValidTitle(title)) {
            return ScheduleCommandResult.Invalid(ScheduleError.BLANK_TITLE)
        }
        if (!ScheduleRules.isValidRange(startAt, endAt)) {
            return ScheduleCommandResult.Invalid(ScheduleError.END_NOT_AFTER_START)
        }
        if (tagId != null && repository.getTag(tagId) == null) {
            return ScheduleCommandResult.Invalid(ScheduleError.TAG_NOT_FOUND)
        }
        val day = startAt.atZone(zoneId).toLocalDate()
        val dayRange = TimeRange(
            startInclusive = day.atStartOfDay(zoneId).toInstant(),
            endExclusive = day.plusDays(1).atStartOfDay(zoneId).toInstant(),
        )
        val sameDaySlots = repository.listInRange(dayRange).map { existing ->
            ColoredTimeSlot(
                startAt = existing.startAt,
                endAt = existing.endAt,
                colorIndex = existing.colorIndex,
                fallbackId = existing.id.value,
            )
        }
        val colorIndex = EventColorRules.pickColorIndex(
            targetStart = startAt,
            targetEnd = endAt,
            sameDaySlots = sameDaySlots,
        )
        val now = clock.now()
        val draft = ScheduleEvent(
            id = ScheduleId(0),
            title = title.trim(),
            note = note?.takeIf { it.isNotBlank() },
            startAt = startAt,
            endAt = endAt,
            source = source,
            tagId = tagId,
            seriesId = null,
            originalStartAt = null,
            createdAt = now,
            updatedAt = now,
            colorIndex = colorIndex,
        )
        val id = repository.insert(draft)
        return ScheduleCommandResult.Success(draft.copy(id = id))
    }

    /** 字段编辑即保存；先应用单字段，再整体校验，任一不通过都不写库。 */
    suspend fun updateField(
        id: ScheduleId,
        update: ScheduleFieldUpdate,
    ): ScheduleCommandResult {
        val existing = repository.get(id) ?: return ScheduleCommandResult.NotFound(id)
        val updated = when (update) {
            is ScheduleFieldUpdate.Title -> existing.copy(title = update.title.trim())
            is ScheduleFieldUpdate.Note -> existing.copy(note = update.note?.takeIf { it.isNotBlank() })
            is ScheduleFieldUpdate.Start -> existing.copy(startAt = update.startAt)
            is ScheduleFieldUpdate.End -> existing.copy(endAt = update.endAt)
            is ScheduleFieldUpdate.Range -> existing.copy(
                startAt = update.startAt,
                endAt = update.endAt,
            )

            is ScheduleFieldUpdate.Tag -> existing.copy(tagId = update.tagId)
        }
        if (!ScheduleRules.isValidTitle(updated.title)) {
            return ScheduleCommandResult.Invalid(ScheduleError.BLANK_TITLE)
        }
        if (!ScheduleRules.isValidRange(updated.startAt, updated.endAt)) {
            return ScheduleCommandResult.Invalid(ScheduleError.END_NOT_AFTER_START)
        }
        val tagId = updated.tagId
        if (tagId != null && repository.getTag(tagId) == null) {
            return ScheduleCommandResult.Invalid(ScheduleError.TAG_NOT_FOUND)
        }
        val stamped = updated.copy(updatedAt = clock.now())
        repository.update(stamped)
        return ScheduleCommandResult.Success(stamped)
    }

    /** 硬删除整条日程；不存在返回 [ScheduleCommandResult.NotFound]。 */
    suspend fun delete(id: ScheduleId): ScheduleCommandResult {
        if (repository.get(id) == null) return ScheduleCommandResult.NotFound(id)
        repository.delete(id)
        return ScheduleCommandResult.Deleted(id)
    }
}
