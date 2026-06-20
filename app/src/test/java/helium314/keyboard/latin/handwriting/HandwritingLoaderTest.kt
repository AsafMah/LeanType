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
}
