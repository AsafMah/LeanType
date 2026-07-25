package helium314.keyboard.latin

import android.content.Context
import android.content.res.AssetManager
import androidx.core.content.edit
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.prefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AppUpgradeTest {

    private lateinit var context: Context
    private lateinit var mockAssets: AssetManager

    @Before
    fun setUp() {
        // Create a spy context around Robolectric Application to allow stubbing context.assets
        val baseContext = RuntimeEnvironment.getApplication()
        context = spy(baseContext)
        mockAssets = mock(AssetManager::class.java)
        doReturn(mockAssets).`when`(context).assets

        val prefs = baseContext.prefs()
        prefs.edit {
            clear()
            putInt(Settings.PREF_VERSION_CODE, 1) // Force upgrade check
        }

        // Clean up any pre-existing cache files
        val cacheDir = File(DictionaryInfoUtils.getWordListCacheDirectory(baseContext))
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun testCheckVersionUpgradePreservesDownloadedAndDeletesAssets() {
        // Stub mock assets to simulate that 'bg' has a main dictionary asset
        doReturn(arrayOf("main_bg.dict")).`when`(mockAssets).list("dicts")
        val mockStream = java.io.ByteArrayInputStream(ByteArray(0))
        doReturn(mockStream).`when`(mockAssets).open("dicts/main_bg.dict")

        // Setup cache directories
        // Locale 'bg' exists in assets (main_bg.dict)
        val bgDir = File(DictionaryInfoUtils.getCacheDirectoryForLocale(java.util.Locale.forLanguageTag("bg"), context)!!)
        bgDir.mkdirs()
        val bgMain = File(bgDir, "main.dict").apply { createNewFile() }
        val bgUser = File(bgDir, "bg_user.dict").apply { createNewFile() }
        val bgEmoji = File(bgDir, "emoji_bg.dict").apply { createNewFile() }

        // Locale 'eo' does NOT exist in assets
        val eoDir = File(DictionaryInfoUtils.getCacheDirectoryForLocale(java.util.Locale.forLanguageTag("eo"), context)!!)
        eoDir.mkdirs()
        val eoMain = File(eoDir, "main.dict").apply { createNewFile() }
        val eoUser = File(eoDir, "eo_user.dict").apply { createNewFile() }

        // Setup preferences for downloads
        val prefs = context.prefs()
        prefs.edit {
            putString("pref_dict_download_link_main_eo", "https://example.com/main_eo.dict")
            putString("pref_dict_download_link_emoji_bg", "https://example.com/emoji_bg.dict")
        }

        // Run checkVersionUpgrade
        AppUpgrade.checkVersionUpgrade(context)

        // Verify:
        // 1. bg/main.dict is asset-backed and has no download link preference -> Should be DELETED
        assertFalse("bg/main.dict should be deleted", bgMain.exists())

        // 2. bg/bg_user.dict ends with USER_DICTIONARY_SUFFIX -> Should NOT be deleted
        assertTrue("bg/bg_user.dict should not be deleted", bgUser.exists())

        // 3. bg/emoji_bg.dict has a download link preference -> Should NOT be deleted
        assertTrue("bg/emoji_bg.dict should not be deleted", bgEmoji.exists())

        // 4. eo/main.dict is not asset-backed and has download link preference -> Should NOT be deleted
        assertTrue("eo/main.dict should not be deleted", eoMain.exists())

        // 5. eo/eo_user.dict ends with USER_DICTIONARY_SUFFIX -> Should NOT be deleted
        assertTrue("eo/eo_user.dict should not be deleted", eoUser.exists())
    }

    @Test
    fun testExtractAssetsDictionaryUsesApkAssetPath() {
        val assetBytes = "dictionary".toByteArray()
        doReturn(java.io.ByteArrayInputStream(assetBytes)).`when`(mockAssets).open("dicts/main_bg.dict")

        val extracted = DictionaryInfoUtils.extractAssetsDictionary(
            "main_bg.dict",
            java.util.Locale.forLanguageTag("bg"),
            context,
        )

        assertTrue("asset dictionary should be extracted", extracted?.readBytes()?.contentEquals(assetBytes) == true)
    }

    @Test
    fun testCheckVersionUpgradePreservesManuallyImportedAssets() {
        // Stub mock assets to simulate that 'bg' has a main dictionary asset
        doReturn(arrayOf("main_bg.dict")).`when`(mockAssets).list("dicts")
        // The asset has length 10
        val mockStream = java.io.ByteArrayInputStream(ByteArray(10))
        doReturn(mockStream).`when`(mockAssets).open("dicts/main_bg.dict")

        val bgDir = File(DictionaryInfoUtils.getCacheDirectoryForLocale(java.util.Locale.forLanguageTag("bg"), context)!!)
        bgDir.mkdirs()
        // File has different length (20 vs 10)
        val bgMain = File(bgDir, "main.dict").apply {
            createNewFile()
            writeBytes(ByteArray(20))
        }

        // Run checkVersionUpgrade
        AppUpgrade.checkVersionUpgrade(context)

        // Verify:
        // bg/main.dict should NOT be deleted because it has a different size
        assertTrue("bg/main.dict should not be deleted since size is different", bgMain.exists())
    }
}
