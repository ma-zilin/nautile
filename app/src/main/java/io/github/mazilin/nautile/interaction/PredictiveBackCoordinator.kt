package io.github.mazilin.nautile.interaction

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 预测预览下方需要组合的页面（T-075、AC-N-21）。
 *
 * 只描述“视觉下层”，不携带任何可写状态或回调；组合方必须使用无副作用参数。
 */
sealed interface PreviewBackdrop {
    data class Tab(val tab: AppTab) : PreviewBackdrop

    data class FullScreen(val page: FullScreenPage) : PreviewBackdrop
}

/**
 * 预测性返回预览范围（AC-N-21 部分覆盖、FR-N-30）。
 *
 * 只在栈顶为全屏子页（设置根/默认页）且 `canLeave` 为 true 时允许注册预览；
 * 半屏、GTD 选择器、新建面板与一级页面不注册；关闭开关由调用方另行判断。
 * 预览只记录进度，不改变导航栈或业务数据。
 */
class PredictiveBackCoordinator {

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    /** 当前栈顶页面是否允许注册预测预览。 */
    fun shouldPreview(page: FullScreenPage?, canLeave: Boolean): Boolean =
        page != null && canLeave

    /** 系统回传的预览进度，夹在 0..1；松手完成或取消后调用 [reset]。 */
    fun onProgress(value: Float) {
        _progress.value = value.coerceIn(0f, 1f)
    }

    fun reset() {
        _progress.value = 0f
    }
}
