package io.github.mazilin.nautile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.mazilin.nautile.di.AppContainer
import io.github.mazilin.nautile.interaction.AppTab
import io.github.mazilin.nautile.interaction.FullScreenPage
import io.github.mazilin.nautile.ui.AppRoot
import io.github.mazilin.nautile.ui.screen.common.PlaceholderScreen
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.theme.NautileTheme

/**
 * 单 Activity 宿主：装配 AppRoot 并注册系统返回分发（S1-T06）。
 *
 * 返回顺序由 [io.github.mazilin.nautile.interaction.BackDispatcher] 决定；
 * 本类只提供收键盘动作与“交还系统”的兜底。
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as NautileApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NautileTheme {
                AppRoot(
                    navigation = container.navigationCoordinator,
                    overlays = container.overlayBackStack,
                    tabContent = { tab ->
                        when (tab) {
                            AppTab.TODO -> TodoScreen()
                            else -> PlaceholderScreen(
                                title = tab.title,
                                onOpenSettings = container.navigationCoordinator::navigateToSettings,
                            )
                        }
                    },
                    fullScreenContent = { page ->
                        FullScreenPlaceholder(page, onBack = { container.navigationCoordinator.back() })
                    },
                )
            }
        }

        // 观察 IME 可见性，供返回分发“先收键盘”使用；不改动 Insets 分发结果。
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            container.keyboardCoordinator.updateImeVisible(
                insets.isVisible(WindowInsetsCompat.Type.ime()),
            )
            insets
        }
        window.decorView.requestApplyInsets()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (container.backDispatcher.dispatch(::hideIme)) return
                    // 未消费：临时停用自身，交还系统默认返回（回桌面/退出）。
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            },
        )
    }

    private fun hideIme() {
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.ime())
    }
}

/** T-06 的全屏子页占位；T-10 替换为设置根与默认页的真实内容。 */
@Composable
private fun FullScreenPlaceholder(page: FullScreenPage, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("返回")
            }
            Text(text = page.title, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = "${page.title}（T-10 实现）",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}
