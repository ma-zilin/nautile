package io.github.mazilin.nautile.ui.screen.todo

import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.ViewOptions
import io.github.mazilin.nautile.domain.rule.TaskGroup
import java.time.LocalDate
import java.time.LocalTime

/**
 * 待办页状态（S1-T07）：只承载当前 GTD 清单、默认分组与待确认的项目操作。
 */
data class TodoUiState(
    val position: GtdPosition = GtdPosition.INBOX,
    val groups: List<TaskGroup> = emptyList(),
    val visibleTasks: List<Task> = emptyList(),
    val projectConfirmation: ProjectConfirmation? = null,
    val projectReopenPrompt: ProjectReopenPrompt? = null,
    /** 搜索入口可用性（设计 §10 RDY-05）：默认 false 保留禁用占位；ViewModel 输出为 true。 */
    val searchAvailable: Boolean = false,
    /** 视图入口可用性（设计 §10 RDY-05）：功能未实现前为 false，入口可见但禁用。 */
    val viewOptionsAvailable: Boolean = false,
    /** 搜索态是否打开（CMP-38）：只影响可见性，不写任务字段、不改排序。 */
    val searchActive: Boolean = false,
    /** 搜索查询串；空串表示不过滤，退出搜索后不保留。 */
    val searchQuery: String = "",
    /** 当前清单生效的视图选项（CMP-39/41）；默认分类=项目、排序=手动。 */
    val viewOptions: ViewOptions = ViewOptions.Default,
    /** 视图选项写入失败提示；非 null 时不显示“已保存”。 */
    val viewOptionsError: String? = null,
    /** 手动排序写入失败提示；失败时列表已回滚到原顺序。 */
    val manualOrderError: String? = null,
    /**
     * 切换清单持久化失败提示（CMP-14）：失败时保留本地新清单，不回滚。
     * 仅记录错误，不阻塞页面使用。
     */
    val positionError: String? = null,
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
    // ---- G6 属性（CMP-30—CMP-37）：新建面板未创建前只保存在内存，点“创建”一次性写入 ----
    /** 优先级，默认无（CMP-33）。 */
    val priority: Priority = Priority.NONE,
    /** 截止日期；清除时同时清空 [dueTime]。 */
    val dueDate: LocalDate? = null,
    /** 可选时刻，必须依附 [dueDate]。 */
    val dueTime: LocalTime? = null,
    /** 所属项目；null 表示无项目。 */
    val projectId: ProjectId? = null,
    /** 项目菜单内“新建项目”的名称草稿（与 PROJECT 类型的 [projectName] 互不影响）。 */
    val projectDraftName: String = "",
    val projectDraftError: String? = null,
    val creatingProject: Boolean = false,
) {
    val canLeave: Boolean get() = !(attempted && invalid)

    val invalid: Boolean
        get() = when (kind) {
            NewTaskKind.TODO -> title.isBlank()
            NewTaskKind.PROJECT -> projectName.isBlank()
        }
}
