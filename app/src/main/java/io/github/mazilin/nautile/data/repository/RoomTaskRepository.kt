package io.github.mazilin.nautile.data.repository

import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.mapper.TaskMapper
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room 任务仓库（S1-T05 步骤 1）；只暴露领域模型，转换集中在 TaskMapper。 */
class RoomTaskRepository(private val database: NautileDatabase) : TaskRepository {

    private val taskDao = database.taskDao()

    override fun observeByPosition(position: GtdPosition): Flow<List<Task>> =
        taskDao.observeByPosition(position.name).map { entities ->
            entities.map(TaskMapper::toDomain)
        }

    override fun observeAll(): Flow<List<Task>> =
        taskDao.observeAll().map { entities -> entities.map(TaskMapper::toDomain) }

    override suspend fun get(id: TaskId): Task? =
        taskDao.get(id.value)?.let(TaskMapper::toDomain)

    override suspend fun insert(task: Task): TaskId =
        TaskId(taskDao.insert(TaskMapper.toEntity(task)))

    override suspend fun update(task: Task) {
        taskDao.update(TaskMapper.toEntity(task))
    }

    override suspend fun delete(id: TaskId) {
        // 关联提醒/日程的清理是后续切片的扩展点；S1 无这两张表，不写静默级联（07 §2.2）。
        taskDao.delete(id.value)
    }

    override suspend fun deleteAll(ids: List<TaskId>) {
        if (ids.isEmpty()) return
        taskDao.deleteByIds(ids.map { it.value })
    }
}
