package io.github.mazilin.nautile.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.repository.TimeCategoryRepository
import io.github.mazilin.nautile.domain.repository.TimeLogRepository
import io.github.mazilin.nautile.domain.usecase.TimeCategoryCommandResult
import io.github.mazilin.nautile.domain.usecase.TimeCategoryCommands
import io.github.mazilin.nautile.domain.usecase.TimeCategoryError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 分类管理页状态与命令入口（R6-G4-T5；架构 §5.3、§7.4）。
 *
 * - 两级树：顶级在前，子级缩进；子级行不提供“添加子分类”；
 * - 第三级创建由 [TimeCategoryCommands] 拒绝并提示（AC-L-10）；
 * - 删除前判断子级/日志，被拒时同时列出原因；不提供批量清空/迁移（D-new2）；
 * - 改名只改名称，历史记录按 id 归属（AC-L-18）。
 */
class CategoryManagementViewModel(
    timeCategoryRepository: TimeCategoryRepository,
    timeLogRepository: TimeLogRepository,
    private val commands: TimeCategoryCommands,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)
    private val edit = MutableStateFlow<CategoryEditUiState?>(null)
    private val guard = MutableStateFlow<CategoryDeleteGuard?>(null)
    private val pendingDelete = MutableStateFlow<CategoryManagementRow?>(null)

    private data class Base(
        val categories: List<TimeCategory>,
        val logCounts: Map<TimeCategoryId, Int>,
    )

    private val base = combine(
        timeCategoryRepository.observeAll(),
        timeLogRepository.observeAllDescending(),
    ) { categories, logs ->
        Base(
            categories = categories,
            logCounts = logs.groupingBy { it.categoryId }.eachCount(),
        )
    }

    val uiState: StateFlow<CategoryManagementUiState> = combine(
        base,
        message,
        edit,
        guard,
        pendingDelete,
    ) { base, message, edit, guard, pendingDelete ->
        CategoryManagementUiState(
            rows = buildRows(base),
            isLoading = false,
            message = message,
            edit = edit,
            guard = guard,
            pendingDelete = pendingDelete,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = CategoryManagementUiState(),
    )

    fun onDismissMessage() {
        message.value = null
    }

    fun openCreateTopLevel() {
        edit.value = CategoryEditUiState(mode = CategoryEditMode.CREATE, parentId = null)
    }

    /** 仅顶级行可添加子级；子级行不显示该入口，命令层再兜底拒绝第三级。 */
    fun openCreateChild(parent: TimeCategory) {
        if (!parent.isTopLevel) {
            message.value = "最多两级分类，不能在子级下再建分类"
            return
        }
        edit.value = CategoryEditUiState(mode = CategoryEditMode.CREATE, parentId = parent.id)
    }

    fun openEdit(row: CategoryManagementRow) {
        edit.value = CategoryEditUiState(
            mode = CategoryEditMode.EDIT,
            categoryId = row.category.id,
            parentId = row.category.parentId,
            name = row.category.name,
        )
    }

    fun onNameChanged(value: String) {
        edit.update { it?.copy(name = value, error = null) }
    }

    fun closeEdit() {
        edit.value = null
    }

    /** 保存：新建/改名；非法或失败保留面板并提示，不显示成功。 */
    fun save() {
        val state = edit.value ?: return
        if (state.name.isBlank()) {
            edit.value = state.copy(error = "名称不能为空")
            return
        }
        viewModelScope.launch {
            edit.update { it?.copy(isSaving = true, error = null) }
            try {
                val result = when (state.mode) {
                    CategoryEditMode.CREATE -> commands.create(state.name, state.parentId)
                    CategoryEditMode.EDIT -> state.categoryId
                        ?.let { commands.rename(it, state.name) }
                        ?: TimeCategoryCommandResult.NotFound(TimeCategoryId(0))
                }
                when (result) {
                    is TimeCategoryCommandResult.Success -> {
                        edit.value = null
                        message.value = if (state.mode == CategoryEditMode.CREATE) {
                            "已创建分类"
                        } else {
                            "已保存分类"
                        }
                    }

                    is TimeCategoryCommandResult.Invalid -> edit.update {
                        it?.copy(isSaving = false, error = result.error.message())
                    }

                    is TimeCategoryCommandResult.NotFound -> edit.update {
                        it?.copy(isSaving = false, error = "分类不存在")
                    }

                    is TimeCategoryCommandResult.Blocked -> edit.update {
                        it?.copy(isSaving = false, error = "分类不存在")
                    }

                    is TimeCategoryCommandResult.Deleted -> edit.value = null
                }
            } catch (error: Exception) {
                edit.update { it?.copy(isSaving = false, error = "保存失败，请重试") }
            }
        }
    }

    /** 删除入口：有子级或日志时直接弹守卫；否则进入确认对话框。 */
    fun requestDelete(row: CategoryManagementRow) {
        if (row.childCount > 0 || row.logCount > 0) {
            guard.value = CategoryDeleteGuard(
                categoryName = row.category.name,
                childCount = row.childCount,
                logCount = row.logCount,
            )
        } else {
            pendingDelete.value = row
        }
    }

    fun dismissGuard() {
        guard.value = null
    }

    fun dismissDeleteConfirm() {
        pendingDelete.value = null
    }

    /** 确认删除：仍由仓库事务再检查一次，避免列表数据过期。 */
    fun confirmDelete() {
        val row = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch {
            try {
                when (val result = commands.delete(row.category.id)) {
                    is TimeCategoryCommandResult.Deleted -> message.value = "已删除分类"
                    is TimeCategoryCommandResult.Blocked -> guard.value = CategoryDeleteGuard(
                        categoryName = row.category.name,
                        childCount = result.childCount,
                        logCount = result.logCount,
                    )

                    is TimeCategoryCommandResult.NotFound -> message.value = "分类不存在"
                    else -> message.value = "删除失败，请重试"
                }
            } catch (error: Exception) {
                message.value = "删除失败，请重试"
            }
        }
    }

    private fun buildRows(base: Base): List<CategoryManagementRow> {
        val topLevel = base.categories.filter { it.parentId == null }
        val childrenByParent = base.categories
            .filter { it.parentId != null }
            .groupBy { it.parentId }
        val knownIds = topLevel.map { it.id }.toSet()
        val rows = mutableListOf<CategoryManagementRow>()
        topLevel.forEach { parent ->
            val children = childrenByParent[parent.id].orEmpty()
            rows += CategoryManagementRow(
                category = parent,
                depth = 0,
                childCount = children.size,
                logCount = base.logCounts[parent.id] ?: 0,
            )
            children.forEach { child ->
                rows += CategoryManagementRow(
                    category = child,
                    depth = 1,
                    childCount = 0,
                    logCount = base.logCounts[child.id] ?: 0,
                )
            }
        }
        // 防御孤儿数据（父级已不存在）：以子级缩进展示，避免数据消失。
        base.categories
            .filter { it.parentId != null && it.parentId !in knownIds }
            .forEach { orphan ->
                rows += CategoryManagementRow(
                    category = orphan,
                    depth = 1,
                    childCount = 0,
                    logCount = base.logCounts[orphan.id] ?: 0,
                )
            }
        return rows
    }

    private fun TimeCategoryError.message(): String = when (this) {
        TimeCategoryError.BLANK_NAME -> "名称不能为空"
        TimeCategoryError.PARENT_NOT_FOUND -> "父级不存在"
        TimeCategoryError.THIRD_LEVEL_FORBIDDEN -> "最多两级分类，不能在子级下再建分类"
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
