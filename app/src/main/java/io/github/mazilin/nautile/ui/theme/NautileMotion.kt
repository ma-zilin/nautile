package io.github.mazilin.nautile.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 动效 token（设计 §5.1 MOT-01）。
 *
 * 不实现 MotionScheme 接口，也不调用 Material3 internal 的 standard()/expressive() 工厂；
 * 页面与组件只引用这里的命名 spec，不直接写 tween/spring 参数（MOT-03、NO-03）。
 */
object NautileMotion {

    /** 位移类 Standard 动效：200ms，FastOutSlowIn。 */
    val StandardSpatial: TweenSpec<Float> = tween(
        durationMillis = 200,
        easing = FastOutSlowInEasing,
    )

    /** 颜色、透明度、图标切换等 Standard 动效：150ms，FastOutSlowIn。 */
    val StandardEffects: TweenSpec<Float> = tween(
        durationMillis = 150,
        easing = FastOutSlowInEasing,
    )

    /** M3 Expressive 专用 spring，仅用于设计 §5.3 的三处（MOT-04）。 */
    val Expressive: SpringSpec<Float> = spring(
        stiffness = 380f,
        dampingRatio = 0.75f,
    )

    /** 边界拉动未超阈值时的回弹：250ms，无过冲（MOT-11）。 */
    val BoundaryRebound: TweenSpec<Float> = tween(
        durationMillis = 250,
        easing = FastOutSlowInEasing,
    )

    /** 降级兜底：200ms，FastOutSlowIn（§5.5）。 */
    val FallbackTween: TweenSpec<Float> = tween(
        durationMillis = 200,
        easing = FastOutSlowInEasing,
    )
}
