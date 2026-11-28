package io.github.mazilin.nautile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 单组事件色槽（06_R7统一视觉与交互规格 §3.1/§3.2，R7 #15/#16/#22）。
 *
 * 十六进制原始色值均集中定义于 [Color.kt]，本文件不出现任何十六进制字面量（COL-11）。
 *
 * @property container 时间轴卡片柔和底色填充
 * @property border 1dp 同色系深色外框描边
 * @property accent 左侧 3dp 强调竖条与统计饼图着色
 * @property onContainer 高对比度主文字（次级文字可取 78% 透明度）
 */
@Immutable
data class EventColorSlot(
    val container: Color,
    val border: Color,
    val accent: Color,
    val onContainer: Color,
)

/**
 * 8 色事件色板（浅/深双套），由 [NautileTheme] 通过 [LocalNautileEventColors] 单点提供。
 *
 * 索引取值 `0..7`；越界或负数索引经 `Math.floorMod` 安全规整，杜绝越界异常。
 */
@Immutable
data class EventColorPalette(
    val slots: List<EventColorSlot>,
) {
    /** 按索引读取色槽；自动取模映射至有效范围。 */
    fun slot(index: Int): EventColorSlot {
        val safeIndex = Math.floorMod(index, slots.size)
        return slots[safeIndex]
    }

    /** 解析事件持久化色槽：合法落库值优先，缺省时按 [fallbackId] 确定性回退。 */
    fun resolve(colorIndex: Int?, fallbackId: Long): EventColorSlot {
        val resolvedIndex = colorIndex?.takeIf { it in slots.indices }
            ?: Math.floorMod(fallbackId, slots.size.toLong()).toInt()
        return slots[resolvedIndex]
    }

    companion object {
        /** 固定 8 色槽容量。 */
        const val PALETTE_SIZE: Int = 8

        /** 浅色套 8 组色槽（按 0..7 索引顺序）。 */
        val lightSlots: List<EventColorSlot> = listOf(
            EventColorSlot(
                container = LightEventSlot0Container,
                border = LightEventSlot0Border,
                accent = LightEventSlot0Accent,
                onContainer = LightEventSlot0OnContainer,
            ),
            EventColorSlot(
                container = LightEventSlot1Container,
                border = LightEventSlot1Border,
                accent = LightEventSlot1Accent,
                onContainer = LightEventSlot1OnContainer,
            ),
            EventColorSlot(
                container = LightEventSlot2Container,
                border = LightEventSlot2Border,
                accent = LightEventSlot2Accent,
                onContainer = LightEventSlot2OnContainer,
            ),
            EventColorSlot(
                container = LightEventSlot3Container,
                border = LightEventSlot3Border,
                accent = LightEventSlot3Accent,
                onContainer = LightEventSlot3OnContainer,
            ),
            EventColorSlot(
                container = LightEventSlot4Container,
                border = LightEventSlot4Border,
                accent = LightEventSlot4Accent,
                onContainer = LightEventSlot4OnContainer,
            ),
            EventColorSlot(
                container = LightEventSlot5Container,
                border = LightEventSlot5Border,
                accent = LightEventSlot5Accent,
                onContainer = LightEventSlot5OnContainer,
            ),
            EventColorSlot(
                container = LightEventSlot6Container,
                border = LightEventSlot6Border,
                accent = LightEventSlot6Accent,
                onContainer = LightEventSlot6OnContainer,
            ),
            EventColorSlot(
                container = LightEventSlot7Container,
                border = LightEventSlot7Border,
                accent = LightEventSlot7Accent,
                onContainer = LightEventSlot7OnContainer,
            ),
        )

        /** 深色套 8 组色槽（按 0..7 索引顺序）。 */
        val darkSlots: List<EventColorSlot> = listOf(
            EventColorSlot(
                container = DarkEventSlot0Container,
                border = DarkEventSlot0Border,
                accent = DarkEventSlot0Accent,
                onContainer = DarkEventSlot0OnContainer,
            ),
            EventColorSlot(
                container = DarkEventSlot1Container,
                border = DarkEventSlot1Border,
                accent = DarkEventSlot1Accent,
                onContainer = DarkEventSlot1OnContainer,
            ),
            EventColorSlot(
                container = DarkEventSlot2Container,
                border = DarkEventSlot2Border,
                accent = DarkEventSlot2Accent,
                onContainer = DarkEventSlot2OnContainer,
            ),
            EventColorSlot(
                container = DarkEventSlot3Container,
                border = DarkEventSlot3Border,
                accent = DarkEventSlot3Accent,
                onContainer = DarkEventSlot3OnContainer,
            ),
            EventColorSlot(
                container = DarkEventSlot4Container,
                border = DarkEventSlot4Border,
                accent = DarkEventSlot4Accent,
                onContainer = DarkEventSlot4OnContainer,
            ),
            EventColorSlot(
                container = DarkEventSlot5Container,
                border = DarkEventSlot5Border,
                accent = DarkEventSlot5Accent,
                onContainer = DarkEventSlot5OnContainer,
            ),
            EventColorSlot(
                container = DarkEventSlot6Container,
                border = DarkEventSlot6Border,
                accent = DarkEventSlot6Accent,
                onContainer = DarkEventSlot6OnContainer,
            ),
            EventColorSlot(
                container = DarkEventSlot7Container,
                border = DarkEventSlot7Border,
                accent = DarkEventSlot7Accent,
                onContainer = DarkEventSlot7OnContainer,
            ),
        )

        /** 浅色主题事件色板。 */
        val Light: EventColorPalette = EventColorPalette(lightSlots)

        /** 深色主题事件色板。 */
        val Dark: EventColorPalette = EventColorPalette(darkSlots)

        /** 从指定色槽列表解析事件颜色。 */
        fun resolve(
            slots: List<EventColorSlot>,
            colorIndex: Int?,
            fallbackId: Long,
        ): EventColorSlot {
            val resolvedIndex = colorIndex?.takeIf { it in slots.indices }
                ?: Math.floorMod(fallbackId, slots.size.toLong()).toInt()
            return slots[resolvedIndex]
        }

        /** 读取当前主题事件色板并按持久化色槽或回退 ID 解析色槽。 */
        @Composable
        fun resolve(colorIndex: Int?, fallbackId: Long): EventColorSlot =
            LocalNautileEventColors.current.resolve(colorIndex, fallbackId)
    }
}

/** 当前主题 8 色事件色板，由 [NautileTheme] 单点注入。 */
val LocalNautileEventColors = staticCompositionLocalOf { EventColorPalette.Light }
