package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.interaction.BoundaryPullHintKind
import io.github.mazilin.nautile.interaction.BoundaryPullReleaseResult
import io.github.mazilin.nautile.interaction.BoundaryPullState
import io.github.mazilin.nautile.interaction.BoundaryPullStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R5-G2 独立验收（JVM 单测）：主页面边界拉动方向锁定状态机（FR-G-18、BR-61、AC-G-05、S-14）。
 *
 * 预期直接从《需求基线》v2.16（4.26）与《交互与动效规格》§3.3.1 派生：
 * - 五位置 × 上下拉：顶部下拉对应上一清单，底部上拉对应下一清单；达阈值提交、未达阈值回弹；
 * - 首尾尽头：首位下拉提示“已是第一个清单”、末位上拉提示“已是最后一个清单”，绝不循环、松手不提交；
 * - 短列表（同时 atTop && atBottom）起手锁定方向与目标：反向拖动只减位移不换目标；
 * - 反向越过（或归零）0 立即隐藏提示并锁定取消：后续继续反向或再次拉回原方向均不换目标、松手不提交；
 * - 连续多次拉拽切换：每次松手重置状态，不跳错、不跳过相邻清单；基准位置漂移时拒绝提交。
 */
class AcBoundaryPullR5G2Test {

    private val thresholdPx = 64f
    private val maxPullPx = 120f
    private val hintMinPx = 8f

    private fun labelOf(position: GtdPosition): String = when (position) {
        GtdPosition.INBOX -> "收件箱"
        GtdPosition.NEXT_ACTION -> "下一步行动"
        GtdPosition.WAITING -> "等待中"
        GtdPosition.SOMEDAY -> "将来／也许"
        GtdPosition.ENDED -> "已结束"
    }

    /**
     * 模拟 NestedScrollConnection 的完整单步派发：先走 onPreDrag，剩余未消费位移在边界交由 onDrag。
     */
    private fun stepNestedScroll(
        state: BoundaryPullState,
        deltaY: Float,
        atTop: Boolean,
        atBottom: Boolean,
        currentPosition: GtdPosition,
    ): BoundaryPullState {
        val (afterPre, consumedPre) = BoundaryPullStateMachine.onPreDrag(state, deltaY)
        val remaining = deltaY - consumedPre
        if (remaining == 0f) return afterPre
        val (afterPost, _) = BoundaryPullStateMachine.onDrag(
            state = afterPre,
            availableY = remaining,
            atTop = atTop,
            atBottom = atBottom,
            currentPosition = currentPosition,
            maxPullPx = maxPullPx,
        )
        return afterPost
    }

    /** AC-G-05：五位置 × 顶部下拉 / 底部上拉全矩阵，达阈值提交相邻清单、首尾不循环。 */
    @Test
    fun acG05_all_five_positions_pull_up_and_down_commit_or_block_at_edges() {
        val ordered = listOf(
            GtdPosition.INBOX,
            GtdPosition.NEXT_ACTION,
            GtdPosition.WAITING,
            GtdPosition.SOMEDAY,
            GtdPosition.ENDED,
        )
        assertEquals("GTD 应固定为五个位置", ordered, GtdPosition.entries)

        ordered.forEachIndexed { index, pos ->
            // 1) 顶部下拉（availableY > 0）→ 上一清单（或首边界阻断）。
            var downState = BoundaryPullState()
            repeat(8) {
                downState = stepNestedScroll(
                    state = downState,
                    deltaY = 25f,
                    atTop = true,
                    atBottom = true,
                    currentPosition = pos,
                )
            }
            val expectedPrev = ordered.getOrNull(index - 1)
            assertEquals("位置 $pos 下拉方向应锁定为 +1", 1, downState.direction)
            assertEquals("位置 $pos 下拉锁定目标错误", expectedPrev, downState.lockedTarget)

            val downHint = BoundaryPullStateMachine.hintText(downState, hintMinPx, ::labelOf)
            val downRelease = BoundaryPullStateMachine.onRelease(downState, pos, thresholdPx)
            if (expectedPrev != null) {
                assertEquals("松手切换到 ${labelOf(expectedPrev)}", downHint)
                assertEquals(BoundaryPullReleaseResult.Commit(expectedPrev), downRelease)
            } else {
                assertEquals("已是第一个清单", downHint)
                assertEquals(BoundaryPullReleaseResult.Rebound(shouldAnimate = true), downRelease)
            }

            // 2) 底部上拉（availableY < 0）→ 下一清单（或尾边界阻断）。
            var upState = BoundaryPullState()
            repeat(8) {
                upState = stepNestedScroll(
                    state = upState,
                    deltaY = -25f,
                    atTop = true,
                    atBottom = true,
                    currentPosition = pos,
                )
            }
            val expectedNext = ordered.getOrNull(index + 1)
            assertEquals("位置 $pos 上拉方向应锁定为 -1", -1, upState.direction)
            assertEquals("位置 $pos 上拉锁定目标错误", expectedNext, upState.lockedTarget)

            val upHint = BoundaryPullStateMachine.hintText(upState, hintMinPx, ::labelOf)
            val upRelease = BoundaryPullStateMachine.onRelease(upState, pos, thresholdPx)
            if (expectedNext != null) {
                assertEquals("松手切换到 ${labelOf(expectedNext)}", upHint)
                assertEquals(BoundaryPullReleaseResult.Commit(expectedNext), upRelease)
            } else {
                assertEquals("已是最后一个清单", upHint)
                assertEquals(BoundaryPullReleaseResult.Rebound(shouldAnimate = true), upRelease)
            }
        }
    }

    /** AC-G-05：中间清单拉动未达阈值（<64px）松手应回弹不切换，且刚过 64px 才提交。 */
    @Test
    fun acG05_below_threshold_rebounds_and_at_threshold_commits() {
        val pos = GtdPosition.WAITING

        // 原始拖动 126px * 0.5 = 63px < 64px：预览存在但松手回弹。
        val belowState = stepNestedScroll(
            state = BoundaryPullState(),
            deltaY = 126f,
            atTop = true,
            atBottom = false,
            currentPosition = pos,
        )
        assertEquals(63f, belowState.signedOffsetPx, 0.01f)
        assertEquals(
            "松手切换到 下一步行动",
            BoundaryPullStateMachine.hintText(belowState, hintMinPx, ::labelOf),
        )
        assertEquals(
            BoundaryPullReleaseResult.Rebound(shouldAnimate = true),
            BoundaryPullStateMachine.onRelease(belowState, pos, thresholdPx),
        )

        // 再同向补拉 2px -> 64px（恰好达阈值）：松手提交。
        val exactState = stepNestedScroll(
            state = belowState,
            deltaY = 2f,
            atTop = true,
            atBottom = false,
            currentPosition = pos,
        )
        assertEquals(64f, exactState.signedOffsetPx, 0.01f)
        assertEquals(
            BoundaryPullReleaseResult.Commit(GtdPosition.NEXT_ACTION),
            BoundaryPullStateMachine.onRelease(exactState, pos, thresholdPx),
        )
    }

    /** AC-G-05 / BR-61：首尾尽头拉动提示准确、阻力更紧且即使拉满最大位移也绝不循环提交。 */
    @Test
    fun acG05_first_and_last_edges_show_boundary_hints_and_never_wrap() {
        // 首位 INBOX 大幅下拉
        var firstState = BoundaryPullState()
        repeat(40) {
            firstState = stepNestedScroll(
                state = firstState,
                deltaY = 50f,
                atTop = true,
                atBottom = true,
                currentPosition = GtdPosition.INBOX,
            )
        }
        assertEquals(maxPullPx, firstState.signedOffsetPx, 0.01f)
        assertEquals(
            BoundaryPullHintKind.FIRST_BOUNDARY,
            BoundaryPullStateMachine.hint(firstState, hintMinPx)?.kind,
        )
        assertEquals(
            "已是第一个清单",
            BoundaryPullStateMachine.hintText(firstState, hintMinPx, ::labelOf),
        )
        assertEquals(
            BoundaryPullReleaseResult.Rebound(shouldAnimate = true),
            BoundaryPullStateMachine.onRelease(firstState, GtdPosition.INBOX, thresholdPx),
        )

        // 末位 ENDED 大幅上拉
        var lastState = BoundaryPullState()
        repeat(40) {
            lastState = stepNestedScroll(
                state = lastState,
                deltaY = -50f,
                atTop = true,
                atBottom = true,
                currentPosition = GtdPosition.ENDED,
            )
        }
        assertEquals(maxPullPx, lastState.signedOffsetPx, 0.01f)
        assertEquals(
            BoundaryPullHintKind.LAST_BOUNDARY,
            BoundaryPullStateMachine.hint(lastState, hintMinPx)?.kind,
        )
        assertEquals(
            "已是最后一个清单",
            BoundaryPullStateMachine.hintText(lastState, hintMinPx, ::labelOf),
        )
        assertEquals(
            BoundaryPullReleaseResult.Rebound(shouldAnimate = true),
            BoundaryPullStateMachine.onRelease(lastState, GtdPosition.ENDED, thresholdPx),
        )
    }

    /**
     * AC-G-05 / BR-61（R5-2 核心）：短列表同时 atTop && atBottom，起手下拉锁定上一清单后反向回推，
     * 只要位移仍 > 0，只减少位移、绝不更换锁定目标；若仍 >= 阈值松手提交原目标，若 < 阈值松手回弹。
     */
    @Test
    fun acG05_short_list_reverse_drag_within_positive_offset_reduces_offset_without_changing_target() {
        val pos = GtdPosition.WAITING
        // 起手下拉：raw +200px -> signedOffset = 100px，锁定 NEXT_ACTION
        var state = stepNestedScroll(
            state = BoundaryPullState(),
            deltaY = 200f,
            atTop = true,
            atBottom = true,
            currentPosition = pos,
        )
        assertEquals(1, state.direction)
        assertEquals(GtdPosition.NEXT_ACTION, state.lockedTarget)
        assertEquals(100f, state.signedOffsetPx, 0.01f)

        // 反向小幅上推 raw -40px -> signedOffset = 80px（仍 >= 64px）
        state = stepNestedScroll(
            state = state,
            deltaY = -40f,
            atTop = true,
            atBottom = true,
            currentPosition = pos,
        )
        assertFalse("未越过 0 不应标记取消", state.cancelled)
        assertEquals("方向锁定不可变更", 1, state.direction)
        assertEquals("目标不可换成 SOMEDAY", GtdPosition.NEXT_ACTION, state.lockedTarget)
        assertEquals(80f, state.signedOffsetPx, 0.01f)
        assertEquals(
            "松手切换到 下一步行动",
            BoundaryPullStateMachine.hintText(state, hintMinPx, ::labelOf),
        )
        assertEquals(
            BoundaryPullReleaseResult.Commit(GtdPosition.NEXT_ACTION),
            BoundaryPullStateMachine.onRelease(state, pos, thresholdPx),
        )

        // 继续反向推 raw -80px -> signedOffset = 40px（0 < 40 < 64）：仍不换目标，但松手回弹
        state = stepNestedScroll(
            state = state,
            deltaY = -80f,
            atTop = true,
            atBottom = true,
            currentPosition = pos,
        )
        assertFalse(state.cancelled)
        assertEquals(GtdPosition.NEXT_ACTION, state.lockedTarget)
        assertEquals(40f, state.signedOffsetPx, 0.01f)
        assertEquals(
            "松手切换到 下一步行动",
            BoundaryPullStateMachine.hintText(state, hintMinPx, ::labelOf),
        )
        assertEquals(
            BoundaryPullReleaseResult.Rebound(shouldAnimate = true),
            BoundaryPullStateMachine.onRelease(state, pos, thresholdPx),
        )
    }

    /**
     * AC-G-05 / BR-61（R5-2 核心）：短列表起手下拉后反向越过 0：
     * 立即隐藏提示并锁定 cancelled = true；继续向反方向猛拉不换目标；
     * 即使再反向拉回原方向也不重新激活、不换目标、提示仍隐藏，松手绝不提交。
     */
    @Test
    fun acG05_short_list_reverse_past_zero_hides_hint_and_locks_cancel_both_directions() {
        val pos = GtdPosition.WAITING
        // 起手下拉：raw +160px -> signedOffset = 80px，锁定 NEXT_ACTION
        var state = stepNestedScroll(
            state = BoundaryPullState(),
            deltaY = 160f,
            atTop = true,
            atBottom = true,
            currentPosition = pos,
        )
        assertEquals(GtdPosition.NEXT_ACTION, state.lockedTarget)
        assertEquals(
            "松手切换到 下一步行动",
            BoundaryPullStateMachine.hintText(state, hintMinPx, ::labelOf),
        )

        // 快速反向大幅上推 raw -400px（远超回零所需 -160px，模拟反向越过 0 继续上拉）
        state = stepNestedScroll(
            state = state,
            deltaY = -400f,
            atTop = true,
            atBottom = true,
            currentPosition = pos,
        )
        assertTrue("反向越过 0 必须锁定取消", state.cancelled)
        assertEquals("取消后位移必须归零", 0f, state.offsetPx, 0.001f)
        assertEquals("取消后有效位移必须为 0", 0f, state.signedOffsetPx, 0.001f)
        assertEquals("取消后方向不得翻转", 1, state.direction)
        assertEquals("取消后目标不得切换为 SOMEDAY", GtdPosition.NEXT_ACTION, state.lockedTarget)
        assertNull(
            "位移归零取消后必须立即隐藏提示",
            BoundaryPullStateMachine.hintText(state, hintMinPx, ::labelOf),
        )

        // 继续反向猛拉（试图触发底部上拉到 SOMEDAY）：仍保持取消锁死
        state = stepNestedScroll(
            state = state,
            deltaY = -300f,
            atTop = true,
            atBottom = true,
            currentPosition = pos,
        )
        assertTrue(state.cancelled)
        assertEquals(0f, state.offsetPx, 0.001f)
        assertEquals(GtdPosition.NEXT_ACTION, state.lockedTarget)
        assertNull(BoundaryPullStateMachine.hintText(state, hintMinPx, ::labelOf))

        // 同一次手势不松手，再次折返向下猛拉：依然保持取消锁死，不可复活提交
        state = stepNestedScroll(
            state = state,
            deltaY = 300f,
            atTop = true,
            atBottom = true,
            currentPosition = pos,
        )
        assertTrue("同一手势内再次折返也不得解除取消", state.cancelled)
        assertEquals(0f, state.offsetPx, 0.001f)
        assertNull(BoundaryPullStateMachine.hintText(state, hintMinPx, ::labelOf))

        // 松手结算：绝不提交切换，且因位移已为 0 无需回弹动画
        assertEquals(
            BoundaryPullReleaseResult.Rebound(shouldAnimate = false),
            BoundaryPullStateMachine.onRelease(state, pos, thresholdPx),
        )
    }

    /** AC-G-05 / BR-61：底部上拉起手后反向越过 0（镜像方向）同样立即隐藏提示并锁死取消。 */
    @Test
    fun acG05_pull_up_then_reverse_past_zero_hides_hint_and_locks_cancel() {
        val pos = GtdPosition.NEXT_ACTION
        // 起手上拉锁定 WAITING
        var state = stepNestedScroll(
            state = BoundaryPullState(),
            deltaY = -180f,
            atTop = true,
            atBottom = true,
            currentPosition = pos,
        )
        assertEquals(-1, state.direction)
        assertEquals(GtdPosition.WAITING, state.lockedTarget)
        assertEquals(
            "松手切换到 等待中",
            BoundaryPullStateMachine.hintText(state, hintMinPx, ::labelOf),
        )

        // 直接通过 onDrag 单步驱动反向越过 0（同时覆盖不经 onPreDrag 的路径）
        val (cancelledState, consumed) = BoundaryPullStateMachine.onDrag(
            state = state,
            availableY = 260f,
            atTop = true,
            atBottom = true,
            currentPosition = pos,
            maxPullPx = maxPullPx,
        )
        assertTrue(consumed)
        assertTrue(cancelledState.cancelled)
        assertEquals(0f, cancelledState.offsetPx, 0.001f)
        assertEquals(GtdPosition.WAITING, cancelledState.lockedTarget)
        assertNull(BoundaryPullStateMachine.hintText(cancelledState, hintMinPx, ::labelOf))

        // 继续向下拉不换成 INBOX
        val (stillCancelled, consumedAgain) = BoundaryPullStateMachine.onDrag(
            state = cancelledState,
            availableY = 200f,
            atTop = true,
            atBottom = true,
            currentPosition = pos,
            maxPullPx = maxPullPx,
        )
        assertTrue(consumedAgain)
        assertTrue(stillCancelled.cancelled)
        assertEquals(0f, stillCancelled.offsetPx, 0.001f)
        assertEquals(GtdPosition.WAITING, stillCancelled.lockedTarget)
        assertEquals(
            BoundaryPullReleaseResult.Rebound(shouldAnimate = false),
            BoundaryPullStateMachine.onRelease(stillCancelled, pos, thresholdPx),
        )
    }

    /** AC-G-05：反向恰好归零（nextSigned == 0）也属于位移 <= 0，立即隐藏提示并锁定取消。 */
    @Test
    fun acG05_reverse_exactly_to_zero_cancels_and_hides_hint() {
        val pos = GtdPosition.WAITING
        var state = stepNestedScroll(
            state = BoundaryPullState(),
            deltaY = 140f,
            atTop = true,
            atBottom = true,
            currentPosition = pos,
        )
        assertEquals(70f, state.signedOffsetPx, 0.01f)

        // 恰好反向推回 0
        state = stepNestedScroll(
            state = state,
            deltaY = -140f,
            atTop = true,
            atBottom = true,
            currentPosition = pos,
        )
        assertTrue("位移归零即 cancelled", state.cancelled)
        assertEquals(0f, state.signedOffsetPx, 0.001f)
        assertNull(BoundaryPullStateMachine.hintText(state, hintMinPx, ::labelOf))
        assertEquals(
            BoundaryPullReleaseResult.Rebound(shouldAnimate = false),
            BoundaryPullStateMachine.onRelease(state, pos, thresholdPx),
        )
    }

    /** AC-G-05：非边界（长列表既不在顶也不在底）拖动不进入边界拉动状态。 */
    @Test
    fun acG05_mid_list_scroll_does_not_activate_boundary_pull() {
        val (downState, downConsumed) = BoundaryPullStateMachine.onDrag(
            state = BoundaryPullState(),
            availableY = 200f,
            atTop = false,
            atBottom = false,
            currentPosition = GtdPosition.WAITING,
            maxPullPx = maxPullPx,
        )
        assertFalse(downConsumed)
        assertFalse(downState.active)
        assertNull(BoundaryPullStateMachine.hintText(downState, hintMinPx, ::labelOf))

        val (upState, upConsumed) = BoundaryPullStateMachine.onDrag(
            state = BoundaryPullState(),
            availableY = -200f,
            atTop = false,
            atBottom = false,
            currentPosition = GtdPosition.WAITING,
            maxPullPx = maxPullPx,
        )
        assertFalse(upConsumed)
        assertFalse(upState.active)
        assertNull(BoundaryPullStateMachine.hintText(upState, hintMinPx, ::labelOf))
    }

    /** AC-G-05：连续多次拉拽切换（正向走通五个位置再反向退回），期间夹杂一次取消拉动。 */
    @Test
    fun acG05_consecutive_multi_pulls_and_interleaved_cancel_stay_accurate() {
        var current = GtdPosition.INBOX
        val visited = mutableListOf(current)

        fun pullAndRelease(deltas: List<Float>) {
            var state = BoundaryPullState()
            for (d in deltas) {
                state = stepNestedScroll(
                    state = state,
                    deltaY = d,
                    atTop = true,
                    atBottom = true,
                    currentPosition = current,
                )
            }
            val result = BoundaryPullStateMachine.onRelease(state, current, thresholdPx)
            if (result is BoundaryPullReleaseResult.Commit) {
                current = result.target
                visited += current
            }
        }

        // 4 次上拉走完 INBOX -> NEXT_ACTION -> WAITING -> SOMEDAY -> ENDED
        repeat(4) { pullAndRelease(listOf(-80f, -80f)) }
        // 第 5 次在末位继续上拉：阻断不切换
        pullAndRelease(listOf(-200f, -200f))
        // 在末位下拉后反向越过 0 取消：留在 ENDED
        pullAndRelease(listOf(180f, -300f))
        // 2 次正常下拉回到 WAITING
        repeat(2) { pullAndRelease(listOf(90f, 90f)) }

        assertEquals(
            listOf(
                GtdPosition.INBOX,
                GtdPosition.NEXT_ACTION,
                GtdPosition.WAITING,
                GtdPosition.SOMEDAY,
                GtdPosition.ENDED,
                GtdPosition.SOMEDAY,
                GtdPosition.WAITING,
            ),
            visited,
        )
    }

    /** AC-G-05 / CMP-14：手势期间外部切走了清单（basePosition != currentPosition）时松手拒绝提交。 */
    @Test
    fun acG05_stale_base_position_rejects_commit_on_release() {
        val state = stepNestedScroll(
            state = BoundaryPullState(),
            deltaY = -200f,
            atTop = true,
            atBottom = true,
            currentPosition = GtdPosition.INBOX,
        )
        assertEquals(GtdPosition.INBOX, state.basePosition)
        assertEquals(GtdPosition.NEXT_ACTION, state.lockedTarget)

        // 若此时外部已经把当前位置切到 WAITING，本次手势松手必须回弹而非错切回 NEXT_ACTION
        val result = BoundaryPullStateMachine.onRelease(
            state = state,
            currentPosition = GtdPosition.WAITING,
            thresholdPx = thresholdPx,
        )
        assertEquals(BoundaryPullReleaseResult.Rebound(shouldAnimate = true), result)
    }
}
