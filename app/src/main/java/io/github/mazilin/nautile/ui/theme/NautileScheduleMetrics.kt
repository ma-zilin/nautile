package io.github.mazilin.nautile.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 日程页度量（设计 05 §3.1、§9.4 修订建议）。
 *
 * 固定时间密度：同一小时高度在日/三天/周三种视图一致，不做双指缩放（AC-S-13）。
 * 页面与组件只引用这里的常量，不各自写像素值。
 */
object NautileScheduleMetrics {

    /** 时间网格固定小时高度（设计默认 60dp）。 */
    val HOUR_HEIGHT = 60.dp

    /** 一天 24 小时的网格总高度。 */
    val DAY_HEIGHT = HOUR_HEIGHT * 24

    /** 小时刻度列宽度。 */
    val HOUR_LABEL_WIDTH = 44.dp

    /** 日期格建议尺寸 48×64dp（设计 §1.2）；触控容器不低于 48dp。 */
    val DATE_CELL_WIDTH = 48.dp
    val DATE_CELL_HEIGHT = 64.dp

    /** 日期条与网格左右内边距。 */
    val PAGE_HORIZONTAL_PADDING = 16.dp

    /** 周/三天视图列间距（设计默认 2dp）。 */
    val COLUMN_GAP = 2.dp

    /** 事件块最小高度（可点区域）。 */
    val EVENT_BLOCK_MIN_HEIGHT = 24.dp

    /** 事件块圆角（shapes.small，8dp）。 */
    val EVENT_BLOCK_CORNER = 8.dp

    /** 当前时间线横线高度与左侧圆点尺寸。 */
    val NOW_LINE_HEIGHT = 1.dp
    val NOW_DOT_SIZE = 8.dp
}
