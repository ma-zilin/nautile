package io.github.mazilin.nautile.ui.screen.log

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.ui.designsystem.NautileBottomSheet
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.theme.NautileMotion
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 统计范围面板内的子视图；返回先退出日期日历，再关闭面板（05 §6.5）。 */
private enum class StatsPeriodSubview { NONE, START, END }

/**
 * 统计范围选择面板（05 §2.5/§3.5/§6.5；AC-L-07/11）。
 *
 * 起止日期必填、结束不早于开始、结束不晚于今天、最长 366 天；
 * 非法时内联提示并禁用“确定”，不静默修正。确定后关闭并立即刷新统计。
 */
@Composable
fun StatsPeriodPanel(
    state: StatsUiState,
    visible: Boolean,
    onStartChanged: (LocalDate) -> Unit,
    onEndChanged: (LocalDate) -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    onExitFinished: () -> Unit = {},
) {
    var subview by remember { mutableStateOf(StatsPeriodSubview.NONE) }
    val scrollState = rememberScrollState()

    LaunchedEffect(visible) {
        if (!visible) subview = StatsPeriodSubview.NONE
    }

    LogSheetBackHandler(
        subviewOpen = subview != StatsPeriodSubview.NONE,
        visible = visible,
        onCloseSubview = { subview = StatsPeriodSubview.NONE },
    )

    val dismissRequest: () -> Unit = {
        if (subview != StatsPeriodSubview.NONE) {
            subview = StatsPeriodSubview.NONE
        } else {
            onDismissRequest()
        }
    }
    val canConfirm = state.customStart != null &&
        state.customEnd != null &&
        state.validationError == null

    NautileBottomSheet(
        visible = visible,
        onDismissRequest = dismissRequest,
        canDismiss = { true },
        onDismissed = onExitFinished,
        contentModifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        contentScrollState = scrollState,
    ) {
        // 面板内日期日历与范围表单切换只做内容区 crossfade（ANIM-S08 同源）。
        Crossfade(
            targetState = subview,
            animationSpec = NautileMotion.StandardEffects,
            label = "stats_period_subview_crossfade",
        ) { currentSubview ->
            when (currentSubview) {
            StatsPeriodSubview.START,
            StatsPeriodSubview.END,
            -> {
                val isStart = currentSubview == StatsPeriodSubview.START
                StatsDatePanel(
                    date = (if (isStart) state.customStart else state.customEnd)
                        ?: state.today,
                    maxDate = state.today,
                    onDateSelected = {
                        if (isStart) onStartChanged(it) else onEndChanged(it)
                        subview = StatsPeriodSubview.NONE
                    },
                    onBack = { subview = StatsPeriodSubview.NONE },
                )
            }

            StatsPeriodSubview.NONE -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "选择统计范围",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stats_period_panel"),
                    )
                    NautileIconButton(
                        iconRes = R.drawable.ic_close,
                        contentDescription = "关闭",
                        onClick = onDismissRequest,
                        testTag = "stats_period_close",
                    )
                }

                StatsDateRow(
                    label = "开始",
                    date = state.customStart,
                    testTag = "stats_custom_start",
                    onClick = { subview = StatsPeriodSubview.START },
                )
                StatsDateRow(
                    label = "结束",
                    date = state.customEnd,
                    testTag = "stats_custom_end",
                    onClick = { subview = StatsPeriodSubview.END },
                )

                val error = state.validationError
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("stats_custom_error"),
                    )
                } else {
                    Text(
                        text = "最长 366 天，结束日期不晚于今天",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = NautileSpacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.testTag("stats_custom_cancel"),
                    ) {
                        Text("取消")
                    }
                    Box(modifier = Modifier.weight(1f))
                    Button(
                        onClick = onConfirm,
                        enabled = canConfirm,
                        modifier = Modifier.testTag("stats_custom_confirm"),
                    ) {
                        Text("确定")
                    }
                }
                }
            }
            }
        }
    }
}

@Composable
private fun StatsDateRow(
    label: String,
    date: LocalDate?,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_calendar_today),
            contentDescription = null,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = NautileSpacing.space2),
        )
        Text(
            text = date?.toString() ?: "未选择",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = NautileSpacing.space2),
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
        )
    }
}

/** 面板内日期日历；未来日期不可选（结束不晚于今天，AC-L-11）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsDatePanel(
    date: LocalDate,
    maxDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    val selectableDates = remember(maxDate) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                Instant.ofEpochMilli(utcTimeMillis)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                    .let { !it.isAfter(maxDate) }

            override fun isSelectableYear(year: Int): Boolean = year <= maxDate.year
        }
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = selectableDates,
    )
    LaunchedEffect(datePickerState.selectedDateMillis) {
        val millis = datePickerState.selectedDateMillis ?: return@LaunchedEffect
        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        if (picked != date) onDateSelected(picked)
    }
    Column(modifier = Modifier.fillMaxWidth().testTag("stats_date_panel")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "选择日期",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack, modifier = Modifier.testTag("stats_date_back")) {
                Text("返回")
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(DATE_PICKER_HEIGHT)) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false,
                title = null,
                headline = null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val DATE_PICKER_HEIGHT = 360.dp
