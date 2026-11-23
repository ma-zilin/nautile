package io.github.mazilin.nautile.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * S1 正式任务表（08 S1-T04 步骤 1；替换 T-047 探针）。
 *
 * 枚举以文本名存 TEXT；日期/时刻用 ISO 文本；时间戳用 UTC epoch 毫秒。
 * 项目外键 RESTRICT：项目删除由用例显式处理，不让数据库静默级联（07 §2.2）。
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("position"), Index("project_id")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    /** 可选详情，TEXT 可空；旧版本迁移行保持 null（FR-D-03、AC-D-09）。 */
    val detail: String? = null,
    @ColumnInfo(name = "project_id")
    val projectId: Long? = null,
    val position: String,
    @ColumnInfo(name = "position_before_close")
    val positionBeforeClose: String? = null,
    val priority: String,
    @ColumnInfo(name = "due_date")
    val dueDate: String? = null,
    @ColumnInfo(name = "due_time")
    val dueTime: String? = null,
    @ColumnInfo(name = "closed_outcome")
    val closedOutcome: String? = null,
    @ColumnInfo(name = "closed_at")
    val closedAt: Long? = null,
    @ColumnInfo(name = "closed_on")
    val closedOn: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
