package io.github.mazilin.nautile.ui.screen.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.ui.designsystem.NautileBottomSheet
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.theme.NautileSpacing

/**
 * 日志模式选择器（05 §2.5/§6.4；AC-T-20/FR-T-21）。
 *
 * 由标题按钮打开的下半屏面板：左侧整块点按“记录/统计”，选中立即切换并关闭；
 * 不提供按钮直接轮换、右滑打开或上下拉切换（本轮延后）。
 */
@Composable
fun LogModeSelectorSheet(
    currentMode: LogMode,
    visible: Boolean,
    onModeSelected: (LogMode) -> Unit,
    onDismissRequest: () -> Unit,
    onExitFinished: () -> Unit = {},
) {
    NautileBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        canDismiss = { true },
        onDismissed = onExitFinished,
        contentModifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("log_mode_selector"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "日志模式",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            NautileIconButton(
                iconRes = R.drawable.ic_close,
                contentDescription = "关闭",
                onClick = onDismissRequest,
                testTag = "log_mode_selector_close",
            )
        }
        Column(modifier = Modifier.selectableGroup()) {
            ModeOption(
                mode = LogMode.RECORD,
                title = "记录",
                description = "按时间倒序查看与补录",
                iconRes = R.drawable.ic_schedule,
                selected = currentMode == LogMode.RECORD,
                onClick = { onModeSelected(LogMode.RECORD) },
            )
            Spacer(modifier = Modifier.height(NautileSpacing.space1))
            ModeOption(
                mode = LogMode.STATS,
                title = "统计",
                description = "按分类查看时间分布",
                iconRes = R.drawable.ic_pie_chart,
                selected = currentMode == LogMode.STATS,
                onClick = { onModeSelected(LogMode.STATS) },
            )
        }
    }
}

@Composable
private fun ModeOption(
    mode: LogMode,
    title: String,
    description: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.small)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    Modifier
                },
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = NautileSpacing.space4, vertical = NautileSpacing.space2)
            .testTag(
                when (mode) {
                    LogMode.RECORD -> "log_mode_record"
                    LogMode.STATS -> "log_mode_stats"
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = NautileSpacing.space3),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
