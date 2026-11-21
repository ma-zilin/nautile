package io.github.mazilin.nautile.interaction

import io.github.mazilin.nautile.domain.model.GtdPosition
import kotlin.math.abs

/**
 * 方向/阈值/相邻清单判定（交互 02 §2.2、BR-61）。
 *
 * 只提供纯计算；Compose 手势层负责消费事件与动画。
 */
object GestureArbiter {

    /** 条目左右滑提交阈值。 */
    const val ROW_SWIPE_COMMIT_DP = 72f

    /** 空白区右滑打开 GTD 的提交阈值。 */
    const val GTD_OPEN_SWIPE_DP = 48f

    /** 半屏清单上下拉切换的提交阈值。 */
    const val BOUNDARY_PULL_THRESHOLD_DP = 64f

    fun isCommitSwipe(dragPx: Float, density: Float): Boolean =
        abs(dragPx) >= ROW_SWIPE_COMMIT_DP * density

    fun isGtdOpenSwipe(dragPx: Float, density: Float): Boolean =
        dragPx >= GTD_OPEN_SWIPE_DP * density

    fun isBoundaryPullCommit(pullPx: Float, density: Float): Boolean =
        abs(pullPx) >= BOUNDARY_PULL_THRESHOLD_DP * density

    /** 相邻 GTD 清单；首尾不循环，返回 null 表示已到尽头。 */
    fun adjacentPosition(current: GtdPosition, direction: Int): GtdPosition? {
        val positions = GtdPosition.entries
        return positions.getOrNull(positions.indexOf(current) + direction)
    }
}
