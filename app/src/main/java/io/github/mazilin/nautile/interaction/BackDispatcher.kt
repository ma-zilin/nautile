package io.github.mazilin.nautile.interaction

/**
 * 系统返回分发（07 §6.3）：IME → 覆盖层 → 全屏子页 → 一级页面/系统。
 *
 * 抽成纯 Kotlin 类，便于在 JVM 测试固定顺序；MainActivity 只负责提供收键盘回调与兜底。
 */
class BackDispatcher(
    private val keyboard: KeyboardCoordinator,
    private val overlays: OverlayBackStack,
    private val navigation: NavigationCoordinator,
) {

    /** @param hideKeyboard 真正收起 IME 的平台动作；仅在 IME 可见时调用。
     *  @return true 表示已消费返回；false 表示交给系统（退出/回桌面）。
     */
    fun dispatch(hideKeyboard: () -> Unit): Boolean {
        if (keyboard.handleBack(hideKeyboard)) return true
        return when (overlays.back()) {
            OverlayBackResult.Closed, OverlayBackResult.Blocked -> true
            OverlayBackResult.NoOverlay -> navigation.back()
        }
    }
}
