package helium314.keyboard.latin.utils

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.screens.createAdvancedSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OfflineLanguageScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun targetLanguagePreferenceShowsLegacyFrenchAfterRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.prefs().edit().clear()
            .putString(Settings.PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE, "French").commit()
        val setting = createAdvancedSettings(context).single { it.key == SettingsWithoutKey.GEMINI_TARGET_LANGUAGE }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { setting.Preference() } }
        compose.onNodeWithText("French (fr)").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("French (fr)").assertExists()
        assertEquals("fr", ProofreadService(context).getTargetLanguage())
        assertEquals("French", context.prefs().getString(Settings.PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE, null))
    }
}
