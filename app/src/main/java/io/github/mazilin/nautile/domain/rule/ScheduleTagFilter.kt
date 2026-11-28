package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId

/**
 * 日程多选标签筛选状态（仅内存，不持久化；R7 #5、架构 §3.3）。
 *
 * 采用“排除集 + 无标签开关”驱动，天然默认全选（[excludedTagIds] 为空且 [includeUntagged] 为 true），
 * 避免标签异步加载首帧或在设置页新建标签后出现默认未勾选闪烁。
 *
 * @property excludedTagIds 当前取消勾选的标签 ID 集合（默认空集 = 全部标签勾选）
 * @property includeUntagged 是否包含无标签日程（默认 true）
 */
data class ScheduleTagFilter(
    val excludedTagIds: Set<ScheduleTagId> = emptySet(),
    val includeUntagged: Boolean = true,
) {

    /** 是否为默认全选态（无任何排除标签且包含无标签）。 */
    val isAllSelected: Boolean
        get() = includeUntagged && excludedTagIds.isEmpty()

    /** 指定标签当前是否处于勾选态。 */
    fun isTagSelected(tagId: ScheduleTagId): Boolean = tagId !in excludedTagIds

    /** 切换指定标签的勾选态。 */
    fun toggleTag(tagId: ScheduleTagId): ScheduleTagFilter = copy(
        excludedTagIds = if (tagId in excludedTagIds) {
            excludedTagIds - tagId
        } else {
            excludedTagIds + tagId
        },
    )

    /** 切换“无标签”的勾选态。 */
    fun toggleUntagged(): ScheduleTagFilter = copy(includeUntagged = !includeUntagged)

    /** 结合当前全部标签计算已勾选的标签 ID 集合。 */
    fun selectedTagIds(allTags: List<ScheduleTag>): Set<ScheduleTagId> =
        allTags.mapTo(LinkedHashSet()) { it.id } - excludedTagIds

    /** 针对当前可见标签列表判断筛选是否处于非全选（激活）态，忽略已删除标签的残留 ID。 */
    fun hasActiveFilter(allTags: List<ScheduleTag>): Boolean =
        !includeUntagged || allTags.any { it.id in excludedTagIds }

    /** 判断单条日程是否命中当前多选筛选条件。 */
    fun matches(
        event: ScheduleEvent,
        courseTagId: ScheduleTagId? = null,
    ): Boolean {
        val effectiveTagId = event.tagId
            ?: if (event.source == ScheduleSource.IMPORTED) courseTagId else null
        return if (effectiveTagId == null) {
            includeUntagged
        } else {
            effectiveTagId !in excludedTagIds
        }
    }
}

/**
 * 按多选标签筛选日程（纯函数，不修改底层数据；R7 #5、架构 §3.3.2）。
 *
 * - 事件显式带 `tagId` 时，严格按 `tagId !in filter.excludedTagIds` 判定；
 * - 事件 `tagId == null` 且 `source == IMPORTED` 时，若存在系统 `COURSE` 标签则兜底归入 `courseTagId` 判定
 *   （防御历史/外部导入漏写 `tag_id` 的极端行）；否则按 `filter.includeUntagged` 判定。
 */
fun filterByTags(
    events: List<ScheduleEvent>,
    filter: ScheduleTagFilter,
    courseTagId: ScheduleTagId? = null,
): List<ScheduleEvent> = events.filter { event -> filter.matches(event, courseTagId) }
