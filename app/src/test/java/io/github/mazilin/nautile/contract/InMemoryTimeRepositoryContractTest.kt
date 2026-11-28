package io.github.mazilin.nautile.contract

import io.github.mazilin.nautile.domain.repository.TimeCategoryRepository
import io.github.mazilin.nautile.domain.repository.TimeLogRepository
import io.github.mazilin.nautile.testing.InMemoryTimeCategoryRepository
import io.github.mazilin.nautile.testing.InMemoryTimeLogRepository

/** 内存替身的日志仓库契约实现（R6-G4-V1）。 */
class InMemoryTimeLogRepositoryContractTest : TimeLogRepositoryContract() {
    private val categoryStore = InMemoryTimeCategoryRepository()

    override val logs: TimeLogRepository = InMemoryTimeLogRepository()

    override val categories: TimeCategoryRepository = categoryStore
}

/** 内存替身的分类仓库契约实现（R6-G4-V1）；日志与分类共享同一存储。 */
class InMemoryTimeCategoryRepositoryContractTest : TimeCategoryRepositoryContract() {
    private val logStore = InMemoryTimeLogRepository()

    override val logs: TimeLogRepository = logStore

    override val categories: TimeCategoryRepository = InMemoryTimeCategoryRepository(logStore)
}
