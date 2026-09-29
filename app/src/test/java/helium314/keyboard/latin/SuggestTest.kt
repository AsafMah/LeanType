// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import androidx.core.content.edit
import helium314.keyboard.ShadowBinaryDictionaryUtils
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.SuggestionResults
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLog
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Suppress("NonAsciiCharacters")
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
    ShadowBinaryDictionaryUtils::class,
    ShadowFacilitator::class,
])
class SuggestTest {
    private lateinit var latinIME: LatinIME
    private val suggest get() = latinIME.mInputLogic.suggest

    // values taken from the string array auto_correction_threshold_mode_indexes
    private val thresholdModest = 0.185f
    private val thresholdAggressive = 0.067f
    private val thresholdVeryAggressive = -1f

    @BeforeTest fun setUp() {
        currentTypingLocale = Locale.ENGLISH
        typingSuggestionResults = SuggestionResults(0, false, false)
        mainDictionaryInitialized = true
        latinIME = Robolectric.setupService(LatinIME::class.java)
        // start logging only after latinIME is created, avoids showing the stack traces if library is not found
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out
        DeviceProtectedUtils.getSharedPreferences(latinIME)
            .edit { putBoolean(Settings.PREF_AUTO_CORRECTION, true) } // need to enable, off by default
    }

    @Test fun `'on' to 'in' if 'in' was used before in this context`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "on",
            listOf(suggestion("on", 1800000, locale), suggestion("in", 600000, locale)),
            suggestion("in", 240, locale),
            null, // never typed "on" in this context
            locale,
            thresholdModest
        )
        assert(!result.last()) // should not be corrected
        // not corrected because first suggestion score is too low
    }

    @Test fun `'ill' to 'I'll' if 'ill' not used before in this context, and I'll is whitelisted`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "ill",
            listOf(suggestion("I'll", Int.MAX_VALUE, locale), suggestion("ill", 1500000, locale)),
            null,
            null,
            locale,
            thresholdModest
        )
        assert(result.last()) // should be corrected
        // correction because both empty scores are 0, which should be fine (next check is comparing empty scores)
    }

    @Test fun `not 'ill' to 'I'll' if only 'ill' was used before in this context`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "ill",
            listOf(suggestion("I'll", Int.MAX_VALUE, locale), suggestion("ill", 1500000, locale)),
            null,
            suggestion("ill", 200, locale),
            locale,
            thresholdModest
        )
        assert(!result.last()) // should not be corrected
        // not corrected because first empty score not high enough
    }

    @Test fun `'ill' to 'I'll' if both have same ngram score`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "ill",
            listOf(suggestion("I'll", Int.MAX_VALUE, locale), suggestion("ill", 1500000, locale)),
            suggestion("I'll", 200, locale),
            suggestion("ill", 200, locale),
            locale,
            thresholdModest
        )
        assert(result.last()) // should be corrected
    }

    @Test fun `no 'ill' to 'I'll' if 'ill' has somewhat better ngram score`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "ill",
            listOf(suggestion("I'll", Int.MAX_VALUE, locale), suggestion("ill", 1500000, locale)),
            suggestion("I'll", 200, locale),
            suggestion("ill", 211, locale),
            locale,
            thresholdModest
        )
        assert(!result.last()) // should not be corrected
    }

    @Test fun `no English 'I' for Polish 'i' when typing in Polish`() {
        val result = shouldBeAutoCorrected(
            "i",
            listOf(suggestion("I", Int.MAX_VALUE, Locale.ENGLISH), suggestion("i", 1500000, Locale("pl"))),
            null,
            null,
            Locale("pl"),
            thresholdVeryAggressive
        )
        assert(!result.last()) // should not be corrected
        // not even checking at modest and aggressive thresholds, this is a locale thing
        // if very aggressive, still no correction because locale matches with typed word only
    }

    @Test fun `English 'I' instead of Polish 'i' when typing in English`() {
        val result = shouldBeAutoCorrected(
            "i",
            listOf(suggestion("I", Int.MAX_VALUE, Locale.ENGLISH), suggestion("i", 1500000, Locale("pl"))),
            null,
            null,
            Locale.ENGLISH,
            thresholdModest
        )
        assert(result.last()) // should be corrected
        // only corrected because it's whitelisted (int max value)
        // if it wasn't whitelisted, it would never be allowed due to utoCorrectionUtils.suggestionExceedsThreshold (unless set to very aggressive)
        //  -> maybe normalizedScore needs adjustment if the only difference is upper/lowercase
        //     todo: consider special score for case-only difference?
    }

    @Test fun `no English 'in' instead of French 'un' when typing in French`() {
        val result = shouldBeAutoCorrected(
            "un",
            listOf(suggestion("in", Int.MAX_VALUE, Locale.ENGLISH), suggestion("un", 1500000, Locale.FRENCH)),
            null,
            null,
            Locale.FRENCH,
            thresholdModest
        )
        assert(!result.last()) // should not be corrected
        // not corrected because of locale matching
    }

    @Test fun `no 'né' instead of 'ne'`() {
        val result = shouldBeAutoCorrected(
            "ne",
            listOf(suggestion("ne", 1900000, Locale.FRENCH), suggestion("né", 1900000-1, Locale.FRENCH)),
            null,
            null,
            Locale.FRENCH,
            thresholdModest
        )
        assert(!result.last()) // should not be corrected
        // not corrected because score is lower
    }

    @Test fun `'né' instead of 'ne' if 'né' in ngram context`() {
        val locale = Locale.FRENCH
        val result = shouldBeAutoCorrected(
            "ne",
            listOf(suggestion("ne", 1900000, locale), suggestion("né", 1900000-1, locale)),
            suggestion("né", 200, locale),
            null,
            locale,
            thresholdModest
        )
        assert(result.last()) // should be corrected
    }

    @Test fun `'né' instead of 'ne' if 'né' has clearly better score in ngram context`() {
        val locale = Locale.FRENCH
        val result = shouldBeAutoCorrected(
            "ne",
            listOf(suggestion("ne", 1900000, locale), suggestion("né", 1900000-1, locale)),
            suggestion("né", 215, locale),
            suggestion("ne", 200, locale),
            locale,
            thresholdModest
        )
        assert(result.last()) // should be corrected
    }

    @Test fun `no 'né' instead of 'ne' if both with same score in ngram context`() {
        val locale = Locale.FRENCH
        val result = shouldBeAutoCorrected(
            "ne",
            listOf(suggestion("ne", 1900000, locale), suggestion("né", 1900000-1, locale)),
            suggestion("né", 200, locale),
            suggestion("ne", 200, locale),
            locale,
            thresholdModest
        )
        assert(!result.last()) // should not be corrected
    }

    @Test fun `no 'ne' instead of 'né'`() {
        val locale = Locale.FRENCH
        val result = shouldBeAutoCorrected(
            "né",
            listOf(suggestion("ne", 600000, locale), suggestion("né", 1600000, locale)),
            suggestion("né", 200, locale),
            suggestion("ne", 200, locale),
            locale,
            thresholdModest
        )
        assert(!result.last()) // should not be corrected
        // not even allowed to check because of low score for ne
    }

    @Test fun `shortcuts might be autocorrected by default`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "gd",
            listOf(suggestion("good", 700000, locale, true)),
            null,
            null,
            locale,
            thresholdAggressive
        )
        assert(result.last()) // should be corrected

        val result2 = shouldBeAutoCorrected(
            "gd",
            listOf(suggestion("good", 300000, locale, true)),
            null,
            null,
            locale,
            thresholdModest
        )
        assert(!result2.last()) // should not be corrected
    }

    @Test fun `shortcuts are not autocorrected when setting is off`() {
        val prefs = latinIME.prefs()
        prefs.edit { putBoolean(Settings.PREF_AUTOCORRECT_SHORTCUTS, false) }
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "gd",
            listOf(suggestion("good", 12000000, locale, true)),
            null,
            null,
            locale,
            thresholdAggressive
        )
        assert(!result.last()) // should not be corrected
    }

    @Test fun `quotes are added to suggestions when needed`() {
        val result = Suggest.getTransformedSuggestedWordInfo(suggestion("word", 1, Locale.ENGLISH, true),
            Locale.ENGLISH, false, false, 1)
        assertEquals("word'", result.mWord)
    }

    @Test fun `misspelled word is corrected using relaxed threshold even with low score`() {
        val locale = Locale.ENGLISH
        // typed word: "recpa" (length 5) -> not in dictionary
        // suggestion: "recep" (score 300,000)
        // edit distance is 2, normalizedScore is 0.3 * (1 - 2/5) = 0.18
        // 0.18 < 0.185 (threshold), but adjustedThreshold is 0.185 * (3/5) = 0.111
        // Since score 300,000 > scoreLimit / 4 (237,500) and length > 3, it should correct!
        val result = shouldBeAutoCorrected(
            "recpa",
            listOf(suggestion("recep", 300000, locale)),
            null,
            null,
            locale,
            thresholdModest
        )
        assert(result.last()) // should be corrected
    }

    @Test fun `missing apostrophe promotes the actual contraction past a case-only distractor`() {
        val contraction = suggestion("you're", 100000, Locale.ENGLISH)
        val candidates = listOf(
            suggestion("Youre", 900000, Locale.ENGLISH),
            suggestion("your", 800000, Locale.ENGLISH),
            suggestion("yours", 700000, Locale.ENGLISH),
            suggestion("yore", 600000, Locale.ENGLISH),
            contraction
        )
        val result = getSuggestedWords("youre", candidates)

        assertTrue(result.mWillAutoCorrect)
        assertSame(contraction, result.getInfo(SuggestedWords.INDEX_OF_AUTO_CORRECTION))
        assertEquals("youre", result.getWord(SuggestedWords.INDEX_OF_TYPED_WORD))
        assertEquals("youre", result.getWord(2))
        assertEquals(listOf("youre", "you're", "youre", "Youre", "your", "yours", "yore"),
            (0 until result.size()).map(result::getWord))
        assertEquals(SuggestedWords.INPUT_STYLE_TYPING, result.mInputStyle)
        assertEquals(42, result.mSequenceNumber)
        assertFalse(result.mTypedWordValid)
    }

    @Test fun `missing apostrophe does not authorize an unrelated first candidate`() {
        val contraction = suggestion("you're", 100000, Locale.ENGLISH)
        val result = getSuggestedWords("youre", listOf(
            suggestion("yours", 900000, Locale.ENGLISH), contraction))

        assertTrue(result.mWillAutoCorrect)
        assertSame(contraction, result.getInfo(SuggestedWords.INDEX_OF_AUTO_CORRECTION))
        assertEquals("youre", result.getWord(2))
        assertEquals("yours", result.getWord(3))
    }

    @Test fun `unshifted contraction prefers existing canonical spelling over a higher capitalized variant`() {
        val contraction = suggestion("you're", 100000, Locale.ENGLISH)
        val result = getSuggestedWords("youre", listOf(
            suggestion("Youre", 950000, Locale.ENGLISH),
            suggestion("You're", 900000, Locale.ENGLISH),
            contraction
        ))
        assertTrue(result.mWillAutoCorrect)
        assertSame(contraction, result.getInfo(SuggestedWords.INDEX_OF_AUTO_CORRECTION))
        assertEquals(listOf("youre", "you're", "youre", "Youre", "You're"),
            (0 until result.size()).map(result::getWord))
    }

    @Test fun `canonical contraction spelling retains English I capitalization`() {
        val contraction = suggestion("I'm", 100000, Locale.ENGLISH)
        val result = getSuggestedWords("im", listOf(
            suggestion("Im", 950000, Locale.ENGLISH),
            suggestion("i'm", 900000, Locale.ENGLISH),
            contraction
        ))
        assertTrue(result.mWillAutoCorrect)
        assertSame(contraction, result.getInfo(SuggestedWords.INDEX_OF_AUTO_CORRECTION))
    }

    @Test fun `contraction shortcut only authorizes the candidate being evaluated`() {
        val result = shouldBeAutoCorrected(
            "youre",
            listOf(suggestion("Youre", 900000, Locale.ENGLISH),
                suggestion("you're", 100000, Locale.ENGLISH)),
            null, null, Locale.ENGLISH, thresholdModest
        )
        assertFalse(result.last())
    }

    @Test fun `promoted contraction retains sentence-start and manual capitalization`() {
        val candidates = listOf(
            suggestion("yours", 900000, Locale.ENGLISH),
            suggestion("you're", 100000, Locale.ENGLISH),
            suggestion("You're", 90000, Locale.ENGLISH)
        )
        val cases = listOf(
            Triple("Youre", KeyboardId.ELEMENT_ALPHABET, WordComposer.CAPS_MODE_AUTO_SHIFTED),
            Triple("Youre", KeyboardId.ELEMENT_ALPHABET, WordComposer.CAPS_MODE_MANUAL_SHIFTED),
            Triple("youre", KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED, WordComposer.CAPS_MODE_OFF),
            Triple("YOURE", KeyboardId.ELEMENT_ALPHABET, WordComposer.CAPS_MODE_MANUAL_SHIFT_LOCKED),
            Triple("youre", KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED, WordComposer.CAPS_MODE_OFF)
        )
        for ((word, element, capsMode) in cases) {
            val result = getSuggestedWords(word, candidates, keyboardElement = element, capsMode = capsMode)
            val expected = if (word == "YOURE" || element == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED)
                "YOU'RE" else "You're"
            assertTrue(result.mWillAutoCorrect, "$word / $element / $capsMode")
            assertEquals(expected, result.getWord(SuggestedWords.INDEX_OF_AUTO_CORRECTION))
            assertEquals(1, (0 until result.size()).count { result.getWord(it) == expected })
            val promoted = result.getInfo(SuggestedWords.INDEX_OF_AUTO_CORRECTION)
            assertEquals(candidates[1].mScore, promoted.mScore)
            assertEquals(candidates[1].mKindAndFlags, promoted.mKindAndFlags)
            assertSame(candidates[1].mSourceDict, promoted.mSourceDict)
            assertEquals(result.getWord(SuggestedWords.INDEX_OF_TYPED_WORD), result.getWord(2))
        }
    }

    @Test fun `promoted contraction retains trailing quote transformation`() {
        for ((word, expected) in listOf("youre'" to "you're", "youre''" to "you're'")) {
            val result = getSuggestedWords(word, listOf(
                suggestion("yours", 900000, Locale.ENGLISH),
                suggestion("you're", 100000, Locale.ENGLISH)
            ))
            assertTrue(result.mWillAutoCorrect)
            assertEquals(expected, result.getWord(SuggestedWords.INDEX_OF_AUTO_CORRECTION))
            assertEquals(word, result.getWord(2))
        }
    }

    @Test fun `disabled correction resumed input and missing main dictionary do not promote contractions`() {
        val candidates = listOf(
            suggestion("Youre", 900000, Locale.ENGLISH),
            suggestion("you're", 100000, Locale.ENGLISH)
        )
        val disabled = getSuggestedWords("youre", candidates, correctionEnabled = false)
        val resumed = getSuggestedWords("youre", candidates, resumed = true)
        mainDictionaryInitialized = false
        val noDictionary = getSuggestedWords("youre", candidates)
        for (result in listOf(disabled, resumed, noDictionary)) {
            assertFalse(result.mWillAutoCorrect)
            assertEquals("Youre", result.getWord(1))
            assertEquals(listOf("Youre", "you're"),
                (0 until result.size()).map(result::getWord).filter { it != "youre" })
        }
    }

    @Test fun `known typed words are not replaced by contractions including in another language`() {
        for ((word, contraction, locale) in listOf(
            Triple("cant", "can't", Locale.ENGLISH),
            Triple("dont", "don't", Locale.FRENCH),
            Triple("you're", "Youre", Locale.ENGLISH)
        )) {
            val result = getSuggestedWords(word, listOf(
                suggestion(contraction, 1900000, Locale.ENGLISH),
                suggestion(word, 1600000, locale)
            ), typingLocale = locale)
            assertTrue(result.mTypedWordValid)
            assertFalse(result.mWillAutoCorrect)
            assertEquals(word, result.mTypedWordInfo?.mWord)
            assertTrue((0 until result.size()).any { result.getWord(it) == word })
        }
    }

    @Test fun `existing dictionary whitelist takes priority over contraction promotion`() {
        val whitelist = suggestion("your", Int.MAX_VALUE, Locale.ENGLISH)
        val result = getSuggestedWords("youre", listOf(
            whitelist, suggestion("you're", 100000, Locale.ENGLISH)))
        assertTrue(result.mWillAutoCorrect)
        assertSame(whitelist, result.getInfo(SuggestedWords.INDEX_OF_AUTO_CORRECTION))
    }

    @Test fun `explicit dictionary shortcut keeps priority and respects its correction setting`() {
        val shortcut = suggestion("your", 1200000, Locale.ENGLISH, shortcut = true)
        val candidates = listOf(shortcut, suggestion("you're", 100000, Locale.ENGLISH))
        for (enabled in listOf(true, false)) {
            latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOCORRECT_SHORTCUTS, enabled) }
            val result = getSuggestedWords("youre", candidates)
            assertEquals(enabled, result.mWillAutoCorrect)
            assertSame(shortcut, result.getInfo(1))
        }
    }

    @Test fun `contraction shortcut does not bypass disabled dictionary shortcuts`() {
        val contraction = suggestion("you're", 100000, Locale.ENGLISH, shortcut = true)
        val candidates = listOf(suggestion("Youre", 900000, Locale.ENGLISH), contraction)
        for (enabled in listOf(true, false)) {
            latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOCORRECT_SHORTCUTS, enabled) }
            val result = getSuggestedWords("youre", candidates)
            assertEquals(enabled, result.mWillAutoCorrect)
            assertSame(if (enabled) contraction else candidates[0], result.getInfo(1))
        }
    }

    @Test fun `absent contractions are not synthesized and case-only protection remains`() {
        val result = getSuggestedWords("youre", listOf(suggestion("Youre", 900000, Locale.ENGLISH)))
        assertFalse(result.mWillAutoCorrect)
        assertEquals("Youre", result.getWord(1))
        assertFalse((0 until result.size()).any { result.getWord(it) == "you're" })
    }

    @Test fun `contraction promotion does not rewrite dictionary casing`() {
        val contraction = suggestion("You're", 100000, Locale.ENGLISH)
        val result = getSuggestedWords("youre", listOf(
            suggestion("yours", 900000, Locale.ENGLISH), contraction))
        assertTrue(result.mWillAutoCorrect)
        assertSame(contraction, result.getInfo(SuggestedWords.INDEX_OF_AUTO_CORRECTION))
    }

    @Test fun `existing contraction matching still works with English as a secondary language`() {
        val contraction = suggestion("you're", 100000, Locale.ENGLISH)
        val result = getSuggestedWords("youre", listOf(
            suggestion("yourte", 900000, Locale.FRENCH), contraction), typingLocale = Locale.FRENCH)
        assertTrue(result.mWillAutoCorrect)
        assertSame(contraction, result.getInfo(SuggestedWords.INDEX_OF_AUTO_CORRECTION))
    }

    @Test fun `intentional mixed case is not replaced or reordered`() {
        val result = getSuggestedWords("yOUre", listOf(
            suggestion("yours", 900000, Locale.ENGLISH),
            suggestion("you're", 100000, Locale.ENGLISH)))
        assertFalse(result.mWillAutoCorrect)
        assertEquals("yours", result.getWord(1))
    }

    private fun getSuggestedWords(
        word: String,
        candidates: List<SuggestedWordInfo>,
        correctionEnabled: Boolean = true,
        keyboardElement: Int = KeyboardId.ELEMENT_ALPHABET,
        capsMode: Int = WordComposer.CAPS_MODE_OFF,
        resumed: Boolean = false,
        typingLocale: Locale = Locale.ENGLISH
    ): SuggestedWords {
        currentTypingLocale = typingLocale
        val dictionaryResults = SuggestionResults(candidates.size, false, false).apply {
            addAll(candidates)
            mRawSuggestions?.addAll(candidates)
        }
        val originalOrder = dictionaryResults.toList()
        typingSuggestionResults = dictionaryResults
        suggest.clearNextWordSuggestionsCache()
        suggest.setAutoCorrectionThreshold(thresholdModest)
        val composer = WordComposer()
        if (resumed) {
            val codePoints = StringUtils.toCodePointArray(word)
            composer.setComposingWord(codePoints, IntArray(codePoints.size * 2))
        } else {
            word.codePoints().forEach {
                composer.applyProcessedEvent(composer.processEvent(
                    Event.createEventForCodePointFromUnknownSource(it)))
            }
        }
        composer.setCapitalizedModeAtStartComposingTime(capsMode)
        val keyboard = Keyboard(KeyboardParams().apply {
            mId = KeyboardLayoutSet.getFakeKeyboardId(keyboardElement)
            GRID_WIDTH = 1
            GRID_HEIGHT = 1
        })
        val result = suggest.getSuggestedWords(
            composer, NgramContext(NgramContext.WordInfo("think")), keyboard,
            SettingsValuesForSuggestion(false, false, ""),
            correctionEnabled, SuggestedWords.INPUT_STYLE_TYPING, 42
        )
        assertEquals(originalOrder, dictionaryResults.toList())
        assertSame(dictionaryResults.mRawSuggestions, result.mRawSuggestions)
        result.mRawSuggestions?.let { assertEquals(candidates, it) }
        return result
    }


    private fun shouldBeAutoCorrected(word: String, // typed word
                              suggestions: List<SuggestedWordInfo>, // suggestions ordered by score, including suggestion for typed word if in dictionary
                              firstSuggestionForEmpty: SuggestedWordInfo?, // first suggestion if typed word would be empty (null if none)
                              typedWordSuggestionForEmpty: SuggestedWordInfo?, // suggestion for actually typed word if typed word would be empty (null if none)
                              typingLocale: Locale, // used for checking whether suggestion locale is the same, relevant e.g. for English i -> I shortcut, but we want Polish i
                              autoCorrectThreshold: Float
    ): List<Boolean> {
        latinIME.prefs().edit { putFloat(Settings.PREF_AUTO_CORRECT_THRESHOLD, autoCorrectThreshold) }
        // enable "more autocorrect" so we actually have autocorrect even though we don't set a compatible input type
        latinIME.prefs().edit { putBoolean(Settings.PREF_MORE_AUTO_CORRECTION, true) }
        currentTypingLocale = typingLocale
        val suggestionsContainer = ArrayList<SuggestedWordInfo>().apply { addAll(suggestions) }
        val suggestionResults = SuggestionResults(suggestions.size, false, false)
        suggestions.forEach { suggestionResults.add(it) }

        // store the original SuggestedWordInfo for typed word, as it will be removed
        // we may want to re-add it in case auto-correction happens, so that the original word can at least be selected
        val typedWordFirstOccurrenceWordInfo: SuggestedWordInfo? = suggestionsContainer.firstOrNull { it.mWord == word }

        val firstOccurrenceOfTypedWordInSuggestions =
            SuggestedWordInfo.removeDupsAndTypedWord(word, suggestionsContainer)

        return suggest.shouldBeAutoCorrected(
            StringUtils.getTrailingSingleQuotesCount(word),
            word,
            suggestionsContainer.firstOrNull(), // todo: get from suggestions? mostly it's just removing the typed word, right?
            { firstSuggestionForEmpty to typedWordSuggestionForEmpty },
            true, // doesn't make sense otherwise
            WordComposer.getComposerForTest(false),
            suggestionResults,
            firstOccurrenceOfTypedWordInSuggestions,
            typedWordFirstOccurrenceWordInfo
        ).toList()
    }
}

private var currentTypingLocale = Locale.ENGLISH
internal var typingSuggestionResults = SuggestionResults(0, false, false)
private var mainDictionaryInitialized = true

fun suggestion(word: String, score: Int, locale: Locale, shortcut: Boolean = false) =
    SuggestedWordInfo(
        /* word */ word,
        /* prevWordsContext */ "", // irrelevant

        // typically 2B for whitelisted, 1.5M for exact match, 600k for close match
        // when previous word context is empty, scores are usually 200+ if word is known and somewhat often used, 0 if unknown
        /* score */ score,

        /* kindAndFlags */ if (score == Int.MAX_VALUE) SuggestedWordInfo.KIND_WHITELIST
            else if (shortcut) SuggestedWordInfo.KIND_SHORTCUT // whitelist & shortcut only counts a whitelist
            else SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION, // shortcuts seem to never have this flag
        /* sourceDict */ TestDict(locale),
        /* indexOfTouchPointOfSecondWord */ 0, // irrelevant
        /* autoCommitFirstWordConfidence */ 0 // irrelevant?
    )

@Implements(DictionaryFacilitatorImpl::class)
class ShadowFacilitator {
    @Implementation
    fun getCurrentLocale(): Locale = currentTypingLocale
    @Implementation
    fun getMainLocale(): Locale = currentTypingLocale
    @Implementation
    fun hasAtLeastOneInitializedMainDictionary() = mainDictionaryInitialized
    @Implementation
    fun isMainDictionaryLoadPending() = false
    @Implementation
    fun getSuggestionResults(
        composedData: ComposedData,
        ngramContext: NgramContext,
        keyboard: Keyboard,
        settingsValuesForSuggestion: SettingsValuesForSuggestion,
        sessionId: Int,
        inputStyle: Int
    ): SuggestionResults = if (composedData.mTypedWord.isEmpty()) SuggestionResults(0, false, false)
        else typingSuggestionResults
}

private class TestDict(locale: Locale) : Dictionary("testDict", locale) {
    override fun getSuggestions(
        composedData: ComposedData,
        ngramContext: NgramContext,
        proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion,
        sessionId: Int,
        weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?
    ): ArrayList<SuggestedWordInfo>? {
        TODO("Not yet implemented")
    }

    override fun isInDictionary(word: String): Boolean {
        TODO("Not yet implemented")
    }
}
