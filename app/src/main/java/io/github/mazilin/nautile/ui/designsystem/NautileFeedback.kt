package io.github.mazilin.nautile.ui.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.withTimeoutOrNull

/** 全局轻量提示自动消失时长：2.5 秒（06_R7统一视觉与交互规格 §7.1）。 */
const val NAUTILE_SNACKBAR_DURATION_MS: Long = 2_500L

/**
 * 一次性轻量提示事件契约（DS-07；由各业务 ViewModel 发射并由顶层宿主统一消费）。
 */
@Immutable
data class NautileFeedbackEvent(
    val message: String,
)

/**
 * 展示统一 2.5s 自动消失的轻量提示（DS-07，#19）。
 *
 * 新消息到达前先关闭当前提示防止队列积压；通过 `Indefinite` 配合 [withTimeoutOrNull]
 * 精确控制 2500ms 自动消失，同时支持用户提前横滑关闭。
 */
suspend fun showNautileSnackbar(
    hostState: SnackbarHostState,
    message: String,
    durationMs: Long = NAUTILE_SNACKBAR_DURATION_MS,
) {
    hostState.currentSnackbarData?.dismiss()
    val result = withTimeoutOrNull(durationMs) {
        hostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Indefinite,
        )
    }
    if (result == null) {
        hostState.currentSnackbarData?.dismiss()
    }
}

/**
 * 全局唯一轻量提示宿主（DS-07；06_R7统一视觉与交互规格 §1.2、§7.1、§9）。
 *
 * 采用 M3 标准 `inverseSurface`/`inverseOnSurface` 与 4dp（`shapes.extraSmall`）圆角，
 * 不覆写 M3 内置进出场动效（MOT-06），固定挂载 `testTag = "nautile_snackbar_host"`。
 */
@Composable
fun NautileSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.testTag("nautile_snackbar_host"),
    ) { snackbarData ->
        Snackbar(
            snackbarData = snackbarData,
            shape = MaterialTheme.shapes.extraSmall,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}

/**
 * 表单/字段内联错误文案（DS-07；06_R7统一视觉与交互规格 §7.1 L2）。
 *
 * 使用 `bodySmall` + `error` 色并标记 `LiveRegionMode.Polite` 供屏幕阅读器礼貌播报；
 * 当 [message] 为 null 或空白时不占布局空间。
 */
@Composable
fun NautileInlineError(
    message: String?,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    if (message.isNullOrBlank()) return
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    )
}
