package io.github.mazilin.nautile.ui.screen.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.R
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.ui.theme.NautileSpacing

/**
 * 导航上方同宽计时条（05 §2.7/§3.4；AC-T-22/23）。
 *
 * - 48dp 高、与导航同宽；分类多时内部横向滚动，不压缩文字与点击区；
 * - 每分类最小宽 120dp，点按结束并保存；运行中不用无限动画（MOT-13）；
 * - 显隐由宿主控制：导航隐藏、键盘打开、覆盖层/全屏子页时整块隐藏，但不结束计时。
 */
@Composable
fun RunningTimerBar(
    running: List<RunningTimerUi>,
    onTimerTap: (TimeCategoryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NautileSpacing.space2),
            horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(running, key = { it.logId.value }) { timer ->
                val name = if (timer.parentName != null) {
                    "${timer.parentName} / ${timer.categoryName}"
                } else {
                    timer.categoryName
                }
                val elapsed = formatElapsedText(timer.elapsed)
                Row(
                    modifier = Modifier
                        .widthIn(min = 120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { onTimerTap(timer.categoryId) }
                        .padding(horizontal = NautileSpacing.space3, vertical = NautileSpacing.space1)
                        // 合并子语义为“分类，已用，点按结束”，并保留整块点击动作（§1.3）。
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$name，已用 $elapsed，点按结束"
                        }
                        .testTag("running_timer_${timer.categoryId.value}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NautileSpacing.space1),
                ) {
                    // 装饰图标：整块语义已含“正在计时/点按结束”，不重复朗读。
                    Icon(
                        painter = painterResource(R.drawable.ic_timer),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = elapsed,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    // 装饰图标：语义已含“点按结束”。
                    Icon(
                        painter = painterResource(R.drawable.ic_stop),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}
