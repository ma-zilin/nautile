package io.github.mazilin.nautile

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.theme.NautileTheme
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class TodoScreenSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun placeholder_is_displayed() {
        composeRule.setContent {
            NautileTheme {
                TodoScreen()
            }
        }

        composeRule.onNodeWithText("Nautile").assertIsDisplayed()
    }

    companion object {
        /**
         * T-040：本机 vivo（Android 16）在“instrumentation 冷启动进程”里调用
         * Instrumentation.startActivitySync 会一直挂起；进程预热后同一调用正常。
         * 先用 shell 身份启动 MainActivity 预热进程，再由 Compose 规则启动宿主 Activity。
         */
        @BeforeClass
        @JvmStatic
        fun prewarmAppProcess() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val command = "am start -W -n io.github.mazilin.nautile/.MainActivity"
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command),
            ).bufferedReader().use { it.readText() }
        }
    }
}
