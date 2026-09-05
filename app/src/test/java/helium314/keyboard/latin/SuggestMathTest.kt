// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.utils.SuggestionResults
import helium314.keyboard.latin.utils.prefs
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class SuggestMathTest {
    private lateinit var ime: LatinIME

    @Before
    fun setup() {
        ime = Robolectric.setupService(LatinIME::class.java)
    }

    @Test
    fun disabledMathDoesNotInjectACalculatedCandidate() {
        assertMathCandidate(enabled = false)
    }

    @Test
    fun enabledMathStillOffersACalculatedCandidate() {
        assertMathCandidate(enabled = true)
    }

    private fun assertMathCandidate(enabled: Boolean) {
        ime.prefs().edit().putBoolean(Settings.PREF_INLINE_MATH_CALCULATION, enabled).commit()
        assertEquals(enabled, Settings.getValues().mInlineMathCalculation)
        val keyboard = Keyboard(KeyboardParams().apply {
            mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardId.ELEMENT_ALPHABET)
            mOccupiedWidth = 400
            mOccupiedHeight = 100
            mBaseWidth = 400
            mBaseHeight = 100
            mMostCommonKeyWidth = 100
            mMostCommonKeyHeight = 100
            GRID_WIDTH = 4
            GRID_HEIGHT = 1
            "1+2=".forEachIndexed { index, letter ->
                onAddKey(Key(letter.toString(), null, letter.code, null, null,
                    0, Key.BACKGROUND_TYPE_NORMAL, index * 100, 0, 100, 100, 0, 0))
            }
        })
        val facilitator = Mockito.mock(DictionaryFacilitator::class.java)
        Mockito.`when`(facilitator.mainLocale).thenReturn(Locale.ENGLISH)
        Mockito.`when`(facilitator.getSuggestionResults(
            Mockito.any(ComposedData::class.java),
            Mockito.any(NgramContext::class.java),
            Mockito.any(Keyboard::class.java),
            Mockito.any(SettingsValuesForSuggestion::class.java),
            Mockito.anyInt(),
            Mockito.anyInt(),
        )).thenReturn(SuggestionResults(4, false, false))
        val composer = WordComposer().apply {
            setComposingWord("1+1=".map { it.code }.toIntArray(), IntArray(8))
        }
        val result = Suggest(facilitator).getSuggestedWords(
            composer, NgramContext.EMPTY_PREV_WORDS_INFO, keyboard,
            SettingsValuesForSuggestion(false, false, "fallback"),
            false, SuggestedWords.INPUT_STYLE_TYPING, 1,
        )
        val candidates = (0 until result.size()).map { result.getWord(it) }
        assertEquals(candidates.toString(), enabled, "2" in candidates)
    }
}
