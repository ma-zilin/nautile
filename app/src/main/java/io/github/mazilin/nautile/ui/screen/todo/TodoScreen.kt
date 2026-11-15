package io.github.mazilin.nautile.ui.screen.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 空壳首页：只证明工程可启动；不实现 S1 业务。
 */
@Composable
fun TodoScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Nautile",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "脚手架已就绪（P1.5 空壳）",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
