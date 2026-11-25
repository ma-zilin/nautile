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
    /** 搜索入口可用性（设计 §10 RDY-05）：功能未实现前为 false，入口可见但禁用。 */
    val searchAvailable: Boolean = false,
    /** 视图入口可用性（设计 §10 RDY-05）：功能未实现前为 false，入口可见但禁用。 */
    val viewOptionsAvailable: Boolean = false,
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

/**
 * 新建待办底部面板状态（S1-T09、FR-D-02、BR-69；T-071 增加项目/目标清单）。
 *
 * 只有点“创建”才产生记录；取消/关闭不生成半成品。
 * [attempted] 为 true 且当前表单无效时，系统返回/背景关闭被阻止并提示。
 */
enum class NewTaskKind { TODO, PROJECT }

data class NewTaskUiState(
    val kind: NewTaskKind = NewTaskKind.TODO,
    val position: GtdPosition = GtdPosition.INBOX,
    val targetPosition: GtdPosition = GtdPosition.INBOX,
    val showTargetPicker: Boolean = false,
    val title: String = "",
    val titleInvalid: Boolean = false,
    val projectName: String = "",
    val projectNameInvalid: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val attempted: Boolean = false,
    val detail: String = "",
) {
    val canLeave: Boolean get() = !(attempted && invalid)

    val invalid: Boolean
        get() = when (kind) {
            NewTaskKind.TODO -> title.isBlank()
            NewTaskKind.PROJECT -> projectName.isBlank()
        }
}
