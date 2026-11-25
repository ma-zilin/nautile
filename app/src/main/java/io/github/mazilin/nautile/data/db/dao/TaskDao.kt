package io.github.mazilin.nautile.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.mazilin.nautile.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/** S1 任务 DAO（08 S1-T04 步骤 3）；位置按文本名查询。 */
@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE position = :position")
    fun observeByPosition(position: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 单条手动排序写入；批量场景由仓库在同一事务内逐条调用（CMP-40.2）。 */
    @Query("UPDATE tasks SET manual_order = :manualOrder WHERE id = :id")
    suspend fun updateManualOrder(id: Long, manualOrder: Long)
}
