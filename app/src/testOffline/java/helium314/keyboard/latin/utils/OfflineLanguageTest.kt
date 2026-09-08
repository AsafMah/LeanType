package helium314.keyboard.latin.utils

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.SettingsWithoutKey
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.Shadows.shadowOf
import org.mockito.Mockito.*
import helium314.keyboard.latin.translation.ITranslationProvider
import helium314.keyboard.latin.translation.TranslationLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [ProofreadHelperOwnershipTest.SwitcherShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class OfflineLanguageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() { context.prefs().edit().clear().commit() }

    @Test fun legacyFrenchSurvivesServiceRecreation() {
        context.prefs().edit().putString(Settings.PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE, "French").commit()
        assertEquals("French", ProofreadService(context).getTargetLanguage())
        assertEquals("French", ProofreadService(context).getTargetLanguage())
        assertEquals("French", context.prefs().getString(Settings.PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE, null))
    }

    @Test fun targetSetterWritesTheUpstreamCodeToBothPreferenceKeys() {
        ProofreadService(context).setTargetLanguage("de")
        assertEquals("de", ProofreadService(context).getTargetLanguage())
        assertEquals("de", context.prefs().getString(SettingsWithoutKey.GEMINI_TARGET_LANGUAGE, null))
        assertEquals("de", context.prefs().getString(Settings.PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE, null))
    }

    @Test fun legacyToolbarCodeWinsOverLegacyOfflineName() {
        context.prefs().edit().putString(SettingsWithoutKey.GEMINI_TARGET_LANGUAGE, "es")
            .putString(Settings.PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE, "French").commit()
        assertEquals("es", ProofreadService(context).getTargetLanguage())
    }

    @Test fun unsetTargetUsesUpstreamEnglishDefaultWithoutWritingPreferences() {
        val before = context.prefs().all.toMap()
        assertEquals("en", ProofreadService(context).getTargetLanguage())
        assertEquals(before, context.prefs().all)
    }

    @Test fun legacyEnglishNameUsesUpstreamCodeAlias() {
        context.prefs().edit().putString(Settings.PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE, "English").commit()
        assertEquals("en", ProofreadService(context).getTargetLanguage())
        assertEquals("English", context.prefs().getString(Settings.PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE, null))
    }

    @Test fun customTargetNameIsStoredWithoutForkSpecificNormalization() {
        ProofreadService(context).setTargetLanguage("Klingon")
        assertEquals("Klingon", ProofreadService(context).getTargetLanguage())
        assertEquals("Klingon", context.prefs().getString(SettingsWithoutKey.GEMINI_TARGET_LANGUAGE, null))
        assertEquals("Klingon", context.prefs().getString(Settings.PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE, null))
    }

    @Test fun legacyFrenchIsPassedAsCodeToActualPluginTranslationPath() {
        val provider = mock(ITranslationProvider::class.java)
        `when`(provider.isAvailable()).thenReturn(true)
        `when`(provider.isModelDownloaded(anyString())).thenReturn(true)
        `when`(provider.translate(anyString(), anyString(), anyString())).thenReturn("bonjour")
        TranslationLoader::class.java.getDeclaredField("activeProvider").apply { isAccessible = true }.set(null, provider)
        context.prefs().edit().putString(Settings.PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE, "French")
            .putString("pref_translation_engine", "plugin").commit()
        val delivered = mutableListOf<String>()
        ProofreadHelper.translateAsync(context, "hello", true, { delivered.add(it) }, { error(it) })
        val job = ProofreadHelper::class.java.getDeclaredField("currentJob").apply { isAccessible = true }.get(null) as Job
        runBlocking { job.join() }
        shadowOf(Looper.getMainLooper()).idle()
        val invocation = mockingDetails(provider).invocations.single { it.method.name == "translate" }
        assertEquals("hello", invocation.arguments[0])
        assertEquals("fr", invocation.arguments[1])
        assertEquals(listOf("bonjour"), delivered)
    }

    @After fun release() {
        ProofreadHelper.cancelCurrentOperation()
        shadowOf(Looper.getMainLooper()).idle()
        TranslationLoader::class.java.getDeclaredField("activeProvider").apply { isAccessible = true }.set(null, null)
    }
}
