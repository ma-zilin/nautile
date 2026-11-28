package io.github.mazilin.nautile.domain.usecase

import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.DeleteCategoryOutcome
import io.github.mazilin.nautile.domain.repository.TimeCategoryRepository

/** 分类命令失败原因；UI 按枚举输出提示（AC-L-10、AC-L-18）。 */
enum class TimeCategoryError {
    /** 名称为空或全空白。 */
    BLANK_NAME,

    /** 指定的父级不存在。 */
    PARENT_NOT_FOUND,

    /** 父级本身不是顶级：禁止第三级。 */
    THIRD_LEVEL_FORBIDDEN,
}

/** 分类命令结果。 */
sealed interface TimeCategoryCommandResult {

    data class Success(val category: TimeCategory) : TimeCategoryCommandResult

    data class Invalid(val error: TimeCategoryError) : TimeCategoryCommandResult

    data class NotFound(val id: TimeCategoryId) : TimeCategoryCommandResult

    data class Deleted(val id: TimeCategoryId) : TimeCategoryCommandResult

    /** 有子级或日志时拒绝删除，同时带出两项计数供 UI 列出原因。 */
    data class Blocked(val childCount: Int, val logCount: Int) : TimeCategoryCommandResult
}

/**
 * 分类写入用例（架构 §3.3、§7.4）。
 *
 * - 最多两级：父级必须存在且本身为顶级，否则返回 [TimeCategoryError.THIRD_LEVEL_FORBIDDEN]；
 * - 改名只改名称，id 不变，历史日志随 id 归属（AC-L-18）；
 * - 删除前由仓库事务检查子级与日志；不提供批量清空/迁移（D-new2）。
 */
class TimeCategoryCommands(
    private val repository: TimeCategoryRepository,
    private val clock: Clock,
) {

    suspend fun create(name: String, parentId: TimeCategoryId?): TimeCategoryCommandResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return TimeCategoryCommandResult.Invalid(TimeCategoryError.BLANK_NAME)
        }
        if (parentId != null) {
            val parent = repository.get(parentId)
                ?: return TimeCategoryCommandResult.Invalid(TimeCategoryError.PARENT_NOT_FOUND)
            if (!parent.isTopLevel) {
                return TimeCategoryCommandResult.Invalid(TimeCategoryError.THIRD_LEVEL_FORBIDDEN)
            }
        }
        val now = clock.now()
        val draft = TimeCategory(
            id = TimeCategoryId(0),
            name = trimmed,
            parentId = parentId,
            sortOrder = repository.nextSortOrder(parentId),
            createdAt = now,
            updatedAt = now,
        )
        val id = repository.insert(draft)
        return TimeCategoryCommandResult.Success(draft.copy(id = id))
    }

    /** 改名：只改 name 与 updatedAt；id 不变。 */
    suspend fun rename(id: TimeCategoryId, name: String): TimeCategoryCommandResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return TimeCategoryCommandResult.Invalid(TimeCategoryError.BLANK_NAME)
        }
        val existing = repository.get(id) ?: return TimeCategoryCommandResult.NotFound(id)
        val updated = existing.copy(name = trimmed, updatedAt = clock.now())
        repository.update(updated)
        return TimeCategoryCommandResult.Success(updated)
    }

    /** 删除：有子级或日志时返回 [TimeCategoryCommandResult.Blocked]，不删除任何数据。 */
    suspend fun delete(id: TimeCategoryId): TimeCategoryCommandResult =
        when (val outcome = repository.deleteIfUnused(id)) {
            DeleteCategoryOutcome.Deleted -> TimeCategoryCommandResult.Deleted(id)
            is DeleteCategoryOutcome.Blocked ->
                TimeCategoryCommandResult.Blocked(outcome.childCount, outcome.logCount)

            DeleteCategoryOutcome.NotFound -> TimeCategoryCommandResult.NotFound(id)
        }
}
