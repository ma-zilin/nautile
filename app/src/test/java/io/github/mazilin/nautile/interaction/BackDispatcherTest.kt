package io.github.mazilin.nautile.interaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1-T06 返回优先级（07 §6.3）：IME → 覆盖层 → 全屏子页 → 系统。
 * 纯 JVM，可在无设备环境执行。
 */
class BackDispatcherTest {

    private val keyboard = KeyboardCoordinator()
    private val overlays = OverlayBackStack()
    private val navigation = NavigationCoordinator()
    private val dispatcher = BackDispatcher(keyboard, overlays, navigation)

    @Test
    fun acN14_ime_consumes_back_before_overlay() {
        var hidden = false
        keyboard.updateImeVisible(true)
        overlays.open(Overlay.NewTaskSheet)

        assertTrue(dispatcher.dispatch { hidden = true })

        assertTrue(hidden)
        assertFalse(keyboard.imeVisible.value)
        assertEquals(Overlay.NewTaskSheet, overlays.current?.overlay)
    }

    @Test
    fun frD02_overlay_with_invalid_fields_blocks_back() {
        overlays.open(Overlay.TaskDetail(taskId = 1L), canDismiss = { false })

        assertTrue(dispatcher.dispatch {})

        assertEquals(Overlay.TaskDetail(taskId = 1L), overlays.current?.overlay)
    }

    @Test
    fun frD02_overlay_closes_when_dismissible_then_fullscreen_pops() {
        overlays.open(Overlay.GtdSelector)
        navigation.navigateToSettings()

        assertTrue(dispatcher.dispatch {})
        assertNull(overlays.current)

        assertTrue(dispatcher.dispatch {})
        assertNull(navigation.currentFullScreenPage)
    }

    @Test
    fun acN14_system_back_is_not_consumed_when_all_empty() {
        assertFalse(dispatcher.dispatch {})
    }

    @Test
    fun acN14_only_one_main_overlay_is_allowed() {
        assertTrue(overlays.open(Overlay.GtdSelector))

        assertFalse(overlays.open(Overlay.TaskDetail(taskId = 2L)))

        assertEquals(Overlay.GtdSelector, overlays.current?.overlay)
    }
}
