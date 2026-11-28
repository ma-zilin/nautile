package io.github.mazilin.nautile.ui.screen.settings

import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId

/** 分类管理行（架构 §5.3）：父级在前，子级缩进一级。 */
data class CategoryManagementRow(
    val category: TimeCategory,
    val depth: Int,
    val childCount: Int,
    val logCount: Int,
)

/** 分类编辑模式：新建（顶级或子级）/ 改名。 */
enum class CategoryEditMode { CREATE, EDIT }

/** 分类编辑半屏状态。 */
data class CategoryEditUiState(
    val mode: CategoryEditMode,
    val categoryId: TimeCategoryId? = null,
    val parentId: TimeCategoryId? = null,
    val name: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = name.isNotBlank() && !isSaving

    val title: String get() = if (mode == CategoryEditMode.CREATE) "新建分类" else "编辑分类"
}

/** 删除被拒原因（05 §7.4）：同时列出子级与日志两项计数。 */
data class CategoryDeleteGuard(
    val categoryName: String,
    val childCount: Int,
    val logCount: Int,
) {
    val reason: String
        get() = buildList {
            if (childCount > 0) add("该分类下有 $childCount 个子分类")
            if (logCount > 0) add("该分类下有 $logCount 条日志")
        }.joinToString("，") + "；请先逐条编辑或删除后再试"
}

/** 分类管理页状态（架构 §5.3）。 */
data class CategoryManagementUiState(
    val rows: List<CategoryManagementRow> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
    val edit: CategoryEditUiState? = null,
    val guard: CategoryDeleteGuard? = null,
    val pendingDelete: CategoryManagementRow? = null,
)
