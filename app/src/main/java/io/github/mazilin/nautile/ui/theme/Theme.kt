package io.github.mazilin.nautile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 浅/深两套 ColorScheme 由种子色 `#3B6FE0` 推导，定义在 Color.kt（COL-08、COL-13）。
 * 组件只消费角色，不读取十六进制颜色；动态取色关闭（COL-07）。
 */

/**
 * 形状只使用 Material3 公开五档（设计 §3.1）；20/32/48dp 内部档位不使用（SHP-05）。
 * Full 圆角不是公开档位，由组件按 §3.2 用 CircleShape 表达，不在此处写死字面量。
 */
private val NautileShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * 字体层级按设计 §2.1 的 token 与字号/行高/字重复核（TYP-01、TYP-04）。
 * 其余未列出的层级保留 Material3 默认值。
 */
private val NautileTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Normal,
        ),
        titleSmall = base.titleSmall.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        ),
        titleMedium = base.titleMedium.copy(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
        ),
        bodyLarge = base.bodyLarge.copy(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
        ),
        bodyMedium = base.bodyMedium.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal,
        ),
        bodySmall = base.bodySmall.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal,
        ),
        labelLarge = base.labelLarge.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        ),
        labelMedium = base.labelMedium.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
        ),
        labelSmall = base.labelSmall.copy(
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

/**
 * 根主题：唯一读取系统深浅色的位置（COL-02、NO-14）。
 * 不传自定义 motionScheme（MOT-02），动效统一引用 NautileMotion 的命名 spec。
 */
@Composable
fun NautileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NautileDarkColorScheme else NautileLightColorScheme,
        typography = NautileTypography,
        shapes = NautileShapes,
        content = content,
    )
}
