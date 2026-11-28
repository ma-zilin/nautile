package io.github.mazilin.nautile.interaction

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 主覆盖层：待办详情/GTD/新建，以及日程新建/详情（R6 G3）。 */
sealed interface Overlay {
    data class TaskDetail(val taskId: Long? = null) : Overlay

    data object ProjectDetail : Overlay

    data object GtdSelector : Overlay

    data object NewTaskSheet : Overlay

    /** 日程新建半屏；只承载 UI，创建成功才落库（AC-D-07）。 */
    data object NewSchedule : Overlay

    /** 日程详情半屏；id 仅用于调试与宿主识别，数据由 ViewModel 读取。 */
    data class ScheduleDetail(val scheduleId: Long) : Overlay

    /** 日志补录/新建半屏；保存才落库，取消不生成半成品（AC-L-02）。 */
    data object NewLog : Overlay

    /** 日志详情半屏；字段直接编辑即保存（AC-D-06 记录分支）。 */
    data class LogDetail(val logId: Long) : Overlay

    /** “全部分类”两级选择器；选中分类后开始计时并关闭（AC-T-21）。 */
    data object LogCategoryPicker : Overlay

    /** 日志模式选择器半屏；打开不改变模式，选中“记录/统计”后才切换并关闭（AC-T-20）。 */
    data object LogModeSelector : Overlay

    /** 统计自定义范围半屏；确认合法范围后关闭并刷新统计（AC-L-07/11）。 */
    data object StatsPeriodSelector : Overlay
}

/**
 * 覆盖层栈项；[canDismiss] 由界面提供，读取当前字段校验状态。
 */
data class OverlayEntry(
    val overlay: Overlay,
    val canDismiss: () -> Boolean,
)

/** 覆盖层返回结果；Blocked 表示已消费返回但需提示用户修正字段。 */
sealed interface OverlayBackResult {
    data object NoOverlay : OverlayBackResult
    data object Closed : OverlayBackResult
    data object Blocked : OverlayBackResult
}

/**
 * 单一主覆盖层（07 §6.3、交互 02 §1.3）。
 *
 * 同一时刻只允许一个详情/GTD 选择器/新建面板；打开第二个时返回 false，由调用方先关闭当前项。
 */
class OverlayBackStack {

    private val _top = MutableStateFlow<OverlayEntry?>(null)
    val top: StateFlow<OverlayEntry?> = _top.asStateFlow()

    val current: OverlayEntry? get() = _top.value

    /** 打开覆盖层；已有覆盖层时不替换并返回 false。 */
    fun open(overlay: Overlay, canDismiss: () -> Boolean = { true }): Boolean {
        if (_top.value != null) return false
        _top.value = OverlayEntry(overlay, canDismiss)
        return true
    }

    /** 字段状态变化后更新关闭条件（例如详情从无效变为有效）。 */
    fun updateCanDismiss(canDismiss: () -> Boolean) {
        _top.value = _top.value?.copy(canDismiss = canDismiss)
    }

    fun close() {
        _top.value = null
    }

    /** 返回：无覆盖层→NoOverlay；可关闭→Closed；不可关闭→Blocked 且保留覆盖层。 */
    fun back(): OverlayBackResult {
        val entry = _top.value ?: return OverlayBackResult.NoOverlay
        return if (entry.canDismiss()) {
            _top.value = null
            OverlayBackResult.Closed
        } else {
            OverlayBackResult.Blocked
        }
    }
}
