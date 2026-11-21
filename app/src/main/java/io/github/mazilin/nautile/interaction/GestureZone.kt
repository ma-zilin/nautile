package io.github.mazilin.nautile.interaction

/**
 * Z0—Z8 手势分区（交互契约 02 §2.1）。
 *
 * 首版无页面横滑；Z0 系统边缘手势不归应用处理，应用不扩大系统边缘排除区。
 */
object GestureZone {

    /** 系统边缘返回区；应用手势只在非边缘起手。 */
    const val SYSTEM_EDGE_EXCLUSION_DP = 20f

    const val Z0_SYSTEM_EDGE = "Z0"
    const val Z1_CONTENT = "Z1"
    const val Z2_LIST_ITEM = "Z2"
    const val Z3_GTD_SHEET = "Z3"
    const val Z6_DETAIL_SHEET = "Z6"
    const val Z7_BOTTOM_PANEL = "Z7"
}
