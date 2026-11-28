package io.github.mazilin.nautile.interaction

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 四个一级入口；设置不占底部导航（FR-N-18、BR-59）。 */
enum class AppTab(val title: String) {
    TODO("待办"),
    SCHEDULE("日程"),
    LOG("日志"),
    AI("AI"),
}

/** 全屏子页；S1 有设置根与默认页，R6-G4 增加分类管理（AC-N-21、D-11）。 */
sealed interface FullScreenPage {
    val title: String

    data object Settings : FullScreenPage {
        override val title: String = "设置"
    }

    data object DefaultPage : FullScreenPage {
        override val title: String = "默认页"
    }

    /** 分类管理全屏子页：从根设置进入，返回“分类管理 → 根设置”（05 §2.6）。 */
    data object CategoryManagement : FullScreenPage {
        override val title: String = "分类管理"
    }

    /** 标签管理全屏子页：从根设置进入，返回“标签管理 → 根设置”（R7 Q2）。 */
    data object TagManagement : FullScreenPage {
        override val title: String = "标签管理"
    }
}

/**
 * 四入口与全屏子页栈（FR-N-18—N-20、AC-N-14）。
 *
 * 一级页面只允许底部导航点按切换；全屏子页打开时不接受 tab 切换。
 * 预测返回预览由 T10 的协调器接入，本类只维护栈与普通返回。
 */
class NavigationCoordinator {

    private val _activeTab = MutableStateFlow(AppTab.TODO)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    private val _fullScreenStack = MutableStateFlow<List<FullScreenPage>>(emptyList())
    val fullScreenStack: StateFlow<List<FullScreenPage>> = _fullScreenStack.asStateFlow()

    val currentFullScreenPage: FullScreenPage? get() = _fullScreenStack.value.lastOrNull()

    private var initialTabApplied = false

    /**
     * 启动时按设置恢复一级页面（AC-N-14、FR-N-20）。
     *
     * 只生效一次，避免设置 Flow 后续发射覆盖用户当前选择；首次启动无 lastPage 时由调用方
     * 传入 defaultPage。全屏子页栈非空时不改动当前页面。
     */
    fun applyInitialTab(tab: AppTab) {
        if (initialTabApplied) return
        initialTabApplied = true
        if (_fullScreenStack.value.isEmpty()) {
            _activeTab.value = tab
        }
    }

    fun selectTab(tab: AppTab) {
        if (_fullScreenStack.value.isNotEmpty()) return
        _activeTab.value = tab
    }

    /** 任一一级页面的设置入口都进入同一个根设置页（FR-N-19）。 */
    fun navigateToSettings() {
        if (currentFullScreenPage != FullScreenPage.Settings) {
            _fullScreenStack.update { it + FullScreenPage.Settings }
        }
    }

    fun navigateToDefaultPage() {
        if (currentFullScreenPage != FullScreenPage.DefaultPage) {
            _fullScreenStack.update { it + FullScreenPage.DefaultPage }
        }
    }

    /** 根设置 → 分类管理（全屏子页，05 §2.6）。 */
    fun navigateToCategoryManagement() {
        if (currentFullScreenPage != FullScreenPage.CategoryManagement) {
            _fullScreenStack.update { it + FullScreenPage.CategoryManagement }
        }
    }

    /** 根设置 → 标签管理（全屏子页，R7 Q2）。 */
    fun navigateToTagManagement() {
        if (currentFullScreenPage != FullScreenPage.TagManagement) {
            _fullScreenStack.update { it + FullScreenPage.TagManagement }
        }
    }

    /** 标签管理入口别名，等价于 [navigateToTagManagement]。 */
    fun openTagManagement() = navigateToTagManagement()

    /** 全屏子页返回；栈空时返回 false，交系统处理。 */
    fun back(): Boolean {
        if (_fullScreenStack.value.isEmpty()) return false
        _fullScreenStack.update { it.dropLast(1) }
        return true
    }
}
