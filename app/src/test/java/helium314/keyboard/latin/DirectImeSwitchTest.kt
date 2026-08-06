package helium314.keyboard.latin

import android.inputmethodservice.InputMethodService
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodSubtype
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, DirectImeServiceShadow::class])
class DirectImeSwitchTest {
    private lateinit var latinIME: LatinIME

    @BeforeTest
    fun setUp() {
        ShadowInputMethodManager2.reset()
        DirectImeServiceShadow.reset()
        latinIME = Robolectric.setupService(LatinIME::class.java)
    }

    @AfterTest
    fun tearDown() {
        ShadowInputMethodManager2.reset()
        DirectImeServiceShadow.reset()
    }

    @Test
    fun emptyAndMissingTargetsAreNoOps() {
        latinIME.prefs().edit().putString(Settings.PREF_DIRECT_IME_SWITCH_TARGET, "").commit()
        latinIME.switchToUserIme()
        assertNull(DirectImeServiceShadow.switchedImeId)

        latinIME.prefs().edit().putString(Settings.PREF_DIRECT_IME_SWITCH_TARGET, "missing/IME").commit()
        latinIME.switchToUserIme()
        assertNull(DirectImeServiceShadow.switchedImeId)
    }

    @Test
    fun enabledExternalImeWithoutOrWithInvalidSubtypeUsesImeFallback() {
        val external = externalIme
        ShadowInputMethodManager2.inputMethods = listOf(ShadowInputMethodManager2.inputMethods.first(), external)

        latinIME.prefs().edit().putString(Settings.PREF_DIRECT_IME_SWITCH_TARGET, external.id).commit()
        latinIME.switchToUserIme()
        assertEquals(external.id, DirectImeServiceShadow.switchedImeId)
        assertNull(DirectImeServiceShadow.switchedSubtype)

        DirectImeServiceShadow.reset()
        latinIME.prefs().edit().putString(Settings.PREF_DIRECT_IME_SWITCH_TARGET, "${external.id};123456").commit()
        latinIME.switchToUserIme()
        assertEquals(external.id, DirectImeServiceShadow.switchedImeId)
        assertNull(DirectImeServiceShadow.switchedSubtype)
    }

    @Test
    fun enabledExternalImeWithValidSubtypeSwitchesImeAndSubtype() {
        val external = externalIme
        val subtype = externalSubtype
        ShadowInputMethodManager2.inputMethods = listOf(ShadowInputMethodManager2.inputMethods.first(), external)
        ShadowInputMethodManager2.enabledSubtypes[external.id] = listOf(subtype)

        latinIME.prefs().edit().putString(
            Settings.PREF_DIRECT_IME_SWITCH_TARGET,
            "${external.id};${subtype.hashCode()}",
        ).commit()
        latinIME.switchToUserIme()

        assertEquals(external.id, DirectImeServiceShadow.switchedImeId)
        assertEquals(subtype, DirectImeServiceShadow.switchedSubtype)
    }

    @Test
    fun sameImeWithValidSubtypeSelectsSubtypeInternally() {
        val richImm = RichInputMethodManager.getInstance()
        val thisIme = richImm.inputMethodInfoOfThisIme
        val subtype = helium314.keyboard.latin.utils.SubtypeSettings.getEnabledSubtypes(true).first()
        ShadowInputMethodManager2.inputMethods = listOf(thisIme)

        latinIME.prefs().edit().putString(
            Settings.PREF_DIRECT_IME_SWITCH_TARGET,
            "${thisIme.id};${subtype.hashCode()}",
        ).commit()
        latinIME.switchToUserIme()

        assertEquals(subtype, richImm.currentSubtype.rawSubtype)
        assertNull(DirectImeServiceShadow.switchedImeId)
    }
    companion object {
        val externalIme = InputMethodInfo("example.ime", "example.ime.Service", "Example IME", null)
        val externalSubtype: InputMethodSubtype = InputMethodSubtype.InputMethodSubtypeBuilder()
            .setSubtypeId(202)
            .setLanguageTag("fr-FR")
            .setSubtypeLocale("fr_FR")
            .setSubtypeMode("keyboard")
            .build()
    }
}

@Implements(InputMethodService::class)
class DirectImeServiceShadow {
    @Implementation
    fun getCurrentInputEditorInfo() = android.view.inputmethod.EditorInfo()

    @Implementation
    fun switchInputMethod(id: String) {
        switchedImeId = id
        switchedSubtype = null
    }

    @Implementation
    fun switchInputMethod(id: String, subtype: InputMethodSubtype) {
        switchedImeId = id
        switchedSubtype = subtype
    }

    companion object {
        var switchedImeId: String? = null
        var switchedSubtype: InputMethodSubtype? = null

        fun reset() {
            switchedImeId = null
            switchedSubtype = null
        }
    }
}
