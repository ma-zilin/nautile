package io.github.mazilin.nautile.domain.model

import java.time.Instant

/** 项目 ID 值类；以 Long 承载。 */
@JvmInline
value class ProjectId(val value: Long)

/**
 * 项目业务事实（AC-T-17）。
 *
 * 项目是任务的独立容器/视图，不是 GTD 位置（D-41）；
 * 项目状态不进入任务完成率（D-11）。
 */
data class Project(
    val id: ProjectId,
    val name: String,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val createdAt: Instant,
    val updatedAt: Instant,
)
