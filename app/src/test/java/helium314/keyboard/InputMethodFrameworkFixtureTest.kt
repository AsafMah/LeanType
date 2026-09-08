// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.content.Context
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35])
class InputMethodFrameworkFixtureTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun fixtureStateDoesNotLeakFromPreviousTest() {
        assertEquals(1, ShadowInputMethodManager2.inputMethods.size)
        assertTrue(ShadowInputMethodManager2.enabledSubtypes.isEmpty())
        assertNull(ShadowInputMethodManager2.switchedImeId)
        assertNull(ShadowInputMethodManager2.switchedSubtype)
        assertFalse(ShadowInputMethodManager2.switchedToNextInputMethod)
    }

    @After
    fun leaveStateForRobolectricToReset() {
        // Both cases dirty the fixture; the next case must start clean without a manual reset.
        ShadowInputMethodManager2.inputMethods = emptyList()
        ShadowInputMethodManager2.enabledSubtypes["fixture"] = emptyList()
        ShadowInputMethodManager2.switchedImeId = "fixture"
        ShadowInputMethodManager2.switchedSubtype = InputMethodSubtype.InputMethodSubtypeBuilder().build()
        ShadowInputMethodManager2.switchedToNextInputMethod = true
    }

    @Test
    fun defaultFixtureSupportsShortcutAndSubtypeQueries() {
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val info = InputMethodInfo(context.packageName, "helium314.keyboard.latin.LatinIME", "Test IME", null)
        // Seed stock Robolectric too, so losing the global shadow reproduces the framework NPE.
        shadowOf(manager).setEnabledInputMethodInfoList(listOf(info))
        assertTrue(manager.enabledInputMethodList.any { it.id == info.id })
        assertEquals(emptyMap<InputMethodInfo, List<InputMethodSubtype>>(), manager.shortcutInputMethodsAndSubtypes)
        assertEquals(emptyList<InputMethodSubtype>(), manager.getEnabledInputMethodSubtypeList(info, true))
    }

    @Test
    fun defaultFixtureAdvertisesThisKeyboard() {
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        assertEquals(listOf(context.packageName), manager.inputMethodList.map { it.packageName })
        assertEquals(manager.inputMethodList, manager.enabledInputMethodList)
    }
}
