package helium314.keyboard.settings.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TextExpanderScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun expandImmediatelyPreferenceAppearsOnce() {
        composeTestRule.setContent {
            TextExpanderScreen(onClickBack = {})
        }

        composeTestRule
            .onAllNodesWithText("Expand immediately", useUnmergedTree = true)
            .assertCountEquals(1)
    }
}
