package io.github.mazilin.nautile.domain.model

/** 任务结项方式；完成与放弃都原子进入“已结束”（AC-T-01、D-11）。 */
enum class ClosedOutcome {
    COMPLETED,
    ABANDONED,
}
