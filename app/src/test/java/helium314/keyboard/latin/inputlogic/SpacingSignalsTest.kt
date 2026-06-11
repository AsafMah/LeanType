// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.inputlogic

import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.dictionary.Dictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [InputLogic.computeSpacingSignals] (#14 spacing policy): the free per-keystroke
 * `complete` + `prefixRichScore` signals derived from the suggestion results. Pure logic.
 */
class SpacingSignalsTest {

    // mDictType != "user_typed" -> counts as a "real" dictionary source for `complete`.
    private val realDict: Dictionary = Dictionary.DICTIONARY_APPLICATION_DEFINED
    private val userTyped: Dictionary = Dictionary.DICTIONARY_USER_TYPED

    private fun info(word: String, kind: Int, dict: Dictionary): SuggestedWordInfo =
        SuggestedWordInfo(word, "", 0, kind, dict,
            SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)

    private fun words(typed: SuggestedWordInfo?, typedValid: Boolean,
                      list: List<SuggestedWordInfo>): SuggestedWords =
        SuggestedWords(ArrayList(list), null, typed, typedValid, false, false,
            SuggestedWords.INPUT_STYLE_TYPING, SuggestedWords.NOT_A_SEQUENCE_NUMBER)

    @Test fun `empty suggestions yield no signals`() {
        val s = InputLogic.computeSpacingSignals(SuggestedWords.getEmptyInstance())
        assertFalse(s.complete)
        assertEquals(0f, s.prefixRichScore, 0f)
    }

    @Test fun `valid typed word from a real dictionary is complete`() {
        val typed = info("the", SuggestedWordInfo.KIND_TYPED, realDict)
        assertTrue(InputLogic.computeSpacingSignals(words(typed, true, listOf(typed))).complete)
    }

    @Test fun `valid typed word from the user-typed source is NOT complete`() {
        val typed = info("xyzzy", SuggestedWordInfo.KIND_TYPED, userTyped)
        assertFalse(InputLogic.computeSpacingSignals(words(typed, true, listOf(typed))).complete)
    }

    @Test fun `invalid typed word is not complete`() {
        val typed = info("teh", SuggestedWordInfo.KIND_TYPED, realDict)
        assertFalse(InputLogic.computeSpacingSignals(words(typed, false, listOf(typed))).complete)
    }

    @Test fun `prefix-rich score is the fraction of completions`() {
        val typed = info("ba", SuggestedWordInfo.KIND_TYPED, realDict)
        val list = listOf(
            typed,
            info("bad", SuggestedWordInfo.KIND_COMPLETION, realDict),
            info("bat", SuggestedWordInfo.KIND_COMPLETION, realDict),
            info("ball", SuggestedWordInfo.KIND_COMPLETION, realDict),
        )
        // 3 completions out of 4 candidates.
        assertEquals(0.75f, InputLogic.computeSpacingSignals(words(typed, false, list)).prefixRichScore, 1e-6f)
    }

    @Test fun `no completions yields zero prefix-rich score`() {
        val typed = info("the", SuggestedWordInfo.KIND_TYPED, realDict)
        assertEquals(0f,
            InputLogic.computeSpacingSignals(words(typed, true, listOf(typed))).prefixRichScore, 0f)
    }
}
