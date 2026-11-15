package io.github.mazilin.nautile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.theme.NautileTheme

/**
 * 单 Activity 宿主；增量 1 只渲染空壳首页，不含业务。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NautileTheme {
                TodoScreen()
            }
        }
    }
}
