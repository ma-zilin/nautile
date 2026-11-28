package io.github.mazilin.nautile.verification

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.repository.RoomTimeCategoryRepository
import io.github.mazilin.nautile.data.repository.RoomTimeLogRepository
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.usecase.TimeLogCommands
import io.github.mazilin.nautile.interaction.AppTab
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.OverlayBackStack
import io.github.mazilin.nautile.ui.AppRoot
import io.github.mazilin.nautile.ui.screen.common.PlaceholderScreen
import io.github.mazilin.nautile.ui.screen.log.LogOverlayHost
import io.github.mazilin.nautile.ui.screen.log.LogScreen
import io.github.mazilin.nautile.ui.screen.log.LogViewModel
import io.github.mazilin.nautile.ui.screen.log.RunningTimerBar
import io.github.mazilin.nautile.ui.theme.NautileTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R6-G4-V2 · AC-T-21 / AC-T-22 / AC-T-23 计时与计时条（自动 UI）。
 *
 * 宿主沿用 AppRoot + NavigationCoordinator，与 MainActivity 相同接线（不启动 Activity）。
 * 计时条显隐：导航隐藏（scrollNavigationVisible=false）时整块移除，但仓库中的运行中记录不变。
 *
 * 说明：为聚焦计时行为，除「全部分类入口」用例外，其余用例先用一条已完成记录建立“最近使用”，
 * 使快捷入口可点；该前置仅为搭建入口，不改变被验行为。
 */
@RunWith(AndroidJUnit4::class)
class AcLogT21T22T23UiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: io.github.mazilin.nautile.data.db.NautileDatabase
    private lateinit var categoryRepository: RoomTimeCategoryRepository
    private lateinit var logRepository: RoomTimeLogRepository
    private lateinit var clock: G4Clock
    private lateinit var viewModel: LogViewModel
    private val navigation = NavigationCoordinator()
    private val overlays = OverlayBackStack()

    private var study: TimeCategoryId = TimeCategoryId(0)
    private var work: TimeCategoryId = TimeCategoryId(0)

    @Before
    fun setUp() {
        database = LogG4.freshDatabase(context)
        categoryRepository = RoomTimeCategoryRepository(database)
        logRepository = RoomTimeLogRepository(database)
        clock = G4Clock()
        viewModel = LogViewModel(
            logRepository = logRepository,
            categoryRepository = categoryRepository,
            settingsRepository = G4FakeSettingsRepository(),
            commands = TimeLogCommands(logRepository, categoryRepository, clock),
            clock = clock,
            zoneId = LogG4.zone,
        )
        runBlocking {
            study = LogG4.seedCategory(categoryRepository, "学习")
            work = LogG4.seedCategory(categoryRepository, "工作")
        }
        // 宿主默认进入日志页（MainActivity 由设置恢复；测试直接指定）。
        navigation.selectTab(AppTab.LOG)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** 建立“最近使用”以便快捷入口出现（AC-T-21 入口缺陷由单独用例红证）。 */
    private fun seedHistory(categoryId: TimeCategoryId = study) {
        runBlocking {
            LogG4.seedLog(
                logRepository,
                categoryId,
                LogG4.instant(LogG4.today, 8),
                LogG4.instant(LogG4.today, 9),
            )
        }
    }

    @Composable
    private fun Host() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val activeTab by navigation.activeTab.collectAsStateWithLifecycle()
        var navVisible by remember { mutableStateOf(true) }
        var overlay by remember { mutableStateOf<String?>(null) }

        NautileTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        navigation = navigation,
                        overlays = overlays,
                        scrollNavigationVisible = navVisible,
                        tabContent = { tab ->
                            when (tab) {
                                AppTab.LOG -> LogScreen(
                                    state = state,
                                    onCategoryTap = viewModel::onCategoryQuickTap,
                                    onShowAllCategories = { overlay = "picker" },
                                    onOpenLog = {},
                                    onOpenManual = viewModel::onOpenManual,
                                    onDismissMessage = viewModel::onDismissMessage,
                                )

                                else -> PlaceholderScreen(title = tab.title, onOpenSettings = {})
                            }
                        },
                        fullScreenContent = { },
                        bottomBarContent = {
                            // 与 MainActivity 一致：日志页有运行中才渲染计时条。
                            if (activeTab == AppTab.LOG && state.running.isNotEmpty()) {
                                Box(modifier = Modifier.testTag("timer_bar_scope")) {
                                    RunningTimerBar(
                                        running = state.running,
                                        onTimerTap = viewModel::onRunningTimerTap,
                                    )
                                }
                            }
                        },
                    )
                    LogOverlayHost(
                        viewModel = viewModel,
                        editor = null,
                        detail = null,
                        uiState = state,
                        pickerOpen = overlay == "picker",
                        onCategoryPicked = { id -> viewModel.onCategoryQuickTap(id) },
                        onPickerClosed = { overlay = null },
                        onOverlayClosed = { overlay = null },
                    )
                    // 测试专用：模拟导航自动隐藏（不进入生产代码）。
                    Column(modifier = Modifier.align(Alignment.TopStart)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("test_toggle_nav")
                                .clickable { navVisible = !navVisible },
                        )
                    }
                }
            }
        }
    }

    private fun awaitTag(tag: String, present: Boolean = true, timeout: Long = 5_000) {
        composeRule.waitUntil(timeout) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present
        }
        composeRule.waitForIdle()
    }

    private fun runningIds(): List<Long> =
        runBlocking { logRepository.observeRunning().first().map { it.id.value } }

    private fun startViaPicker(categoryId: TimeCategoryId) {
        composeRule.onNodeWithTag("log_all_categories").performClick()
        awaitTag("category_picker")
        composeRule.onNodeWithTag("category_parent_${categoryId.value}").performClick()
        awaitTag("category_picker", present = false)
    }

    @Test
    fun AC_T_21_all_categories_entry_is_available_without_history() {
        // 基线 AC-T-21 + 设计 05 §3.3：快捷入口末尾常驻“全部分类”，无历史时也可开始计时。
        composeRule.setContent { Host() }
        awaitTag("log_empty_state")
        composeRule.onAllNodesWithTag("log_all_categories").assertCountEquals(1)
        composeRule.onNodeWithTag("log_all_categories").assertIsDisplayed()
    }

    @Test
    fun AC_T_21_start_stop_restart_without_pause() {
        seedHistory()
        composeRule.setContent { Host() }
        awaitTag("log_category_quick_${study.value}")

        // 点分类开始。
        composeRule.onNodeWithTag("log_category_quick_${study.value}").performClick()
        awaitTag("timer_bar_scope")
        val firstId = runningIds().single()
        // 计时条模块的 tag 以分类 id 标识（同分类最多一段）。
        awaitTag("running_timer_${study.value}")
        val firstStart = runBlocking { logRepository.get(io.github.mazilin.nautile.domain.model.TimeLogId(firstId)) }?.startAt

        clock.advanceSeconds(120)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("00:02:00").fetchSemanticsNodes().isNotEmpty()
        }

        // 同分类再点：只提示，不重置、不结束。
        composeRule.onNodeWithTag("log_category_quick_${study.value}").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("该分类已在计时").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("同分类不得出现第二段", listOf(firstId), runningIds())
        assertEquals(
            "开始时刻不得重置",
            firstStart,
            runBlocking { logRepository.get(io.github.mazilin.nautile.domain.model.TimeLogId(firstId)) }?.startAt,
        )

        // 无暂停按钮/状态。
        composeRule.onAllNodesWithText("暂停").assertCountEquals(0)
        composeRule.onAllNodesWithTag("pause").assertCountEquals(0)

        // 点计时条结束并保存。
        composeRule.onNodeWithTag("running_timer_${study.value}").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("已结束并保存", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        awaitTag("timer_bar_scope", present = false)
        val saved = runBlocking {
            logRepository.get(io.github.mazilin.nautile.domain.model.TimeLogId(firstId))
        }
        assertEquals("按结束时刻保存", clock.now(), saved?.endAt)

        // 再次开始生成新记录（不复用旧记录）。
        composeRule.onNodeWithTag("log_category_quick_${study.value}").performClick()
        awaitTag("timer_bar_scope")
        val secondId = runningIds().single()
        assertNotEquals("再次开始必须生成新记录", firstId, secondId)

        // 重启后不存在暂停状态：新建 ViewModel 复用同一仓库，运行中记录恢复。
        val reopened = LogViewModel(
            logRepository = logRepository,
            categoryRepository = categoryRepository,
            settingsRepository = G4FakeSettingsRepository(),
            commands = TimeLogCommands(logRepository, categoryRepository, clock),
            clock = clock,
            zoneId = LogG4.zone,
        )
        val reopenedState = runBlocking {
            reopened.uiState.first { !it.isLoading && it.running.isNotEmpty() }
        }
        assertEquals(secondId, reopenedState.running.single().logId.value)
        assertNull("重启后仍为运行中", runBlocking { logRepository.get(io.github.mazilin.nautile.domain.model.TimeLogId(secondId)) }?.endAt)
    }

    @Test
    fun AC_T_22_parallel_then_hide_nav_and_leave_page_keep_running() {
        seedHistory()
        composeRule.setContent { Host() }
        awaitTag("log_category_quick_${study.value}")

        composeRule.onNodeWithTag("log_category_quick_${study.value}").performClick()
        awaitTag("timer_bar_scope")
        startViaPicker(work)
        composeRule.waitUntil(5_000) { runningIds().size == 2 }
        assertEquals(2, runningIds().size)
        awaitTag("running_timer_${study.value}")
        awaitTag("running_timer_${work.value}")

        // 每分类最多一段：两个分类各再点一次。
        composeRule.onNodeWithTag("log_category_quick_${study.value}").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("该分类已在计时").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("每分类最多一段", 2, runningIds().size)

        // 分别结束：只结束被点的分类。
        composeRule.onNodeWithTag("running_timer_${study.value}").performClick()
        composeRule.waitUntil(5_000) {
            runningIds().size == 1 &&
                runBlocking { logRepository.observeRunning().first().single().categoryId } == work
        }

        // 导航隐藏：计时条一起隐藏，计时不结束。
        composeRule.onNodeWithTag("test_toggle_nav").performClick()
        awaitTag("timer_bar_scope", present = false)
        assertEquals("导航隐藏不得结束计时", 1, runningIds().size)

        composeRule.onNodeWithTag("test_toggle_nav").performClick()
        awaitTag("timer_bar_scope")
        assertEquals("恢复后仍是同一段", 1, runningIds().size)

        // 离开页面（切到待办）不结束计时，返回后计时条恢复。
        composeRule.onNodeWithTag("tab_TODO").performClick()
        awaitTag("timer_bar_scope", present = false)
        assertEquals("离开页面不得结束计时", 1, runningIds().size)
        composeRule.onNodeWithTag("tab_LOG").performClick()
        awaitTag("timer_bar_scope")
        assertEquals("返回日志页仍是同一段", 1, runningIds().size)
    }

    @Test
    fun AC_T_23_timer_bar_layout_scroll_and_hide() {
        // 计时条布局/横滚只看渲染：预置 5 个分类并直接开始计时，避免重复走选择器干扰断言。
        val commands = TimeLogCommands(logRepository, categoryRepository, clock)
        val categoryIds = runBlocking {
            val names = listOf("学习", "很长的分类名称1", "很长的分类名称2", "很长的分类名称3", "很长的分类名称4")
            names.map { name ->
                val categoryId = if (name == "学习") study else LogG4.seedCategory(categoryRepository, name)
                commands.startTimer(categoryId)
                categoryId
            }
        }
        composeRule.setContent { Host() }
        awaitTag("timer_bar_scope")
        composeRule.waitUntil(5_000) { runningIds().size == 5 }
        composeRule.waitForIdle()

        // 与屏幕同宽（Scaffold bottomBar 内的整宽容器），位于导航上方。
        val rootWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val barWidth = composeRule.onNodeWithTag("timer_bar_scope").fetchSemanticsNode().boundsInRoot.width
        assertEquals("计时条应与导航同宽（整宽）", rootWidth, barWidth, 1f)
        val barBottom = composeRule.onNodeWithTag("timer_bar_scope").fetchSemanticsNode().boundsInRoot.bottom
        val navTop = composeRule.onNodeWithTag("tab_TODO").fetchSemanticsNode().boundsInRoot.top
        assertTrue("计时条应位于导航上方", barBottom <= navTop + 1f)

        // 不压缩文字与点击区：每段最小 120dp。
        val density = composeRule.density
        val minWidthPx = with(density) { 120.dp.toPx() }
        categoryIds.filter { composeRule.onAllNodesWithTag("running_timer_${it.value}").fetchSemanticsNodes().isNotEmpty() }
            .forEach { categoryId ->
                val width = composeRule.onNodeWithTag("running_timer_${categoryId.value}")
                    .fetchSemanticsNode().boundsInRoot.width
                assertTrue(
                    "计时段不得被压缩（category=${categoryId.value}, width=$width, min=$minWidthPx）",
                    width >= minWidthPx,
                )
            }

        // 分类多时内部横滚：初始只组合部分分段，横滑后末尾出现；一级页不切换。
        assertTrue(
            "分类多时应有未显示的分段（内部可滚动）",
            categoryIds.any {
                composeRule.onAllNodesWithTag("running_timer_${it.value}").fetchSemanticsNodes().isEmpty()
            },
        )
        repeat(4) {
            composeRule.onNodeWithTag("timer_bar_scope").performTouchInput { swipeLeft() }
        }
        // 计时段在计时条内部横滚：用滚动语义直接滚到末项，避免快速滑动的不确定性。
        composeRule.onNode(
            hasScrollAction() and hasAnyAncestor(hasTestTag("timer_bar_scope")),
        ).performScrollToIndex(4)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("running_timer_${categoryIds.last().value}")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("很长的分类名称4").onFirst().assertIsDisplayed()
        assertEquals("内部横滑不得切换一级页", AppTab.LOG, navigation.activeTab.value)

        // 导航隐藏：计时条一起隐藏；隐藏后计时继续。
        composeRule.onNodeWithTag("test_toggle_nav").performClick()
        awaitTag("timer_bar_scope", present = false)
        composeRule.onAllNodesWithTag("tab_TODO").assertCountEquals(0)
        assertEquals("隐藏后计时继续", 5, runningIds().size)
    }
}
