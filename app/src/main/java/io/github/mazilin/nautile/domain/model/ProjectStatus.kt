package io.github.mazilin.nautile.domain.model

/** 项目状态；项目有独立结项状态，不进入任务完成率（D-11、AC-T-17）。 */
enum class ProjectStatus {
    ACTIVE,
    COMPLETED,
    ABANDONED,
}
