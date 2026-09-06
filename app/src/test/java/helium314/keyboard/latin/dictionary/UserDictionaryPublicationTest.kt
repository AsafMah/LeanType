package helium314.keyboard.latin.dictionary

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.UserDictionary.Words
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.latin.DictionaryFacilitatorImpl
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.Suggest
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.WordComposer
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.function.BiConsumer

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class UserDictionaryPublicationTest {
    private val pending = ArrayDeque<Runnable>()
    private lateinit var provider: Provider
    private lateinit var userDictionary: InMemoryUserDictionary
    private lateinit var facilitator: DictionaryFacilitatorImpl
    private lateinit var group: Any

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
        // Only the controlled dictionary below should observe this test provider.
        ime.dictionaryFacilitator.closeDictionaries()
        val executor = Mockito.mock(ScheduledExecutorService::class.java)
        Mockito.doAnswer {
            pending.add(it.getArgument(0))
            null
        }.`when`(executor).execute(Mockito.any())
        ExecutorUtils.setExecutorServiceForTests(executor)
        provider = Provider()
        ShadowContentResolver.registerProviderInternal(Words.CONTENT_URI.authority, provider)
        userDictionary = InMemoryUserDictionary(RuntimeEnvironment.getApplication())
        facilitator = DictionaryFacilitatorImpl()
        val groupClass = Class.forName("helium314.keyboard.latin.DictionaryGroup")
        group = groupClass.declaredConstructors.first { it.parameterCount == 4 }
            .apply { isAccessible = true }
            .newInstance(Locale.ENGLISH, null, mapOf(Dictionary.TYPE_USER to userDictionary), null)
        DictionaryFacilitatorImpl::class.java.getDeclaredField("dictionaryGroups")
            .apply { isAccessible = true }.set(facilitator, listOf(group))
        DictionaryFacilitatorImpl::class.java
            .getDeclaredMethod("observeDictionaryChanges", List::class.java)
            .apply { isAccessible = true }.invoke(facilitator, listOf(group))
        runAll()
    }

    @After
    fun tearDown() {
        userDictionary.close()
        runAll()
        ExecutorUtils.setExecutorServiceForTests(null)
        JniUtils.sHaveGestureLib = false
        JniUtils.sHaveNativeGestureLib = false
    }

    @Test
    fun providerInsertionIsNotPublicationAndNativeCompletionUnblocksWord() {
        facilitator.blockWord(WORD)
        runAll()
        assertFalse(facilitator.isValidSpellingWord(WORD))
        val revision = facilitator.getDictionaryRevision()
        provider.onInsert = {
            assertTrue(facilitator.isBlacklisted(WORD))
            assertFalse(facilitator.isValidSpellingWord(WORD))
            assertEquals(revision, facilitator.getDictionaryRevision())
        }

        facilitator.addToUserDictionary(WORD)
        assertTrue(pending.isNotEmpty())
        assertEquals(revision, facilitator.getDictionaryRevision())
        runAll()

        assertFalse(facilitator.isBlacklisted(WORD))
        assertTrue(facilitator.isValidSpellingWord(WORD))
        assertTrue(facilitator.getDictionaryRevision() > revision)
        val enumerated = mutableListOf<String>()
        facilitator.forEachMainDictionaryWord { word, _ -> enumerated.add(word) }
        assertEquals(listOf(WORD), enumerated)
    }

    @Test
    fun rejectedProviderInsertKeepsBlacklistAndNativeContentsUnchanged() {
        facilitator.blockWord(WORD)
        runAll()
        assertFalse(facilitator.isValidSpellingWord(WORD))
        val revision = facilitator.getDictionaryRevision()
        provider.reject = true

        facilitator.addToUserDictionary(WORD)
        runAll()

        assertTrue(facilitator.isBlacklisted(WORD))
        assertFalse(facilitator.isValidSpellingWord(WORD))
        assertFalse(userDictionary.isInDictionary(WORD))
        assertEquals(revision, facilitator.getDictionaryRevision())
    }

    @Test
    fun externalProviderChangeInvalidatesNegativeCacheOnlyAfterReload() {
        assertFalse(facilitator.isValidSpellingWord(WORD))
        val revision = facilitator.getDictionaryRevision()
        provider.words.add(WORD)
        RuntimeEnvironment.getApplication().contentResolver.notifyChange(Words.CONTENT_URI, null)

        assertFalse(facilitator.isValidSpellingWord(WORD))
        assertEquals(revision, facilitator.getDictionaryRevision())
        runAll()

        assertTrue(facilitator.isValidSpellingWord(WORD))
        assertTrue(facilitator.getDictionaryRevision() > revision)
    }

    @Test
    fun queuedMutationsCompleteInCallOrderEvenIfExecutorReordersTasks() {
        val completed = mutableListOf<String>()
        userDictionary.addWordToUserDictionary("first") { completed.add("first") }
        userDictionary.addWordToUserDictionary("second") { completed.add("second") }

        while (pending.isNotEmpty()) pending.removeLast().run()

        assertEquals(listOf("first", "second"), completed)
    }

    @Test
    fun rejectedSubmissionDoesNotWedgeLaterAcceptedMutation() {
        val executor = ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD)
        val rejected = Mockito.mock(ScheduledExecutorService::class.java)
        Mockito.doThrow(RejectedExecutionException("closed executor"))
            .`when`(rejected).execute(Mockito.any(Runnable::class.java))
        ExecutorUtils.setExecutorServiceForTests(rejected)
        try {
            org.junit.Assert.assertThrows(RejectedExecutionException::class.java) {
                userDictionary.addWordToUserDictionary("rejected") {}
            }
        } finally {
            ExecutorUtils.setExecutorServiceForTests(executor)
        }
        val completed = mutableListOf<Boolean>()

        userDictionary.addWordToUserDictionary("accepted") { completed.add(it) }
        runAll()

        assertEquals(listOf(true), completed)
        assertTrue(userDictionary.isInDictionary("accepted"))
        assertFalse(userDictionary.isInDictionary("rejected"))
    }

    @Test
    fun acceptedQueuedMutationsDrainEvenIfExecutorClosesDuringFirstCompletion() {
        val executor = ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD)
        val rejected = Mockito.mock(ScheduledExecutorService::class.java)
        Mockito.doThrow(RejectedExecutionException("closed during completion"))
            .`when`(rejected).execute(Mockito.any(Runnable::class.java))
        val completed = mutableListOf<String>()
        userDictionary.addWordToUserDictionary("first") {
            completed.add("first")
            ExecutorUtils.setExecutorServiceForTests(rejected)
        }
        userDictionary.addWordToUserDictionary("second") { completed.add("second") }
        try {
            runAll()
        } finally {
            ExecutorUtils.setExecutorServiceForTests(executor)
        }

        assertEquals(listOf("first", "second"), completed)
    }

    @Test
    fun rejectedReloadCanRetryAfterExecutorRecovers() {
        val executor = ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD)
        val rejected = Mockito.mock(ScheduledExecutorService::class.java)
        Mockito.doThrow(RejectedExecutionException("reload executor closed"))
            .`when`(rejected).execute(Mockito.any(Runnable::class.java))
        ExecutorUtils.setExecutorServiceForTests(rejected)
        try {
            org.junit.Assert.assertThrows(RejectedExecutionException::class.java) {
                userDictionary.markDirty()
                userDictionary.reloadDictionaryIfRequired()
            }
        } finally {
            ExecutorUtils.setExecutorServiceForTests(executor)
        }
        provider.words.add("retry")

        userDictionary.reloadDictionaryIfRequired()
        runAll()

        assertTrue(userDictionary.isInDictionary("retry"))
    }

    @Test
    fun failedCompletionStillDrainsAcceptedMutationsAndPropagatesFailure() {
        val completed = mutableListOf<String>()
        userDictionary.addWordToUserDictionary("first") {
            throw IllegalStateException("completion failed")
        }
        userDictionary.addWordToUserDictionary("second") { completed.add("second") }

        org.junit.Assert.assertThrows(IllegalStateException::class.java) { runAll() }

        assertEquals(listOf("second"), completed)
        userDictionary.addWordToUserDictionary("third") { completed.add("third") }
        runAll()
        assertEquals(listOf("second", "third"), completed)
    }

    @Test
    fun providerChangeDuringReloadIsNotLostAfterOldQuerySnapshot() {
        assertFalse(facilitator.isValidSpellingWord("late"))
        val publishedLate = mutableListOf<Boolean>()
        userDictionary.addDictionaryChangeListener {
            publishedLate.add(userDictionary.isInDictionary("late"))
        }
        provider.words.add("initial")
        provider.onQuery = {
            provider.onQuery = {}
            provider.words.add("late")
            RuntimeEnvironment.getApplication().contentResolver.notifyChange(Words.CONTENT_URI, null)
        }

        RuntimeEnvironment.getApplication().contentResolver.notifyChange(Words.CONTENT_URI, null)
        runAll()

        assertTrue(userDictionary.isInDictionary("late"))
        assertTrue(facilitator.isValidSpellingWord("late"))
        assertTrue(publishedLate.isNotEmpty())
        assertTrue("only the newest loaded snapshot is published", publishedLate.all { it })
    }

    @Test
    fun providerChangeDuringDirectAdditionIsLoadedBeforeCompletion() {
        assertFalse(facilitator.isValidSpellingWord("late"))
        val publishedLate = mutableListOf<Boolean>()
        userDictionary.addDictionaryChangeListener {
            publishedLate.add(userDictionary.isInDictionary("late"))
        }
        provider.onQuery = {
            provider.onQuery = {}
            provider.words.add("late")
            RuntimeEnvironment.getApplication().contentResolver.notifyChange(Words.CONTENT_URI, null)
        }
        var completed = false

        userDictionary.addWordToUserDictionary("promoted") { success ->
            assertTrue(success)
            assertTrue("completion must see the newer provider snapshot", userDictionary.isInDictionary("late"))
            completed = true
        }
        runAll()

        assertTrue(completed)
        assertTrue(facilitator.isValidSpellingWord("late"))
        assertTrue(publishedLate.isNotEmpty())
        assertTrue(publishedLate.all { it })
    }

    @Test
    fun closingDuringDirtyDirectLoadDoesNotReopenOrPublishSuccess() {
        var queries = 0
        val completions = mutableListOf<Boolean>()
        provider.onQuery = {
            queries++
            provider.onQuery = { queries++ }
            provider.words.add("late")
            RuntimeEnvironment.getApplication().contentResolver.notifyChange(Words.CONTENT_URI, null)
            userDictionary.close()
        }

        userDictionary.addWordToUserDictionary("promoted") { completions.add(it) }
        runAll()

        assertEquals("no queued reload may reopen a closing dictionary", 1, queries)
        assertEquals(listOf(false), completions)
    }

    @Test
    fun closingDuringDirtyObserverLoadDoesNotScheduleAnotherRead() {
        var queries = 0
        provider.words.add("initial")
        provider.onQuery = {
            queries++
            provider.onQuery = { queries++ }
            provider.words.add("late")
            RuntimeEnvironment.getApplication().contentResolver.notifyChange(Words.CONTENT_URI, null)
            userDictionary.close()
        }

        RuntimeEnvironment.getApplication().contentResolver.notifyChange(Words.CONTENT_URI, null)
        runAll()

        assertEquals("the dirty follow-up belongs to the closed dictionary", 1, queries)
    }

    @Test
    fun realFacilitatorBlockAndPromotionChangeSuggestRankOutputAfterPublication() {
        val main = Mockito.mock(Dictionary::class.java)
        Mockito.`when`(main.isValidWord("hello")).thenReturn(true)
        Mockito.doAnswer {
            it.getArgument<BiConsumer<String, Int>>(0).accept("hello", 100)
            null
        }.`when`(main).forEachWord(Mockito.any())
        group.javaClass.getDeclaredMethod("setMainDict", Dictionary::class.java)
            .apply { isAccessible = true }.invoke(group, main)
        val keyboard = keyboardFor("helpo")
        val suggest = Suggest(facilitator)
        suggest.buildGestureIndexAsync(keyboard)
        runAll()
        assertEquals("hello", suggestions(suggest, keyboard).getWord(0))
        assertTrue(facilitator.isValidSpellingWord("hello"))
        assertFalse(facilitator.isValidSpellingWord("help"))

        facilitator.blockWord("hello")

        assertFalse(facilitator.isValidSpellingWord("hello"))
        assertEquals("stale index must not return the blocked word", 0, suggestions(suggest, keyboard).size())
        runAll()
        assertEquals(0, suggestions(suggest, keyboard).size())
        provider.onInsert = {
            assertFalse(facilitator.isValidSpellingWord("help"))
            assertEquals("provider insertion is not native publication", 0, suggestions(suggest, keyboard).size())
        }

        facilitator.addToUserDictionary("help")
        assertEquals(0, suggestions(suggest, keyboard).size())
        runAll()
        assertTrue(facilitator.isValidSpellingWord("help"))
        assertEquals("old completed index is rejected before rebuilding", 0, suggestions(suggest, keyboard).size())
        runAll()

        assertEquals("help", suggestions(suggest, keyboard).getWord(0))
    }

    @Test
    fun blockedPersonalWordDisappearsFromActualRanksBeforeNativeRemovalCompletes() {
        facilitator.addToUserDictionary("help")
        runAll()
        val keyboard = keyboardFor("helpo")
        val suggest = Suggest(facilitator)
        suggest.buildGestureIndexAsync(keyboard)
        runAll()
        assertEquals("help", suggestions(suggest, keyboard).getWord(0))
        assertTrue(facilitator.isValidSpellingWord("help"))

        facilitator.blockWord("help")

        assertTrue("native removal remains queued", userDictionary.isInDictionary("help"))
        assertFalse(facilitator.isValidSpellingWord("help"))
        assertEquals(0, suggestions(suggest, keyboard).size())
        runAll()
        assertEquals(0, suggestions(suggest, keyboard).size())
    }

    @Test
    fun realFacilitatorMutationInvalidatesPublicNextWordPredictions() {
        userDictionary.mReturnPredictions = true
        facilitator.addToUserDictionary("hello")
        runAll()
        val keyboard = keyboardFor("helpo")
        val suggest = Suggest(facilitator)
        assertEquals(listOf("hello"), predictions(suggest, keyboard))
        val fallbackPredictions = listOf("is", "are", "the", "to")

        facilitator.blockWord("hello")

        assertEquals(fallbackPredictions, predictions(suggest, keyboard))
        runAll()
        provider.onInsert = {
            assertEquals(fallbackPredictions, predictions(suggest, keyboard))
        }
        facilitator.addToUserDictionary("help")
        assertEquals(fallbackPredictions, predictions(suggest, keyboard))
        runAll()

        assertEquals(listOf("help"), predictions(suggest, keyboard))
    }

    private fun predictions(suggest: Suggest, keyboard: Keyboard): List<String> {
        val result = suggest.getSuggestedWords(
            WordComposer(), NgramContext.EMPTY_PREV_WORDS_INFO, keyboard,
            SettingsValuesForSuggestion(false, false, "fallback"),
            false, SuggestedWords.INPUT_STYLE_TYPING, 1,
        )
        return List(result.size()) { result.getWord(it) }
    }

    private fun suggestions(suggest: Suggest, keyboard: Keyboard): SuggestedWords {
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

    private fun runAll() {
        while (pending.isNotEmpty()) pending.removeFirst().run()
    }

    private class Provider : ContentProvider() {
        val words = mutableSetOf<String>()
        var reject = false
        var onInsert: () -> Unit = {}
        var onQuery: () -> Unit = {}

        override fun onCreate() = true

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            if (reject) return null
            words.add(values!!.getAsString(Words.WORD))
            onInsert()
            return Uri.withAppendedPath(uri, "1")
        }

        override fun query(
            uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?,
        ): Cursor {
            val columns = projection ?: arrayOf(Words.WORD, Words.SHORTCUT, Words.FREQUENCY)
            return MatrixCursor(columns).apply {
                words.forEach { word ->
                    addRow(columns.map {
                        when (it) {
                            Words.WORD -> word
                            Words.FREQUENCY -> 250
                            else -> null
                        }
                    })
                }
            }.also { onQuery() }
        }

        override fun getType(uri: Uri): String? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    private companion object {
        const val WORD = "personalonly"
    }
}
