package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.ViewClassification
import io.github.mazilin.nautile.domain.model.ViewOptions
import io.github.mazilin.nautile.domain.model.ViewSort
import java.time.LocalDate

/**
 * 分组结果（FR-V-01、CMP-39）。
 *
 * [project] 非 null 时是项目组（可滑动结项、点入详情）；为 null 时是“单独任务”组
 * （优先级/截止日期分类的组也走这里，用 [title] 提供组头文案）。
 * [stableKey] 供列表稳定复用；未提供时由项目或“单独任务”推导。
 */
data class TaskGroup(
    val project: Project?,
    val tasks: List<Task>,
    val title: String? = null,
    val stableKey: String? = null,
) {
    val isUngrouped: Boolean get() = project == null

    /** 组头文案：项目名 > 分类组标题 > “单独任务”。 */
    val headerTitle: String get() = project?.name ?: title ?: "单独任务"

    /** 列表 key：项目组沿用 project_<id>，其余由分类提供稳定文本。 */
    val key: String
        get() = stableKey ?: project?.let { "project_${it.id.value}" } ?: "ungrouped_header"
}

/**
 * 默认按项目分组（FR-V-01、D-10）：项目任务只出现在所属项目组，不重复进入单独任务组；
 * 无项目或项目不在给定列表内的任务进入末尾的单独任务组。
 */
object TaskGrouping {

    /** 旧语义：只输出有任务的项目组与（非空时）单独任务组，保持既有调用与单测不变。 */
    fun byProject(tasks: List<Task>, projects: List<Project>): List<TaskGroup> =
        groupByProject(tasks, projects, includeEmptyProjects = false)

    /**
     * 分组卡片视图：按 [projects] 顺序输出所有项目组（0 任务也保留），
     * 供待办列表显示空项目并点入项目详情（SHP-07、SPC-07）；任务顺序与 [byProject] 一致。
     */
    fun byProjectIncludingEmpty(tasks: List<Task>, projects: List<Project>): List<TaskGroup> =
        groupByProject(tasks, projects, includeEmptyProjects = true)

    /**
     * G9 视图分组：先按 [options] 的排序得到组内顺序，再按分类分组（CMP-39/CMP-40）。
     *
     * 只改变展示顺序；项目分类在 [includeEmptyProjects] 为 true 时保留空项目组（G2），
     * 优先级与截止日期分组不输出空组。搜索态由调用方传 false（只保留有命中的组）。
     */
    fun byView(
        tasks: List<Task>,
        projects: List<Project>,
        options: ViewOptions,
        includeEmptyProjects: Boolean,
    ): List<TaskGroup> {
        val sorted = sort(tasks, options.sort)
        return when (options.classification) {
            ViewClassification.PROJECT ->
                groupByProject(sorted, projects, includeEmptyProjects)

            ViewClassification.PRIORITY -> byPriority(sorted)
            ViewClassification.DUE -> byDue(sorted)
        }
    }

    /** 组内排序分派；未知组合不会出现，枚举已固定。 */
    fun sort(tasks: List<Task>, sort: ViewSort): List<Task> = when (sort) {
        ViewSort.DUE -> TaskSorting.byDue(tasks)
        ViewSort.PRIORITY -> TaskSorting.byPriority(tasks)
        ViewSort.CREATED -> TaskSorting.byCreated(tasks)
        ViewSort.MANUAL -> TaskSorting.byManual(tasks)
    }

    /** 优先级分组：高→中→低→无；只输出非空组（CMP-39）。 */
    private fun byPriority(tasks: List<Task>): List<TaskGroup> =
        priorityOrder.mapNotNull { level ->
            val members = tasks.filter { it.priority == level }
            if (members.isEmpty()) {
                null
            } else {
                TaskGroup(
                    project = null,
                    tasks = members,
                    title = priorityTitle(level),
                    stableKey = "priority_${level.name}",
                )
            }
        }

    /**
     * 截止日期分组：日期由近到远，无日期组置末（CMP-39）。
     * 组内顺序沿用调用方已排好的列表；组头用“M月d日”或“无截止日期”。
     */
    private fun byDue(tasks: List<Task>): List<TaskGroup> {
        val groupedByDate = LinkedHashMap<LocalDate?, MutableList<Task>>()
        tasks.forEach { task ->
            groupedByDate.getOrPut(task.dueDate) { mutableListOf() }.add(task)
        }
        val dated = groupedByDate.keys.filterNotNull().sorted().mapNotNull { date ->
            val members = groupedByDate[date].orEmpty()
            if (members.isEmpty()) {
                null
            } else {
                TaskGroup(
                    project = null,
                    tasks = members,
                    title = dueTitle(date),
                    stableKey = "due_$date",
                )
            }
        }
        val undated = groupedByDate[null].orEmpty()
        return if (undated.isEmpty()) {
            dated
        } else {
            dated + TaskGroup(
                project = null,
                tasks = undated,
                title = "无截止日期",
                stableKey = "due_none",
            )
        }
    }

    private val priorityOrder = listOf(
        Priority.HIGH,
        Priority.MEDIUM,
        Priority.LOW,
        Priority.NONE,
    )

    private fun priorityTitle(level: Priority): String = when (level) {
        Priority.HIGH -> "高优先级"
        Priority.MEDIUM -> "中优先级"
        Priority.LOW -> "低优先级"
        Priority.NONE -> "无优先级"
    }

    private fun dueTitle(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

    private fun groupByProject(
        tasks: List<Task>,
        projects: List<Project>,
        includeEmptyProjects: Boolean,
    ): List<TaskGroup> {
        val projectById: Map<ProjectId, Project> = projects.associateBy { it.id }
        val grouped = LinkedHashMap<ProjectId, MutableList<Task>>()
        val ungrouped = mutableListOf<Task>()

        tasks.forEach { task ->
            val projectId = task.projectId
            if (projectId != null && projectById.containsKey(projectId)) {
                grouped.getOrPut(projectId) { mutableListOf() }.add(task)
            } else {
                ungrouped.add(task)
            }
        }

        val projectGroups = projects.mapNotNull { project ->
            val memberTasks = grouped[project.id]
            when {
                includeEmptyProjects -> TaskGroup(project, memberTasks.orEmpty().toList())
                memberTasks != null -> TaskGroup(project, memberTasks.toList())
                else -> null
            }
        }
        return if (ungrouped.isEmpty()) projectGroups else projectGroups + TaskGroup(null, ungrouped)
    }
}
