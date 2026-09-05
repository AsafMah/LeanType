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
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.SuggestionResults
import helium314.keyboard.latin.utils.prefs
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.function.BiConsumer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class GestureIndexLifetimeTest {
    private val tasks = ArrayDeque<Runnable>()
    private lateinit var facilitator: DictionaryFacilitator
    private lateinit var suggest: Suggest
    private lateinit var keyboard: Keyboard
    private var enumeration: (BiConsumer<String, Int>) -> Unit = { it.accept("help", 100) }
    private var revision = 0L

    @Before
    fun setUp() {
        val ime = Robolectric.setupService(LatinIME::class.java)
        JniUtils.sHaveGestureLib = true
        JniUtils.sHaveNativeGestureLib = false
        ime.prefs().edit().putBoolean(Settings.PREF_GESTURE_INPUT, true).commit()
        val values = Settings.getValues()
        Settings.getInstance().loadSettings(
            ime, values.mLocale, values.mInputAttributes, values.mCurrentKeyboardScript,
        )
        assertTrue(Settings.getValues().mGestureInputEnabled)
        val executor = Mockito.mock(ScheduledExecutorService::class.java)
        Mockito.doAnswer { tasks.addLast(it.getArgument(0)); null }
            .`when`(executor).execute(Mockito.any(Runnable::class.java))
        ExecutorUtils.setExecutorServiceForTests(executor)
        facilitator = Mockito.mock(DictionaryFacilitator::class.java)
        Mockito.`when`(facilitator.mainLocale).thenReturn(Locale.ENGLISH)
        Mockito.`when`(facilitator.dictionaryRevision).thenAnswer { revision }
        Mockito.doAnswer {
            enumeration(it.getArgument(0))
            null
        }.`when`(facilitator).forEachMainDictionaryWord(Mockito.any())
        Mockito.`when`(facilitator.getSuggestionResults(
            Mockito.any(ComposedData::class.java), Mockito.any(NgramContext::class.java),
            Mockito.any(Keyboard::class.java), Mockito.any(SettingsValuesForSuggestion::class.java),
            Mockito.anyInt(), Mockito.anyInt(),
        )).thenAnswer { SuggestionResults(1, false, false) }
        keyboard = keyboardFor("helpo")
        suggest = Suggest(facilitator)
    }

    @After
    fun tearDown() {
        ExecutorUtils.setExecutorServiceForTests(null)
        JniUtils.sHaveGestureLib = false
        JniUtils.sHaveNativeGestureLib = false
    }

    @Test
    fun clearDuringBuildDoesNotAllowOldSuccessToOverwriteNewSameLayoutIndex() {
        enumeration = { old ->
            old.accept("hello", 100)
            suggest.clearNextWordSuggestionsCache()
            enumeration = { it.accept("help", 100) }
            suggest.buildGestureIndexAsync(keyboard)
            tasks.removeFirst().run()
        }
        suggest.buildGestureIndexAsync(keyboard)
        tasks.removeFirst().run()

        assertEquals("help", suggestions().getWord(0))
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun oldFailureDoesNotEraseNewSameLayoutIndex() {
        enumeration = {
            suggest.clearNextWordSuggestionsCache()
            enumeration = { it.accept("help", 100) }
            suggest.buildGestureIndexAsync(keyboard)
            tasks.removeFirst().run()
            throw IllegalStateException("old build failed after replacement completed")
        }
        suggest.buildGestureIndexAsync(keyboard)
        tasks.removeFirst().run()

        assertEquals("help", suggestions().getWord(0))
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun oldFinallyCannotReleaseNewSameLayoutBuildOwner() {
        enumeration = {
            suggest.clearNextWordSuggestionsCache()
            enumeration = { it.accept("help", 100) }
            suggest.buildGestureIndexAsync(keyboard)
            throw IllegalStateException("old build failed while replacement is queued")
        }
        suggest.buildGestureIndexAsync(keyboard)
        tasks.removeFirst().run()
        assertEquals(1, tasks.size)

        suggest.buildGestureIndexAsync(keyboard)
        assertEquals("only the replacement request may own this layout", 1, tasks.size)
        tasks.removeFirst().run()
        assertEquals("help", suggestions().getWord(0))
    }

    @Test
    fun clearingCompletedIndexRebuildsUnchangedLayout() {
        suggest.buildGestureIndexAsync(keyboard)
        tasks.removeFirst().run()
        assertEquals("help", suggestions().getWord(0))
        suggest.clearNextWordSuggestionsCache()
        enumeration = { it.accept("hello", 100) }
        suggest.buildGestureIndexAsync(keyboard)
        tasks.removeFirst().run()
        assertEquals("hello", suggestions().getWord(0))
    }

    @Test
    fun dictionaryRevisionRejectsCompletedIndexBeforeRankingAndRebuildsSameLayout() {
        enumeration = { it.accept("hello", 100) }
        suggest.buildGestureIndexAsync(keyboard)
        tasks.removeFirst().run()
        assertEquals("hello", suggestions().getWord(0))

        revision++
        enumeration = { it.accept("help", 100) }
        assertEquals(0, suggestions().size())
        assertEquals(1, tasks.size)
        tasks.removeFirst().run()
        assertEquals("help", suggestions().getWord(0))
    }

    @Test
    fun dictionaryRevisionSupersedesInFlightBuildWithoutExplicitClear() {
        enumeration = { old ->
            old.accept("hello", 100)
            revision++
            enumeration = { it.accept("help", 100) }
            suggest.buildGestureIndexAsync(keyboard)
            tasks.removeFirst().run()
        }
        suggest.buildGestureIndexAsync(keyboard)
        tasks.removeFirst().run()
        assertEquals("help", suggestions().getWord(0))
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun shutdownRejectionDoesNotLeaveLayoutPermanentlyMarkedBuilding() {
        val rejected = Mockito.mock(ScheduledExecutorService::class.java)
        Mockito.doThrow(RejectedExecutionException("executor shut down"))
            .`when`(rejected).execute(Mockito.any(Runnable::class.java))
        val executor = ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD)
        ExecutorUtils.setExecutorServiceForTests(rejected)
        suggest.buildGestureIndexAsync(keyboard)
        ExecutorUtils.setExecutorServiceForTests(executor)
        suggest.buildGestureIndexAsync(keyboard)
        assertEquals(1, tasks.size)
        tasks.removeFirst().run()
        assertEquals("help", suggestions().getWord(0))
    }

    private fun suggestions(): SuggestedWords {
        val pointers = InputPointers(4).apply {
            addPointer(50, 50, 0, 0)
            addPointer(150, 50, 0, 10)
            addPointer(250, 50, 0, 20)
            addPointer(350, 50, 0, 30)
        }
        return suggest.getSuggestedWords(
            WordComposer().apply { setBatchInputPointers(pointers) },
            NgramContext.EMPTY_PREV_WORDS_INFO, keyboard,
            SettingsValuesForSuggestion(false, false, "fallback"),
            false, SuggestedWords.INPUT_STYLE_TAIL_BATCH, 1,
        )
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
                letter.toString(), null, letter.code, null, null, 0, Key.BACKGROUND_TYPE_NORMAL,
                index * 100, 0, 100, 100, 0, 0,
            ))
        }
        return Keyboard(params)
    }
}
