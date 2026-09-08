// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [ShadowInputMethodManager2::class])
class BlockedWordsNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dictionaryEntryOpensTheExistingBlockedWordsEditor() {
        val ime = Robolectric.setupService(LatinIME::class.java)
        SettingsActivity.settingsContainer = SettingsContainer(ime)
        SettingsDestination.navTarget.value = SettingsDestination.Settings
        compose.setContent {
            MaterialTheme { SettingsNavHost({}, startDestination = SettingsDestination.Dictionaries) }
        }
        compose.onNodeWithText(ime.getString(R.string.edit_blocked_words)).performClick()
        compose.onNodeWithText("${ime.getString(R.string.edit_blocked_words)} (0)").assertExists()
        compose.onNodeWithText(ime.getString(R.string.add_blocked_word), useUnmergedTree = true).assertExists()
    }
}
