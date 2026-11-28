package io.github.mazilin.nautile.domain.repository

import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import kotlinx.coroutines.flow.Flow

/**
 * 分类删除结果（架构 §3.2、§7.4）。
 *
 * 有子级或日志时返回 [Blocked]，同时带出两项计数，供 UI 同时列出原因：
 * “该分类下有 N 个子分类/ N 条日志”（D-new2：不提供批量清空）。
 */
sealed interface DeleteCategoryOutcome {
    data object Deleted : DeleteCategoryOutcome

    data class Blocked(val childCount: Int, val logCount: Int) : DeleteCategoryOutcome

    data object NotFound : DeleteCategoryOutcome
}

/** 时间分类仓库（架构 §3.2）；两级结构与删除守卫在数据层事务内兜底。 */
interface TimeCategoryRepository {

    fun observeAll(): Flow<List<TimeCategory>>

    suspend fun get(id: TimeCategoryId): TimeCategory?

    suspend fun insert(category: TimeCategory): TimeCategoryId

    suspend fun update(category: TimeCategory)

    /** 事务内检查子级与日志；被使用时返回 [DeleteCategoryOutcome.Blocked]。 */
    suspend fun deleteIfUnused(id: TimeCategoryId): DeleteCategoryOutcome

    suspend fun countChildren(id: TimeCategoryId): Int

    suspend fun countLogs(id: TimeCategoryId): Int

    /** 同级下一个排序号；顶级 parentId 为 null。 */
    suspend fun nextSortOrder(parentId: TimeCategoryId?): Int
}
