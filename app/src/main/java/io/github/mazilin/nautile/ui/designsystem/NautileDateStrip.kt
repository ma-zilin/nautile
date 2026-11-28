package io.github.mazilin.nautile.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.ScheduleViewMode
import io.github.mazilin.nautile.ui.theme.NautileMotion
import io.github.mazilin.nautile.ui.theme.NautileScheduleMetrics
import io.github.mazilin.nautile.ui.theme.NautileSpacing
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 共享三段式日期条（DS-02；06_R7统一视觉与交互规格 §1.2、§5.4、§6.2）。
 *
 * 固定结构：`[左侧选择日期日历图标] [中间可横滑日期格 LazyRow] [右侧回到今天图标]`，
 * 彻底移除左右步进箭头；左右横滑仅滚动日期条，点按日期格才切换选中日期。
 * 供日程（P-05）与时间日志记录模式（P-07）100% 复用。
 */
@Composable
fun NautileDateStrip(
    dates: List<LocalDate>,
    selectedDate: LocalDate,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onPickDateClick: () -> Unit,
    onTodayClick: () -> Unit,
    modifier: Modifier = Modifier,
    pickDateContentDescription: String = "选择日期",
    todayContentDescription: String = "回到今日",
    pickDateTestTag: String? = null,
    stripTestTag: String? = null,
    todayTestTag: String? = null,
    dateCellTestTag: ((LocalDate) -> String?)? = null,
) {
    val listState = rememberLazyListState()

    // 选中日变化（点按日期格 / 弹窗选日 / 回到今日）时滚动至可见；横滑不改变选中日。
    LaunchedEffect(selectedDate, dates) {
        val index = dates.indexOf(selectedDate)
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DATE_STRIP_MIN_HEIGHT)
            .padding(
                horizontal = NautileSpacing.space1,
                vertical = NautileSpacing.space1,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NautileIconButton(
            iconRes = R.drawable.ic_calendar_today,
            contentDescription = pickDateContentDescription,
            onClick = onPickDateClick,
            testTag = pickDateTestTag,
        )
        LazyRow(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .then(if (stripTestTag != null) Modifier.testTag(stripTestTag) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(dates, key = { it.toEpochDay() }) { date ->
                NautileDateCell(
                    date = date,
                    selected = date == selectedDate,
                    isToday = date == today,
                    onClick = { onDateSelected(date) },
                    testTag = dateCellTestTag?.invoke(date),
                )
            }
        }
        NautileIconButton(
            iconRes = R.drawable.ic_today,
            contentDescription = todayContentDescription,
            onClick = onTodayClick,
            enabled = selectedDate != today,
            testTag = todayTestTag,
        )
    }
}

/** 单个日期格：48×64dp，选中态 secondaryContainer 全圆角胶囊（CircleShape），今天显示 4dp 圆点。 */
@Composable
private fun NautileDateCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    testTag: String?,
) {
    val weekday = date.dayOfWeek.shortChineseLabel()
    val description = buildString {
        append("${date.monthValue}月${date.dayOfMonth}日，周$weekday")
        if (isToday) append("，今日")
        if (selected) append("，已选中")
    }
    Box(
        modifier = Modifier
            .width(NautileScheduleMetrics.DATE_CELL_WIDTH)
            .height(NautileScheduleMetrics.DATE_CELL_HEIGHT)
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = description
                role = Role.Button
            }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = weekday,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (isToday) {
                Box(
                    modifier = Modifier
                        .size(TODAY_DOT_SIZE)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        ),
                )
            }
        }
    }
}

/**
 * 视图切换菜单选项数据（泛型轻量结构，避免组件反向耦合具体业务枚举）。
 */
@Immutable
data class CalendarViewOption<T>(
    val value: T,
    val label: String,
    val iconRes: Int,
    val testTag: String? = null,
)

/**
 * 顶栏紧凑视图切换触发按钮 + 无遮罩锚定下拉菜单（DS-02；06_R7统一视觉与交互规格 §1.2、§2.3）。
 *
 * 按钮最小高度 48dp、全圆角（[CircleShape]），展示当前视图名 + 随展开旋转 180° 的 `ic_expand_more`；
 * 下拉菜单当前项尾部带 `ic_check`，点选后立即回调并收起。
 */
@Composable
fun <T> NautileCalendarViewMenu(
    selectedValue: T,
    options: List<CalendarViewOption<T>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    buttonContentDescription: (CalendarViewOption<T>) -> String = { "切换视图，当前：${it.label}" },
    buttonTestTag: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentOption = options.firstOrNull { it.value == selectedValue } ?: options.firstOrNull()
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = NautileMotion.StandardSpatial,
        label = "calendar_view_menu_arrow",
    )

    Box(modifier = modifier) {
        if (currentOption != null) {
            Row(
                modifier = Modifier
                    .heightIn(min = NautileSpacing.space12)
                    .clip(CircleShape)
                    .clickable { expanded = true }
                    .semantics(mergeDescendants = true) {
                        contentDescription = buttonContentDescription(currentOption)
                        role = Role.Button
                    }
                    .then(if (buttonTestTag != null) Modifier.testTag(buttonTestTag) else Modifier)
                    .padding(horizontal = NautileSpacing.space2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space1),
            ) {
                Text(
                    text = currentOption.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(VIEW_ARROW_ICON_SIZE)
                        .graphicsLayer { rotationZ = arrowRotation },
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                val isSelected = option.value == selectedValue
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(option.iconRes),
                            contentDescription = null,
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelected(option.value)
                    },
                    modifier = Modifier
                        .semantics { this.selected = isSelected }
                        .then(if (option.testTag != null) Modifier.testTag(option.testTag) else Modifier),
                )
            }
        }
    }
}

/**
 * 面向日/三天/周 [ScheduleViewMode] 的便捷视图切换菜单（日程与日志记录模式直接复用）。
 */
@Composable
fun NautileCalendarViewMenu(
    viewMode: ScheduleViewMode,
    onViewModeSelected: (ScheduleViewMode) -> Unit,
    modifier: Modifier = Modifier,
    accessibilityPrefix: String = "日程视图",
    buttonTestTag: String? = null,
    optionTestTagPrefix: String? = null,
) {
    val options = remember(optionTestTagPrefix) {
        listOf(
            CalendarViewOption(
                value = ScheduleViewMode.DAY,
                label = "日",
                iconRes = R.drawable.ic_calendar_view_day,
                testTag = optionTestTagPrefix?.let { "${it}${ScheduleViewMode.DAY.name}" },
            ),
            CalendarViewOption(
                value = ScheduleViewMode.THREE_DAY,
                label = "三天",
                iconRes = R.drawable.ic_date_range,
                testTag = optionTestTagPrefix?.let { "${it}${ScheduleViewMode.THREE_DAY.name}" },
            ),
            CalendarViewOption(
                value = ScheduleViewMode.WEEK,
                label = "周",
                iconRes = R.drawable.ic_view_week,
                testTag = optionTestTagPrefix?.let { "${it}${ScheduleViewMode.WEEK.name}" },
            ),
        )
    }
    NautileCalendarViewMenu(
        selectedValue = viewMode,
        options = options,
        onSelected = onViewModeSelected,
        modifier = modifier,
        buttonContentDescription = { "切换$accessibilityPrefix，当前：${it.label}" },
        buttonTestTag = buttonTestTag,
    )
}

/** 星期中文单字简称（一..日），供日期条与时间轴列头复用。 */
internal fun DayOfWeek.shortChineseLabel(): String = when (this) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}

private val DATE_STRIP_MIN_HEIGHT = 72.dp
private val TODAY_DOT_SIZE = 4.dp
private val VIEW_ARROW_ICON_SIZE = 18.dp
