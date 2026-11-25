package io.github.mazilin.nautile.ui.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R

/**
 * 空态（设计 §7.6 CMP-20、§4.2）。
 *
 * 水平 24dp；装饰图标 48dp；图标与标题 12dp；标题与说明 8dp。
 */
@Composable
fun NautileEmptyState(
    title: String,
    description: String? = null,
    iconRes: Int? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                // 空态图标为装饰性，不参与朗读（§6.2 #16）。
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 错误态（设计 §7.6 CMP-21、§8.2 A11Y-08）。
 *
 * 文案使用 error 角色并带 liveRegion=Polite；重试按钮距文字 12dp。
 */
@Composable
fun NautileErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(12.dp))
            NautileButton(
                text = "重试",
                onClick = onRetry,
                variant = NautileButtonVariant.Text,
            )
        }
    }
}

/**
 * 加载态（设计 §7.6 CMP-22、§8.2 A11Y-09）。
 *
 * 居中静态文案 + 静态装饰图标（架构裁定：不使用 indeterminate 进度环或其他无限动画）；
 * stateDescription 固定为“加载中”。
 */
@Composable
fun NautileLoadingState(
    label: String? = "加载中…",
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .semantics { stateDescription = "加载中" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 装饰性图标，不参与朗读；静态形态，无动画。
        Icon(
            painter = painterResource(R.drawable.ic_inbox),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        if (label != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
