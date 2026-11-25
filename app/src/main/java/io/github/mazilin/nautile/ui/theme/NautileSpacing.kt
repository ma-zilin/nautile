package io.github.mazilin.nautile.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 4dp 网格间距 token（设计 §4.1 SPC-01）。
 * 例外只有 1dp 分隔线与 22dp 拖拽柄区，分别见组件与 [NautileSheetMetrics]。
 */
object NautileSpacing {
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space6 = 24.dp
    val space8 = 32.dp
    val space12 = 48.dp
}

/**
 * 底部面板度量（设计 §4.1、§4.2）。
 * 22dp 只用于拖拽柄区，不得外溢到其他 padding 或高度。
 */
object NautileSheetMetrics {
    val HANDLE_AREA_DP = 22.dp
    val HANDLE_WIDTH_DP = 32.dp
    val HANDLE_HEIGHT_DP = 4.dp
}

/**
 * 状态透明度与遮罩透明度常量（设计 §1.5 COL-09、COL-10）。
 * 页面不得各自书写 alpha 数值。
 */
object NautileStates {
    /** 禁用态内容透明度 38%。 */
    const val DISABLED_CONTENT_ALPHA = 0.38f

    /** 禁用态容器透明度 12%。 */
    const val DISABLED_CONTAINER_ALPHA = 0.12f

    /** 按压态状态层透明度 12%（COL-06）。 */
    const val PRESSED_STATE_LAYER_ALPHA = 0.12f

    /** 遮罩透明度唯一取值 32%（COL-10）。 */
    const val SCRIM_ALPHA = 0.32f
}
