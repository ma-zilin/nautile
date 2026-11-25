package io.github.mazilin.nautile.verification

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.unit.Density
import io.github.mazilin.nautile.ui.designsystem.SheetDragMetrics
import io.github.mazilin.nautile.ui.designsystem.SheetMotion
import io.github.mazilin.nautile.ui.designsystem.commitThresholdPx
import io.github.mazilin.nautile.ui.designsystem.dragScrimFactor
import io.github.mazilin.nautile.ui.designsystem.shouldCommit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G4 闸门：面板下拉阈值与速度判定的纯函数验证（MOT-08、SPC-08）。
 *
 * 预期依据（用户 2026-11-25 确认）：提交阈值 = max(25% 面板高度, 96dp)；
 * 速度 > 800dp/s 也提交（严格大于）；遮罩随位移 1:1 联动。
 * 只调用生产代码的 internal 实现，不复制数学公式到测试中充当断言依据。
 */
class SheetDragMetricsG4Test {

    private val density = Density(density = 2f)

    private fun motion(heightPx: Float): SheetMotion =
        SheetMotion(Animatable(0f), Animatable(0f)).apply { sheetHeightPx = heightPx }

    @Test
    fun G4_MOT08_threshold_is_max_of_96dp_and_25_percent_height() {
        // 面板 200dp 高：25% = 50dp < 96dp，阈值取下限 96dp。
        assertEquals(192f, motion(400f).commitThresholdPx(density), 0.01f)
        // 面板 600dp 高：25% = 150dp > 96dp，阈值取 25%。
        assertEquals(300f, motion(1_200f).commitThresholdPx(density), 0.01f)
        assertEquals(96f, SheetDragMetrics.MIN_THRESHOLD_DP.value, 0.01f)
        assertEquals(0.25f, SheetDragMetrics.HEIGHT_FRACTION, 0.0001f)
        assertEquals(800f, SheetDragMetrics.VELOCITY_DP_PER_SEC, 0.0001f)
    }

    @Test
    fun G4_MOT08_commit_needs_displacement_at_threshold_or_velocity_over_800dp_s() {
        val sheet = motion(400f) // 阈值 192px = 96dp

        assertFalse("低于阈值且无速度应回弹", sheet.shouldCommit(density, 191.9f, 0f))
        assertTrue("位移恰为阈值应提交", sheet.shouldCommit(density, 192f, 0f))
        assertFalse("速度恰为 800dp/s 不算超过", sheet.shouldCommit(density, 10f, 1_600f))
        assertTrue("速度超过 800dp/s 应提交", sheet.shouldCommit(density, 10f, 1_600.1f))
        assertFalse(sheet.shouldCommit(density, 10f, -1_600.1f))
    }

    @Test
    fun G4_MOT08_scrim_follows_drag_progress_one_to_one() {
        val sheet = motion(400f)

        assertEquals(1f, sheet.dragScrimFactor(), 0.0001f)
        sheet.dragOffsetPx = 100f
        assertEquals(0.75f, sheet.dragScrimFactor(), 0.0001f)
        sheet.dragOffsetPx = 400f
        assertEquals(0f, sheet.dragScrimFactor(), 0.0001f)
        sheet.dragOffsetPx = 600f
        assertEquals("拖出面板高度后不得出现负透明度", 0f, sheet.dragScrimFactor(), 0.0001f)
        assertEquals("高度未知时保持全遮罩", 1f, motion(0f).dragScrimFactor(), 0.0001f)
    }
}
