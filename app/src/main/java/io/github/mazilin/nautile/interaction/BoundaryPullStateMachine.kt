package io.github.mazilin.nautile.interaction

import io.github.mazilin.nautile.domain.model.GtdPosition
import kotlin.math.abs

/**
 * 主待办页单次边界拉动手势的不可变状态（FR-G-18、BR-61、R5-2、CMP-14）。
 *
 * 起手越界即按边界侧锁定 [basePosition]、[direction] 与 [lockedTarget]；
 * 同一手势期间绝不更换锁定目标，反向越过 0 后置 [cancelled] = true，必须松手重新起手。
 */
data class BoundaryPullState(
    /** 进入预览瞬间锁定的基准清单快照。 */
    val basePosition: GtdPosition? = null,
    /** +1 = 顶部下拉（目标上一清单），-1 = 底部上拉（目标下一清单），0 = 未激活。 */
    val direction: Int = 0,
    /** 起手锁定的相邻目标清单；首项下拉或尾项上拉为 null（边界阻断，不循环）。 */
    val lockedTarget: GtdPosition? = null,
    /** 有符号视觉位移（px），与 [direction] 同号或为 0。 */
    val offsetPx: Float = 0f,
    /** 反向拖动使锁定方向位移 <= 0 后置 true，本次手势剩余过程锁定取消。 */
    val cancelled: Boolean = false,
) {
    /** 当前是否已进入一次边界拉动手势周期（含已取消但尚未松手的残余阶段）。 */
    val active: Boolean get() = direction != 0

    /** 朝向锁定方向的有效位移（非负；未激活或已取消时为 0f）。 */
    val signedOffsetPx: Float get() = if (direction == 0 || cancelled) 0f else offsetPx * direction
}

/** 边界拉动提示语义类型。 */
enum class BoundaryPullHintKind {
    /** 中间清单：提示“松手切换到 X”。 */
    SWITCH_TARGET,

    /** 首位清单继续下拉：提示“已是第一个清单”。 */
    FIRST_BOUNDARY,

    /** 末位清单继续上拉：提示“已是最后一个清单”。 */
    LAST_BOUNDARY,
}

/** 边界拉动提示的结构化描述。 */
data class BoundaryPullHint(
    val kind: BoundaryPullHintKind,
    val target: GtdPosition? = null,
)

/** 松手结算结果。 */
sealed interface BoundaryPullReleaseResult {
    /** 仅当未取消、锁定目标有效、基准位置未变且位移达阈值时提交切换。 */
    data class Commit(val target: GtdPosition) : BoundaryPullReleaseResult

    /** 其余情形一律取消切换并按需播放回弹动画。 */
    data class Rebound(val shouldAnimate: Boolean) : BoundaryPullReleaseResult
}

/**
 * 主待办页上下拉边界切换的纯 Kotlin 状态机（FR-G-18、BR-61、R5-2、CMP-14）。
 *
 * 不依赖 Android / Compose，供 UI 嵌套滚动手势桥接与 JVM 单元测试直接复用。
 */
object BoundaryPullStateMachine {

    /** 存在可切换相邻清单时的拉动阻力。 */
    const val NORMAL_RESISTANCE = 0.5f

    /** 首尾尽头（无可切换相邻清单）时的拉动阻力。 */
    const val EDGE_RESISTANCE = 0.15f

    private fun resistanceFor(lockedTarget: GtdPosition?): Float =
        if (lockedTarget == null) EDGE_RESISTANCE else NORMAL_RESISTANCE

    /**
     * 预滚动阶段处理（对应 `NestedScrollConnection.onPreScroll`）：
     * 当已处于边界预览且用户反向回推时，先抵消锁定方向位移，防止长列表在预览未收拢前就开始滚动，
     * 并在短列表/长列表反向越过 0 时立即归零并置 `cancelled = true`。
     *
     * @return 更新后的状态，以及本次在预滚动阶段消费掉的纵向原始位移（px）。
     */
    fun onPreDrag(
        state: BoundaryPullState,
        availableY: Float,
    ): Pair<BoundaryPullState, Float> {
        if (!state.active || state.cancelled || state.signedOffsetPx <= 0f || availableY == 0f) {
            return state to 0f
        }
        // 同向拖动不在此拦截，交由列表滚动确认仍在边界后再在 onDrag 累加。
        if (availableY * state.direction > 0f) {
            return state to 0f
        }
        val resistance = resistanceFor(state.lockedTarget)
        val nextOffset = state.offsetPx + availableY * resistance
        val nextSigned = nextOffset * state.direction
        if (nextSigned <= 0f) {
            // 反向越过（或到达）0：立即归零并标记取消，仅消费刚好把预览推回 0 的那部分原始位移。
            val consumedToZero = -state.offsetPx / resistance
            return state.copy(offsetPx = 0f, cancelled = true) to consumedToZero
        }
        return state.copy(offsetPx = nextOffset) to availableY
    }

    /**
     * 越界拖动阶段处理（对应 `NestedScrollConnection.onPostScroll`，亦可直接作为单步驱动入口）。
     *
     * - 未激活时：按边界侧锁定 `basePosition`、`direction`（下拉 +1 / 上拉 -1）与 `lockedTarget`；
     * - 已激活且未取消时：同向按阻力累加并上限裁剪，反向仅减少位移，<= 0 时归零并标记 `cancelled = true`；
     * - 已取消时：同一手势内绝不更换目标或重新激活，必须松手后重新起手。
     *
     * @return 更新后的状态，以及本次位移是否被边界拉动逻辑消费。
     */
    fun onDrag(
        state: BoundaryPullState,
        availableY: Float,
        atTop: Boolean,
        atBottom: Boolean,
        currentPosition: GtdPosition,
        maxPullPx: Float,
    ): Pair<BoundaryPullState, Boolean> {
        if (availableY == 0f) return state to false
        val clampedMax = maxPullPx.coerceAtLeast(0f)

        if (!state.active) {
            val direction = when {
                availableY > 0f && atTop -> 1
                availableY < 0f && atBottom -> -1
                else -> return state to false
            }
            // 下拉(+1)对应上一清单(-1)，上拉(-1)对应下一清单(+1)。
            val lockedTarget = GestureArbiter.adjacentPosition(currentPosition, -direction)
            val resistance = resistanceFor(lockedTarget)
            val nextSigned = (abs(availableY) * resistance).coerceAtMost(clampedMax)
            return BoundaryPullState(
                basePosition = currentPosition,
                direction = direction,
                lockedTarget = lockedTarget,
                offsetPx = nextSigned * direction,
                cancelled = false,
            ) to true
        }

        if (state.cancelled) {
            // 反向越过 0 后本次手势彻底锁死取消，绝不转向反方向目标。
            return state to true
        }

        val resistance = resistanceFor(state.lockedTarget)
        if (availableY * state.direction > 0f) {
            val stillAtLockedEdge = if (state.direction > 0) atTop else atBottom
            if (!stillAtLockedEdge) return state to false
            val nextSigned = (state.signedOffsetPx + abs(availableY) * resistance)
                .coerceAtMost(clampedMax)
            return state.copy(offsetPx = nextSigned * state.direction) to true
        }

        // 反向拖动：仅减少锁定方向位移；<= 0 时归零并取消，绝不更换锁定目标。
        val nextSigned = (state.offsetPx + availableY * resistance) * state.direction
        if (nextSigned <= 0f) {
            return state.copy(offsetPx = 0f, cancelled = true) to true
        }
        return state.copy(offsetPx = nextSigned * state.direction) to true
    }

    /**
     * 松手结算：仅当未取消、锁定目标非空且不同于当前位置、基准位置与当前一致且锁定方向位移达阈值时提交；
     * 其余情形一律返回 [BoundaryPullReleaseResult.Rebound]。
     */
    fun onRelease(
        state: BoundaryPullState,
        currentPosition: GtdPosition,
        thresholdPx: Float,
    ): BoundaryPullReleaseResult {
        val target = state.lockedTarget
        val canCommit = state.active &&
            !state.cancelled &&
            target != null &&
            target != currentPosition &&
            state.basePosition == currentPosition &&
            state.signedOffsetPx >= thresholdPx
        return if (canCommit) {
            BoundaryPullReleaseResult.Commit(target)
        } else {
            BoundaryPullReleaseResult.Rebound(shouldAnimate = abs(state.offsetPx) > 0f)
        }
    }

    /**
     * 计算当前是否应显示提示及其语义类型：
     * 未激活、已取消、或锁定方向位移 <= 0（或小于 [minSignedOffsetPx]）时一律返回 null（隐藏提示）。
     */
    fun hint(
        state: BoundaryPullState,
        minSignedOffsetPx: Float = 0f,
    ): BoundaryPullHint? {
        if (!state.active || state.cancelled) return null
        val signed = state.signedOffsetPx
        if (signed <= 0f || signed < minSignedOffsetPx) return null
        val target = state.lockedTarget
        return when {
            target != null -> BoundaryPullHint(BoundaryPullHintKind.SWITCH_TARGET, target)
            state.direction > 0 -> BoundaryPullHint(BoundaryPullHintKind.FIRST_BOUNDARY, null)
            else -> BoundaryPullHint(BoundaryPullHintKind.LAST_BOUNDARY, null)
        }
    }

    /**
     * 格式化提示文案：
     * - 有锁定目标："松手切换到 ${label(target)}"（与既有 AC-G-05 文案保持一致）；
     * - 首项下拉："已是第一个清单"；
     * - 尾项上拉："已是最后一个清单"；
     * - 已取消或位移 <= 0：返回 null（隐藏提示）。
     */
    fun hintText(
        state: BoundaryPullState,
        minSignedOffsetPx: Float = 0f,
        label: (GtdPosition) -> String,
    ): String? {
        val hint = hint(state, minSignedOffsetPx) ?: return null
        return when (hint.kind) {
            BoundaryPullHintKind.SWITCH_TARGET -> "松手切换到 ${label(hint.target!!)}"
            BoundaryPullHintKind.FIRST_BOUNDARY -> "已是第一个清单"
            BoundaryPullHintKind.LAST_BOUNDARY -> "已是最后一个清单"
        }
    }
}
