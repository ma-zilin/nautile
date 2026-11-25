package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.Task

/**
 * 待办搜索（G8、CMP-38、FR-N-14/15、BR-76）。
 *
 * 只做展示过滤：按任务标题或所属项目名匹配，大小写不敏感、忽略首尾空白；
 * 保持入参顺序、不复制/修改任务对象、不写入任何字段；不匹配标签（用户 2026-11-25 决定），
 * 也不跨 GTD 清单——调用方只传入当前清单的任务。
 */
object TaskSearch {

    /**
     * @param tasks 当前 GTD 清单的全部任务（调用方已按 position 过滤）。
     * @param projects 项目列表，用于把任务的项目 ID 解析为项目名。
     * @param query 查询串；空串或全空白返回 [tasks] 原列表。
     * @return 命中任务（原对象、原顺序）；无命中返回空列表。
     */
    fun filter(tasks: List<Task>, projects: List<Project>, query: String): List<Task> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return tasks
        val projectNames = projects.associate { it.id to it.name }
        return tasks.filter { task ->
            task.title.contains(keyword, ignoreCase = true) ||
                task.projectId?.let { projectNames[it] }
                    ?.contains(keyword, ignoreCase = true) == true
        }
    }
}
