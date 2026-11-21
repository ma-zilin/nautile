package io.github.mazilin.nautile.domain.model

/**
 * 用户设置（AC-N-14、AC-N-21）。
 *
 * 设置只属于 UI 偏好，不参与任务/项目业务事实（07 §2.1）。
 *
 * @property lastPage 上次一级页面；首次使用尚未记录时为 null，回落到 [defaultPage]
 * @property lastGtdPosition 待办上次 GTD 清单；尚未记录时为 null
 * @property perModuleViewOptions 按 GTD 模块记忆的视图选项，内容为不透明文本；
 *   S1 只做默认分组（D-10），不解析具体选项，键值格式由 T05/T07 约定
 */
data class UserSettings(
    val defaultPage: AppPage = AppPage.TODO,
    val lastPage: AppPage? = null,
    val lastGtdPosition: GtdPosition? = null,
    val predictiveBackEnabled: Boolean = true,
    val perModuleViewOptions: Map<GtdPosition, String> = emptyMap(),
)
