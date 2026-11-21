package io.github.mazilin.nautile.contract

import io.github.mazilin.nautile.domain.repository.TaskRepository
import io.github.mazilin.nautile.testing.InMemoryProjectRepository
import io.github.mazilin.nautile.testing.InMemoryTaskRepository
import io.github.mazilin.nautile.domain.repository.ProjectRepository

/** 内存替身的任务仓库契约实现（T-051）。 */
class InMemoryTaskRepositoryContractTest : TaskRepositoryContract() {
    override val tasks: TaskRepository = InMemoryTaskRepository()
}

/** 内存替身的项目仓库契约实现（T-051）；与任务替身共享同一份存储。 */
class InMemoryProjectRepositoryContractTest : ProjectRepositoryContract() {
    private val taskStore = InMemoryTaskRepository()

    override val tasks: TaskRepository = taskStore

    override val projects: ProjectRepository = InMemoryProjectRepository(taskStore)
}
