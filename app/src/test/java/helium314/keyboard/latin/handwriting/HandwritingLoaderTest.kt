package helium314.keyboard.latin.handwriting

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class HandwritingLoaderTest {

    private lateinit var context: Context
    private lateinit var testApk: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testApk = File(context.cacheDir, "test_plugin.apk")
        testApk.writeText("dummy content for handwriting plugin")
    }

    @After
    fun tearDown() {
        testApk.delete()
        HandwritingLoader.removePlugin(context)
    }

    @Test
    fun testImportCleanupOnInvalidApk() {
        // Initially no plugin
        assertFalse(HandwritingLoader.hasPlugin(context))

        // Import invalid apk
        val uri = Uri.fromFile(testApk)
        val result = HandwritingLoader.importPlugin(context, uri)
        assertFalse(result) // Must fail because dummy text is not a valid DEX/APK with the class

        // Verify the file was cleaned up on failure
        val apkFile = File(context.filesDir, "handwriting_plugin.apk")
        assertFalse(apkFile.exists())
    }

    @Test
    fun testGetEffectiveLanguageDefaultFallback() {
        // Reset pref
        HandwritingLoader.setHandwritingLanguage(context, HandwritingLoader.LANG_FOLLOW_KEYBOARD)
        val effective = HandwritingLoader.getEffectiveLanguage(context, "ml-IN")
        org.junit.Assert.assertEquals("ml-IN", effective)
    }

    @Test
    fun testGetEffectiveLanguageCustomOverride() {
        HandwritingLoader.setHandwritingLanguage(context, "en-US")
        val effective = HandwritingLoader.getEffectiveLanguage(context, "ml-IN")
        org.junit.Assert.assertEquals("en-US", effective)
    }

    @Test
    fun testGetEffectiveDisplayName() {
        HandwritingLoader.setHandwritingLanguage(context, "en-US")
        val name = HandwritingLoader.getEffectiveDisplayName(context, "ml-IN")
        org.junit.Assert.assertTrue(name.contains("English") || name.contains("en-US"))

        HandwritingLoader.setHandwritingLanguage(context, HandwritingLoader.LANG_FOLLOW_KEYBOARD)
        val defaultName = HandwritingLoader.getEffectiveDisplayName(context, "ml-IN")
        org.junit.Assert.assertTrue(defaultName.contains("Malayalam") || defaultName.contains("ml-IN"))
    }

    @Test
    fun testGetEffectiveDisplayNameCached() {
        HandwritingLoader.setHandwritingLanguage(context, "en-US")
        val name1 = HandwritingLoader.getEffectiveDisplayName(context, "ml-IN")
        val name2 = HandwritingLoader.getEffectiveDisplayName(context, "ml-IN")
        org.junit.Assert.assertSame(name1, name2)

        HandwritingLoader.setHandwritingLanguage(context, HandwritingLoader.LANG_FOLLOW_KEYBOARD)
        val name3 = HandwritingLoader.getEffectiveDisplayName(context, "ml-IN")
        org.junit.Assert.assertTrue(name3.contains("Malayalam") || name3.contains("ml-IN"))
    }
}
