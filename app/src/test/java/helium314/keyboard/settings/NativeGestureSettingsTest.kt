// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.TwoThumbTypingScreen
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NativeGestureSettingsTest {
    @get:Rule
    val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private var hadGestureLib = false
    private var hadNativeGestureLib = false

    @Before
    fun setUp() {
        hadGestureLib = JniUtils.sHaveGestureLib
        hadNativeGestureLib = JniUtils.sHaveNativeGestureLib
        JniUtils.sHaveGestureLib = true
        context.prefs().edit().putBoolean(Settings.PREF_GESTURE_INPUT, true).apply()
        SettingsActivity.settingsContainer = SettingsContainer(context)
    }

    @After
    fun restoreLibraryState() {
        JniUtils.sHaveGestureLib = hadGestureLib
        JniUtils.sHaveNativeGestureLib = hadNativeGestureLib
    }

    @Test
    fun dictionaryOnlyLibraryShowsSetupRequirementInsteadOfTwoThumbControls() {
        showScreen(nativeAvailable = false)
        compose.onNodeWithText(context.getString(R.string.two_thumb_typing_requires_gesture_library))
            .assertExists()
        compose.onAllNodesWithText(context.getString(R.string.settings_category_two_thumb_typing_words))
            .assertCountEquals(0)
    }

    @Test
    fun nativeLibraryEnablesTwoThumbControls() {
        showScreen(nativeAvailable = true)
        compose.onNodeWithText(context.getString(R.string.settings_category_two_thumb_typing_words))
            .assertExists()
        compose.onAllNodesWithText(context.getString(R.string.two_thumb_typing_requires_gesture_library))
            .assertCountEquals(0)
    }

    private fun showScreen(nativeAvailable: Boolean) {
        JniUtils.sHaveNativeGestureLib = nativeAvailable
        compose.setContent {
            MaterialTheme { TwoThumbTypingScreen {} }
        }
    }
}
