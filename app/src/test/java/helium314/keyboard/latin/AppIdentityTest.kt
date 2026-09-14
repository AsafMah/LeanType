// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.app.Application
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.inputmethod.InputMethodManager
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.latin.common.Links
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class AppIdentityTest {
    @Test
    fun packageAndVersionMatchForkIdentity() {
        val flavorSuffix = if (BuildConfig.FLAVOR == "offline") ".offline" else ""
        val debugSuffix = if (BuildConfig.BUILD_TYPE in listOf("debug", "debugNoMinify")) ".debug" else ""
        assertEquals("com.asafmah.leantypedual$flavorSuffix$debugSuffix", BuildConfig.APPLICATION_ID)
        val (major, minor, patch) = BuildConfig.VERSION_NAME.split('.').map { it.toInt() }
        assertEquals(4000 + major * 1000 + minor * 100 + patch * 10, BuildConfig.VERSION_CODE)
        assertEquals(BuildConfig.APPLICATION_ID, RuntimeEnvironment.getApplication().packageName)
    }

    @Test
    @Config(shadows = [ShadowInputMethodManager2::class])
    fun testImeUsesTheActualApplicationId() {
        val context = RuntimeEnvironment.getApplication()
        val ime = context.getSystemService(InputMethodManager::class.java).inputMethodList.single()
        assertEquals(BuildConfig.APPLICATION_ID, ime.packageName)
        assertEquals("helium314.keyboard.latin.LatinIME", ime.serviceName)
    }

    @Test
    fun localizedAppAndServiceNamesRetainForkBranding() {
        val context = RuntimeEnvironment.getApplication()
        val names = listOf(
            R.string.english_ime_name, R.string.ime_name, R.string.ime_settings,
            R.string.spell_checker_service_name, R.string.android_spell_checker_settings
        )
        for (locale in context.assets.locales) {
            val config = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(locale.replace('_', '-')))
            }
            val localized = context.createConfigurationContext(config)
            for (name in names) {
                val label = localized.getString(name)
                assertTrue("$locale: $label", label.contains("LeanTypeDual"))
            }
        }
        val expectedName = if (BuildConfig.BUILD_TYPE in listOf("debug", "debugNoMinify"))
            "LeanTypeDual debug" else "LeanTypeDual"
        assertEquals(expectedName, context.applicationInfo.loadLabel(context.packageManager).toString())
    }

    @Test
    fun componentsRetainNamespaceAndProvidersUseApplicationId() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS
        )
        assertTrue(info.services.orEmpty().any { it.name == "helium314.keyboard.latin.LatinIME" })
        assertTrue(info.services.orEmpty().any { it.name == "helium314.keyboard.latin.spellcheck.AndroidSpellCheckerService" })
        assertTrue(info.providers.orEmpty().any { it.authority == "${context.packageName}.fileprovider" })
        assertTrue(info.providers.orEmpty().any { it.authority == "${context.packageName}.androidx-startup" })
    }

    @Test
    fun appUpdatesUseForkReleasesWithoutChangingVoicePluginReleases() {
        assertEquals("https://api.github.com/repos/AsafMah/LeanType/releases/latest", Links.GITHUB_RELEASES_API)
        assertEquals("https://github.com/AsafMah/LeanType/releases", Links.GITHUB_RELEASES_PAGE)
        assertEquals(
            "https://api.github.com/repos/LeanBitLab/LeanType-Voice-Plugin/releases/latest",
            Links.VOICE_PLUGIN_RELEASES_API
        )
    }
}
