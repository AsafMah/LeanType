package helium314.keyboard.latin

import android.content.Context
import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.ocr.ITextRecognizer
import helium314.keyboard.latin.ocr.OcrCameraManager
import helium314.keyboard.latin.ocr.OcrCameraView
import helium314.keyboard.latin.ocr.OcrPipeline
import helium314.keyboard.latin.ocr.OcrPluginLoader
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
class OcrWorkflowPreferenceTest {
    private lateinit var ime: OcrWorkflowIme
    private lateinit var cameraView: OcrCameraView
    private lateinit var resultView: View
    private lateinit var bitmap: Bitmap
    private lateinit var pipeline: OcrPipeline
    private var recognize: () -> List<String> = { listOf("first line", "second line") }

    @Before fun setUp() {
        ime = Robolectric.setupService(OcrWorkflowIme::class.java)
        val switcher = spy(KeyboardSwitcher.getInstance())
        doNothing().`when`(switcher).setAlphabetKeyboard()
        LatinIME::class.java.getDeclaredField("mKeyboardSwitcher").apply { isAccessible = true }.set(ime, switcher)
        val root = switcher.onCreateInputView(ime, false)
        cameraView = root.findViewById(R.id.ocr_camera_view)
        resultView = root.findViewById(R.id.ocr_result_view)
        bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val recognizer = object : ITextRecognizer {
            override fun recognize(bitmap: Bitmap, keepLineBreaks: Boolean) = recognize()
            override fun init(context: Context) = Unit
            override fun getScriptName() = "fake"
            override fun getDisplayName() = "fake"
            override fun isAvailable() = true
            override fun release() = Unit
        }
        pipeline = OcrPipeline(ime) { recognizer }
        val manager = mock(OcrCameraManager::class.java) { invocation ->
            if (invocation.method.name == "capturePhoto") {
                @Suppress("UNCHECKED_CAST")
                (invocation.arguments[0] as (Bitmap) -> Unit)(bitmap)
                null
            } else RETURNS_DEFAULTS.answer(invocation)
        }
        field("cameraManager").set(cameraView, manager)
        field("pipeline").set(cameraView, pipeline)
        field("isCameraStarted").set(cameraView, true)
        cameraView.visibility = View.VISIBLE
        ime.prefs().edit().putBoolean(OcrPluginLoader.PREF_OCR_KEEP_LINE_BREAKS, true)
            .putBoolean(OcrPluginLoader.PREF_OCR_AUTO_INSERT, true).commit()
    }

    @After fun tearDown() {
        cameraView.release()
        pipeline.release()
        ShadowInputMethodManager2.reset()
    }

    @Test fun cameraAutoInsertCommitsThroughImeAndClosesOcrPanels() {
        capture()
        awaitBitmap()
        assertEquals(listOf("first line\nsecond line"), ime.insertions)
        assertEquals(View.GONE, cameraView.visibility)
        assertEquals(View.GONE, resultView.visibility)
        assertFalse(field("isLoadingAnimationActive").getBoolean(cameraView))
    }

    @Test fun cameraDefaultWorkflowStillShowsResultWithoutInserting() {
        ime.prefs().edit().remove(OcrPluginLoader.PREF_OCR_AUTO_INSERT).commit()
        capture()
        awaitBitmap()
        assertTrue(ime.insertions.isEmpty())
        assertEquals(View.VISIBLE, resultView.visibility)
        assertEquals(View.GONE, cameraView.visibility)
    }

    @Test fun cameraAutoInsertRequiresAnActiveEditor() {
        ime.inputActive = false
        capture()
        awaitBitmap()
        assertTrue(ime.insertions.isEmpty())
    }

    @Test fun editorRestartDuringNativeRecognitionCannotAutoInsert() {
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        recognize = {
            entered.countDown()
            check(resume.await(10, TimeUnit.SECONDS))
            listOf("stale")
        }
        capture()
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            ime.onStartInput(ime.currentInputEditorInfo, true)
        } finally {
            resume.countDown()
        }
        awaitBitmap()
        assertTrue(ime.insertions.isEmpty())
        assertEquals(View.GONE, resultView.visibility)
    }

    @Test fun screenshotAutoInsertUsesTheSameGuardedImePath() {
        val queue = ArrayDeque<Runnable>()
        val executor = Executor { queue.addLast(it) }
        val manager = ClipboardHistoryManager(ime, executor, { bitmap }, { pipeline })
        manager.extractScreenshot(Uri.parse("content://media/external/images/media/1"))
        while (queue.isNotEmpty()) queue.removeFirst().run()
        awaitBitmap()
        assertEquals(listOf("first line\nsecond line"), ime.insertions)
        assertEquals(View.GONE, resultView.visibility)
        manager.onFinishInputView()
    }

    @Test fun cameraReadyReflectsRestoredTorchAndStopClearsIcon() {
        val pluginFile = ime.filesDir.resolve("ocr_plugin.apk")
        val original = if (pluginFile.exists()) pluginFile.readBytes() else null
        val oldHasPlugin = ime.prefs().getBoolean("pref_ocr_has_plugin", false)
        try {
            pluginFile.writeBytes(byteArrayOf(1))
            ime.prefs().edit().putBoolean("pref_ocr_has_plugin", true).commit()
            shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
            val manager = mock(OcrCameraManager::class.java) { invocation ->
                when (invocation.method.name) {
                    "startCamera" -> {
                        @Suppress("UNCHECKED_CAST")
                        (invocation.arguments[1] as () -> Unit)()
                        null
                    }
                    "isTorchEnabled" -> true
                    else -> RETURNS_DEFAULTS.answer(invocation)
                }
            }
            field("cameraManager").set(cameraView, manager)
            cameraView.startCamera()
            val flash = cameraView.findViewById<android.widget.ImageButton>(R.id.btn_ocr_flash)
            assertEquals(R.drawable.ic_flash_on, shadowOf(flash.drawable).createdFromResId)
            cameraView.stopCamera()
            assertEquals(R.drawable.ic_flash_off, shadowOf(flash.drawable).createdFromResId)
        } finally {
            if (original == null) pluginFile.delete() else pluginFile.writeBytes(original)
            ime.prefs().edit().putBoolean("pref_ocr_has_plugin", oldHasPlugin).commit()
            bitmap.recycle()
        }
    }

    private fun capture() {
        OcrCameraView::class.java.getDeclaredMethod("captureFromCamera").apply { isAccessible = true }.invoke(cameraView)
    }

    private fun field(name: String) = OcrCameraView::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun awaitBitmap() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!bitmap.isRecycled && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(bitmap.isRecycled)
    }
}

class OcrWorkflowIme : LatinIME() {
    var inputActive = true
    val insertions = mutableListOf<String>()
    private val editor = EditorInfo().apply { packageName = "ocr.workflow"; inputType = android.text.InputType.TYPE_CLASS_TEXT }
    override fun getCurrentInputStarted() = inputActive
    override fun getCurrentInputEditorInfo() = editor
    override fun isInputViewShown() = true
    override fun onTextInput(text: String) { insertions.add(text) }
}
