package io.github.mazilin.nautile.interaction

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * IME 与两步返回协调（FR-D-02、BR-69、07 §6.3）。
 *
 * 返回优先级最高的一步：键盘可见时先收键盘，不关闭面板/详情。
 */
class KeyboardCoordinator {

    private val _imeVisible = MutableStateFlow(false)
    val imeVisible: StateFlow<Boolean> = _imeVisible.asStateFlow()

    fun updateImeVisible(visible: Boolean) {
        _imeVisible.value = visible
    }

    /** 键盘可见时隐藏并消费返回；返回 true 表示已消费。 */
    fun handleBack(hideKeyboard: () -> Unit): Boolean {
        if (!_imeVisible.value) return false
        hideKeyboard()
        _imeVisible.value = false
        return true
    }
}
