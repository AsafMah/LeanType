package helium314.keyboard.latin

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.latin.ocr.ITextRecognizer
import helium314.keyboard.latin.ocr.OcrPipeline
import helium314.keyboard.latin.ocr.OcrPluginLoader
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class])
class ScreenshotLifecycleTest {
    private lateinit var ime: ScreenshotTestIme
    private lateinit var manager: ClipboardHistoryManager
    private lateinit var executor: QueuedExecutor
    private lateinit var resolver: ContentResolver
    private val uri = Uri.parse("content://media/external/images/media/19")
    private var recognitionCalls = 0
    private var bitmap: Bitmap? = null
    private var onRecognize: () -> Unit = {}
    private val pipelines = mutableListOf<OcrPipeline>()

    @Before fun setUp() {
        ime = Robolectric.setupService(ScreenshotTestIme::class.java)
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_IMAGES)
        resolver = mock(ContentResolver::class.java)
        ime.testResolver = resolver
        ime.prefs().edit().putBoolean(Settings.PREF_SUGGEST_SCREENSHOTS, true)
            .putBoolean(OcrPluginLoader.PREF_OCR_AUTO_COPY, true)
            .putBoolean(OcrPluginLoader.PREF_OCR_AUTO_INSERT, false).commit()
        val current = ime.mSettings.current
        ime.mSettings.loadSettings(ime, current.mLocale, current.mInputAttributes, current.mCurrentKeyboardScript)
        executor = QueuedExecutor()
        val recognizer = object : ITextRecognizer {
            override fun recognize(bitmap: Bitmap, keepLineBreaks: Boolean): List<String> {
                recognitionCalls++
                onRecognize()
                return listOf("late screenshot")
            }
            override fun init(context: Context) = Unit
            override fun getScriptName() = "fake"
            override fun getDisplayName() = "fake"
            override fun isAvailable() = true
            override fun release() = Unit
        }
        manager = ClipboardHistoryManager(ime, executor, { bitmap }) {
            OcrPipeline(ime) { recognizer }.also { pipelines.add(it) }
        }
    }

    @After fun tearDown() {
        manager.onFinishInputView()
        pipelines.forEach { it.release() }
        ShadowInputMethodManager2.reset()
    }

    @Test fun contentObserverRefreshesVisibleFloatingKeyboard() {
        ime.dockedVisible = false
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val overlay = FrameLayout(activity)
        activity.setContentView(overlay)
        val floating = mock(FloatingKeyboardManager::class.java)
        `when`(floating.isFloating).thenReturn(true)
        `when`(floating.overlayRoot).thenReturn(overlay)
        ime.testFloating = floating
        notifyObserver()
        assertEquals("observer must schedule a screenshot refresh for a visible overlay", 1, executor.tasks.size)
        executor.drain()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, ime.suggestionRefreshes)
    }

    @Test fun contentObserverDoesNotRefreshWhenBothSurfacesAreHidden() {
        ime.dockedVisible = false
        notifyObserver()
        assertTrue(executor.tasks.isEmpty())
        assertEquals(0, ime.suggestionRefreshes)
    }

    @Test fun contentObserverIgnoresHiddenFloatingOverlay() {
        ime.dockedVisible = false
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val overlay = FrameLayout(activity)
        activity.setContentView(overlay)
        overlay.visibility = android.view.View.GONE
        val floating = mock(FloatingKeyboardManager::class.java)
        `when`(floating.isFloating).thenReturn(true)
        `when`(floating.overlayRoot).thenReturn(overlay)
        ime.testFloating = floating
        notifyObserver()
        assertTrue(executor.tasks.isEmpty())
    }

    @Test fun contentObserverRefreshesDockedKeyboard() {
        ime.dockedVisible = true
        notifyObserver()
        assertEquals(1, executor.tasks.size)
        executor.drain()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, ime.suggestionRefreshes)
    }

    @Test fun observerCompletionAfterHideCannotRefreshSuggestions() {
        ime.dockedVisible = true
        notifyObserver()
        ime.dockedVisible = false
        executor.drain()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, ime.suggestionRefreshes)
    }

    @Test fun closeBeforeScreenshotDecodeDiscardsBitmapWithoutRecognizing() {
        bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        manager.extractScreenshot(uri)
        manager.onFinishInputView()
        executor.drain()
        waitForBitmap()
        assertEquals(0, recognitionCalls)
    }

    @Test fun closeAfterDecodeBeforeMainDeliveryDisposesBitmap() {
        bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        manager.extractScreenshot(uri)
        executor.drain()
        manager.onFinishInputView()
        waitForBitmap()
        assertEquals(0, recognitionCalls)
    }

    @Test fun editorChangeWithoutViewFinishRejectsDecodedScreenshot() {
        bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        manager.extractScreenshot(uri)
        ime.testEditor = EditorInfo().apply { packageName = "other.editor"; fieldId = 3 }
        executor.drain()
        waitForBitmap()
        assertEquals(0, recognitionCalls)
    }

    @Test fun sameEditorInputSessionRestartRejectsDecodedScreenshot() {
        bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        manager.extractScreenshot(uri)
        val editor = ime.currentInputEditorInfo
        manager.onStartInput()
        assertSame(editor, ime.currentInputEditorInfo)
        executor.drain()
        waitForBitmap()
        assertEquals(0, recognitionCalls)
    }

    @Test fun sameEditorObjectRestartRejectsLateNativeResultAndAutoCopy() {
        ime.prefs().edit().putBoolean(OcrPluginLoader.PREF_OCR_AUTO_INSERT, true).commit()
        bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val clipboard = ime.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("original", "keep"))
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        onRecognize = { entered.countDown(); check(resume.await(10, TimeUnit.SECONDS)) }
        manager.extractScreenshot(uri)
        executor.drain()
        shadowOf(Looper.getMainLooper()).idle()
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val sameEditor = ime.currentInputEditorInfo
            ime.onStartInput(sameEditor, true)
            assertSame(sameEditor, ime.currentInputEditorInfo)
        } finally {
            resume.countDown()
        }
        waitForBitmap()
        assertEquals("keep", clipboard.primaryClip!!.getItemAt(0).text)
        assertTrue(ime.insertions.isEmpty())
    }

    private fun notifyObserver() {
        ClipboardHistoryManager::class.java.getDeclaredMethod("registerScreenshotObserver")
            .apply { isAccessible = true }.invoke(manager)
        val observer = ClipboardHistoryManager::class.java.getDeclaredField("screenshotObserver")
            .apply { isAccessible = true }.get(manager) as ContentObserver
        observer.onChange(false, uri)
    }

    private fun waitForBitmap() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (bitmap?.isRecycled == false && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(bitmap!!.isRecycled)
    }

    private class QueuedExecutor : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }
}

class ScreenshotTestIme : LatinIME() {
    var dockedVisible = true
    var suggestionRefreshes = 0
    var testFloating: FloatingKeyboardManager? = null
    var testResolver: ContentResolver? = null
    val insertions = mutableListOf<String>()
    var testEditor = EditorInfo().apply { packageName = "screenshot.editor"; fieldId = 1 }
    override fun isInputViewShown() = dockedVisible
    override fun getCurrentInputEditorInfo() = testEditor
    override fun getCurrentInputStarted() = true
    override fun getFloatingKeyboardManager() = testFloating
    override fun getContentResolver(): ContentResolver = testResolver ?: super.getContentResolver()
    override fun tryShowClipboardSuggestion(): Boolean { suggestionRefreshes++; return true }
    override fun onTextInput(text: String) { insertions.add(text) }
}
