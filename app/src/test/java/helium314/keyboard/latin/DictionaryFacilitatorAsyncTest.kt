package helium314.keyboard.latin

import android.content.Context
import com.android.inputmethod.keyboard.ProximityInfo
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.utils.SuggestionResults
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DictionaryFacilitatorAsyncTest {
    @Test
    fun failedMainDictionaryLoadReleasesWaiters() {
        val facilitator = DictionaryFacilitatorImpl()
        val loadMethod = DictionaryFacilitatorImpl::class.java.declaredMethods.single {
            it.name == "asyncReloadUninitializedMainDictionaries"
        }.apply { isAccessible = true }
        val latchField = DictionaryFacilitatorImpl::class.java
            .getDeclaredField("mLatchForWaitingLoadingMainDictionaries")
            .apply { isAccessible = true }

        Mockito.mockStatic(Settings::class.java).use { settings ->
            settings.`when`<SettingsValues> { Settings.getValues() }
                .thenThrow(IllegalStateException("forced dictionary-load failure"))

            loadMethod.invoke(
                facilitator,
                Mockito.mock(Context::class.java),
                listOf(Locale.ENGLISH),
                null,
            )

            val latch = latchField.get(facilitator) as CountDownLatch
            assertTrue(latch.await(1, TimeUnit.SECONDS), "failed dictionary load must release waiters")
        }
    }

    @Test
    fun failedSecondaryDictionarySuggestionDoesNotBlockPrimaryResults() {
        Robolectric.setupService(LatinIME::class.java)
        val facilitator = DictionaryFacilitatorImpl()
        val primaryDictionary = Mockito.mock(Dictionary::class.java)
        val secondaryDictionary = Mockito.mock(Dictionary::class.java)
        stubSuggestions(primaryDictionary, arrayListOf())
        stubSuggestions(secondaryDictionary, IllegalStateException("forced secondary suggestion failure"))

        val dictionaryGroupClass = Class.forName("helium314.keyboard.latin.DictionaryGroup")
        val constructor = dictionaryGroupClass.declaredConstructors.first { it.parameterCount == 4 }
            .apply { isAccessible = true }
        val primaryGroup = constructor.newInstance(Locale.ENGLISH, primaryDictionary, emptyMap<String, Any>(), null)
        val secondaryGroup = constructor.newInstance(Locale.FRENCH, secondaryDictionary, emptyMap<String, Any>(), null)
        DictionaryFacilitatorImpl::class.java.getDeclaredField("dictionaryGroups")
            .apply { isAccessible = true }
            .set(facilitator, listOf(primaryGroup, secondaryGroup))

        val proximityInfo = Mockito.mock(ProximityInfo::class.java)
        Mockito.`when`(proximityInfo.nativeProximityInfo).thenReturn(0L)
        val keyboard = Mockito.mock(Keyboard::class.java)
        Mockito.`when`(keyboard.proximityInfo).thenReturn(proximityInfo)
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<SuggestionResults> {
            facilitator.getSuggestionResults(
                ComposedData(InputPointers(1), false, "test"),
                NgramContext.EMPTY_PREV_WORDS_INFO,
                keyboard,
                SettingsValuesForSuggestion(false, false, "fallback"),
                Suggest.SESSION_ID_TYPING,
                SuggestedWords.INPUT_STYLE_TYPING,
            )
        }

        try {
            assertEquals(0, future.get(1, TimeUnit.SECONDS).size)
        } finally {
            future.cancel(true)
            executor.shutdownNow()
        }
    }

    private fun stubSuggestions(
        dictionary: Dictionary,
        result: ArrayList<SuggestedWordInfo>,
    ) {
        Mockito.`when`(
            dictionary.getSuggestions(
                Mockito.any(ComposedData::class.java),
                Mockito.any(NgramContext::class.java),
                Mockito.anyLong(),
                Mockito.any(SettingsValuesForSuggestion::class.java),
                Mockito.anyInt(),
                Mockito.anyFloat(),
                Mockito.any(FloatArray::class.java),
            ),
        ).thenReturn(result)
    }

    private fun stubSuggestions(
        dictionary: Dictionary,
        failure: RuntimeException,
    ) {
        Mockito.`when`(
            dictionary.getSuggestions(
                Mockito.any(ComposedData::class.java),
                Mockito.any(NgramContext::class.java),
                Mockito.anyLong(),
                Mockito.any(SettingsValuesForSuggestion::class.java),
                Mockito.anyInt(),
                Mockito.anyFloat(),
                Mockito.any(FloatArray::class.java),
            ),
        ).thenThrow(failure)
    }
}
