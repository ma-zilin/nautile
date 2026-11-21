package io.github.mazilin.nautile.domain.repository

import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import kotlinx.coroutines.flow.Flow

/**
 * 任务仓库（07 §2.2、T-02 接口约定）。
 *
 * 只暴露领域模型，不返回 entity；多行写入的事务边界由实现保证。
 */
interface TaskRepository {

    /** 观察某个 GTD 位置的任务。 */
    fun observeByPosition(position: GtdPosition): Flow<List<Task>>

    /** 观察全部任务；用于项目分组与统计。 */
    fun observeAll(): Flow<List<Task>>

    suspend fun get(id: TaskId): Task?

    /** 插入任务并返回仓库分配的 ID。 */
    suspend fun insert(task: Task): TaskId

    suspend fun update(task: Task)

    suspend fun delete(id: TaskId)

    /** 批量删除；用于项目删除等需要单事务的场景。 */
    suspend fun deleteAll(ids: List<TaskId>)
}
