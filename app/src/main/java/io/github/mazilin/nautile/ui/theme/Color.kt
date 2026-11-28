package io.github.mazilin.nautile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 品牌色板（设计 §1 COL-08/COL-13）。
 *
 * 种子色为 `#3B6FE0`（用户 2026-11-25 确认）；浅/深两套角色由该蓝色系推导，
 * 正文与图标按 A11Y-10 校验对比度。动态取色关闭（COL-07）。
 *
 * 颜色十六进制字面量只允许出现在本文件（COL-11）；组件与页面只消费角色。
 */

// ---- 浅色 ----

private val LightPrimary = Color(0xFF2F5FD0)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFD9E2FF)
private val LightOnPrimaryContainer = Color(0xFF0A1E4E)
private val LightInversePrimary = Color(0xFFB0C6FF)

private val LightSecondary = Color(0xFF575E71)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFDBE2F9)
private val LightOnSecondaryContainer = Color(0xFF141B2C)

private val LightTertiary = Color(0xFF6D5679)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFF6D9FF)
private val LightOnTertiaryContainer = Color(0xFF27142F)

private val LightBackground = Color(0xFFFEFBFF)
private val LightOnBackground = Color(0xFF1B1B1F)
private val LightSurface = Color(0xFFFEFBFF)
private val LightOnSurface = Color(0xFF1B1B1F)
private val LightSurfaceVariant = Color(0xFFE1E2EC)
private val LightOnSurfaceVariant = Color(0xFF44474F)

private val LightInverseSurface = Color(0xFF303034)
private val LightInverseOnSurface = Color(0xFFF2F0F4)

private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)

private val LightOutline = Color(0xFF74777F)
private val LightOutlineVariant = Color(0xFFC4C6D0)

private val LightSurfaceDim = Color(0xFFDFDDE2)
private val LightSurfaceBright = Color(0xFFFEFBFF)
private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFF8F7FB)
private val LightSurfaceContainer = Color(0xFFF2F1F6)
private val LightSurfaceContainerHigh = Color(0xFFECEBF0)
private val LightSurfaceContainerHighest = Color(0xFFE6E5EA)

// ---- 深色 ----

private val DarkPrimary = Color(0xFFB0C6FF)
private val DarkOnPrimary = Color(0xFF002C73)
private val DarkPrimaryContainer = Color(0xFF12449E)
private val DarkOnPrimaryContainer = Color(0xFFD9E2FF)
private val DarkInversePrimary = Color(0xFF2F5FD0)

private val DarkSecondary = Color(0xFFBFC6DC)
private val DarkOnSecondary = Color(0xFF293041)
private val DarkSecondaryContainer = Color(0xFF3F4759)
private val DarkOnSecondaryContainer = Color(0xFFDBE2F9)

private val DarkTertiary = Color(0xFFD9BDDF)
private val DarkOnTertiary = Color(0xFF3D2846)
private val DarkTertiaryContainer = Color(0xFF553F5E)
private val DarkOnTertiaryContainer = Color(0xFFF6D9FF)

private val DarkBackground = Color(0xFF1B1B1F)
private val DarkOnBackground = Color(0xFFE4E2E6)
private val DarkSurface = Color(0xFF1B1B1F)
private val DarkOnSurface = Color(0xFFE4E2E6)
private val DarkSurfaceVariant = Color(0xFF44474F)
private val DarkOnSurfaceVariant = Color(0xFFC4C6D0)

private val DarkInverseSurface = Color(0xFFE4E2E6)
private val DarkInverseOnSurface = Color(0xFF303034)

private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)

private val DarkOutline = Color(0xFF8E9099)
private val DarkOutlineVariant = Color(0xFF44474F)

private val DarkSurfaceDim = Color(0xFF131316)
private val DarkSurfaceBright = Color(0xFF39393D)
private val DarkSurfaceContainerLowest = Color(0xFF0F0F13)
private val DarkSurfaceContainerLow = Color(0xFF1F1F23)
private val DarkSurfaceContainer = Color(0xFF232327)
private val DarkSurfaceContainerHigh = Color(0xFF2E2E32)
private val DarkSurfaceContainerHighest = Color(0xFF39393D)

/** 遮罩统一纯黑，透明度由 NautileStates.SCRIM_ALPHA 提供（COL-10）。 */
private val NautileScrim = Color(0xFF000000)

/** 浅色角色（COL-12 截图核对对象）。 */
internal val NautileLightColorScheme: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightPrimary,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = NautileScrim,
    surfaceBright = LightSurfaceBright,
    surfaceDim = LightSurfaceDim,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainerLowest = LightSurfaceContainerLowest,
)

/** 深色角色（COL-12 截图核对对象）。 */
internal val NautileDarkColorScheme: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkPrimary,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = NautileScrim,
    surfaceBright = DarkSurfaceBright,
    surfaceDim = DarkSurfaceDim,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
)

// ---- R7 8 色事件色板：浅色套（06_R7统一视觉与交互规格 §3.1；COL-11 唯一十六进制落点） ----

/** 0 湛蓝（浅色） */
internal val LightEventSlot0Container = Color(0xFFE3ECFF)
internal val LightEventSlot0Border = Color(0xFF5B83D9)
internal val LightEventSlot0Accent = Color(0xFF3B6FE0)
internal val LightEventSlot0OnContainer = Color(0xFF102A63)

/** 1 青瓷（浅色） */
internal val LightEventSlot1Container = Color(0xFFDCF2EE)
internal val LightEventSlot1Border = Color(0xFF4A998E)
internal val LightEventSlot1Accent = Color(0xFF267F74)
internal val LightEventSlot1OnContainer = Color(0xFF0B3630)

/** 2 鼠尾草绿（浅色） */
internal val LightEventSlot2Container = Color(0xFFE3F2DF)
internal val LightEventSlot2Border = Color(0xFF5E9958)
internal val LightEventSlot2Accent = Color(0xFF3D7F37)
internal val LightEventSlot2OnContainer = Color(0xFF153612)

/** 3 暖琥珀（浅色） */
internal val LightEventSlot3Container = Color(0xFFFCEFD6)
internal val LightEventSlot3Border = Color(0xFFC28A38)
internal val LightEventSlot3Accent = Color(0xFFA66E1B)
internal val LightEventSlot3OnContainer = Color(0xFF422905)

/** 4 陶土珊瑚（浅色） */
internal val LightEventSlot4Container = Color(0xFFFCE6DF)
internal val LightEventSlot4Border = Color(0xFFCC7058)
internal val LightEventSlot4Accent = Color(0xFFB5543B)
internal val LightEventSlot4OnContainer = Color(0xFF4A190D)

/** 5 烟粉玫瑰（浅色） */
internal val LightEventSlot5Container = Color(0xFFF9E3EC)
internal val LightEventSlot5Border = Color(0xFFBF668C)
internal val LightEventSlot5Accent = Color(0xFFA3476F)
internal val LightEventSlot5OnContainer = Color(0xFF47122A)

/** 6 薰衣草紫（浅色） */
internal val LightEventSlot6Container = Color(0xFFEEE6FA)
internal val LightEventSlot6Border = Color(0xFF886CC2)
internal val LightEventSlot6Accent = Color(0xFF6B4EA8)
internal val LightEventSlot6OnContainer = Color(0xFF29164D)

/** 7 岩板青灰（浅色） */
internal val LightEventSlot7Container = Color(0xFFE7ECF2)
internal val LightEventSlot7Border = Color(0xFF6E7E91)
internal val LightEventSlot7Accent = Color(0xFF516175)
internal val LightEventSlot7OnContainer = Color(0xFF1E2733)

// ---- R7 8 色事件色板：深色套（06_R7统一视觉与交互规格 §3.1；COL-11 唯一十六进制落点） ----

/** 0 湛蓝（深色） */
internal val DarkEventSlot0Container = Color(0xFF1E2D4D)
internal val DarkEventSlot0Border = Color(0xFF6B94E8)
internal val DarkEventSlot0Accent = Color(0xFF8FB0FF)
internal val DarkEventSlot0OnContainer = Color(0xFFDCE7FF)

/** 1 青瓷（深色） */
internal val DarkEventSlot1Container = Color(0xFF183633)
internal val DarkEventSlot1Border = Color(0xFF5CB3A6)
internal val DarkEventSlot1Accent = Color(0xFF7ED1C4)
internal val DarkEventSlot1OnContainer = Color(0xFFD2F2EC)

/** 2 鼠尾草绿（深色） */
internal val DarkEventSlot2Container = Color(0xFF20361E)
internal val DarkEventSlot2Border = Color(0xFF75B36E)
internal val DarkEventSlot2Accent = Color(0xFF94D18D)
internal val DarkEventSlot2OnContainer = Color(0xFFDAF0D6)

/** 3 暖琥珀（深色） */
internal val DarkEventSlot3Container = Color(0xFF3D2E16)
internal val DarkEventSlot3Border = Color(0xFFD4A150)
internal val DarkEventSlot3Accent = Color(0xFFEBC073)
internal val DarkEventSlot3OnContainer = Color(0xFFFAE8C8)

/** 4 陶土珊瑚（深色） */
internal val DarkEventSlot4Container = Color(0xFF3F231C)
internal val DarkEventSlot4Border = Color(0xFFDF8B75)
internal val DarkEventSlot4Accent = Color(0xFFF2A894)
internal val DarkEventSlot4OnContainer = Color(0xFFFCDFD7)

/** 5 烟粉玫瑰（深色） */
internal val DarkEventSlot5Container = Color(0xFF3D202D)
internal val DarkEventSlot5Border = Color(0xFFD682A5)
internal val DarkEventSlot5Accent = Color(0xFFEB9EBD)
internal val DarkEventSlot5OnContainer = Color(0xFFFADCE8)

/** 6 薰衣草紫（深色） */
internal val DarkEventSlot6Container = Color(0xFF2D2342)
internal val DarkEventSlot6Border = Color(0xFFA48CD9)
internal val DarkEventSlot6Accent = Color(0xFFBEA8ED)
internal val DarkEventSlot6OnContainer = Color(0xFFEAE0FA)

/** 7 岩板青灰（深色） */
internal val DarkEventSlot7Container = Color(0xFF262C34)
internal val DarkEventSlot7Border = Color(0xFF8999AD)
internal val DarkEventSlot7Accent = Color(0xFFA5B5C9)
internal val DarkEventSlot7OnContainer = Color(0xFFE0E6EE)
