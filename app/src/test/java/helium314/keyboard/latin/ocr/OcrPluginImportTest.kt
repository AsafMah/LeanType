// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ocr

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OcrPluginImportTest {
    private lateinit var fixture: File
    private lateinit var context: Context
    private lateinit var installed: File
    private lateinit var recognizer: ITextRecognizer
    private lateinit var classLoader: Any
    private val validator = OcrPluginLoader.importValidator
    private val moveFile = OcrPluginLoader.moveImportedFile

    @Before
    fun setUp() {
        fixture = File("build/import-fixtures/ocr-${UUID.randomUUID()}").absoluteFile
        context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun getFilesDir() = File(fixture, "files").apply { mkdirs() }
            override fun getCacheDir() = File(fixture, "cache").apply { mkdirs() }
            override fun getCodeCacheDir() = File(fixture, "code_cache").apply { mkdirs() }
            override fun getApplicationContext(): Context = this
        }
        installed = File(context.filesDir, "ocr_plugin.apk").apply { writeText("valid old plugin") }
        File(context.codeCacheDir, "live.dex").writeText("cached code")
        File(context.filesDir, "plugin_libs/ocr_live/lib.so")
            .apply { parentFile!!.mkdirs(); writeText("cached native library") }
        context.prefs().edit().putBoolean("pref_ocr_has_plugin", true).commit()
        recognizer = mock(ITextRecognizer::class.java)
        `when`(recognizer.isAvailable()).thenReturn(true)
        field("activeRecognizer").set(null, recognizer)
        classLoader = mock(field("cachedClassLoader").type)
        field("cachedClassLoader").set(null, classLoader)
        field("cachedApkModified").setLong(null, 123L)
        OcrPluginLoader.importValidator = { _, _ -> false }
    }

    @After
    fun tearDown() {
        OcrPluginLoader.importValidator = validator
        OcrPluginLoader.moveImportedFile = moveFile
        OcrPluginLoader.resetRecognizer()
        field("cachedClassLoader").set(null, null)
        field("cachedApkModified").setLong(null, 0L)
        context.prefs().edit().remove("pref_ocr_has_plugin").commit()
        fixture.walkTopDown().forEach { it.setWritable(true) }
        fixture.deleteRecursively()
    }

    @Test
    fun missingTempInputPreservesInstalledPluginAndLiveCaches() {
        assertFalse(OcrPluginLoader.importPluginFromTempFile(context, File(context.cacheDir, "missing.apk")))
        assertOldPluginUsable()
    }

    @Test
    fun missingUriInputPreservesInstalledPluginAndLiveCaches() {
        assertFalse(OcrPluginLoader.importPlugin(context, Uri.fromFile(File(context.cacheDir, "missing.apk"))))
        assertOldPluginUsable()
    }

    @Test
    fun rejectedTempPluginPreservesInstalledPluginAndLiveCaches() = assertRejectedPlugin(false, false)

    @Test
    fun rejectedUriPluginPreservesInstalledPluginAndLiveCaches() = assertRejectedPlugin(true, false)

    @Test
    fun throwingTempValidatorPreservesInstalledPluginAndLiveCaches() = assertRejectedPlugin(false, true)

    @Test
    fun throwingUriValidatorPreservesInstalledPluginAndLiveCaches() = assertRejectedPlugin(true, true)

    private fun assertRejectedPlugin(uri: Boolean, throws: Boolean) {
        var validated = false
        OcrPluginLoader.importValidator = { _, candidate ->
            validated = true
            assertEquals("new plugin", candidate.readText())
            if (throws) throw IOException("invalid candidate")
            false
        }
        val source = File(context.cacheDir, "new.apk").apply { writeText("new plugin") }
        assertFalse(if (uri) OcrPluginLoader.importPlugin(context, Uri.fromFile(source))
            else OcrPluginLoader.importPluginFromTempFile(context, source))
        assertTrue("validation seam must run without DEX/native loading", validated)
        assertOldPluginUsable()
    }

    @Test
    fun interruptedUriCopyPreservesInstalledPluginAndLiveCaches() {
        val uri = Uri.parse("content://fixture/partial.apk")
        shadowOf(context.contentResolver).registerInputStream(uri, object : InputStream() {
            private var reads = 0
            override fun read(): Int {
                if (++reads > 10) throw IOException("broken input")
                return 42
            }
        })
        assertFalse(OcrPluginLoader.importPlugin(context, uri))
        assertOldPluginUsable()
    }

    @Test
    fun successfulTempImportValidatesBeforeInvalidatingCaches() = assertSuccessfulImport(false)

    @Test
    fun successfulUriImportValidatesBeforeInvalidatingCaches() = assertSuccessfulImport(true)

    private fun assertSuccessfulImport(uri: Boolean) {
        OcrPluginLoader.importValidator = { _, candidate ->
            assertNotEquals(installed.canonicalFile, candidate.canonicalFile)
            assertEquals("new plugin", candidate.readText())
            assertOldPluginUsable(allowStaging = true)
            true
        }
        val source = File(context.cacheDir, "new.apk").apply { writeText("new plugin") }
        assertTrue(if (uri) OcrPluginLoader.importPlugin(context, Uri.fromFile(source))
            else OcrPluginLoader.importPluginFromTempFile(context, source))
        assertEquals("new plugin", installed.readText())
        assertTrue(OcrPluginLoader.hasPlugin(context))
        verify(recognizer).release()
        assertNull(field("activeRecognizer").get(null))
        assertNull(field("cachedClassLoader").get(null))
        assertEquals(0L, field("cachedApkModified").getLong(null))
    }

    @Test
    fun failedCommitRestoresOldApkAndPreservesLiveCaches() {
        OcrPluginLoader.importValidator = { _, _ -> true }
        OcrPluginLoader.moveImportedFile = { source, target ->
            if (source.name == "candidate.apk") false else source.renameTo(target)
        }
        val source = File(context.cacheDir, "new.apk").apply { writeText("new plugin") }
        assertFalse(OcrPluginLoader.importPluginFromTempFile(context, source))
        assertOldPluginUsable()
    }

    @Test
    fun failedUriCommitRestoresOldApkAndPreservesLiveCaches() {
        OcrPluginLoader.importValidator = { _, _ -> true }
        OcrPluginLoader.moveImportedFile = { source, target ->
            if (source.name == "candidate.apk") throw IOException("commit failure")
            source.renameTo(target)
        }
        val source = File(context.cacheDir, "new.apk").apply { writeText("new plugin") }
        assertFalse(OcrPluginLoader.importPlugin(context, Uri.fromFile(source)))
        assertOldPluginUsable()
    }

    @Test
    fun failedBackupLeavesInstalledPluginAndLiveCachesUntouched() {
        OcrPluginLoader.importValidator = { _, _ -> true }
        OcrPluginLoader.moveImportedFile = { _, _ -> false }
        val source = File(context.cacheDir, "new.apk").apply { writeText("new plugin") }
        assertFalse(OcrPluginLoader.importPluginFromTempFile(context, source))
        assertOldPluginUsable()
    }

    @Test
    fun rejectedFirstInstallLeavesNoPluginAndNoStagingFiles() {
        installed.delete()
        OcrPluginLoader.resetRecognizer()
        context.prefs().edit().putBoolean("pref_ocr_has_plugin", false).commit()
        val source = File(context.cacheDir, "new.apk").apply { writeText("new plugin") }
        assertFalse(OcrPluginLoader.importPluginFromTempFile(context, source))
        assertFalse(installed.exists())
        assertFalse(OcrPluginLoader.hasPlugin(context))
        assertFalse(context.filesDir.listFiles()!!.any { it.name.startsWith("ocr_import_") })
    }

    @Test
    fun backgroundValidationDoesNotBlockInstalledRecognizerLookup() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val validationFinished = CountDownLatch(1)
        OcrPluginLoader.importValidator = { _, _ ->
            entered.countDown()
            release.await(2, TimeUnit.SECONDS)
            validationFinished.countDown()
            false
        }
        val source = File(context.cacheDir, "new.apk").apply { writeText("new plugin") }
        val worker = Thread { OcrPluginLoader.importPluginFromTempFile(context, source) }
        try {
            worker.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(OcrPluginLoader.hasPlugin(context))
            assertSame(recognizer, OcrPluginLoader.getRecognizer(context))
            assertEquals("recognizer lookup must not wait for validation", 1L, validationFinished.count)
        } finally {
            release.countDown()
            worker.join(5000)
        }
        assertFalse(worker.isAlive)
        assertOldPluginUsable()
    }

    private fun assertOldPluginUsable(allowStaging: Boolean = false) {
        assertTrue("the old APK must survive a failed import", installed.isFile)
        assertEquals("valid old plugin", installed.readText())
        assertTrue(OcrPluginLoader.hasPlugin(context))
        assertSame(recognizer, OcrPluginLoader.getRecognizer(context))
        assertTrue(recognizer.isAvailable())
        verify(recognizer, never()).release()
        assertSame(classLoader, field("cachedClassLoader").get(null))
        assertEquals(123L, field("cachedApkModified").getLong(null))
        assertEquals("cached code", File(context.codeCacheDir, "live.dex").readText())
        assertTrue(File(context.filesDir, "plugin_libs/ocr_live/lib.so").isFile)
        if (!allowStaging) {
            assertFalse(context.filesDir.listFiles()!!.any { it.name.startsWith("ocr_import_") })
        }
    }

    private fun field(name: String) =
        OcrPluginLoader::class.java.getDeclaredField(name).apply { isAccessible = true }
}
