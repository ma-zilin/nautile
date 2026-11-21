package io.github.mazilin.nautile.domain.repository

import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import kotlinx.coroutines.flow.Flow

/**
 * 项目仓库；只暴露领域模型，不返回 entity。
 *
 * 项目与其子任务的批量结项事务由实现保证（AC-T-17）。
 */
interface ProjectRepository {

    fun observeAll(): Flow<List<Project>>

    suspend fun get(id: ProjectId): Project?

    /** 插入项目并返回仓库分配的 ID。 */
    suspend fun insert(project: Project): ProjectId

    suspend fun update(project: Project)

    suspend fun delete(id: ProjectId)

    /**
     * 同一事务写入项目与其子任务（批量结项/重开，AC-T-17、07 §2.2）。
     * T03 按 08 的失败处理补充此方法；T05 用 Room `withTransaction` 实现。
     */
    suspend fun updateWithTasks(project: Project, tasks: List<Task>)

    /**
     * 同一事务删除项目与其子任务（删除项目级联，X-03）。
     * T05 用 Room `withTransaction` 实现；S1 无日程/日志表。
     */
    suspend fun deleteWithTasks(id: ProjectId, taskIds: List<TaskId>)
}
