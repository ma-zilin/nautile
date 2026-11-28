package io.github.mazilin.nautile.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.mazilin.nautile.R

/**
 * 顶部栏动作（设计 §7.1 HeaderActionState 的最小化版本）。
 *
 * contentDescription 必填，对应 §6.2 功能性图标；enabled=false 时仍可见并保留 disabled 语义（A11Y-07）。
 */
data class NautileTopBarAction(
    val iconRes: Int,
    val contentDescription: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val testTag: String? = null,
)

/**
 * 顶部栏兼容薄包装（设计 §7.1、§4.2）。
 *
 * R7 起一级页顶栏统一演进为 [NautilePageHeader]（DS-01），本函数保留旧签名并直接委托其渲染。
 */
@Composable
fun NautileTopBar(
    title: String,
    subtitle: String?,
    onOpenGtd: () -> Unit,
    actions: List<NautileTopBarAction>,
    modifier: Modifier = Modifier,
) {
    NautilePageHeader(
        title = title,
        subtitle = subtitle,
        subtitleMaxLines = 2,
        leading = {
            NautileIconButton(
                iconRes = R.drawable.ic_menu,
                contentDescription = "打开 GTD 清单选择器",
                onClick = onOpenGtd,
            )
        },
        actions = {
            actions.forEach { action ->
                NautileIconButton(
                    iconRes = action.iconRes,
                    contentDescription = action.contentDescription,
                    onClick = action.onClick,
                    enabled = action.enabled,
                    testTag = action.testTag,
                )
            }
        },
        modifier = modifier,
    )
}
