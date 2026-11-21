package io.github.mazilin.nautile.ui.screen.todo

import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.rule.TaskGroup

/**
 * 待办页状态（S1-T07）：只承载当前 GTD 清单、默认分组与待确认的项目操作。
 */
data class TodoUiState(
    val position: GtdPosition = GtdPosition.INBOX,
    val groups: List<TaskGroup> = emptyList(),
    val visibleTasks: List<Task> = emptyList(),
    val projectConfirmation: ProjectConfirmation? = null,
    val projectReopenPrompt: ProjectReopenPrompt? = null,
) {
    val isEmpty: Boolean get() = visibleTasks.isEmpty()
}

/** 项目滑动后的数量确认；文案由界面按 [outcome] 与 [taskCount] 生成。 */
data class ProjectConfirmation(
    val projectId: ProjectId,
    val projectName: String,
    val outcome: ClosedOutcome,
    val taskCount: Int,
)
