package io.github.mazilin.nautile.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mazilin.nautile.interaction.AppTab
import io.github.mazilin.nautile.interaction.FullScreenPage
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.Overlay
import io.github.mazilin.nautile.interaction.OverlayBackStack
import io.github.mazilin.nautile.interaction.PredictiveBackCoordinator

/**
 * 单一 Compose 宿主（S1-T06/T10、07 §6.3）。
 *
 * 只有底部导航点按切换一级页面；覆盖层/全屏子页通过插槽由 T07—T10 填充。
 * 底部导航可见性 = 滚动状态（由 T07 传入）∧ 无主覆盖层 ∧ 不在全屏子页。
 *
 * 预测性返回（AC-N-21 部分覆盖）：仅当开关开启且栈顶为设置根/默认页时注册预览；
 * 预览只做位移提示，不改变导航栈；半屏/选择器/新建面板/一级页面不注册。
 */
@Composable
fun AppRoot(
    navigation: NavigationCoordinator,
    overlays: OverlayBackStack,
    tabContent: @Composable (AppTab) -> Unit,
    fullScreenContent: @Composable (FullScreenPage) -> Unit,
    modifier: Modifier = Modifier,
    scrollNavigationVisible: Boolean = true,
    predictiveBackEnabled: Boolean = false,
    overlayContent: @Composable (Overlay) -> Unit = {},
) {
    val activeTab by navigation.activeTab.collectAsStateWithLifecycle()
    val fullScreenStack by navigation.fullScreenStack.collectAsStateWithLifecycle()
    val overlayEntry by overlays.top.collectAsStateWithLifecycle()
    val topFullScreen = fullScreenStack.lastOrNull()
    val predictiveBack = remember { PredictiveBackCoordinator() }
    val predictiveProgress by predictiveBack.progress.collectAsStateWithLifecycle()
    val density = LocalDensity.current.density

    if (topFullScreen != null) {
        // S1 设置根/默认页没有无效字段；canLeave 恒为 true。
        val previewEnabled = predictiveBackEnabled &&
            predictiveBack.shouldPreview(topFullScreen, canLeave = true)
        if (previewEnabled) {
            PredictiveBackHandler { progressFlow ->
                progressFlow.collect { event -> predictiveBack.onProgress(event.progress) }
                predictiveBack.reset()
                // 松手完成：沿用既有返回栈，不改变结果。
                navigation.back()
            }
        }
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = predictiveProgress * 48f * density
                },
        ) {
            fullScreenContent(topFullScreen)
        }
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (scrollNavigationVisible && overlayEntry == null) {
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == activeTab,
                            onClick = { navigation.selectTab(tab) },
                            icon = {},
                            label = { Text(tab.title) },
                            modifier = Modifier.testTag("tab_${tab.name}"),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("app_root_content"),
        ) {
            tabContent(activeTab)
            if (overlayEntry != null) {
                overlayContent(overlayEntry!!.overlay)
            }
        }
    }
}
