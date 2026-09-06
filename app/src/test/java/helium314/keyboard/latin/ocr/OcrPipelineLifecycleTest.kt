package helium314.keyboard.latin.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import helium314.keyboard.latin.utils.prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class OcrPipelineLifecycleTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun stopDuringNativeRecognitionDiscardsResultAndAutoCopy() = cancelDuringRecognition(false)
    @Test fun releaseDuringNativeRecognitionDiscardsResultAndAutoCopy() = cancelDuringRecognition(true)

    private fun cancelDuringRecognition(release: Boolean) {
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val recognizer = FakeRecognizer {
            entered.countDown()
            check(resume.await(10, TimeUnit.SECONDS))
            listOf("late OCR text")
        }
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("original", "keep me"))
        context.prefs().edit().putBoolean(OcrPluginLoader.PREF_OCR_AUTO_COPY, true).commit()
        val pipeline = OcrPipeline(context) { recognizer }
        val view = OcrCameraView(context)
        OcrCameraView::class.java.getDeclaredField("pipeline").apply { isAccessible = true }.set(view, pipeline)
        var callbacks = 0
        pipeline.processImage(bitmap, { callbacks++ }, { callbacks++ })
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            if (release) view.release() else view.stopCamera()
        } finally {
            resume.countDown()
        }
        awaitRecycled(bitmap)
        assertEquals("keep me", clipboard.primaryClip!!.getItemAt(0).text)
        assertEquals(0, callbacks)
        view.release()
    }

    @Test fun stopDuringPluginInitializationDoesNotStartRecognition() {
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val recognizer = FakeRecognizer { listOf("late") }
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val pipeline = OcrPipeline(context) {
            entered.countDown()
            check(resume.await(10, TimeUnit.SECONDS))
            recognizer
        }
        val view = OcrCameraView(context)
        OcrCameraView::class.java.getDeclaredField("pipeline").apply { isAccessible = true }.set(view, pipeline)
        pipeline.processImage(bitmap, {}, {})
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            view.stopCamera()
        } finally {
            resume.countDown()
        }
        awaitRecycled(bitmap)
        assertEquals(0, recognizer.calls)
        view.release()
    }

    @Test fun releaseIsTerminalAndRecyclesSubsequentImages() {
        val recognizer = FakeRecognizer { listOf("unexpected") }
        val pipeline = OcrPipeline(context) { recognizer }
        pipeline.release()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        pipeline.processImage(bitmap, { fail("released pipeline returned text") }, { fail(it) })
        assertTrue(bitmap.isRecycled)
        assertEquals(0, recognizer.calls)
    }

    @Test fun pluginInitializationFailureRecyclesImageAndDeliversError() {
        val pipeline = OcrPipeline(context) { throw IllegalStateException("init failed") }
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        var error: String? = null
        var callbackThread: Thread? = null
        pipeline.processImage(bitmap, { fail("unexpected result") }, {
            error = it
            callbackThread = Thread.currentThread()
        })
        awaitRecycled(bitmap)
        assertTrue(error!!.contains("init failed"))
        assertSame(Looper.getMainLooper().thread, callbackThread)
        pipeline.release()
    }

    @Test fun originatingRequestInvalidationSuppressesNativeResultAndClipboard() {
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val recognizer = FakeRecognizer {
            entered.countDown()
            check(resume.await(10, TimeUnit.SECONDS))
            listOf("stale")
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("original", "keep"))
        context.prefs().edit().putBoolean(OcrPluginLoader.PREF_OCR_AUTO_COPY, true).commit()
        val pipeline = OcrPipeline(context) { recognizer }
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        var current = true
        var callbacks = 0
        pipeline.processImage(bitmap, { callbacks++ }, { callbacks++ }, { current })
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            current = false
        } finally {
            resume.countDown()
        }
        awaitRecycled(bitmap)
        assertEquals(0, callbacks)
        assertEquals("keep", clipboard.primaryClip!!.getItemAt(0).text)
        pipeline.release()
    }

    @Test fun autoInsertOptInUsesGuardedInsertionInsteadOfResultPanel() {
        context.prefs().edit().putBoolean(OcrPluginLoader.PREF_OCR_AUTO_INSERT, true)
            .putBoolean(OcrPluginLoader.PREF_OCR_KEEP_LINE_BREAKS, true).commit()
        val pipeline = OcrPipeline(context) { FakeRecognizer { listOf("first line", "second line") } }
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        var resultPanels = 0
        var inserted: String? = null
        var insertionThread: Thread? = null
        pipeline.processImage(bitmap, { resultPanels++ }, { fail(it) }, onInsertText = {
            inserted = it
            insertionThread = Thread.currentThread()
        })
        awaitRecycled(bitmap)
        assertEquals("first line\nsecond line", inserted)
        assertEquals(0, resultPanels)
        assertSame(Looper.getMainLooper().thread, insertionThread)
        pipeline.release()
    }

    @Test fun autoInsertDefaultOffKeepsSuccessfulResultPanel() {
        context.prefs().edit().remove(OcrPluginLoader.PREF_OCR_AUTO_INSERT).commit()
        val pipeline = OcrPipeline(context) { FakeRecognizer { listOf("recognized") } }
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        var result: List<String>? = null
        pipeline.processImage(bitmap, { result = it }, { fail(it) }, onInsertText = { fail("default must not insert") })
        awaitRecycled(bitmap)
        assertEquals(listOf("recognized"), result)
        pipeline.release()
    }

    @Test fun delayedAutoInsertCannotReachAnotherInputSession() {
        context.prefs().edit().putBoolean(OcrPluginLoader.PREF_OCR_AUTO_INSERT, true).commit()
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val pipeline = OcrPipeline(context) {
            FakeRecognizer {
                entered.countDown()
                check(resume.await(10, TimeUnit.SECONDS))
                listOf("do not insert")
            }
        }
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        var current = true
        var insertions = 0
        var results = 0
        pipeline.processImage(bitmap, { results++ }, { fail(it) }, { current }, { insertions++ })
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            current = false
        } finally {
            resume.countDown()
        }
        awaitRecycled(bitmap)
        assertEquals(0, insertions)
        assertEquals(0, results)
        pipeline.release()
    }

    private fun awaitRecycled(bitmap: Bitmap) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!bitmap.isRecycled && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("pipeline must dispose owned bitmap", bitmap.isRecycled)
    }

    private class FakeRecognizer(val recognize: () -> List<String>) : ITextRecognizer {
        var calls = 0
        override fun recognize(bitmap: Bitmap, keepLineBreaks: Boolean): List<String> {
            calls++
            return recognize()
        }
        override fun getScriptName() = "test"
        override fun getDisplayName() = "test"
        override fun init(context: Context) = Unit
        override fun isAvailable() = true
        override fun release() = Unit
    }
}
