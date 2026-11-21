package io.github.mazilin.nautile.domain.model

/**
 * GTD 任务组织位置（D-41）。
 *
 * 五个位置不含“日历”；项目是独立容器，不作为第六个位置。
 * 以枚举名文本持久化（T-02 接口约定）。
 */
enum class GtdPosition {
    INBOX,
    NEXT_ACTION,
    WAITING,
    SOMEDAY,
    ENDED,
}
