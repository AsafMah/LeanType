// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import android.app.Activity
import android.content.DialogInterface
import android.os.Looper
import android.view.View
import android.widget.TextView
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "keysexposed-qwerty", shadows = [ShadowInputMethodManager2::class])
class SuggestionCurationTest {
    private lateinit var strip: SuggestionStripView
    private lateinit var wordView: TextView
    private val listener = mock(SuggestionStripView.Listener::class.java)
    private val word = "hello"

    @Before
    fun setUp() {
        val ime = Robolectric.setupService(LatinIME::class.java)
        ime.prefs().edit()
            .putBoolean(Settings.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD, true)
            .putString(Settings.PREF_PHYSICAL_KEYBOARD_SUGGESTION_SHORTCUTS, "alt")
            .commit()
        val current = Settings.getValues()
        Settings.getInstance().loadSettings(ime, current.mLocale,
            current.mInputAttributes, current.mCurrentKeyboardScript)
        val root = KeyboardSwitcher.getInstance().onCreateInputView(ime, false)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(root)
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 600)
        strip = root.findViewById(R.id.suggestion_strip_view)
        strip.setListener(listener, root)
        val info = SuggestedWordInfo(word, "", 100, SuggestedWordInfo.KIND_TYPED,
            Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX,
            SuggestedWordInfo.NOT_A_CONFIDENCE)
        strip.setSuggestions(SuggestedWords(arrayListOf(info), null, info, true,
            false, false, SuggestedWords.INPUT_STYLE_PREDICTION, 0), false)
        val views = SuggestionStripView::class.java.getDeclaredField("wordViews").run {
            isAccessible = true
            get(strip) as List<*>
        }
        wordView = views.filterIsInstance<TextView>().single { it.tag == 0 }
        assertTrue("Fixture must use the real hardware-shortcut label",
            wordView.text.toString().matches(Regex("$word [\u00b9\u00b2\u00b3]")))
    }

    @Test
    fun addUsesCanonicalWordWithoutDisplayedShortcut() {
        assertTrue(strip.onLongClick(wordView))
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        verify(listener).addToDictionary(word)
        assertEquals(word, shadowOf(dialog).title.toString())
    }

    @Test
    fun blockUsesCanonicalWordWithoutDisplayedShortcut() {
        assertTrue(strip.onLongClick(wordView))
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        verify(listener).blockWord(word)
        assertEquals(word, shadowOf(dialog).title.toString())
    }
}
