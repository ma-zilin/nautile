package io.github.mazilin.nautile.ui.screen.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.ui.designsystem.NautileEmptyState
import io.github.mazilin.nautile.ui.designsystem.NautileIconButton
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import java.time.LocalDate

/**
 * 日志页 P-07/P-08（R6-G4-T3/T4、R6-G5；05 §2.4/§2.5、§3.3/§3.5）。
 *
 * 顶部“日志”+模式按钮+设置；记录模式快捷入口与倒序列表；统计模式复用同一页壳，
 * 显示范围分段、饼图、图例与父级汇总。模式由标题按钮的半屏选择器切换（AC-T-20），
 * 切回记录或离开日志后统计恢复今日（AC-T-25）。
 */
@Composable
fun LogScreen(
    state: LogUiState = LogUiState(),
    onOpenSettings: () -> Unit = {},
    onCategoryTap: (TimeCategoryId) -> Unit = {},
    onShowAllCategories: () -> Unit = {},
    onOpenLog: (TimeLogId) -> Unit = {},
    onOpenManual: () -> Unit = {},
    onDismissMessage: () -> Unit = {},
    onNavigationVisibilityChanged: (Boolean) -> Unit = {},
    onOpenModeSelector: () -> Unit = {},
    onSelectDayPeriod: () -> Unit = {},
    onSelectMonthPeriod: () -> Unit = {},
    onSelectYearPeriod: () -> Unit = {},
    onOpenPeriodPanel: () -> Unit = {},
    onToggleStatsParent: (TimeCategoryId) -> Unit = {},
    onLeaveScreen: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val latestNavigationCallback by rememberUpdatedState(onNavigationVisibilityChanged)
    val latestLeaveCallback by rememberUpdatedState(onLeaveScreen)

    // 离开日志页（切换一级页）时丢弃统计状态，再次进入默认记录与今日（AC-T-25）。
    DisposableEffect(Unit) {
        onDispose { latestLeaveCallback() }
    }

    // 记录列表向下滚动时隐藏导航与计时条；向上或回到顶部恢复。空列表与统计模式始终可见。
    LaunchedEffect(listState, state.isEmpty, state.mode) {
        if (state.mode != LogMode.RECORD || state.isEmpty) {
            latestNavigationCallback(true)
            return@LaunchedEffect
        }
        var lastIndex = 0
        var lastOffset = 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val movedDown = index > lastIndex || (index == lastIndex && offset > lastOffset + SCROLL_THRESHOLD_PX)
                val movedUp = index < lastIndex || (index == lastIndex && offset < lastOffset - SCROLL_THRESHOLD_PX)
                when {
                    index == 0 && offset == 0 -> latestNavigationCallback(true)
                    movedDown -> latestNavigationCallback(false)
                    movedUp -> latestNavigationCallback(true)
                }
                lastIndex = index
                lastOffset = offset
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LogTopBar(
                mode = state.mode,
                onOpenSettings = onOpenSettings,
                onOpenModeSelector = onOpenModeSelector,
            )
            if (state.mode == LogMode.RECORD) {
                LogRecordContent(
                    state = state,
                    listState = listState,
                    onCategoryTap = onCategoryTap,
                    onShowAllCategories = onShowAllCategories,
                    onOpenLog = onOpenLog,
                    onDismissMessage = onDismissMessage,
                )
            } else {
                StatsContent(
                    state = state.stats,
                    categories = state.categories,
                    onSelectDay = onSelectDayPeriod,
                    onSelectMonth = onSelectMonthPeriod,
                    onSelectYear = onSelectYearPeriod,
                    onOpenCustom = onOpenPeriodPanel,
                    onToggleParent = onToggleStatsParent,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (state.mode == LogMode.RECORD) {
            FloatingActionButton(
                onClick = onOpenManual,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(NautileSpacing.space4)
                    .testTag("add_log"),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = "补录记录",
                )
            }
        }

        // 模式选择器与统计范围面板不在此渲染：由 MainActivity 的覆盖层宿主统一铺满全屏，
        // 遮罩覆盖底部导航与计时条，返回层级由 OverlayBackStack 负责（05 §1.5/§6.4/§6.5）。
    }
}

/**
 * 日志模式选择器与统计范围面板的覆盖层宿主（05 §1.5/§6.4/§6.5；AC-T-20/AC-L-07/11）。
 *
 * 由 MainActivity 渲染在 AppRoot 之上：`NautileBottomSheet` 的 32% 遮罩铺满全屏，
 * 覆盖底部导航与计时条区域。打开选择器不改变当前模式，点按“记录/统计”才切换并关闭；
 * 点遮罩、下拉、关闭图标与系统返回都由覆盖层栈关闭后同步 ViewModel 状态。
 */
@Composable
fun LogModeOverlayHost(
    modeSelectorOpen: Boolean,
    periodPanelOpen: Boolean,
    mode: LogMode,
    stats: StatsUiState,
    onModeSelected: (LogMode) -> Unit,
    onDismissModeSelector: () -> Unit,
    onStartChanged: (LocalDate) -> Unit,
    onEndChanged: (LocalDate) -> Unit,
    onConfirmPeriod: () -> Unit,
    onDismissPeriodPanel: () -> Unit,
) {
    // 覆盖层关闭后仍保留到退出动效结束，再释放节点（CMP-19）。
    var lastModeSelectorOpen by remember { mutableStateOf(false) }
    if (modeSelectorOpen) lastModeSelectorOpen = true
    var lastPeriodPanelOpen by remember { mutableStateOf(false) }
    if (periodPanelOpen) lastPeriodPanelOpen = true

    if (lastModeSelectorOpen) {
        LogModeSelectorSheet(
            currentMode = mode,
            visible = modeSelectorOpen,
            onModeSelected = onModeSelected,
            onDismissRequest = onDismissModeSelector,
            onExitFinished = {
                if (!modeSelectorOpen) lastModeSelectorOpen = false
            },
        )
    }
    if (lastPeriodPanelOpen) {
        StatsPeriodPanel(
            state = stats,
            visible = periodPanelOpen,
            onStartChanged = onStartChanged,
            onEndChanged = onEndChanged,
            onConfirm = onConfirmPeriod,
            onDismissRequest = onDismissPeriodPanel,
            onExitFinished = {
                if (!periodPanelOpen) lastPeriodPanelOpen = false
            },
        )
    }
}

@Composable
private fun ColumnScope.LogRecordContent(
    state: LogUiState,
    listState: LazyListState,
    onCategoryTap: (TimeCategoryId) -> Unit,
    onShowAllCategories: () -> Unit,
    onOpenLog: (TimeLogId) -> Unit,
    onDismissMessage: () -> Unit,
) {
    if (state.categories.isEmpty()) {
        Text(
            text = "还没有分类，请到设置 > 分类管理添加",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NautileSpacing.space4, vertical = NautileSpacing.space2)
                .testTag("log_no_categories"),
        )
    }
    // 快捷入口常驻：无历史或分类为空时只显示“全部分类”，仍可打开选择器（AC-T-21）。
    CategoryQuickEntry(
        categories = state.recentCategories,
        running = state.running,
        onCategoryTap = onCategoryTap,
        onShowAll = onShowAllCategories,
        modifier = Modifier.padding(vertical = NautileSpacing.space2),
    )
    if (state.message != null) {
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDismissMessage)
                .padding(horizontal = NautileSpacing.space4)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("log_message"),
        )
    }
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        if (state.isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("log_empty_state"),
                contentAlignment = Alignment.Center,
            ) {
                NautileEmptyState(
                    title = "还没有记录",
                    description = "点上方分类开始计时，或补录一条记录",
                    iconRes = R.drawable.ic_schedule,
                )
            }
        } else {
            LogRecordList(
                logs = state.logs,
                today = state.today,
                zoneId = state.zoneId,
                onOpenLog = onOpenLog,
                listState = listState,
            )
        }
    }
}

@Composable
private fun LogTopBar(
    mode: LogMode,
    onOpenSettings: () -> Unit,
    onOpenModeSelector: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NautileSpacing.space1, vertical = NautileSpacing.space1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space1),
            ) {
                Text(
                    text = "日志",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = NautileSpacing.space3)
                        .semantics { heading() },
                )
                // 标题按钮只打开半屏模式选择器，不做直接轮换、右滑或上下拉切换（AC-T-20）。
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenModeSelector)
                        .padding(horizontal = NautileSpacing.space2)
                        .testTag("log_mode_indicator"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(
                            if (mode == LogMode.RECORD) R.drawable.ic_schedule else R.drawable.ic_pie_chart,
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (mode == LogMode.RECORD) "记录" else "统计",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = NautileSpacing.space1),
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = NautileSpacing.space1),
                    )
                }
                NautileIconButton(
                    iconRes = R.drawable.ic_settings,
                    contentDescription = "设置",
                    onClick = onOpenSettings,
                    testTag = "log_settings_entry",
                )
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

private const val SCROLL_THRESHOLD_PX = 8
