// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dictionary

import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Tests the gesture-lexicon enumeration added to the [Dictionary] hierarchy. */
class DictionaryCollectionGestureTest {

    private open class StubDict(private val words: Map<String, Int>?) : Dictionary("stub", Locale.ENGLISH) {
        override fun getSuggestions(
            composedData: ComposedData?, ngramContext: NgramContext?, proximityInfoHandle: Long,
            settingsValuesForSuggestion: SettingsValuesForSuggestion?, sessionId: Int,
            weightForLocale: Float, inOutWeightOfLangModelVsSpatialModel: FloatArray?
        ): ArrayList<SuggestedWordInfo> = ArrayList()

        override fun isInDictionary(word: String?): Boolean = words?.containsKey(word) == true

        // null -> exercise the base-class default (no override)
        override fun getWordsForGesture(): MutableMap<String, Int> =
            if (words == null) super.getWordsForGesture() else LinkedHashMap(words)
    }

    private fun collection(vararg dicts: Dictionary) =
        DictionaryCollection("main", Locale.ENGLISH, dicts.toList(), FloatArray(dicts.size) { 1f })

    @Test fun `merges words from all child dictionaries`() {
        val merged = collection(StubDict(mapOf("cat" to 100)), StubDict(mapOf("dog" to 200))).wordsForGesture
        assertEquals(setOf("cat", "dog"), merged.keys)
        assertEquals(100, merged["cat"])
        assertEquals(200, merged["dog"])
    }

    @Test fun `keeps the maximum probability for a duplicated word`() {
        val merged = collection(StubDict(mapOf("cat" to 50)), StubDict(mapOf("cat" to 200))).wordsForGesture
        assertEquals(200, merged["cat"])
    }

    @Test fun `base Dictionary returns an empty lexicon by default`() {
        assertTrue(StubDict(null).wordsForGesture.isEmpty())
    }
}
