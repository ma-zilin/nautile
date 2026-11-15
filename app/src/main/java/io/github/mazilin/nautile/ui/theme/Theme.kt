package io.github.mazilin.nautile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * 最小主题包装；完整色彩与排版在后续 UI 切片补充。
 */
@Composable
fun NautileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content,
    )
}
