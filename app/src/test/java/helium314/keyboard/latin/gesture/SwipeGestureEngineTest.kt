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
import helium314.keyboard.latin.common.InputPointers
import java.util.function.BiConsumer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class SwipeGestureEngineTest {
    @BeforeTest
    fun setUp() {
        Robolectric.setupService(LatinIME::class.java)
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
