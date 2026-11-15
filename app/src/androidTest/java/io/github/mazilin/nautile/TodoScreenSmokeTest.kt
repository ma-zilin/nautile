package io.github.mazilin.nautile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.mazilin.nautile.ui.screen.todo.TodoScreen
import io.github.mazilin.nautile.ui.theme.NautileTheme
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
}
