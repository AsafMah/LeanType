// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.settings.preferences.OcrPluginPreferenceImports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OcrPluginPreferenceImportsTest {
    private lateinit var fixture: File
    private lateinit var context: Context
    private lateinit var scope: CoroutineScope
    private val entered = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private val finished = CountDownLatch(1)
    private val importThread = AtomicReference<Thread>()
    private val callbackThread = AtomicReference<Thread>()

    @Before
    fun setUp() {
        fixture = File("build/import-fixtures/preference-${UUID.randomUUID()}").absoluteFile
        context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun getCacheDir() = File(fixture, "cache").apply { mkdirs() }
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    @After
    fun tearDown() {
        release.countDown()
        scope.cancel()
        if (entered.count == 0L) finished.await(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        fixture.deleteRecursively()
    }

    @Test
    fun localUriImportDoesNotBlockMainWhileImporterIsWaiting() = assertResponsive(false)

    @Test
    fun downloadedFileImportDoesNotBlockMainWhileImporterIsWaiting() = assertResponsive(true)

    @Test
    fun disposingScopeSuppressesLocalImportCompletion() = assertCancelledImport(false)

    @Test
    fun disposingScopeSuppressesDownloadedImportCompletion() = assertCancelledImport(true)

    private fun assertCancelledImport(download: Boolean) {
        val imports = OcrPluginPreferenceImports(
            context, scope,
            uriImporter = { _, _ -> blockedImport() },
            fileImporter = { _, _ -> blockedImport() },
            downloader = { _, file -> file.writeText("plugin"); true }
        )
        val completed = AtomicBoolean()
        val job = if (download) imports.download { completed.set(true) }
            else imports.importUri(Uri.parse("content://fixture/plugin.apk")) { completed.set(true) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        scope.cancel()
        release.countDown()
        waitFor { job.isCompleted }
        assertFalse("disposed preference must receive no result callback", completed.get())
        assertEquals(0L, finished.count)
        assertTrue(context.cacheDir.listFiles()!!.isEmpty())
    }

    @Test
    fun cancellingDownloadPreventsStartingImportAndCleansDownloadedFile() {
        val imported = AtomicBoolean()
        val completed = AtomicBoolean()
        val imports = OcrPluginPreferenceImports(
            context, scope,
            fileImporter = { _, _ -> imported.set(true); true },
            downloader = { _, file -> file.writeText("plugin"); blockedImport() }
        )
        val job = imports.download { completed.set(true) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        scope.cancel()
        release.countDown()
        waitFor { job.isCompleted }
        assertFalse(imported.get())
        assertFalse(completed.get())
        assertTrue(context.cacheDir.listFiles()!!.isEmpty())
    }

    @Test
    fun importExceptionReportsFailureOnMainAndCleansDownloadedFile() {
        ShadowLog.clear()
        val result = AtomicReference<Boolean>()
        val imports = OcrPluginPreferenceImports(
            context, scope,
            fileImporter = { _, _ -> throw IOException("unreadable plugin") },
            downloader = { _, file -> file.writeText("plugin"); true }
        )
        val job = imports.download {
            callbackThread.set(Thread.currentThread())
            result.set(it)
        }
        waitFor { job.isCompleted }
        assertEquals(false, result.get())
        assertSame(Looper.getMainLooper().thread, callbackThread.get())
        assertTrue(context.cacheDir.listFiles()!!.isEmpty())
        assertTrue(ShadowLog.getLogsForTag("LoadOcrPluginPreference").any {
            it.type == Log.ERROR && it.throwable?.message == "unreadable plugin"
        })
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("asynchronous operation did not finish", condition())
    }

    private fun assertResponsive(download: Boolean) {
        val imports = OcrPluginPreferenceImports(
            context, scope,
            uriImporter = { _, _ -> blockedImport() },
            fileImporter = { _, file ->
                assertEquals("downloaded plugin", file.readText())
                blockedImport()
            },
            downloader = { _, file -> file.writeText("downloaded plugin"); true }
        )
        val result: (Boolean) -> Unit = {
            assertTrue(it)
            callbackThread.set(Thread.currentThread())
        }
        if (download) imports.download(result)
        else imports.importUri(Uri.parse("content://fixture/plugin.apk"), result)

        // The download worker may still need to post the old Main-thread import.
        val startDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (entered.count != 0L && System.nanoTime() < startDeadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertEquals("importer must have started", 0L, entered.count)
        val heartbeat = AtomicBoolean()
        Handler(Looper.getMainLooper()).post { heartbeat.set(true) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("main must process callbacks while import remains blocked", heartbeat.get())
        assertEquals("import must still be waiting while Main responds", 1L, finished.count)
        assertNotSame(Looper.getMainLooper().thread, importThread.get())
        release.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (callbackThread.get() == null && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertSame("UI completion must run on Main", Looper.getMainLooper().thread, callbackThread.get())
    }

    private fun blockedImport(): Boolean {
        importThread.set(Thread.currentThread())
        entered.countDown()
        release.await(2, TimeUnit.SECONDS)
        finished.countDown()
        return true
    }
}
