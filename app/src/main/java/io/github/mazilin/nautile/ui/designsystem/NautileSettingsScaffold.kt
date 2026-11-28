package io.github.mazilin.nautile.ui.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.ui.theme.NautileSpacing

/**
 * 全屏设置页与设置子页统一外壳（DS-06；06_R7统一视觉与交互规格 §1.2、§5.5）。
 *
 * - 统一 64dp 顶栏：左侧 `NautileIconButton(ic_arrow_back)` + `headlineSmall` 标题 + 可选右侧 [actions] + 1dp 底线；
 * - **绝不消费 `WindowInsets.safeDrawing`**：该 inset 已由 `AppRoot.FullScreenPageContainer` 单次消费，
 *   在此重复消费会导致顶部/底部出现双倍留白；
 * - 页面级操作反馈统一走 DS-07 全局 `NautileSnackbarHost`，本壳不内置红字消息横幅。
 */
@Composable
fun NautileSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backContentDescription: String = "返回",
    backTestTag: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            NautilePageHeader(
                title = title,
                leading = {
                    NautileIconButton(
                        iconRes = R.drawable.ic_arrow_back,
                        contentDescription = backContentDescription,
                        onClick = onBack,
                        testTag = backTestTag,
                    )
                },
                actions = actions,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                content = content,
            )
        }
    }
}

/**
 * 设置页统一列表行（DS-06；最小高 56dp、水平 16dp、垂直 12dp）。
 *
 * 支持前导图标槽 [leading]、主标题 [title]、可选副标题 [subtitle]、可选尾部摘要 [trailingSummary]、
 * 可选跳转箭头 [showChevron] 以及自定义尾部交互槽 [trailing]（如 Switch、徽标或删除图标按钮）。
 */
@Composable
fun NautileSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailingSummary: String? = null,
    showChevron: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    showDivider: Boolean = false,
    rowContentDescription: String? = null,
    testTag: String? = null,
) {
    val mergeWholeRow = trailing == null
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SETTINGS_ROW_MIN_HEIGHT)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(enabled = enabled, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (mergeWholeRow) {
                        Modifier.semantics(mergeDescendants = true) {
                            if (rowContentDescription != null) {
                                contentDescription = rowContentDescription
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                .padding(
                    start = NautileSpacing.space4,
                    end = if (trailing != null) NautileSpacing.space2 else NautileSpacing.space4,
                    top = NautileSpacing.space3,
                    bottom = NautileSpacing.space3,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space2),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (!mergeWholeRow) {
                            Modifier.semantics(mergeDescendants = true) {
                                if (rowContentDescription != null) {
                                    contentDescription = rowContentDescription
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space3),
            ) {
                if (leading != null) {
                    leading()
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (trailingSummary != null) {
                    Text(
                        text = trailingSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
            if (showChevron) {
                Spacer(modifier = Modifier.width(NautileSpacing.space1))
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = NautileSpacing.space4),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

private val SETTINGS_ROW_MIN_HEIGHT = 56.dp
