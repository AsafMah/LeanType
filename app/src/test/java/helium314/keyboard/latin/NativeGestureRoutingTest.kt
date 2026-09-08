package helium314.keyboard.latin

import helium314.keyboard.ShadowBinaryDictionaryUtils
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.SuggestionResults
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowBinaryDictionaryUtils::class])
class NativeGestureRoutingTest {
    private lateinit var ime: LatinIME
    private lateinit var facilitator: DictionaryFacilitator
    private lateinit var keyboard: Keyboard
    private var hadNativeLibrary = false
    private val batchRequests = mutableListOf<ComposedData>()

    @Before fun setUp() {
        ime = Robolectric.setupService(LatinIME::class.java)
        hadNativeLibrary = JniUtils.sHaveNativeGestureLib
        facilitator = mock(DictionaryFacilitator::class.java)
        `when`(facilitator.mainLocale).thenReturn(Locale.ENGLISH)
        keyboard = mock(Keyboard::class.java)
        Keyboard::class.java.getDeclaredField("mId").apply { isAccessible = true }
            .set(keyboard, mock(KeyboardId::class.java))
        doAnswer { invocation ->
            val composed = invocation.getArgument<ComposedData>(0)
            SuggestionResults(1, false, false).apply {
                if (composed.mIsBatchMode) {
                    batchRequests.add(composed)
                    add(SuggestedWords.SuggestedWordInfo(
                        "hello", "", 100000,
                        SuggestedWords.SuggestedWordInfo.KIND_CORRECTION,
                        Dictionary.DICTIONARY_USER_TYPED,
                        SuggestedWords.SuggestedWordInfo.NOT_AN_INDEX,
                        SuggestedWords.SuggestedWordInfo.NOT_A_CONFIDENCE,
                    ))
                }
            }
        }.`when`(facilitator).getSuggestionResults(
            any(), any(), any(), any(), anyInt(), anyInt(),
        )
    }

    @After fun tearDown() {
        JniUtils.sHaveNativeGestureLib = hadNativeLibrary
        ime.onDestroy()
        ShadowInputMethodManager2.reset()
    }

    @Test fun storedFallbackChoiceDoesNotBypassTheNativeDecoder() {
        JniUtils.sHaveNativeGestureLib = true
        val result = request("fallback")

        assertEquals("hello", result.getWord(0))
        assertEquals(1, batchRequests.size)
        assertEquals(2, batchRequests.single().mInputPointers.pointerSize)
        assertEquals(91, result.mSequenceNumber)
    }

    @Test fun nativeChoiceUsesTheSameDecoderPath() {
        JniUtils.sHaveNativeGestureLib = true
        assertEquals("hello", request("native").getWord(0))
        assertEquals(1, batchRequests.size)
    }

    @Test fun absentNativeLibraryDoesNotCallTheDictionaryOnlyDecoder() {
        JniUtils.sHaveNativeGestureLib = false
        assertEquals(0, request("fallback").size())
        assertTrue(batchRequests.isEmpty())
        verify(facilitator, never()).getSuggestionResults(
            any(), any(), any(), any(), anyInt(), anyInt(),
        )
    }

    private fun request(legacyMethod: String): SuggestedWords {
        val pointers = InputPointers(2).apply {
            addPointer(20, 20, 0, 0)
            addPointer(40, 20, 0, 30)
        }
        return Suggest(facilitator).getSuggestedWords(
            WordComposer().apply { setBatchInputPointers(pointers) },
            NgramContext.EMPTY_PREV_WORDS_INFO,
            keyboard,
            SettingsValuesForSuggestion(false, false, legacyMethod),
            false,
            SuggestedWords.INPUT_STYLE_TAIL_BATCH,
            91,
        )
    }
}
