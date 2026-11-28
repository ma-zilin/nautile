package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.ui.theme.NautileScheduleMetrics
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R6-G2-V1 · AC-S-13 固定时间密度部分的 JVM 检查（补充模拟器 UI 断言）。
 *
 * 预期来自需求基线 v2.16 AC-S-13（L541）：视图切换不改时间密度、不出现双指缩放入口；
 * 设计 05 §3.1 与架构 §5：小时高度固定 60dp，日/三天/周共用同一常量。
 * 这里核对度量常量与日程 UI 源码中不存在缩放/捏合参数；真实渲染密度由
 * `AcScheduleS13UiTest` 在模拟器上逐视图读取小时行高断言。
 */
class AcScheduleS13DensityRulesTest {

    private val mainRoot: File = listOf(
        File("src/main/java"),
        File("app/src/main/java"),
    ).firstOrNull { it.isDirectory }
        ?: error("找不到 src/main/java；当前工作目录=${File(".").absolutePath}")

    private val scheduleUiDir = File(
        mainRoot,
        "io/github/mazilin/nautile/ui/screen/schedule",
    )

    @Test
    fun acS13_hourHeightIsFixedConstantAndDayHeightIs24Hours() {
        assertEquals("小时高度应为设计固定的 60dp", 60f, NautileScheduleMetrics.HOUR_HEIGHT.value, 0.001f)
        assertEquals(
            "一天网格高度必须等于 24×小时高度",
            NautileScheduleMetrics.HOUR_HEIGHT.value * 24f,
            NautileScheduleMetrics.DAY_HEIGHT.value,
            0.001f,
        )
    }

    @Test
    fun acS13_timeGridUsesFixedDensityConstantInsteadOfViewDependentValue() {
        val grid = File(scheduleUiDir, "ScheduleTimeGrid.kt")
        assertTrue("缺少 ScheduleTimeGrid.kt", grid.isFile)
        val text = grid.readText()

        assertTrue(
            "时间网格必须引用固定 HOUR_HEIGHT 常量",
            text.contains("NautileScheduleMetrics.HOUR_HEIGHT"),
        )
        assertTrue(
            "时间网格高度必须引用固定 DAY_HEIGHT 常量",
            text.contains("NautileScheduleMetrics.DAY_HEIGHT"),
        )
        // 拒绝：三种视图若各自传入不同的小时高度，会出现按 viewMode 分支的密度参数。
        assertFalse(
            "日程网格不得按视图模式改变小时高度",
            text.contains("hourHeightFor") ||
                text.contains("HOUR_HEIGHT * viewMode") ||
                (text.contains("viewMode") && text.contains("hourHeight")),
        )
    }

    @Test
    fun acS13_scheduleUiHasNoPinchOrZoomEntry() {
        val forbidden = listOf(
            "detectTransformGestures",
            "TransformableState",
            "rememberTransformableState",
            ".transformable(",
            "zoomIn",
            "zoomOut",
            "pinch",
        )
        val violations = scheduleUiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().withIndex().mapNotNull { (index, line) ->
                    if (forbidden.any { line.contains(it) }) {
                        "${file.name}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertTrue("日程 UI 不得出现双指缩放参数：\n${violations.joinToString("\n")}", violations.isEmpty())
    }
}
