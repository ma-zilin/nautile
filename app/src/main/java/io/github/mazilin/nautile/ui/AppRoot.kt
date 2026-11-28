package io.github.mazilin.nautile.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
    /** 导航上方插槽（R6-G4 计时条）；与导航同一可见性条件下渲染，隐藏时整块不占位。 */
    bottomBarContent: @Composable () -> Unit = {},
) {
    val activeTab by navigation.activeTab.collectAsStateWithLifecycle()
    val fullScreenStack by navigation.fullScreenStack.collectAsStateWithLifecycle()
    val overlayEntry by overlays.top.collectAsStateWithLifecycle()
    val topFullScreen = fullScreenStack.lastOrNull()
    val fullScreenDepth = fullScreenStack.size
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
    var lastFullScreenDepth by remember { mutableStateOf(fullScreenDepth) }
    // pop 时保留最后一页到退出动画结束再移除（CMP-29），返回栈语义不变。
    var displayedFullScreen by remember { mutableStateOf(topFullScreen) }
    // pop 到上一个全屏页（设置根↔默认页）时，动画期间在下方绘制目标页。
    var underlayFullScreen by remember { mutableStateOf<FullScreenPage?>(null) }
    // 预测性返回手势完成时由系统动画收尾，不叠加 200ms 自绘 pop（MOT-09.2）。
    var skipPopAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(topFullScreen, fullScreenDepth) {
        val previousDepth = lastFullScreenDepth
        lastFullScreenDepth = fullScreenDepth
        if (!fullScreenMotionInitialized) {
            fullScreenMotionInitialized = true
            displayedFullScreen = topFullScreen
            baseRendered = topFullScreen == null
            return@LaunchedEffect
        }
        if (topFullScreen != null && fullScreenDepth >= previousDepth) {
            // push：新页从右侧滑入，旧页左移 0.3×宽度，遮罩淡入（MOT-09.2）。
            displayedFullScreen = topFullScreen
            underlayFullScreen = null
            skipPopAnimation = false
            baseRendered = true
            pushEnter.snapTo(0f)
            pushScrim.snapTo(0f)
            coroutineScope {
                launch { pushEnter.animateTo(1f, NautileMotion.Expressive) }
                launch { pushScrim.animateTo(1f, NautileMotion.StandardEffects) }
            }
            // push 完成后释放下层，回到“全屏时只组合子页”的既有语义。
            baseRendered = false
        } else if (topFullScreen != null) {
            // pop 到上一个全屏页（如默认页 → 设置根）：保留离开页到动画结束，
            // 目标页作为下层绘制；位移 StandardSpatial（约 200ms）。
            if (displayedFullScreen == null) {
                displayedFullScreen = topFullScreen
                baseRendered = false
                pushEnter.snapTo(1f)
                pushScrim.snapTo(1f)
                return@LaunchedEffect
            }
            underlayFullScreen = topFullScreen
            baseRendered = false
            if (skipPopAnimation) {
                skipPopAnimation = false
                pushEnter.snapTo(0f)
                pushScrim.snapTo(0f)
            } else {
                coroutineScope {
                    launch { pushEnter.animateTo(0f, NautileMotion.StandardSpatial) }
                    launch { pushScrim.animateTo(0f, NautileMotion.StandardEffects) }
                }
            }
            displayedFullScreen = topFullScreen
            underlayFullScreen = null
            // 回到目标页的静止态。
            pushEnter.snapTo(1f)
            pushScrim.snapTo(1f)
        } else {
            if (displayedFullScreen == null) {
                baseRendered = true
                return@LaunchedEffect
            }
            // pop 到一级页：当前页 0 → width、旧页 -0.3×width → 0、遮罩 0.32 → 0，
            // 位移用 StandardSpatial（约 200ms），页面保留到动画结束（MOT-09.2、CMP-29）。
            underlayFullScreen = null
            baseRendered = true
            if (skipPopAnimation) {
                skipPopAnimation = false
                pushEnter.snapTo(0f)
                pushScrim.snapTo(0f)
            } else {
                coroutineScope {
                    launch { pushEnter.animateTo(0f, NautileMotion.StandardSpatial) }
                    launch { pushScrim.animateTo(0f, NautileMotion.StandardEffects) }
                }
            }
            displayedFullScreen = null
        }
    }

    val previewBackdrop = topFullScreen?.let { page ->
        when (page) {
            FullScreenPage.Settings -> PreviewBackdrop.Tab(activeTab)
            FullScreenPage.DefaultPage -> PreviewBackdrop.FullScreen(FullScreenPage.Settings)
            // 分类管理返回根设置；预览下层映射为设置页（架构 §5.4）。
            FullScreenPage.CategoryManagement -> PreviewBackdrop.FullScreen(FullScreenPage.Settings)
        }
    }
    // pop 动画期间仍按最后一页渲染，结束后才移除（CMP-29）。
    val displayBackdrop = displayedFullScreen?.let { page ->
        when (page) {
            FullScreenPage.Settings -> PreviewBackdrop.Tab(activeTab)
            FullScreenPage.DefaultPage -> PreviewBackdrop.FullScreen(FullScreenPage.Settings)
            FullScreenPage.CategoryManagement -> PreviewBackdrop.FullScreen(FullScreenPage.Settings)
        }
    }
    // pop 到上一个全屏页时，动画期间用只读实例绘制目标页，避免触发导航/数据副作用。
    val underlayBackdrop = underlayFullScreen?.let { PreviewBackdrop.FullScreen(it) }
    val previewEnabled = topFullScreen != null &&
        predictiveBackEnabled &&
        predictiveBack.shouldPreview(topFullScreen, canLeave = true)
    if (previewEnabled) {
        PredictiveBackHandler { progressFlow ->
            var hadProgress = false
            try {
                progressFlow.collect { event ->
                    if (event.progress > 0f) hadProgress = true
                    predictiveBack.onProgress(event.progress)
                }
                // 手势完成：以系统动画为准，不再播 200ms 自绘 pop，避免双动画（MOT-09.2）。
                if (hadProgress) skipPopAnimation = true
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
                        Column {
                            // 计时条是导航的扩展：同一可见性条件下渲染在导航正上方（05 §2.7）。
                            bottomBarContent()
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

        if (displayedFullScreen != null && displayBackdrop != null) {
            // 下层目标页（pop 到设置根时）；预测手势另有 previewContent 路径。
            if (underlayBackdrop != null) {
                // 全屏页统一容器：背景铺满 + 内容区只消费一次 safeDrawing（SPC-09、CMP-50）。
                FullScreenPageContainer {
                    previewContent(underlayBackdrop)
                }
            }
            // 遮罩：push 用 StandardEffects 淡入；pop 同 spec 淡出；预测手势进行中按进度映射。
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
                previewBackdrop?.let { backdrop ->
                    // 全屏页预览沿用同一容器与 insets（只读预览，不新增副作用）；
                    // 一级页预览由页面自身 Scaffold 处理 inset，不在此重复加。
                    if (backdrop is PreviewBackdrop.FullScreen) {
                        FullScreenPageContainer { previewContent(backdrop) }
                    } else {
                        previewContent(backdrop)
                    }
                }
            }
            FullScreenPageContainer(
                modifier = Modifier.graphicsLayer {
                    // 系统返回手势自左向右；当前页右移，逐步露出下层页面。
                    translationX =
                        (1f - pushEnter.value) * size.width +
                            predictiveProgress * size.width * 0.3f
                },
            ) {
                displayedFullScreen?.let { fullScreenContent(it) }
            }
        }
    }
}

/**
 * 全屏子页统一容器（SPC-09、CMP-50）。
 *
 * Surface 绘制 `background` 并铺满屏幕（含状态栏/导航栏区域），`contentColor = onBackground`
 * 保证深色下文字可读；内容区 `safeDrawing`（状态栏上 + 导航栏下）只在此消费一次，
 * 页面内部不再重复 padding。宿主其余分支（Scaffold/抽屉/面板）各自既有策略不变。
 */
@Composable
private fun FullScreenPageContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            content()
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
