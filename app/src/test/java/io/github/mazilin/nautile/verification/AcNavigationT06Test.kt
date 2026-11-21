package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.interaction.AppTab
import io.github.mazilin.nautile.interaction.BackDispatcher
import io.github.mazilin.nautile.interaction.FullScreenPage
import io.github.mazilin.nautile.interaction.KeyboardCoordinator
import io.github.mazilin.nautile.interaction.NavigationCoordinator
import io.github.mazilin.nautile.interaction.Overlay
import io.github.mazilin.nautile.interaction.OverlayBackStack
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1-T06 导航与返回的 AC 验证（T-053）。
 *
 * 预期取自基线 v2.10 的 AC-N-14/16/21 与 FR-N-18—N-23、07 §6.3；
 * 只通过公开协调器断言，不复用开发自测的 AppRootTest/BackDispatcherTest 断言。
 */
class AcNavigationT06Test {

    @Test
    fun AC_N_14_bottom_entries_are_fixed_and_start_with_todo() {
        assertEquals(
            listOf(AppTab.TODO, AppTab.SCHEDULE, AppTab.LOG, AppTab.AI),
            AppTab.entries.toList(),
        )
        assertTrue("设置不应占底部导航位置", AppTab.entries.none { it.name == "SETTINGS" })

        val navigation = NavigationCoordinator()
        assertEquals(AppTab.TODO, navigation.activeTab.value)
        AppTab.entries.forEach { tab ->
            navigation.selectTab(tab)
            assertEquals(tab, navigation.activeTab.value)
        }
    }

    @Test
    fun AC_N_14_tab_switch_is_rejected_while_full_screen_settings_is_open() {
        val navigation = NavigationCoordinator()
        navigation.selectTab(AppTab.LOG)
        navigation.navigateToSettings()

        navigation.selectTab(AppTab.AI)

        assertEquals("全屏子页打开时不得切换一级页面", AppTab.LOG, navigation.activeTab.value)
        assertEquals(FullScreenPage.Settings, navigation.currentFullScreenPage)
    }

    @Test
    fun AC_N_14_settings_entry_from_each_tab_opens_same_root() {
        AppTab.entries.forEach { tab ->
            val navigation = NavigationCoordinator()
            navigation.selectTab(tab)

            navigation.navigateToSettings()

            assertEquals(FullScreenPage.Settings, navigation.currentFullScreenPage)
            assertEquals(1, navigation.fullScreenStack.value.size)
            assertTrue(navigation.back())
            assertNull(navigation.currentFullScreenPage)
            assertEquals("返回后应回到进入前的一级页面", tab, navigation.activeTab.value)
        }
    }

    @Test
    fun AC_N_14_repeated_settings_entry_does_not_stack_duplicate() {
        val navigation = NavigationCoordinator()

        navigation.navigateToSettings()
        navigation.navigateToSettings()

        assertEquals(1, navigation.fullScreenStack.value.size)
        assertTrue(navigation.back())
        assertTrue(navigation.fullScreenStack.value.isEmpty())
    }

    @Test
    fun AC_N_21_settings_is_full_screen_and_back_returns_to_entering_tab() {
        val navigation = NavigationCoordinator()
        navigation.selectTab(AppTab.SCHEDULE)

        navigation.navigateToSettings()
        assertEquals(FullScreenPage.Settings, navigation.currentFullScreenPage)

        assertTrue(navigation.back())
        assertNull(navigation.currentFullScreenPage)
        assertEquals(AppTab.SCHEDULE, navigation.activeTab.value)
    }

    @Test
    fun AC_N_14_back_priority_ime_then_overlay_then_fullscreen_then_system() {
        val keyboard = KeyboardCoordinator()
        val overlays = OverlayBackStack()
        val navigation = NavigationCoordinator()
        val dispatcher = BackDispatcher(keyboard, overlays, navigation)

        var keyboardHidden = false
        keyboard.updateImeVisible(true)
        overlays.open(Overlay.NewTaskSheet)
        assertTrue(dispatcher.dispatch { keyboardHidden = true })
        assertTrue(keyboardHidden)
        assertFalse(keyboard.imeVisible.value)
        assertEquals(Overlay.NewTaskSheet, overlays.current?.overlay)

        assertTrue(dispatcher.dispatch {})
        assertNull(overlays.current)

        navigation.navigateToSettings()
        assertTrue(dispatcher.dispatch {})
        assertNull(navigation.currentFullScreenPage)

        assertFalse("一级页面返回应交系统处理", dispatcher.dispatch {})
    }

    @Test
    fun AC_N_14_blocked_overlay_consumes_back_without_closing() {
        val keyboard = KeyboardCoordinator()
        val overlays = OverlayBackStack()
        val navigation = NavigationCoordinator()
        val dispatcher = BackDispatcher(keyboard, overlays, navigation)
        val detail = Overlay.TaskDetail(taskId = 1L)
        overlays.open(detail, canDismiss = { false })

        assertTrue(dispatcher.dispatch {})

        assertEquals(detail, overlays.current?.overlay)
    }

    @Test
    fun AC_N_16_app_root_does_not_use_a_horizontal_pager() {
        val source = listOf(
            File("src/main/java/io/github/mazilin/nautile/ui/AppRoot.kt"),
            File("app/src/main/java/io/github/mazilin/nautile/ui/AppRoot.kt"),
        ).firstOrNull { it.isFile }
            ?: error("找不到 AppRoot.kt；当前工作目录=${File(".").absolutePath}")

        val text = source.readText()
        listOf("HorizontalPager", "PagerState", "rememberPagerState").forEach { symbol ->
            assertFalse("一级页面宿主不应引入 $symbol", text.contains(symbol))
        }
    }
}
