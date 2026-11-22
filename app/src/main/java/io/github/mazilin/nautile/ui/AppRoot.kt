package io.github.mazilin.nautile.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mazilin.nautile.interaction.AppTab
import io.github.mazilin.nautile.interaction.FullScreenPage
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.Overlay
import io.github.mazilin.nautile.interaction.OverlayBackStack
import io.github.mazilin.nautile.interaction.PredictiveBackCoordinator
import io.github.mazilin.nautile.interaction.PreviewBackdrop

/**
 * 单一 Compose 宿主（S1-T06/T10、07 §6.3）。
 *
 * 只有底部导航点按切换一级页面；覆盖层/全屏子页通过插槽由 T07—T10 填充。
 * 底部导航可见性 = 滚动状态（由 T07 传入）∧ 无主覆盖层 ∧ 不在全屏子页。
 *
 * 预测性返回（AC-N-21）：仅当开关开启且栈顶为设置根/默认页时注册预览；
 * 手势进度大于 0 时在其下方组合一个无副作用的下层页面，随进度平移露出；
 * 归零/完成/取消时释放组合。下层页面使用无回调、独立本地状态的预览实例，
 * 不写数据、不改真实滚动、不触发导航显隐/覆盖层/返回栈。
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
    previewContent: @Composable (PreviewBackdrop) -> Unit = {},
) {
    val activeTab by navigation.activeTab.collectAsStateWithLifecycle()
    val fullScreenStack by navigation.fullScreenStack.collectAsStateWithLifecycle()
    val overlayEntry by overlays.top.collectAsStateWithLifecycle()
    val topFullScreen = fullScreenStack.lastOrNull()
    val predictiveBack = remember { PredictiveBackCoordinator() }
    val predictiveProgress by predictiveBack.progress.collectAsStateWithLifecycle()

    if (topFullScreen != null) {
        // S1 设置根/默认页没有无效字段；canLeave 恒为 true。
        val previewEnabled = predictiveBackEnabled &&
            predictiveBack.shouldPreview(topFullScreen, canLeave = true)
        val backdrop = when (topFullScreen) {
            FullScreenPage.Settings -> PreviewBackdrop.Tab(activeTab)
            FullScreenPage.DefaultPage -> PreviewBackdrop.FullScreen(FullScreenPage.Settings)
        }
        if (previewEnabled) {
            PredictiveBackHandler { progressFlow ->
                try {
                    progressFlow.collect { event -> predictiveBack.onProgress(event.progress) }
                } finally {
                    // 取消或完成都释放下层组合；取消时协程被取消，不会走到 back()。
                    predictiveBack.reset()
                }
                // 松手完成：沿用既有返回栈，不改变结果。
                navigation.back()
            }
        }
        Box(modifier = modifier.fillMaxSize()) {
            // 仅手势进行中组合下层页面；progress 归零后立即释放。
            if (previewEnabled && predictiveProgress > 0f) {
                previewContent(backdrop)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .graphicsLayer {
                        // 系统返回手势自左向右；当前页右移，逐步露出下层页面。
                        translationX = predictiveProgress * size.width * 0.3f
                    },
            ) {
                fullScreenContent(topFullScreen)
            }
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
