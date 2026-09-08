// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import android.content.res.Configuration
import android.view.View
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.keyboard.KeyboardSwitcher.KeyboardSwitchState
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.prefs
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [ShadowInputMethodManager2::class])
class HardwareKeyboardVisibilityTest {
    @Test
    fun mainFrameKeepsToolbarOnlyAndSpecialPanelSemantics() {
        val ime = Robolectric.setupService(LatinIME::class.java)
        val switcher = KeyboardSwitcher.getInstance()
        val root = switcher.onCreateInputView(ime, false)
        val setFrame = KeyboardSwitcher::class.java.getDeclaredMethod(
            "setMainKeyboardFrame", SettingsValues::class.java, KeyboardSwitchState::class.java,
        ).apply { isAccessible = true }
        for (hardware in listOf(false, true)) {
            val configuration = Configuration(ime.resources.configuration).apply {
                keyboard = if (hardware) Configuration.KEYBOARD_QWERTY else Configuration.KEYBOARD_NOKEYS
                hardKeyboardHidden = if (hardware) Configuration.HARDKEYBOARDHIDDEN_NO
                    else Configuration.HARDKEYBOARDHIDDEN_YES
            }
            @Suppress("DEPRECATION")
            ime.resources.updateConfiguration(configuration, ime.resources.displayMetrics)
            for (toolbarOnly in listOf(false, true)) {
                ime.prefs().edit().putBoolean(
                    Settings.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD, toolbarOnly,
                ).commit()
                val current = Settings.getValues()
                Settings.getInstance().loadSettings(
                    ime, current.mLocale, current.mInputAttributes, current.mCurrentKeyboardScript,
                )
                val settings = Settings.getValues()
                assertEquals(hardware, settings.mHasHardwareKeyboard)
                assertEquals(hardware && toolbarOnly, settings.mShowToolbarOnly)
                for (state in KeyboardSwitchState.values()) {
                    val specialPanel = state == KeyboardSwitchState.EMOJI || state == KeyboardSwitchState.CLIPBOARD
                    val suppressed = hardware && (toolbarOnly || state == KeyboardSwitchState.HIDDEN)
                    assertEquals(!specialPanel && suppressed,
                        switcher.isImeSuppressedByHardwareKeyboard(settings, state))
                    setFrame.invoke(switcher, settings, state)
                    val expected = if (suppressed) View.GONE else View.VISIBLE
                    val label = "hardware=$hardware toolbarOnly=$toolbarOnly state=$state"
                    assertEquals(label, expected, root.findViewById<View>(R.id.main_keyboard_frame).visibility)
                    assertEquals(label, expected, root.findViewById<View>(R.id.keyboard_view).visibility)
                }
            }
        }
    }
}
