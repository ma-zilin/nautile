package io.github.mazilin.nautile.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.mazilin.nautile.domain.model.AppPage

/**
 * 默认页全屏子页（S1-T10、FR-N-20）。
 *
 * 选择立即写入 DataStore；返回根设置由 NavigationCoordinator 处理。
 * S1 该页没有无效字段，预测预览不注册抑制分支。
 */
@Composable
fun DefaultPageScreen(
    currentPage: AppPage,
    onSelect: (AppPage) -> Unit,
    onBack: () -> Unit,
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
            Text("默认页", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        Column(modifier = Modifier.selectableGroup()) {
            AppPage.entries.forEach { page ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = page == currentPage,
                            onClick = { onSelect(page) },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("default_page_${page.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = page == currentPage, onClick = null)
                    Text(
                        text = appPageLabel(page),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}
