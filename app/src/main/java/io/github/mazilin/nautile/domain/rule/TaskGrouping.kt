package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task

/**
 * 默认分组结果；[project] 为 null 表示“单独任务”组（FR-V-01）。
 */
data class TaskGroup(val project: Project?, val tasks: List<Task>) {
    val isUngrouped: Boolean get() = project == null
}

/**
 * 默认按项目分组（FR-V-01、D-10）：项目任务只出现在所属项目组，不重复进入单独任务组；
 * 无项目或项目不在给定列表内的任务进入末尾的单独任务组。S1 不做其他分组/排序。
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
