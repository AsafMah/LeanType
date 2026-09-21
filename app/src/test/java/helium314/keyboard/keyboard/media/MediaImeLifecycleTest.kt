// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.text.InputType
import android.view.inputmethod.EditorInfo
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.emoji.EmojiPalettesView
import helium314.keyboard.latin.LatinIME
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [
    ShadowInputMethodManager2::class, ShadowLocaleManagerCompat::class, ShadowProximityInfo::class
])
class MediaImeLifecycleTest {
    private lateinit var ime: LatinIME
    private lateinit var palettes: EmojiPalettesView

    @Before fun setUp() {
        ime = Robolectric.setupService(LatinIME::class.java)
        palettes = Mockito.mock(EmojiPalettesView::class.java)
        KeyboardSwitcher::class.java.getDeclaredField("mEmojiPalettesView")
            .apply { isAccessible = true }.set(KeyboardSwitcher.getInstance(), palettes)
    }

    @After fun tearDown() {
        KeyboardSwitcher::class.java.getDeclaredField("mEmojiPalettesView")
            .apply { isAccessible = true }.set(KeyboardSwitcher.getInstance(), null)
        ime.onDestroy()
    }

    @Test fun finishingInputViewClearsExplicitShowAndStopsMedia() {
        assertFinishInvalidatesMedia { ime.onFinishInputView(false) }
    }

    @Test fun finishingInputClearsExplicitShowAndStopsMedia() {
        assertFinishInvalidatesMedia { ime.onFinishInput() }
    }

    @Test fun startingNewEditorInvalidatesMediaBeforeReusingKeyboard() {
        val before = ime.mediaEditorVersion
        ime.onStartInputInternal(EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            packageName = "media.lifecycle.fixture"
        }, false)
        assertEquals(before + 1, ime.mediaEditorVersion)
        Mockito.verify(palettes).stopMediaSession()
    }

    @Test fun hidingWindowStopsMedia() {
        ime.onWindowHidden()
        Mockito.verify(palettes, Mockito.atLeastOnce()).stopMediaSession()
    }

    private fun assertFinishInvalidatesMedia(finish: () -> Unit) {
        val explicitShow = LatinIME::class.java.getDeclaredField("isExplicitShowRequested")
            .apply { isAccessible = true }
        explicitShow.setBoolean(ime, true)
        val before = ime.mediaEditorVersion
        finish()
        assertFalse(explicitShow.getBoolean(ime))
        assertEquals(before + 1, ime.mediaEditorVersion)
        Mockito.verify(palettes).stopMediaSession()
    }
}
