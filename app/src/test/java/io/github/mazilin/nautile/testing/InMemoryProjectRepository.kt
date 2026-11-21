package io.github.mazilin.nautile.testing

import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 项目仓库内存替身，供 T03/T06 使用（不放 androidTest）。
 *
 * ID 为 0 时视为新项目，由替身分配递增 ID；其余按传入 ID 保存。
 * 需调用批量写方法时，构造时传入同一份任务替身 [taskStore]。
 */
class InMemoryProjectRepository(
    private val taskStore: InMemoryTaskRepository? = null,
    initial: List<Project> = emptyList(),
) : ProjectRepository {

    private val projects = MutableStateFlow(initial.associateBy { it.id })
    private var nextId = (initial.maxOfOrNull { it.id.value } ?: 0L) + 1

    override fun observeAll(): Flow<List<Project>> =
        projects.map { stored -> stored.values.sortedBy { it.id.value } }

    override suspend fun get(id: ProjectId): Project? = projects.value[id]

    override suspend fun insert(project: Project): ProjectId {
        val id = if (project.id.value == 0L) ProjectId(nextId++) else project.id
        if (id.value >= nextId) nextId = id.value + 1
        projects.value = projects.value + (id to project.copy(id = id))
        return id
    }

    override suspend fun update(project: Project) {
        require(projects.value.containsKey(project.id)) { "项目不存在：${project.id.value}" }
        projects.value = projects.value + (project.id to project)
    }

    override suspend fun delete(id: ProjectId) {
        projects.value = projects.value - id
    }

    override suspend fun updateWithTasks(project: Project, tasks: List<Task>) {
        val store = requireNotNull(taskStore) { "批量写需要构造时传入 InMemoryTaskRepository" }
        require(projects.value.containsKey(project.id)) { "项目不存在：${project.id.value}" }
        tasks.forEach { store.update(it) }
        projects.value = projects.value + (project.id to project)
    }

    override suspend fun deleteWithTasks(id: ProjectId, taskIds: List<TaskId>) {
        if (taskIds.isNotEmpty()) {
            val store = requireNotNull(taskStore) { "批量删除需要构造时传入 InMemoryTaskRepository" }
            store.deleteAll(taskIds)
        }
        projects.value = projects.value - id
    }
}
