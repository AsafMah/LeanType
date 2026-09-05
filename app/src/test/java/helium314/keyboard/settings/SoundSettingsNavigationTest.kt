// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SoundSettingsNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun soundSearchResultNavigatesToTheOwningSoundScreen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val setting = SettingsContainer(context)[Settings.PREF_KEYPRESS_SOUND_STYLE]!!
        SettingsDestination.navTarget.value = SettingsDestination.Settings
        val destination = CoroutineScope(Dispatchers.Unconfined).async {
            SettingsDestination.navTarget.first { it == SettingsDestination.Sound }
        }
        try {
            compose.setContent { setting.Preference() }
            compose.onNodeWithText(setting.title).performClick()
            assertEquals(SettingsDestination.Sound, runBlocking {
                withTimeout(1000) { destination.await() }
            })
        } finally {
            destination.cancel()
            SettingsDestination.navTarget.value = SettingsDestination.Settings
        }
    }
}
