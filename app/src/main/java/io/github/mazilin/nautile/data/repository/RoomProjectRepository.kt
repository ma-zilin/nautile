package io.github.mazilin.nautile.data.repository

import androidx.room.withTransaction
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.mapper.ProjectMapper
import io.github.mazilin.nautile.data.mapper.TaskMapper
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room 项目仓库（S1-T05 步骤 1）；项目与子任务的批量写在同一事务内完成。 */
class RoomProjectRepository(private val database: NautileDatabase) : ProjectRepository {

    private val projectDao = database.projectDao()
    private val taskDao = database.taskDao()

    override fun observeAll(): Flow<List<Project>> =
        projectDao.observeAll().map { entities -> entities.map(ProjectMapper::toDomain) }

    override suspend fun get(id: ProjectId): Project? =
        projectDao.get(id.value)?.let(ProjectMapper::toDomain)

    override suspend fun insert(project: Project): ProjectId =
        ProjectId(projectDao.insert(ProjectMapper.toEntity(project)))

    override suspend fun update(project: Project) {
        projectDao.update(ProjectMapper.toEntity(project))
    }

    override suspend fun delete(id: ProjectId) {
        // 子任务、关联日程/日志由用例显式处理；不做数据库静默级联（07 §2.2、X-03）。
        projectDao.delete(id.value)
    }

    override suspend fun updateWithTasks(project: Project, tasks: List<Task>) {
        database.withTransaction {
            projectDao.update(ProjectMapper.toEntity(project))
            tasks.forEach { taskDao.update(TaskMapper.toEntity(it)) }
        }
    }

    override suspend fun deleteWithTasks(id: ProjectId, taskIds: List<TaskId>) {
        database.withTransaction {
            if (taskIds.isNotEmpty()) {
                // 关联日程/日志的外键置空是后续切片的扩展点；S1 无这两张表。
                taskDao.deleteByIds(taskIds.map { it.value })
            }
            projectDao.delete(id.value)
        }
    }
}
