package helium314.keyboard.latin.gesture

import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.latin.DictionaryFacilitator
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.Suggest
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.WordComposer
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.SuggestionResults
import helium314.keyboard.latin.utils.prefs
import java.util.function.BiConsumer
import java.util.ArrayDeque
import java.util.concurrent.ScheduledExecutorService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class SwipeGestureEngineTest {
    private lateinit var latinIME: LatinIME

    @BeforeTest
    fun setUp() {
        latinIME = Robolectric.setupService(LatinIME::class.java)
    }

    @AfterTest
    fun tearDown() {
        ExecutorUtils.setExecutorServiceForTests(null)
        JniUtils.sHaveGestureLib = false
        JniUtils.sHaveNativeGestureLib = false
    }

    @Test
    fun fallbackOutputUsesCanonicalLowercaseBeforeSuggestPresentationCasing() {
        val keyboard = keyboardFor("helo")
        val facilitator = Mockito.mock(DictionaryFacilitator::class.java)
        Mockito.`when`(facilitator.isBlacklisted(Mockito.anyString())).thenReturn(false)
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val consumer = invocation.arguments[0] as BiConsumer<String, Int>
            consumer.accept("Hello", 100)
            null
        }.`when`(facilitator).forEachMainDictionaryWord(Mockito.any())

        val index = SwipeGestureEngine.buildIndex(facilitator, keyboard)
        val pointers = InputPointers(4).apply {
            addPointer(50, 50, 0, 0)
            addPointer(150, 50, 0, 10)
            addPointer(250, 50, 0, 20)
            addPointer(350, 50, 0, 30)
        }

        val result = SwipeGestureEngine.rankByIndex(index, pointers, keyboard, 1, emptySet())

        assertEquals("hello", result.iterator().next().mWord)
    }

    @Test
    fun fallbackSuggestBuildsIndexAndReturnsCandidateWithoutNativeLibrary() {
        JniUtils.sHaveGestureLib = true
        JniUtils.sHaveNativeGestureLib = false
        latinIME.prefs().edit()
            .putBoolean(Settings.PREF_GESTURE_INPUT, true)
            .putString(Settings.PREF_GESTURE_METHOD, "fallback")
            .commit()
        assertTrue(Settings.getValues().mGestureInputEnabled)

        val keyboard = keyboardFor("helo")
        val facilitator = Mockito.mock(DictionaryFacilitator::class.java)
        Mockito.`when`(facilitator.mainLocale).thenReturn(java.util.Locale.ENGLISH)
        Mockito.`when`(facilitator.isBlacklisted(Mockito.anyString())).thenReturn(false)
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val consumer = invocation.arguments[0] as BiConsumer<String, Int>
            consumer.accept("Hello", 100)
            null
        }.`when`(facilitator).forEachMainDictionaryWord(Mockito.any())
        Mockito.`when`(facilitator.getSuggestionResults(
            Mockito.any(ComposedData::class.java),
            Mockito.any(NgramContext::class.java),
            Mockito.any(Keyboard::class.java),
            Mockito.any(SettingsValuesForSuggestion::class.java),
            Mockito.anyInt(),
            Mockito.anyInt(),
        )).thenReturn(SuggestionResults(1, false, false))

        val pointers = InputPointers(4).apply {
            addPointer(50, 50, 0, 0)
            addPointer(150, 50, 0, 10)
            addPointer(250, 50, 0, 20)
            addPointer(350, 50, 0, 30)
        }
        val composer = WordComposer().apply { setBatchInputPointers(pointers) }
        val suggest = Suggest(facilitator)
        val settings = SettingsValuesForSuggestion(false, false, "fallback")
        val tasks = ArrayDeque<Runnable>()
        val executor = Mockito.mock(ScheduledExecutorService::class.java)
        Mockito.doAnswer { tasks.addLast(it.getArgument(0)); null }
            .`when`(executor).execute(Mockito.any(Runnable::class.java))
        ExecutorUtils.setExecutorServiceForTests(executor)

        suggest.getSuggestedWords(
            composer, NgramContext.EMPTY_PREV_WORDS_INFO, keyboard, settings,
            false, SuggestedWords.INPUT_STYLE_TAIL_BATCH, 1,
        )

        assertEquals(1, tasks.size)
        tasks.removeFirst().run()

        val result = suggest.getSuggestedWords(
            composer, NgramContext.EMPTY_PREV_WORDS_INFO, keyboard, settings,
            false, SuggestedWords.INPUT_STYLE_TAIL_BATCH, 2,
        )
        assertEquals("hello", result.getWord(0))
    }

    private fun keyboardFor(letters: String): Keyboard {
        val params = KeyboardParams().apply {
            mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardId.ELEMENT_ALPHABET)
            mOccupiedWidth = letters.length * 100
            mOccupiedHeight = 100
            mBaseWidth = mOccupiedWidth
            mBaseHeight = mOccupiedHeight
            mMostCommonKeyWidth = 100
            mMostCommonKeyHeight = 100
            GRID_WIDTH = letters.length
            GRID_HEIGHT = 1
        }
        letters.forEachIndexed { index, letter ->
            params.onAddKey(Key(
                letter.toString(), null, letter.code, null, null,
                0, Key.BACKGROUND_TYPE_NORMAL,
                index * 100, 0, 100, 100, 0, 0,
            ))
        }
        return Keyboard(params)
    }
}
