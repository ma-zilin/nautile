package io.github.mazilin.nautile.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.mazilin.nautile.data.db.entity.TimeCategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 时间分类 DAO（架构 §4.1；本闸门只建结构与查询，G4 才写入业务行）。
 *
 * 列表排序：顶级在前、子级随后，各自按 sort_order、id 升序；删除前用计数查询检查使用情况。
 */
@Dao
interface TimeCategoryDao {

    @Query(
        "SELECT * FROM time_categories " +
            "ORDER BY (parent_id IS NOT NULL), sort_order ASC, id ASC",
    )
    fun observeAll(): Flow<List<TimeCategoryEntity>>

    @Query("SELECT * FROM time_categories WHERE id = :id")
    suspend fun get(id: Long): TimeCategoryEntity?

    @Query("SELECT COUNT(*) FROM time_categories WHERE parent_id = :id")
    suspend fun countChildren(id: Long): Int

    @Query("SELECT COUNT(*) FROM time_logs WHERE category_id = :id")
    suspend fun countLogs(id: Long): Int

    @Insert
    suspend fun insert(category: TimeCategoryEntity): Long

    @Update
    suspend fun update(category: TimeCategoryEntity)

    @Query("DELETE FROM time_categories WHERE id = :id")
    suspend fun delete(id: Long)
}
