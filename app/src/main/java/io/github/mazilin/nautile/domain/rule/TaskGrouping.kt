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

    fun byProject(tasks: List<Task>, projects: List<Project>): List<TaskGroup> {
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
            grouped[project.id]?.let { memberTasks -> TaskGroup(project, memberTasks.toList()) }
        }
        return if (ungrouped.isEmpty()) projectGroups else projectGroups + TaskGroup(null, ungrouped)
    }
}
