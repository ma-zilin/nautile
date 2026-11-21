package io.github.mazilin.nautile.data.mapper

import io.github.mazilin.nautile.data.db.entity.ProjectEntity
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** S1-T04 项目映射：状态文本名与时间戳往返（08 T04 步骤 7）。 */
class ProjectMapperTest {

    private val now: Instant = Instant.parse("2026-11-19T02:00:00Z")

    @Test
    fun acT17_project_round_trip_keeps_status_text_name() {
        val project = Project(
            id = ProjectId(4L),
            name = "课程项目",
            status = ProjectStatus.ABANDONED,
            createdAt = now,
            updatedAt = now,
        )

        val entity = ProjectMapper.toEntity(project)

        assertEquals("ABANDONED", entity.status)
        assertEquals(project, ProjectMapper.toDomain(entity))
    }

    @Test
    fun acT17_domain_from_entity_reads_status_name() {
        val entity = ProjectEntity(
            id = 8L,
            name = "课程项目",
            status = "COMPLETED",
            createdAt = now.toEpochMilli(),
            updatedAt = now.toEpochMilli(),
        )

        val project = ProjectMapper.toDomain(entity)

        assertEquals(ProjectStatus.COMPLETED, project.status)
        assertEquals(ProjectId(8L), project.id)
    }
}
