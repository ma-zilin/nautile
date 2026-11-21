package io.github.mazilin.nautile.data.mapper

import io.github.mazilin.nautile.data.db.entity.ProjectEntity
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import java.time.Instant

/** entity ↔ domain 纯函数（07 §2.3）；项目状态按文本名往返。 */
object ProjectMapper {

    fun toDomain(entity: ProjectEntity): Project = Project(
        id = ProjectId(entity.id),
        name = entity.name,
        status = ProjectStatus.valueOf(entity.status),
        createdAt = Instant.ofEpochMilli(entity.createdAt),
        updatedAt = Instant.ofEpochMilli(entity.updatedAt),
    )

    fun toEntity(project: Project): ProjectEntity = ProjectEntity(
        id = project.id.value,
        name = project.name,
        status = project.status.name,
        createdAt = project.createdAt.toEpochMilli(),
        updatedAt = project.updatedAt.toEpochMilli(),
    )
}
