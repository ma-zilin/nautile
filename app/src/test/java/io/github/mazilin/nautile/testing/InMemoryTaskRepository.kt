package io.github.mazilin.nautile.testing

import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 任务仓库内存替身，供 T03/T06 使用（不放 androidTest）。
 *
 * ID 为 0 时视为新任务，由替身分配递增 ID；其余按传入 ID 保存。
 */
class InMemoryTaskRepository(initial: List<Task> = emptyList()) : TaskRepository {

    private val tasks = MutableStateFlow(initial.associateBy { it.id })
    private var nextId = (initial.maxOfOrNull { it.id.value } ?: 0L) + 1

    override fun observeByPosition(position: GtdPosition): Flow<List<Task>> =
        tasks.map { stored ->
            stored.values.filter { it.position == position }.sortedBy { it.id.value }
        }

    override fun observeAll(): Flow<List<Task>> =
        tasks.map { stored -> stored.values.sortedBy { it.id.value } }

    override suspend fun get(id: TaskId): Task? = tasks.value[id]

    override suspend fun insert(task: Task): TaskId {
        val id = if (task.id.value == 0L) TaskId(nextId++) else task.id
        if (id.value >= nextId) nextId = id.value + 1
        tasks.value = tasks.value + (id to task.copy(id = id))
        return id
    }

    override suspend fun update(task: Task) {
        require(tasks.value.containsKey(task.id)) { "任务不存在：${task.id.value}" }
        tasks.value = tasks.value + (task.id to task)
    }

    override suspend fun delete(id: TaskId) {
        tasks.value = tasks.value - id
    }

    override suspend fun deleteAll(ids: List<TaskId>) {
        tasks.value = tasks.value - ids.toSet()
    }
}
