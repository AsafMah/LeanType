package helium314.keyboard.latin.ocr

import android.content.Context
import android.app.Activity
import android.graphics.Bitmap
import android.os.Looper
import android.widget.LinearLayout
import android.widget.FrameLayout
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.utils.prefs
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class])
class OcrCameraLifecycleTest {
    private lateinit var context: Context
    private lateinit var manager: OcrCameraManager
    private lateinit var preview: PreviewView
    private lateinit var provider: ProcessCameraProvider

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.prefs().edit().putBoolean(OcrPluginLoader.PREF_OCR_PERSIST_FLASH, false)
            .remove("ocr_last_torch_enabled").commit()
        manager = OcrCameraManager(context)
        preview = PreviewView(context)
        provider = mock(ProcessCameraProvider::class.java)
    }

    @After fun tearDown() { manager.release() }

    private class PendingProvider(provider: ProcessCameraProvider) {
        @Suppress("UNCHECKED_CAST")
        val future = mock(ListenableFuture::class.java) as ListenableFuture<ProcessCameraProvider>
        private lateinit var callback: Runnable
        private lateinit var executor: Executor
        init {
            `when`(future.get()).thenReturn(provider)
            doAnswer {
                callback = it.getArgument(0)
                executor = it.getArgument(1)
                null
            }.`when`(future).addListener(any(Runnable::class.java), any(Executor::class.java))
        }
        fun complete() {
            executor.execute(callback)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test fun stoppedProviderCompletionCannotBindOrSignalReady() {
        val pending = PendingProvider(provider)
        var ready = 0
        manager.release()
        manager = OcrCameraManager(context) { pending.future }
        manager.startCamera(preview, { ready++ })
        manager.stopCamera()
        pending.complete()
        assertEquals(0, ready)
        assertFalse(mockingDetails(provider).invocations.any { it.method.name == "bindToLifecycle" })
    }

    @Test fun releasedProviderCompletionCannotResurrectDestroyedLifecycle() {
        val pending = PendingProvider(provider)
        var ready = 0
        manager.release()
        manager = OcrCameraManager(context) { pending.future }
        manager.startCamera(preview, { ready++ })
        manager.release()
        pending.complete()
        val owner = field("lifecycleOwner") as LifecycleOwner
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        assertEquals(0, ready)
    }

    @Test fun rapidRestartIgnoresOlderProviderCompletion() {
        val old = PendingProvider(provider)
        val current = PendingProvider(provider)
        var oldReady = 0
        var currentReady = 0
        manager.release()
        var calls = 0
        manager = OcrCameraManager(context) { if (calls++ == 0) old.future else current.future }
        manager.startCamera(preview, { oldReady++ })
        manager.stopCamera()
        manager.startCamera(preview, { currentReady++ })
        current.complete()
        old.complete()
        assertEquals(0, oldReady)
        assertEquals(1, currentReady)
    }

    @Test fun staleProviderFailureDoesNotDeliverAnError() {
        val pending = PendingProvider(provider)
        `when`(pending.future.get()).thenThrow(IllegalStateException("late failure"))
        manager.release()
        manager = OcrCameraManager(context) { pending.future }
        var errors = 0
        manager.startCamera(preview, {}, { errors++ })
        manager.stopCamera()
        pending.complete()
        assertEquals(0, errors)
    }

    @Test fun bindingFailureNeverSignalsReadyAndStopsLifecycle() {
        provider = mock(ProcessCameraProvider::class.java) { invocation ->
            if (invocation.method.name == "bindToLifecycle") throw IllegalStateException("bind failed")
            RETURNS_DEFAULTS.answer(invocation)
        }
        val pending = PendingProvider(provider)
        manager.release()
        manager = OcrCameraManager(context) { pending.future }
        var ready = 0
        var errors = 0
        manager.startCamera(preview, { ready++ }, { errors++ })
        pending.complete()
        assertEquals(0, ready)
        assertEquals(1, errors)
        assertEquals(Lifecycle.State.CREATED, (field("lifecycleOwner") as LifecycleOwner).lifecycle.currentState)
        assertNull(field("imageCapture"))
    }

    @Test fun stopUnbindsOnlyOwnedUseCases() {
        val pending = PendingProvider(provider)
        manager.release()
        manager = OcrCameraManager(context) { pending.future }
        manager.startCamera(preview)
        pending.complete()
        manager.stopCamera()
        verify(provider, never()).unbindAll()
        assertTrue(mockingDetails(provider).invocations.any { it.method.name == "unbind" })
    }

    @Test fun backgroundCaptureErrorIsDeliveredOnMainThread() {
        startReadyCamera()
        val capture = mock(ImageCapture::class.java)
        setField("imageCapture", capture)
        var callback: ImageCapture.OnImageCapturedCallback? = null
        var executor: Executor? = null
        doAnswer {
            executor = it.getArgument(0)
            callback = it.getArgument(1)
            null
        }.`when`(capture).takePicture(any(Executor::class.java), any(ImageCapture.OnImageCapturedCallback::class.java))
        var callbackThread: Thread? = null
        manager.capturePhoto({}, { callbackThread = Thread.currentThread() })
        (executor as ExecutorService).submit {
            callback!!.onError(ImageCaptureException(ImageCapture.ERROR_UNKNOWN, "capture", null))
        }.get()
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(Looper.getMainLooper().thread, callbackThread)
    }

    @Test fun captureErrorAfterStopIsDiscarded() {
        startReadyCamera()
        val capture = mock(ImageCapture::class.java)
        setField("imageCapture", capture)
        var callback: ImageCapture.OnImageCapturedCallback? = null
        doAnswer { callback = it.getArgument(1); null }.`when`(capture)
            .takePicture(any(Executor::class.java), any(ImageCapture.OnImageCapturedCallback::class.java))
        var errors = 0
        manager.capturePhoto({}, { errors++ })
        manager.stopCamera()
        callback!!.onError(ImageCaptureException(ImageCapture.ERROR_UNKNOWN, "capture", null))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, errors)
    }

    @Test fun captureErrorStopsAnActiveLoadingAnimatorOnMainThread() {
        Robolectric.setupService(LatinIME::class.java)
        startReadyCamera()
        val capture = mock(ImageCapture::class.java)
        setField("imageCapture", capture)
        var callback: ImageCapture.OnImageCapturedCallback? = null
        var executor: ExecutorService? = null
        doAnswer {
            executor = it.getArgument(0)
            callback = it.getArgument(1)
            null
        }.`when`(capture).takePicture(any(Executor::class.java), any(ImageCapture.OnImageCapturedCallback::class.java))
        val view = OcrCameraView(context)
        fun viewField(name: String) = OcrCameraView::class.java.getDeclaredField(name).apply { isAccessible = true }
        viewField("cameraManager").set(view, manager)
        viewField("isCameraStarted").set(view, true)
        viewField("controlsBar").set(view, LinearLayout(context))
        OcrCameraView::class.java.getDeclaredMethod("captureFromCamera").apply { isAccessible = true }.invoke(view)
        assertTrue(viewField("isLoadingAnimationActive").getBoolean(view))
        assertNotNull(viewField("loadingAnimator").get(view))
        executor!!.submit {
            callback!!.onError(ImageCaptureException(ImageCapture.ERROR_UNKNOWN, "delayed failure", null))
        }.get()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(viewField("isLoadingAnimationActive").getBoolean(view))
        assertNull(viewField("loadingAnimator").get(view))
        view.release()
    }

    @Test fun staleSuccessfulCaptureClosesImageAndRecyclesBitmap() {
        startReadyCamera()
        val capture = mock(ImageCapture::class.java)
        setField("imageCapture", capture)
        var callback: ImageCapture.OnImageCapturedCallback? = null
        var executor: ExecutorService? = null
        doAnswer {
            executor = it.getArgument(0)
            callback = it.getArgument(1)
            null
        }.`when`(capture).takePicture(any(Executor::class.java), any(ImageCapture.OnImageCapturedCallback::class.java))
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val image = mock(ImageProxy::class.java)
        `when`(image.imageInfo).thenReturn(mock(ImageInfo::class.java))
        `when`(image.toBitmap()).thenReturn(bitmap)
        var results = 0
        manager.capturePhoto({ results++ }, { fail(it.toString()) })
        manager.stopCamera()
        executor!!.submit { callback!!.onCaptureSuccess(image) }.get()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, results)
        assertTrue(bitmap.isRecycled)
        verify(image, times(1)).close()
    }

    @Test fun successfulCaptureDeliversOwnedBitmapOnMainThread() {
        startReadyCamera()
        val capture = mock(ImageCapture::class.java)
        setField("imageCapture", capture)
        var callback: ImageCapture.OnImageCapturedCallback? = null
        var executor: ExecutorService? = null
        doAnswer {
            executor = it.getArgument(0)
            callback = it.getArgument(1)
            null
        }.`when`(capture).takePicture(any(Executor::class.java), any(ImageCapture.OnImageCapturedCallback::class.java))
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val image = mock(ImageProxy::class.java)
        `when`(image.imageInfo).thenReturn(mock(ImageInfo::class.java))
        `when`(image.toBitmap()).thenReturn(bitmap)
        var delivered: Bitmap? = null
        var callbackThread: Thread? = null
        manager.capturePhoto({ delivered = it; callbackThread = Thread.currentThread() }, { fail(it.toString()) })
        executor!!.submit { callback!!.onCaptureSuccess(image) }.get()
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(bitmap, delivered)
        assertFalse(bitmap.isRecycled)
        assertSame(Looper.getMainLooper().thread, callbackThread)
        verify(image, times(1)).close()
        bitmap.recycle()
    }

    @Test fun startAfterReleaseDoesNotRequestAnotherProvider() {
        manager.release()
        var requests = 0
        val terminal = OcrCameraManager(context) {
            requests++
            PendingProvider(provider).future
        }
        terminal.release()
        terminal.startCamera(preview)
        assertEquals(0, requests)
    }

    @Test fun persistentTorchRestoresChoiceAfterReleaseButHardwareStops() {
        context.prefs().edit().putBoolean(OcrPluginLoader.PREF_OCR_PERSIST_FLASH, true).commit()
        val control = installFlashCamera()
        startReadyCamera()
        assertTrue(manager.toggleTorch())
        manager.release()
        verify(control, atLeastOnce()).enableTorch(false)
        clearInvocations(control)
        startReadyCamera()
        assertTrue("remembered choice must restore only on a valid new bind", manager.isTorchEnabled())
        verify(control).enableTorch(true)
    }

    @Test fun disablingTorchPersistenceClearsPreviousChoice() {
        context.prefs().edit().putBoolean(OcrPluginLoader.PREF_OCR_PERSIST_FLASH, true).commit()
        installFlashCamera()
        startReadyCamera()
        assertTrue(manager.toggleTorch())
        manager.stopCamera()
        context.prefs().edit().putBoolean(OcrPluginLoader.PREF_OCR_PERSIST_FLASH, false).commit()
        startReadyCamera()
        assertFalse(manager.isTorchEnabled())
        context.prefs().edit().putBoolean(OcrPluginLoader.PREF_OCR_PERSIST_FLASH, true).commit()
        startReadyCamera()
        assertFalse("reenabling persistence must not resurrect a discarded old choice", manager.isTorchEnabled())
    }

    @Test fun persistentTorchOffChoiceStaysOffOnReopen() {
        context.prefs().edit().putBoolean(OcrPluginLoader.PREF_OCR_PERSIST_FLASH, true).commit()
        installFlashCamera()
        startReadyCamera()
        assertTrue(manager.toggleTorch())
        assertFalse(manager.toggleTorch())
        startReadyCamera()
        assertFalse(manager.isTorchEnabled())
    }

    @Test fun staleProviderCannotRestoreRememberedTorchAfterStop() {
        context.prefs().edit().putBoolean(OcrPluginLoader.PREF_OCR_PERSIST_FLASH, true).commit()
        val control = installFlashCamera()
        startReadyCamera()
        assertTrue(manager.toggleTorch())
        manager.release()
        clearInvocations(control)
        val pending = PendingProvider(provider)
        manager = OcrCameraManager(context) { pending.future }
        manager.startCamera(preview)
        manager.stopCamera()
        pending.complete()
        verify(control, never()).enableTorch(true)
        assertFalse(manager.isTorchEnabled())
    }

    private fun installFlashCamera(): CameraControl {
        val camera = mock(Camera::class.java)
        val info = mock(CameraInfo::class.java)
        val control = mock(CameraControl::class.java)
        `when`(info.hasFlashUnit()).thenReturn(true)
        `when`(camera.cameraInfo).thenReturn(info)
        `when`(camera.cameraControl).thenReturn(control)
        provider = mock(ProcessCameraProvider::class.java) { invocation ->
            if (invocation.method.name == "bindToLifecycle") camera else RETURNS_DEFAULTS.answer(invocation)
        }
        return control
    }

    @Test fun detachReleasesExecutorAndDoesNotRestartOnReattach() {
        startReadyCamera()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val dock = FrameLayout(activity)
        val floating = FrameLayout(activity)
        val root = LinearLayout(activity)
        root.addView(dock)
        root.addView(floating)
        activity.setContentView(root)
        val view = OcrCameraView(context)
        dock.addView(view)
        OcrCameraView::class.java.getDeclaredField("cameraManager").apply { isAccessible = true }
            .set(view, manager)
        dock.removeView(view)
        floating.addView(view)
        assertTrue((field("cameraExecutor") as ExecutorService).isShutdown)
        assertEquals(Lifecycle.State.DESTROYED, (field("lifecycleOwner") as LifecycleOwner).lifecycle.currentState)
        assertNull(OcrCameraView::class.java.getDeclaredField("cameraManager").apply { isAccessible = true }.get(view))
        view.release()
        assertTrue((field("cameraExecutor") as ExecutorService).isShutdown)
    }

    private fun field(name: String): Any? =
        OcrCameraManager::class.java.getDeclaredField(name).apply { isAccessible = true }.get(manager)
    private fun setField(name: String, value: Any) =
        OcrCameraManager::class.java.getDeclaredField(name).apply { isAccessible = true }.set(manager, value)

    private fun startReadyCamera() {
        val pending = PendingProvider(provider)
        manager.release()
        manager = OcrCameraManager(context) { pending.future }
        manager.startCamera(preview)
        pending.complete()
    }
}
