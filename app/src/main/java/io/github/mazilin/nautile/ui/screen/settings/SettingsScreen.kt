package io.github.mazilin.nautile.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.UserSettings

/**
 * 根设置页 P-13（S1-T10、FR-N-19）。
 *
 * 只提供默认页入口与预测性返回开关；分类/标签/Key/个人记忆等未实现入口不展示。
 * 设置页为全屏子页，返回由 NavigationCoordinator 逐级处理。
 */
@Composable
fun SettingsScreen(
    settings: UserSettings,
    onBack: () -> Unit,
    onOpenDefaultPage: () -> Unit,
    onPredictiveBackChanged: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("返回")
            }
            Text("设置", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenDefaultPage)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("settings_default_page"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("默认页", modifier = Modifier.weight(1f))
            Text(appPageLabel(settings.defaultPage), style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("预测性返回", modifier = Modifier.weight(1f))
            Switch(
                checked = settings.predictiveBackEnabled,
                onCheckedChange = onPredictiveBackChanged,
                modifier = Modifier.testTag("predictive_back_switch"),
            )
        }
    }
}

/** 默认页显示名；仅四个一级页面（FR-N-20）。 */
fun appPageLabel(page: AppPage): String = when (page) {
    AppPage.TODO -> "待办"
    AppPage.SCHEDULE -> "日程"
    AppPage.LOG -> "日志"
    AppPage.AI -> "AI"
}
