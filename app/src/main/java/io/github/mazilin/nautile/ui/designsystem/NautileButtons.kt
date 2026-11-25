package io.github.mazilin.nautile.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.ui.theme.NautileStates

/** 按钮变体（设计 §7.3）；主/次差异只通过该参数表达，不拆成多个函数。 */
enum class NautileButtonVariant {
    Filled,
    Tonal,
    Outlined,
    Text,
}

/**
 * 文字按钮（设计 §7.3 CMP-08）。
 *
 * 视觉高度 40dp、触控 48dp 与 Full 圆角沿用 Material3 默认；
 * loading 时仅禁用点击并保留原文案，不使用任何进度环或无限动画（架构裁定）。
 */
@Composable
fun NautileButton(
    text: String,
    onClick: () -> Unit,
    variant: NautileButtonVariant = NautileButtonVariant.Filled,
    enabled: Boolean = true,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // 加载中不可重复点击，文字与按钮尺寸保持不变；不使用进度环等无限动画。
    val interactive = enabled && !loading
    val label: @Composable () -> Unit = {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
    when (variant) {
        NautileButtonVariant.Filled -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = interactive,
            content = { label() },
        )

        NautileButtonVariant.Tonal -> FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = interactive,
            content = { label() },
        )

        NautileButtonVariant.Outlined -> OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = interactive,
            content = { label() },
        )

        NautileButtonVariant.Text -> TextButton(
            onClick = onClick,
            modifier = modifier,
            enabled = interactive,
            content = { label() },
        )
    }
}

/**
 * 图标按钮（设计 §7.3 CMP-09、§8.1 A11Y-01/02）。
 *
 * 视觉容器 40dp、图标 24dp、触控 48dp、圆形；
 * contentDescription 为非空必填参数，装饰性图标请由调用方直接使用 Icon(contentDescription = null)。
 */
@Composable
fun NautileIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        // 无容器时只用 Transparent，不用它模拟语义底色（NO-01）。
        Color.Transparent
    }
    val tint = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        LocalContentColor.current
    }
    val contentAlpha = if (enabled) 1f else NautileStates.DISABLED_CONTENT_ALPHA
    Box(
        modifier = modifier
            .size(48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .alpha(contentAlpha)
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
