package io.github.mazilin.nautile.domain.repository

import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSourceFilter
import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.model.TimeRange
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * 日程仓库（架构 §3.2、R7 §2.3 / §3.2）。
 *
 * 只暴露领域模型，不返回 entity。R7 区间主查询走 [observeInRange]（单参），
 * 保留带 [ScheduleSourceFilter] 重载以兼容历史契约测试。
 */
interface ScheduleRepository {

    /** 观察全部标签，按 sort_order、id 升序。 */
    fun observeTags(): Flow<List<ScheduleTag>>

    /** 观察每个标签关联的日程数量（只统计 tagId != null）。 */
    fun observeTagEventCounts(): Flow<Map<ScheduleTagId, Int>>

    /** 观察与区间相交的全部事件（R7 主接口，内存按多选标签过滤）。 */
    fun observeInRange(range: TimeRange): Flow<List<ScheduleEvent>> =
        observeInRange(range, ScheduleSourceFilter.MERGED)

    /** 观察与区间相交的事件；按来源筛选，保留以兼容既有契约。 */
    fun observeInRange(range: TimeRange, source: ScheduleSourceFilter): Flow<List<ScheduleEvent>>

    /** 一次性查询与区间相交的事件列表（供新建日程计算同日相邻异色）。 */
    suspend fun listInRange(range: TimeRange): List<ScheduleEvent>

    /** 观察单条日程；不存在时发射 null。 */
    fun observeEvent(id: ScheduleId): Flow<ScheduleEvent?>

    suspend fun get(id: ScheduleId): ScheduleEvent?

    /** 插入日程并返回仓库分配的 ID。 */
    suspend fun insert(event: ScheduleEvent): ScheduleId

    suspend fun update(event: ScheduleEvent)

    /** 硬删除；本轮无回收站（架构 §1.3 第 4 条）。 */
    suspend fun delete(id: ScheduleId)

    /** 一次性列出全部标签，按 sort_order、id 升序（供标签增改判重）。 */
    suspend fun listTags(): List<ScheduleTag>

    suspend fun getTag(id: ScheduleTagId): ScheduleTag?

    /** 按系统键查找标签（如 `COURSE`）；不存在返回 null。 */
    suspend fun findSystemTag(systemKey: String): ScheduleTag?

    /** 计算新标签的下一个排序值。 */
    suspend fun nextTagSortOrder(): Int

    /** 插入自定义标签并返回仓库分配的 ID。 */
    suspend fun insertTag(tag: ScheduleTag, now: Instant): ScheduleTagId

    /** 更新标签名称与时间戳。 */
    suspend fun updateTagName(id: ScheduleTagId, name: String, updatedAt: Instant)

    /**
     * 事务内解绑关联日程并删除标签（适配 `schedule_events.tag_id` 的 `ON DELETE RESTRICT` 外键）。
     *
     * 先将挂接该标签的日程置 `tag_id = NULL`，再删除标签行；标签不存在返回 false。
     */
    suspend fun deleteTagAndDetachEvents(id: ScheduleTagId, updatedAt: Instant): Boolean
}
