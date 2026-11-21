package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.interaction.BackDispatcher
import io.github.mazilin.nautile.interaction.GestureArbiter
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
 * S1-T07 手势与 GTD 纯逻辑 AC 验证（T-054）。
 *
 * 断言取自 AC-G-04/05、AC-N-16 与交互 05 §2.5；只调用公开的 GestureArbiter 与返回栈。
 * touch slop 与条目优先涉及 Compose/平台手势，放在 androidTest 编译，未在 JVM 伪造。
 */
class AcGestureT07Test {

    private val density = 1f

    @Test
    fun AC_G_04_row_swipe_commits_only_at_or_above_threshold() {
        val threshold = GestureArbiter.ROW_SWIPE_COMMIT_DP

        assertFalse(GestureArbiter.isCommitSwipe((threshold - 1f) * density, density))
        assertTrue(GestureArbiter.isCommitSwipe(threshold * density, density))
        assertTrue(GestureArbiter.isCommitSwipe(-threshold * density, density))
    }

    @Test
    fun AC_G_04_gtd_open_swipe_is_right_only_and_respects_threshold() {
        val threshold = GestureArbiter.GTD_OPEN_SWIPE_DP

        assertFalse(GestureArbiter.isGtdOpenSwipe((threshold - 1f) * density, density))
        assertTrue(GestureArbiter.isGtdOpenSwipe(threshold * density, density))
        assertFalse(GestureArbiter.isGtdOpenSwipe(-threshold * 2f * density, density))
    }

    @Test
    fun AC_G_05_boundary_pull_commits_at_or_above_threshold() {
        val threshold = GestureArbiter.BOUNDARY_PULL_THRESHOLD_DP

        assertFalse(GestureArbiter.isBoundaryPullCommit((threshold - 1f) * density, density))
        assertTrue(GestureArbiter.isBoundaryPullCommit(threshold * density, density))
        assertTrue(GestureArbiter.isBoundaryPullCommit(-threshold * density, density))
    }

    @Test
    fun AC_G_04_first_and_last_gtd_positions_do_not_wrap() {
        assertNull(GestureArbiter.adjacentPosition(GtdPosition.INBOX, -1))
        assertNull(GestureArbiter.adjacentPosition(GtdPosition.ENDED, 1))
        assertEquals(
            GtdPosition.NEXT_ACTION,
            GestureArbiter.adjacentPosition(GtdPosition.INBOX, 1),
        )
        assertEquals(
            GtdPosition.SOMEDAY,
            GestureArbiter.adjacentPosition(GtdPosition.ENDED, -1),
        )
    }

    @Test
    fun AC_G_04_gtd_positions_are_exactly_five_without_calendar_or_project() {
        assertEquals(
            listOf(
                GtdPosition.INBOX,
                GtdPosition.NEXT_ACTION,
                GtdPosition.WAITING,
                GtdPosition.SOMEDAY,
                GtdPosition.ENDED,
            ),
            GtdPosition.entries.toList(),
        )
        assertFalse(GtdPosition.entries.any { it.name == "CALENDAR" })
        assertFalse(GtdPosition.entries.any { it.name == "PROJECT" })
    }

    @Test
    fun AC_G_04_system_back_closes_gtd_selector_without_selection() {
        val keyboard = KeyboardCoordinator()
        val overlays = OverlayBackStack()
        val navigation = NavigationCoordinator()
        val dispatcher = BackDispatcher(keyboard, overlays, navigation)
        overlays.open(Overlay.GtdSelector)

        assertTrue(dispatcher.dispatch {})

        assertNull(overlays.current)
    }

    @Test
    fun AC_N_16_todo_and_selector_do_not_use_a_horizontal_pager() {
        val sources = listOf(
            File("src/main/java/io/github/mazilin/nautile/ui/screen/todo/TodoScreen.kt"),
            File("src/main/java/io/github/mazilin/nautile/ui/screen/todo/GtdSelector.kt"),
            File("app/src/main/java/io/github/mazilin/nautile/ui/screen/todo/TodoScreen.kt"),
            File("app/src/main/java/io/github/mazilin/nautile/ui/screen/todo/GtdSelector.kt"),
        ).filter { it.isFile }

        assertFalse("找不到 T-07 源码", sources.isEmpty())
        sources.forEach { file ->
            val text = file.readText()
            listOf("HorizontalPager", "PagerState", "rememberPagerState").forEach { symbol ->
                assertFalse("${file.name} 不应引入 $symbol", text.contains(symbol))
            }
        }
    }
}
