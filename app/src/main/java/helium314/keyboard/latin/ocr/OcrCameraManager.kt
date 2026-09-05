// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ocr

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.Surface
import androidx.annotation.MainThread
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.util.concurrent.ListenableFuture
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@MainThread
class OcrCameraManager(
    private val context: Context,
    private val getCameraProvider: () -> ListenableFuture<ProcessCameraProvider> =
        { ProcessCameraProvider.getInstance(context) }
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var isTorchOn: Boolean = false
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val lifecycleOwner = ImeLifecycleOwner()
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private var generation = 0L
    private var captureGeneration = 0L
    private var active = false
    private var released = false

    companion object {
        private const val TAG = "OcrCameraManager"
        private const val MAX_IMAGE_DIMENSION = 1920
        private const val PREF_TORCH_CHOICE = "ocr_last_torch_enabled"
    }

    private class ImeLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        init {
            registry.currentState = Lifecycle.State.CREATED
        }

        fun start() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.CREATED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }

        override val lifecycle: Lifecycle get() = registry
    }

    @SuppressLint("RestrictedApi")
    fun startCamera(previewView: PreviewView, onReady: () -> Unit = {}, onError: (Exception) -> Unit = {}) {
        if (released) return
        stopCamera()
        active = true
        val request = generation
        try {
            val future = getCameraProvider()
            future.addListener({
                if (!isCurrent(request)) return@addListener
                try {
                    cameraProvider = future.get()
                    bindCamera(previewView)
                    if (isCurrent(request)) onReady()
                } catch (e: Exception) {
                    if (isCurrent(request)) {
                        stopCamera()
                        Log.e(TAG, "Failed to start camera", e)
                        onError(e)
                    }
                }
            }, mainExecutor)
        } catch (e: Exception) {
            stopCamera()
            onError(e)
        }
    }

    private fun isCurrent(request: Long) = active && !released && request == generation

    private fun bindCamera(previewView: PreviewView) {
        val provider = cameraProvider ?: return

        lifecycleOwner.start()

        preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
            .build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        camera = provider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture
        )
        isTorchOn = false
        if (context.prefs().getBoolean(OcrPluginLoader.PREF_OCR_PERSIST_FLASH, false) &&
            context.prefs().getBoolean(PREF_TORCH_CHOICE, false)) {
            setTorchEnabled(true)
        }
    }

    fun toggleTorch(): Boolean {
        if (!active || released) return false
        val enabled = setTorchEnabled(!isTorchOn)
        val prefs = context.prefs()
        if (prefs.getBoolean(OcrPluginLoader.PREF_OCR_PERSIST_FLASH, false)) {
            prefs.edit().putBoolean(PREF_TORCH_CHOICE, enabled).apply()
        } else {
            prefs.edit().remove(PREF_TORCH_CHOICE).apply()
        }
        return enabled
    }

    private fun setTorchEnabled(enabled: Boolean): Boolean {
        val cam = camera ?: return false
        return try {
            if (cam.cameraInfo.hasFlashUnit()) {
                cam.cameraControl.enableTorch(enabled)
                isTorchOn = enabled
                isTorchOn
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle torch", e)
            false
        }
    }

    fun isTorchEnabled(): Boolean = isTorchOn

    fun capturePhoto(onCaptured: (Bitmap) -> Unit, onError: (Exception) -> Unit) {
        if (!active || released) return
        val session = generation
        val request = ++captureGeneration
        fun deliverError(error: Exception) {
            mainExecutor.execute {
                if (isCurrent(session) && request == captureGeneration) onError(error)
            }
        }
        val capture = imageCapture ?: run {
            deliverError(IllegalStateException("Camera capture is not ready"))
            return
        }

        try {
            capture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        var rawBitmap: Bitmap? = null
                        try {
                            val rotation = image.imageInfo.rotationDegrees
                            rawBitmap = image.toBitmap()
                            val scaledBitmap = scaleAndRotateBitmap(rawBitmap, rotation)
                            if (scaledBitmap != rawBitmap) {
                                rawBitmap.recycle()
                            }
                            rawBitmap = null
                            mainExecutor.execute {
                                if (isCurrent(session) && request == captureGeneration) {
                                    onCaptured(scaledBitmap)
                                } else {
                                    scaledBitmap.recycle()
                                }
                            }
                        } catch (e: Exception) {
                            rawBitmap?.recycle()
                            Log.e(TAG, "Error processing captured frame", e)
                            deliverError(e)
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Image capture error", exception)
                        deliverError(exception)
                    }
                }
            )
        } catch (e: Exception) {
            deliverError(e)
        }
    }

    private fun scaleAndRotateBitmap(src: Bitmap, rotationDegrees: Int): Bitmap {
        val width = src.width
        val height = src.height

        val maxDim = maxOf(width, height)
        val scale = if (maxDim > MAX_IMAGE_DIMENSION) {
            MAX_IMAGE_DIMENSION.toFloat() / maxDim.toFloat()
        } else {
            1.0f
        }

        val matrix = Matrix()
        if (scale < 1.0f) {
            matrix.postScale(scale, scale)
        }
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }

        return if (!matrix.isIdentity) {
            Bitmap.createBitmap(src, 0, 0, width, height, matrix, true)
        } else {
            src
        }
    }

    fun stopCamera() {
        generation++
        active = false
        val prefs = context.prefs()
        if (!prefs.getBoolean(OcrPluginLoader.PREF_OCR_PERSIST_FLASH, false)) {
            prefs.edit().remove(PREF_TORCH_CHOICE).apply()
        }
        try {
            if (isTorchOn) {
                camera?.cameraControl?.enableTorch(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling torch", e)
        }
        try {
            val ownedUseCases = listOfNotNull(preview, imageCapture).toTypedArray()
            if (ownedUseCases.isNotEmpty()) cameraProvider?.unbind(*ownedUseCases)
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding camera", e)
        } finally {
            isTorchOn = false
            camera = null
            preview = null
            imageCapture = null
            cameraProvider = null
            if (!released) lifecycleOwner.stop()
        }
    }

    fun release() {
        if (released) return
        stopCamera()
        released = true
        lifecycleOwner.destroy()
        cameraExecutor.shutdown()
    }
}
