package helium314.keyboard.latin

import android.util.LruCache
import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.ExpandableBinaryDictionary
import helium314.keyboard.latin.dictionary.UserBinaryDictionary
import helium314.keyboard.latin.utils.ExecutorUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ScheduledExecutorService
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.coroutines.CoroutineContext

@RunWith(RobolectricTestRunner::class)
class DictionaryMutationLifecycleTest {
    @Test
    fun blockWordInvalidatesPositiveSpellingCachesImmediately() {
        val fixture = fixture()
        val readCache = LruCache<String, Boolean>(10)
        val writeCache = LruCache<String, Boolean>(10)
        fixture.facilitator.setValidSpellingWordReadCache(readCache)
        fixture.facilitator.setValidSpellingWordWriteCache(writeCache)
        assertTrue(fixture.facilitator.isValidSpellingWord(WORD))
        writeCache.put(WORD, true)

        fixture.facilitator.blockWord(WORD)

        assertTrue(fixture.facilitator.isBlacklisted(WORD))
        assertFalse(fixture.facilitator.isValidSpellingWord(WORD))
        assertFalse(readCache.get(WORD) == true)
        assertFalse(writeCache.get(WORD) == true)
    }

    @Test
    fun removeWordInvalidatesCachedCapitalizedSpelling() {
        val fixture = fixture()
        assertTrue(fixture.facilitator.isValidSpellingWord(WORD))
        assertTrue(fixture.facilitator.isValidSpellingWord(CAPITALIZED))

        fixture.facilitator.removeWord(WORD)

        assertFalse(fixture.facilitator.isValidSpellingWord(WORD))
        assertFalse(fixture.facilitator.isValidSpellingWord(CAPITALIZED))
    }

    @Test
    fun personalDictionaryCannotBypassBlockWhileRemovalIsPending() {
        val userDictionary = Mockito.mock(ExpandableBinaryDictionary::class.java)
        Mockito.`when`(userDictionary.isInDictionary(WORD)).thenReturn(true)
        Mockito.`when`(userDictionary.isValidWord(WORD)).thenReturn(true)
        val fixture = fixture(userDictionary = userDictionary)
        assertTrue(fixture.facilitator.isValidSpellingWord(WORD))

        fixture.facilitator.blockWord(WORD)

        // The queued native removal deliberately has not run.
        Mockito.verify(userDictionary).removeUnigramEntryDynamically(WORD)
        assertTrue(fixture.facilitator.isBlacklisted(WORD))
        assertFalse(fixture.facilitator.isValidSpellingWord(WORD))
    }

    @Test
    fun reloadBlacklistInvalidatesCachedNegativeSpelling() {
        val fixture = fixture()
        fixture.facilitator.blockWord(WORD)
        assertFalse(fixture.facilitator.isValidSpellingWord(WORD))

        // A group with no blacklist file reloads the empty blacklist synchronously.
        fixture.facilitator.reloadBlacklist()

        assertFalse(fixture.facilitator.isBlacklisted(WORD))
        assertTrue(fixture.facilitator.isValidSpellingWord(WORD))
    }

    @Test
    fun fileBlacklistReloadInvalidatesOnlyAfterContentsArePublished() {
        val fixture = fixture()
        val file = File(RuntimeEnvironment.getApplication().filesDir, "blacklist-mutation.txt")
        fixture.group.javaClass.getDeclaredField("blacklistFile")
            .apply { isAccessible = true }.set(fixture.group, file)
        fixture.group.javaClass.getDeclaredField("scope")
            .apply { isAccessible = true }
            .set(fixture.group, CoroutineScope(SupervisorJob() + fixture.dispatcher))
        assertTrue(fixture.facilitator.isValidSpellingWord(WORD))
        val revision = fixture.facilitator.getDictionaryRevision()
        try {
            file.writeText("$WORD\n")
            fixture.facilitator.reloadBlacklist()
            assertTrue(fixture.facilitator.isValidSpellingWord(WORD))
            assertEquals(revision, fixture.facilitator.getDictionaryRevision())

            fixture.dispatcher.runAll()

            assertFalse(fixture.facilitator.isValidSpellingWord(WORD))
            assertTrue(fixture.facilitator.getDictionaryRevision() > revision)

            file.writeText("")
            fixture.facilitator.reloadBlacklist()
            assertFalse(fixture.facilitator.isValidSpellingWord(WORD))
            fixture.dispatcher.runAll()
            assertTrue(fixture.facilitator.isValidSpellingWord(WORD))
        } finally {
            file.delete()
        }
    }

    @Test
    fun failedPersonalDictionaryInsertionDoesNotUnblockWord() {
        val userDictionary = Mockito.mock(UserBinaryDictionary::class.java)
        var completion: Consumer<Boolean>? = null
        Mockito.doAnswer {
            completion = it.getArgument(1)
            null
        }.`when`(userDictionary).addWordToUserDictionary(Mockito.eq(WORD), Mockito.any())
        val fixture = fixture(userDictionary = userDictionary)
        fixture.facilitator.blockWord(WORD)
        assertFalse(fixture.facilitator.isValidSpellingWord(WORD))

        fixture.facilitator.addToUserDictionary(WORD)
        completion!!.accept(false)

        assertTrue(fixture.facilitator.isBlacklisted(WORD))
        assertFalse(fixture.facilitator.isValidSpellingWord(WORD))
    }

    @Test
    fun successfulPromotionPublishesBeforeUnblockingAndClearsNegativeCache() {
        val words = mutableSetOf<String>()
        val user = Mockito.mock(UserBinaryDictionary::class.java)
        Mockito.`when`(user.isValidWord(WORD)).thenAnswer { WORD in words }
        Mockito.doAnswer {
            val consumer = it.getArgument<BiConsumer<String, Int>>(0)
            words.forEach { word -> consumer.accept(word, 160) }
            null
        }.`when`(user).forEachWord(Mockito.any())
        var completion: Consumer<Boolean>? = null
        Mockito.doAnswer {
            completion = it.getArgument(1)
            null
        }.`when`(user).addWordToUserDictionary(Mockito.eq(WORD), Mockito.any())
        val fixture = fixture(user, mainHasWord = false)
        fixture.facilitator.blockWord(WORD)
        assertFalse(fixture.facilitator.isValidSpellingWord(WORD))
        val revision = fixture.facilitator.dictionaryRevision

        fixture.facilitator.addToUserDictionary(WORD)

        assertTrue(fixture.facilitator.isBlacklisted(WORD))
        assertEquals(revision, fixture.facilitator.dictionaryRevision)
        assertFalse(fixture.facilitator.isValidSpellingWord(WORD))
        words.add(WORD)
        completion!!.accept(true)

        assertFalse(fixture.facilitator.isBlacklisted(WORD))
        assertTrue(fixture.facilitator.isValidSpellingWord(WORD))
        assertTrue(fixture.facilitator.dictionaryRevision > revision)
        val enumerated = mutableListOf<String>()
        fixture.facilitator.forEachMainDictionaryWord { word, _ -> enumerated.add(word) }
        assertEquals(listOf(WORD), enumerated)
    }

    @Test
    fun laterBlockWinsOverPendingPromotionCompletion() {
        val user = Mockito.mock(UserBinaryDictionary::class.java)
        var completion: Consumer<Boolean>? = null
        Mockito.doAnswer {
            completion = it.getArgument(1)
            null
        }.`when`(user).addWordToUserDictionary(Mockito.eq(WORD), Mockito.any())
        val fixture = fixture(user)
        fixture.facilitator.addToUserDictionary(WORD)
        fixture.facilitator.blockWord(WORD)

        completion!!.accept(true)

        assertTrue(fixture.facilitator.isBlacklisted(WORD))
        assertFalse(fixture.facilitator.isValidSpellingWord(WORD))
    }

    @Test
    fun emptyMutationsDoNotInvalidateDictionaryContents() {
        val fixture = fixture()
        assertTrue(fixture.facilitator.isValidSpellingWord(WORD))
        val revision = fixture.facilitator.getDictionaryRevision()

        fixture.facilitator.addToUserDictionary("")
        fixture.facilitator.blockWord("")
        fixture.facilitator.removeWord("")

        assertEquals(revision, fixture.facilitator.getDictionaryRevision())
        assertTrue(fixture.facilitator.isValidSpellingWord(WORD))
    }

    @Test
    fun promotionCompletionAfterCloseCannotReviveDictionaryOrRevision() {
        val user = Mockito.mock(UserBinaryDictionary::class.java)
        var completion: Consumer<Boolean>? = null
        Mockito.doAnswer {
            completion = it.getArgument(1)
            null
        }.`when`(user).addWordToUserDictionary(Mockito.eq(WORD), Mockito.any())
        val fixture = fixture(user)
        fixture.facilitator.addToUserDictionary(WORD)
        fixture.facilitator.closeDictionaries()
        val revision = fixture.facilitator.getDictionaryRevision()

        completion!!.accept(true)

        assertFalse(fixture.facilitator.isActive)
        assertFalse(fixture.facilitator.isValidSpellingWord(WORD))
        assertEquals(revision, fixture.facilitator.getDictionaryRevision())
        Mockito.verify(user).removeDictionaryChangeListener(Mockito.any())
    }

    @Test
    fun promotionCompletionAfterLocaleReplacementCannotUnblockOldGroup() {
        val user = Mockito.mock(UserBinaryDictionary::class.java)
        var completion: Consumer<Boolean>? = null
        Mockito.doAnswer {
            completion = it.getArgument(1)
            null
        }.`when`(user).addWordToUserDictionary(Mockito.eq(WORD), Mockito.any())
        val fixture = fixture(user)
        fixture.facilitator.blockWord(WORD)
        fixture.facilitator.addToUserDictionary(WORD)
        val replacement = fixture(locale = Locale.FRENCH)
        DictionaryFacilitatorImpl::class.java.getDeclaredField("dictionaryGroups")
            .apply { isAccessible = true }
            .set(fixture.facilitator, listOf(replacement.group))
        val revision = fixture.facilitator.getDictionaryRevision()

        completion!!.accept(true)

        assertEquals(Locale.FRENCH, fixture.facilitator.mainLocale)
        assertEquals(revision, fixture.facilitator.getDictionaryRevision())
        assertEquals(
            true,
            fixture.group.javaClass.getDeclaredMethod("isBlacklisted", String::class.java)
                .apply { isAccessible = true }.invoke(fixture.group, WORD),
        )
    }

    @Test
    fun nativeReloadCompletionInvalidatesCachedNegativeAndPositiveWords() {
        val user = Mockito.mock(UserBinaryDictionary::class.java)
        var valid = false
        var listener: ExpandableBinaryDictionary.DictionaryChangeListener? = null
        Mockito.`when`(user.isValidWord(WORD)).thenAnswer { valid }
        Mockito.doAnswer {
            listener = it.getArgument(0)
            null
        }.`when`(user).addDictionaryChangeListener(Mockito.any())
        val fixture = fixture(user, mainHasWord = false)
        assertFalse(fixture.facilitator.isValidSpellingWord(WORD))
        val initialRevision = fixture.facilitator.dictionaryRevision

        valid = true
        listener!!.onDictionaryChanged(true)
        assertTrue(fixture.facilitator.isValidSpellingWord(WORD))
        assertTrue(fixture.facilitator.dictionaryRevision > initialRevision)

        valid = false
        listener!!.onDictionaryChanged(true)
        assertFalse(fixture.facilitator.isValidSpellingWord(WORD))
    }

    @Test
    fun routineHistoryLearningInvalidatesSpellingWithoutChangingIndexRevision() {
        val context = RuntimeEnvironment.getApplication()
        val history = object : ExpandableBinaryDictionary(
            context, "lifecycle-history", Locale.ENGLISH, Dictionary.TYPE_USER_HISTORY,
            File(context.filesDir, "lifecycle-history.dict"),
        ) {
            override fun loadInitialContentsLocked() {}
        }
        val native = Mockito.mock(BinaryDictionary::class.java)
        var valid = false
        Mockito.`when`(native.isInDictionary(WORD)).thenAnswer { valid }
        Mockito.`when`(native.updateEntriesForWordWithNgramContext(
            Mockito.any(NgramContext::class.java), Mockito.eq(WORD),
            Mockito.anyBoolean(), Mockito.anyInt(), Mockito.anyInt(),
        )).thenAnswer { valid = !valid; true }
        Mockito.`when`(native.removeUnigramEntry(WORD)).thenAnswer { valid = false; true }
        ExpandableBinaryDictionary::class.java.getDeclaredField("mBinaryDictionary")
            .apply { isAccessible = true }.set(history, native)
        val tasks = ArrayDeque<Runnable>()
        val executor = Mockito.mock(ScheduledExecutorService::class.java)
        Mockito.doAnswer { tasks.add(it.getArgument(0)); null }
            .`when`(executor).execute(Mockito.any(Runnable::class.java))
        ExecutorUtils.setExecutorServiceForTests(executor)
        try {
            val fixture = fixture(mainHasWord = false, historyDictionary = history)
            assertFalse(fixture.facilitator.isValidSpellingWord(WORD))
            val revision = fixture.facilitator.getDictionaryRevision()

            history.updateEntriesForWord(NgramContext.EMPTY_PREV_WORDS_INFO, WORD, true, 1, 1)
            assertFalse(fixture.facilitator.isValidSpellingWord(WORD))
            while (tasks.isNotEmpty()) tasks.removeFirst().run()

            assertTrue(fixture.facilitator.isValidSpellingWord(WORD))
            assertEquals(revision, fixture.facilitator.getDictionaryRevision())

            history.updateEntriesForWord(NgramContext.EMPTY_PREV_WORDS_INFO, WORD, true, 1, 2)
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
            assertFalse(fixture.facilitator.isValidSpellingWord(WORD))
            assertEquals(revision, fixture.facilitator.getDictionaryRevision())

            history.removeUnigramEntryDynamically(WORD)
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
            assertTrue(fixture.facilitator.getDictionaryRevision() > revision)
        } finally {
            ExecutorUtils.setExecutorServiceForTests(null)
        }
    }

    @Test
    fun inFlightSpellingLookupCannotRepopulateCacheAfterReload() {
        val user = Mockito.mock(UserBinaryDictionary::class.java)
        var valid = false
        var listener: ExpandableBinaryDictionary.DictionaryChangeListener? = null
        Mockito.doAnswer {
            listener = it.getArgument(0)
            null
        }.`when`(user).addDictionaryChangeListener(Mockito.any())
        Mockito.`when`(user.isValidWord(WORD)).thenAnswer {
            val result = valid
            if (!valid) {
                valid = true
                listener!!.onDictionaryChanged(true)
            }
            result
        }
        val fixture = fixture(user, mainHasWord = false)

        fixture.facilitator.isValidSpellingWord(WORD)

        assertTrue(fixture.facilitator.isValidSpellingWord(WORD))
    }

    @Test
    fun mainDictionaryReplacementInvalidatesCachedSpellingAndRevision() {
        val fixture = fixture()
        assertTrue(fixture.facilitator.isValidSpellingWord(WORD))
        val initialRevision = fixture.facilitator.dictionaryRevision
        fixture.group.javaClass.getDeclaredMethod("setMainDict", Dictionary::class.java)
            .apply { isAccessible = true }
            .invoke(fixture.group, Mockito.mock(Dictionary::class.java))

        assertFalse(fixture.facilitator.isValidSpellingWord(WORD))
        assertTrue(fixture.facilitator.dictionaryRevision > initialRevision)
    }

    @Test
    fun closingDictionariesInvalidatesCachedSpelling() {
        val fixture = fixture()
        assertTrue(fixture.facilitator.isValidSpellingWord(WORD))

        fixture.facilitator.closeDictionaries()

        assertFalse(fixture.facilitator.isValidSpellingWord(WORD))
    }

    private fun fixture(
        userDictionary: ExpandableBinaryDictionary? = null,
        mainHasWord: Boolean = true,
        locale: Locale = Locale.ENGLISH,
        historyDictionary: ExpandableBinaryDictionary? = null,
    ): Fixture {
        val facilitator = DictionaryFacilitatorImpl()
        val main = Mockito.mock(Dictionary::class.java)
        Mockito.`when`(main.isValidWord(WORD)).thenReturn(mainHasWord)
        Mockito.`when`(main.isValidWord(CAPITALIZED)).thenReturn(mainHasWord)
        val groupClass = Class.forName("helium314.keyboard.latin.DictionaryGroup")
        val group = groupClass.declaredConstructors.first { it.parameterCount == 4 }
            .apply { isAccessible = true }
            .newInstance(
                locale, main,
                buildMap {
                    if (userDictionary != null) put(Dictionary.TYPE_USER, userDictionary)
                    if (historyDictionary != null) put(Dictionary.TYPE_USER_HISTORY, historyDictionary)
                },
                null,
            )
        DictionaryFacilitatorImpl::class.java.getDeclaredField("dictionaryGroups")
            .apply { isAccessible = true }
            .set(facilitator, listOf(group))
        DictionaryFacilitatorImpl::class.java
            .getDeclaredMethod("observeDictionaryChanges", List::class.java)
            .apply { isAccessible = true }
            .invoke(facilitator, listOf(group))
        val dispatcher = QueueDispatcher()
        DictionaryFacilitatorImpl::class.java.getDeclaredField("scope")
            .apply { isAccessible = true }
            .set(facilitator, CoroutineScope(SupervisorJob() + dispatcher))
        return Fixture(facilitator, dispatcher, group)
    }

    private data class Fixture(
        val facilitator: DictionaryFacilitatorImpl,
        val dispatcher: QueueDispatcher,
        val group: Any,
    )

    private class QueueDispatcher : CoroutineDispatcher() {
        private val pending = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            pending.add(block)
        }

        fun runAll() {
            while (pending.isNotEmpty()) pending.removeFirst().run()
        }
    }

    private companion object {
        const val WORD = "lifecycle"
        const val CAPITALIZED = "Lifecycle"
    }
}
