package io.github.mazilin.nautile.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.interaction.AppTab
import io.github.mazilin.nautile.interaction.FullScreenPage
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.Overlay
import io.github.mazilin.nautile.interaction.OverlayBackStack
import io.github.mazilin.nautile.interaction.PredictiveBackCoordinator
import io.github.mazilin.nautile.interaction.PreviewBackdrop
import io.github.mazilin.nautile.ui.theme.NautileMotion
import io.github.mazilin.nautile.ui.theme.NautileStates
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 单一 Compose 宿主（S1-T06/T10、07 §6.3）。
 *
 * 只有底部导航点按切换一级页面；覆盖层/全屏子页通过插槽由 T07—T10 填充。
 * 底部导航可见性 = 滚动状态（由 T07 传入）∧ 无主覆盖层 ∧ 不在全屏子页。
 *
 * 页面切换（MOT-09）：
 * - 一级页：旧内容 alpha 1→0、新内容 0→1（[NautileMotion.StandardEffects]），
 *   新内容可带 8dp 垂直位移（[NautileMotion.Expressive]）；不做横向滑动；
 * - 全屏子页 push：新页 `translationX = width → 0`（Expressive），旧页 `0 → -0.3×width`，
 *   遮罩 0→0.32（StandardEffects）；pop 在状态移除后直接收尾；
 * - 预测性返回手势进行中直接按进度映射，不叠加自绘动画；初次进入不播放入场。
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

    // 一级页切换：旧内容淡出、新内容淡入并带 8dp 垂直位移，不做横向滑动（MOT-09.1）。
    // AnimatedContent 保留旧页自身的组合状态（不重建），过渡结束后再释放。
    val tabOffsetPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    // 仅底部导航点按触发新内容 8dp 入场；首次进入与外部状态变化不放（MOT-09.4）。
    var animateTabEntry by remember { mutableStateOf(false) }

    // 全屏子页 push：新页从右侧滑入，旧页左移 0.3×宽度，遮罩淡入（MOT-09.2）。
    val pushEnter = remember { Animatable(if (topFullScreen == null) 0f else 1f) }
    val pushScrim = remember { Animatable(if (topFullScreen == null) 0f else 1f) }
    var baseRendered by remember { mutableStateOf(topFullScreen == null) }
    var fullScreenMotionInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(topFullScreen) {
        if (!fullScreenMotionInitialized) {
            fullScreenMotionInitialized = true
            baseRendered = topFullScreen == null
            return@LaunchedEffect
        }
        if (topFullScreen != null) {
            baseRendered = true
            pushEnter.snapTo(0f)
            pushScrim.snapTo(0f)
            coroutineScope {
                launch { pushEnter.animateTo(1f, NautileMotion.Expressive) }
                launch { pushScrim.animateTo(1f, NautileMotion.StandardEffects) }
            }
            // push 完成后释放下层，回到“全屏时只组合子页”的既有语义。
            baseRendered = false
        } else {
            // pop 时页面状态已移除，直接显示下层，不叠加自绘退出动画（MOT-09.2）。
            pushEnter.snapTo(0f)
            pushScrim.snapTo(0f)
            baseRendered = true
        }
    }

    val previewBackdrop = topFullScreen?.let { page ->
        when (page) {
            FullScreenPage.Settings -> PreviewBackdrop.Tab(activeTab)
            FullScreenPage.DefaultPage -> PreviewBackdrop.FullScreen(FullScreenPage.Settings)
        }
    }
    val previewEnabled = topFullScreen != null &&
        predictiveBackEnabled &&
        predictiveBack.shouldPreview(topFullScreen, canLeave = true)
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
        if (baseRendered) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    if (scrollNavigationVisible && overlayEntry == null && topFullScreen == null) {
                        NavigationBar {
                            AppTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = tab == activeTab,
                                    onClick = {
                                        if (tab != activeTab) animateTabEntry = true
                                        navigation.selectTab(tab)
                                    },
                                    icon = {
                                        // 装饰性图标：可见标签负责可读性，避免 TalkBack 重复朗读（§6.2、A11Y-16）。
                                        Icon(
                                            painter = painterResource(tab.iconRes),
                                            contentDescription = null,
                                        )
                                    },
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
                        .graphicsLayer {
                            // push 时旧页左移 0.3×宽度，位移用 Expressive（MOT-09.2）。
                            translationX = -0.3f * size.width * pushEnter.value
                        }
                        .testTag("app_root_content"),
                ) {
                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = {
                            // 旧内容 StandardEffects 淡出；新内容 StandardEffects 淡入（MOT-09.1）。
                            fadeIn(animationSpec = NautileMotion.StandardEffects) togetherWith
                                fadeOut(animationSpec = NautileMotion.StandardEffects)
                        },
                        label = "tab_switch",
                    ) { tab ->
                        // 位移单独用 Float 动效 token：新内容从 8dp 回到 0，用 Expressive。
                        val entryOffset = remember {
                            Animatable(if (animateTabEntry) tabOffsetPx.toFloat() else 0f)
                        }
                        LaunchedEffect(Unit) {
                            if (animateTabEntry) {
                                entryOffset.animateTo(0f, NautileMotion.Expressive)
                                animateTabEntry = false
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationY = entryOffset.value },
                        ) {
                            tabContent(tab)
                        }
                    }
                    if (overlayEntry != null && topFullScreen == null) {
                        overlayContent(overlayEntry!!.overlay)
                    }
                }
            }
        }

        if (topFullScreen != null && previewBackdrop != null) {
            // 遮罩：push 用 StandardEffects 淡入；预测手势进行中按进度映射，不播自绘动画。
            val scrimColor = MaterialTheme.colorScheme.scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = pushScrim.value * (1f - predictiveProgress)
                    }
                    .background(scrimColor.copy(alpha = NautileStates.SCRIM_ALPHA)),
            )
            // 仅手势进行中组合下层页面；progress 归零后立即释放。
            if (previewEnabled && predictiveProgress > 0f) {
                previewContent(previewBackdrop)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .graphicsLayer {
                        // 系统返回手势自左向右；当前页右移，逐步露出下层页面。
                        translationX =
                            (1f - pushEnter.value) * size.width +
                                predictiveProgress * size.width * 0.3f
                    },
            ) {
                fullScreenContent(topFullScreen)
            }
        }
    }
}

/** 底部导航图标（设计 §6.2 #1—#4）：顺序与标签固定，图标为装饰性。 */
private val AppTab.iconRes: Int
    get() = when (this) {
        AppTab.TODO -> R.drawable.ic_checklist
        AppTab.SCHEDULE -> R.drawable.ic_calendar_today
        AppTab.LOG -> R.drawable.ic_schedule
        AppTab.AI -> R.drawable.ic_auto_awesome
    }
