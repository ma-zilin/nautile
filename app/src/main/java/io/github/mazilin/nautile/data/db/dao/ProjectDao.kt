package io.github.mazilin.nautile.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.mazilin.nautile.data.db.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

/** S1 项目 DAO（08 S1-T04 步骤 3）。 */
@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: Long): ProjectEntity?

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)
}
