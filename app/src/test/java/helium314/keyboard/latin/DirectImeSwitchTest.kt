package helium314.keyboard.latin

import android.inputmethodservice.InputMethodService
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
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
import org.robolectric.shadows.ShadowInputMethodManager

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [DirectImeInputMethodManagerShadow::class, DirectImeServiceShadow::class])
class DirectImeSwitchTest {
    private lateinit var latinIME: LatinIME

    @BeforeTest
    fun setUp() {
        DirectImeInputMethodManagerShadow.reset()
        DirectImeServiceShadow.reset()
        latinIME = Robolectric.setupService(LatinIME::class.java)
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
        val external = DirectImeInputMethodManagerShadow.externalIme
        DirectImeInputMethodManagerShadow.enabledImes = listOf(DirectImeInputMethodManagerShadow.thisIme, external)

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
        val external = DirectImeInputMethodManagerShadow.externalIme
        val subtype = DirectImeInputMethodManagerShadow.externalSubtype
        DirectImeInputMethodManagerShadow.enabledImes = listOf(DirectImeInputMethodManagerShadow.thisIme, external)
        DirectImeInputMethodManagerShadow.subtypes[external.id] = listOf(subtype)

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
        DirectImeInputMethodManagerShadow.enabledImes = listOf(thisIme)

        latinIME.prefs().edit().putString(
            Settings.PREF_DIRECT_IME_SWITCH_TARGET,
            "${thisIme.id};${subtype.hashCode()}",
        ).commit()
        latinIME.switchToUserIme()

        assertEquals(subtype, richImm.currentSubtype.rawSubtype)
        assertNull(DirectImeServiceShadow.switchedImeId)
    }
}

@Implements(InputMethodManager::class)
class DirectImeInputMethodManagerShadow : ShadowInputMethodManager() {
    @Implementation
    override fun getInputMethodList(): List<InputMethodInfo> = enabledImes

    @Implementation
    override fun getEnabledInputMethodList(): List<InputMethodInfo> = enabledImes

    @Implementation
    fun getEnabledInputMethodSubtypeList(
        imi: InputMethodInfo?,
        allowsImplicitlySelectedSubtypes: Boolean,
    ): List<InputMethodSubtype> = imi?.let { subtypes[it.id] }.orEmpty()

    companion object {
        val thisIme = InputMethodInfo(BuildConfig.APPLICATION_ID, "helium314.keyboard.latin.LatinIME", "LeanTypeDual", null)
        val externalIme = InputMethodInfo("example.ime", "example.ime.Service", "Example IME", null)
        val thisImeSubtype: InputMethodSubtype = subtype(101, "en-US")
        val externalSubtype: InputMethodSubtype = subtype(202, "fr-FR")
        var enabledImes: List<InputMethodInfo> = listOf(thisIme)
        val subtypes = mutableMapOf<String, List<InputMethodSubtype>>()

        fun reset() {
            enabledImes = listOf(thisIme)
            subtypes.clear()
            subtypes[thisIme.id] = listOf(thisImeSubtype)
        }

        private fun subtype(id: Int, languageTag: String) = InputMethodSubtype.InputMethodSubtypeBuilder()
            .setSubtypeId(id)
            .setLanguageTag(languageTag)
            .setSubtypeLocale(languageTag.replace('-', '_'))
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
