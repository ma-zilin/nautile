package io.github.mazilin.nautile.domain.usecase

import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.domain.model.TimerMode
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.StartTimerOutcome
import io.github.mazilin.nautile.domain.repository.StopTimerOutcome
import io.github.mazilin.nautile.domain.repository.TimeCategoryRepository
import io.github.mazilin.nautile.domain.repository.TimeLogRepository
import io.github.mazilin.nautile.domain.rule.ColoredTimeSlot
import io.github.mazilin.nautile.domain.rule.EventColorRules
import io.github.mazilin.nautile.domain.rule.TimeLogRules
import java.time.Instant
import java.time.ZoneId

/** 日志命令失败原因；UI 按枚举输出提示，不捕获裸异常（架构 §3.3、R7 Q3）。 */
enum class TimeLogError {
    /** 分类不存在（含已删除）。 */
    CATEGORY_NOT_FOUND,

    /** 结束时间未晚于开始时间。 */
    END_NOT_AFTER_START,

    /** 起止时间晚于当前时刻：严禁补录或修改为未来时间（R7 Q3，统一提示“不能补录未来时间”）。 */
    FUTURE_TIME_FORBIDDEN,

    /** 本轮只支持默认并行；单活动模式延后。 */
    UNSUPPORTED_MODE,

    /** 运行中记录不能直接编辑/删除；先结束计时。 */
    RUNNING_LOG,
}

/** 日志命令结果；开始/结束计时用专门结果，避免 UI 解析裸异常。 */
sealed interface TimeLogCommandResult {

    data class Success(val log: TimeLog) : TimeLogCommandResult

    data class Invalid(val error: TimeLogError) : TimeLogCommandResult

    data class NotFound(val id: TimeLogId) : TimeLogCommandResult

    data class Deleted(val id: TimeLogId) : TimeLogCommandResult

    /** 同分类已有运行中：只提示，不重置、不结束（AC-T-21）。 */
    data class AlreadyRunning(val logId: TimeLogId) : TimeLogCommandResult

    /** 无运行中记录可结束。 */
    data object NotRunning : TimeLogCommandResult
}

/**
 * 日志写入用例（架构 §3.3、§7.1）。
 *
 * - 补录允许与已有记录重叠（FR-T-09/AC-L-16），但起止必须合法；
 * - 开始计时在仓库事务内保证“每分类最多一段”；
 * - 结束时间 `max(now, startAt)`，无暂停；
 * - 时间一律取注入的 [Clock]，不读系统时钟。
 */
class TimeLogCommands(
    private val logRepository: TimeLogRepository,
    private val categoryRepository: TimeCategoryRepository,
    private val clock: Clock,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    /** 补录一条已完成记录；严禁未来时间，并自动计算同日相邻异色色槽。 */
    suspend fun createManual(
        categoryId: TimeCategoryId,
        startAt: Instant,
        endAt: Instant,
    ): TimeLogCommandResult {
        if (categoryRepository.get(categoryId) == null) {
            return TimeLogCommandResult.Invalid(TimeLogError.CATEGORY_NOT_FOUND)
        }
        if (!endAt.isAfter(startAt)) {
            return TimeLogCommandResult.Invalid(TimeLogError.END_NOT_AFTER_START)
        }
        val now = clock.now()
        if (startAt.isAfter(now) || endAt.isAfter(now)) {
            return TimeLogCommandResult.Invalid(TimeLogError.FUTURE_TIME_FORBIDDEN)
        }
        val colorIndex = pickColorForDay(
            targetStart = startAt,
            targetEnd = endAt,
            now = now,
        )
        val draft = TimeLog(
            id = TimeLogId(0),
            categoryId = categoryId,
            startAt = startAt,
            endAt = endAt,
            createdAt = now,
            updatedAt = now,
            colorIndex = colorIndex,
        )
        val id = logRepository.insert(draft)
        return TimeLogCommandResult.Success(draft.copy(id = id))
    }

    /** 整条更新（详情字段编辑即保存）；严禁未来时间，保留原色槽，不写非法中间状态。 */
    suspend fun update(
        id: TimeLogId,
        categoryId: TimeCategoryId,
        startAt: Instant,
        endAt: Instant?,
    ): TimeLogCommandResult {
        val existing = logRepository.get(id) ?: return TimeLogCommandResult.NotFound(id)
        if (categoryRepository.get(categoryId) == null) {
            return TimeLogCommandResult.Invalid(TimeLogError.CATEGORY_NOT_FOUND)
        }
        // 运行中记录由计时结束写 endAt；此处只更新已完成记录，避免绕过结束流程。
        if (existing.isRunning) {
            return TimeLogCommandResult.Invalid(TimeLogError.RUNNING_LOG)
        }
        if (endAt == null || !endAt.isAfter(startAt)) {
            return TimeLogCommandResult.Invalid(TimeLogError.END_NOT_AFTER_START)
        }
        val now = clock.now()
        if (startAt.isAfter(now) || endAt.isAfter(now)) {
            return TimeLogCommandResult.Invalid(TimeLogError.FUTURE_TIME_FORBIDDEN)
        }
        val updated = existing.copy(
            categoryId = categoryId,
            startAt = startAt,
            endAt = endAt,
            updatedAt = now,
        )
        logRepository.update(updated)
        return TimeLogCommandResult.Success(updated)
    }

    /** 整条删除；运行中记录不允许在此删除（先结束再删，避免留下暂停态）。 */
    suspend fun delete(id: TimeLogId): TimeLogCommandResult {
        val existing = logRepository.get(id) ?: return TimeLogCommandResult.NotFound(id)
        if (existing.isRunning) {
            return TimeLogCommandResult.Invalid(TimeLogError.RUNNING_LOG)
        }
        logRepository.delete(id)
        return TimeLogCommandResult.Deleted(id)
    }

    /** 开始计时；同分类已有运行中只返回 [TimeLogCommandResult.AlreadyRunning]，新记录写入相邻异色色槽。 */
    suspend fun startTimer(
        categoryId: TimeCategoryId,
        mode: TimerMode = TimerMode.PARALLEL,
    ): TimeLogCommandResult {
        if (mode != TimerMode.PARALLEL) {
            return TimeLogCommandResult.Invalid(TimeLogError.UNSUPPORTED_MODE)
        }
        if (categoryRepository.get(categoryId) == null) {
            return TimeLogCommandResult.Invalid(TimeLogError.CATEGORY_NOT_FOUND)
        }
        val now = clock.now()
        val colorIndex = pickColorForDay(
            targetStart = now,
            targetEnd = now.plusSeconds(1),
            now = now,
        )
        val draft = TimeLog(
            id = TimeLogId(0),
            categoryId = categoryId,
            startAt = now,
            endAt = null,
            createdAt = now,
            updatedAt = now,
            colorIndex = colorIndex,
        )
        return when (val outcome = logRepository.startTimer(categoryId, draft)) {
            is StartTimerOutcome.Started -> TimeLogCommandResult.Success(outcome.log)
            is StartTimerOutcome.AlreadyRunning -> TimeLogCommandResult.AlreadyRunning(outcome.logId)
        }
    }

    /** 结束指定分类的运行中记录；`endAt = max(now, startAt)`，无暂停。 */
    suspend fun stopTimer(categoryId: TimeCategoryId): TimeLogCommandResult {
        val now = clock.now()
        return when (val outcome = logRepository.stopTimer(categoryId, now)) {
            is StopTimerOutcome.Stopped -> TimeLogCommandResult.Success(outcome.log)
            StopTimerOutcome.NotRunning -> TimeLogCommandResult.NotRunning
        }
    }

    /** 按开始时刻所属本地日查已有日志（含运行中），挑选相邻异色槽位（R7 §4.2）。 */
    private suspend fun pickColorForDay(
        targetStart: Instant,
        targetEnd: Instant,
        now: Instant,
    ): Int {
        val startDate = TimeLogRules.startDate(targetStart, zoneId)
        val sameDaySlots = logRepository.listForDay(startDate, zoneId).map { existing ->
            ColoredTimeSlot(
                startAt = existing.startAt,
                endAt = existing.endAt ?: TimeLogRules.stopEndAt(existing.startAt, now),
                colorIndex = existing.colorIndex,
                fallbackId = existing.id.value,
            )
        }
        return EventColorRules.pickColorIndex(
            targetStart = targetStart,
            targetEnd = targetEnd,
            sameDaySlots = sameDaySlots,
        )
    }
}
