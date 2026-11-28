package io.github.mazilin.nautile.domain.repository

import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSourceFilter
import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.model.TimeRange
import kotlinx.coroutines.flow.Flow

/**
 * 日程仓库（架构 §3.2）。
 *
 * 只暴露领域模型，不返回 entity。标签筛选本轮不做（D-new1）：[observeInRange] 只接受来源单一筛选，
 * 标签随事件读取用于显示，不进入查询条件。
 */
interface ScheduleRepository {

    /** 观察全部标签，按 sort_order、id 升序；仅用于标签字段选择/显示。 */
    fun observeTags(): Flow<List<ScheduleTag>>

    /** 观察与区间相交的事件；按来源筛选，不接收标签条件。 */
    fun observeInRange(range: TimeRange, source: ScheduleSourceFilter): Flow<List<ScheduleEvent>>

    /** 观察单条日程；不存在时发射 null。 */
    fun observeEvent(id: ScheduleId): Flow<ScheduleEvent?>

    suspend fun get(id: ScheduleId): ScheduleEvent?

    /** 插入日程并返回仓库分配的 ID。 */
    suspend fun insert(event: ScheduleEvent): ScheduleId

    suspend fun update(event: ScheduleEvent)

    /** 硬删除；本轮无回收站（架构 §1.3 第 4 条）。 */
    suspend fun delete(id: ScheduleId)

    suspend fun getTag(id: ScheduleTagId): ScheduleTag?

    /** 按系统键查找标签（如 `COURSE`）；不存在返回 null。 */
    suspend fun findSystemTag(systemKey: String): ScheduleTag?
}
