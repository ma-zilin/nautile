package io.github.mazilin.nautile.data.repository

import androidx.room.withTransaction
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.mapper.TimeCategoryMapper
import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.repository.DeleteCategoryOutcome
import io.github.mazilin.nautile.domain.repository.TimeCategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Room 分类仓库（R6-G4-T2）。
 *
 * 删除守卫在同一事务内查子级、查日志、删除；任一步失败回滚（架构 §4.1）。
 * 第三级兜底由用例层完成，数据层只保证删除一致性。
 */
class RoomTimeCategoryRepository(private val database: NautileDatabase) : TimeCategoryRepository {

    private val categoryDao = database.timeCategoryDao()

    override fun observeAll(): Flow<List<TimeCategory>> =
        categoryDao.observeAll().map { entities -> entities.map(TimeCategoryMapper::toDomain) }

    override suspend fun get(id: TimeCategoryId): TimeCategory? =
        categoryDao.get(id.value)?.let(TimeCategoryMapper::toDomain)

    override suspend fun insert(category: TimeCategory): TimeCategoryId =
        TimeCategoryId(categoryDao.insert(TimeCategoryMapper.toEntity(category)))

    override suspend fun update(category: TimeCategory) {
        categoryDao.update(TimeCategoryMapper.toEntity(category))
    }

    override suspend fun deleteIfUnused(id: TimeCategoryId): DeleteCategoryOutcome =
        database.withTransaction {
            val existing = categoryDao.get(id.value)
                ?: return@withTransaction DeleteCategoryOutcome.NotFound
            val childCount = categoryDao.countChildren(existing.id)
            val logCount = categoryDao.countLogs(existing.id)
            if (childCount > 0 || logCount > 0) {
                return@withTransaction DeleteCategoryOutcome.Blocked(childCount, logCount)
            }
            categoryDao.delete(existing.id)
            DeleteCategoryOutcome.Deleted
        }

    override suspend fun countChildren(id: TimeCategoryId): Int = categoryDao.countChildren(id.value)

    override suspend fun countLogs(id: TimeCategoryId): Int = categoryDao.countLogs(id.value)

    override suspend fun nextSortOrder(parentId: TimeCategoryId?): Int =
        observeAll().first()
            .filter { it.parentId == parentId }
            .maxOfOrNull { it.sortOrder + 1 }
            ?: 0
}
