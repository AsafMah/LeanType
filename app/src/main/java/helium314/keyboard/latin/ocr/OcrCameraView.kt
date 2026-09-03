// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ocr

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination

class OcrCameraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface OcrViewListener {
        fun onOcrTextExtracted(lines: List<String>)
        fun onCloseOcr()
    }

    private var previewView: PreviewView? = null
    private var pluginPanel: LinearLayout? = null
    private var controlsBar: LinearLayout? = null
    private var shutterBtn: ImageButton? = null
    private var flashBtn: ImageButton? = null
    private var closeBtn: ImageButton? = null
    private var loadingOverlay: FrameLayout? = null

    private var cameraManager: OcrCameraManager? = null
    private var pipeline: OcrPipeline? = null
    private var listener: OcrViewListener? = null
    private var isCameraStarted = false

    companion object {
        private const val TAG = "OcrCameraView"
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        previewView = findViewById(R.id.ocr_preview_view)
        pluginPanel = findViewById(R.id.ocr_plugin_required_panel)
        controlsBar = findViewById(R.id.ocr_controls_bar)
        shutterBtn = findViewById(R.id.btn_ocr_shutter)
        flashBtn = findViewById(R.id.btn_ocr_flash)
        closeBtn = findViewById(R.id.btn_ocr_close)
        loadingOverlay = findViewById(R.id.ocr_loading_overlay)

        previewView?.scaleType = PreviewView.ScaleType.FILL_CENTER

        findViewById<Button>(R.id.ocr_btn_load_plugin)?.setOnClickListener {
            openOcrSettings()
        }

        setupButtons()
    }

    fun setListener(listener: OcrViewListener) {
        this.listener = listener
    }

    private fun setupButtons() {
        shutterBtn?.setOnClickListener {
            captureFromCamera()
        }

        flashBtn?.setOnClickListener {
            val isNowOn = cameraManager?.toggleTorch() ?: false
            flashBtn?.setImageResource(if (isNowOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
        }

        closeBtn?.setOnClickListener {
            stopCamera()
            listener?.onCloseOcr()
        }
    }

    fun startCamera() {
        if (!OcrPluginLoader.hasPlugin(context)) {
            pluginPanel?.visibility = View.VISIBLE
            controlsBar?.visibility = View.GONE
            previewView?.visibility = View.GONE
            return
        }

        pluginPanel?.visibility = View.GONE
        controlsBar?.visibility = View.VISIBLE
        previewView?.visibility = View.VISIBLE

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, R.string.ocr_camera_permission_required, Toast.LENGTH_LONG).show()
            openOcrSettings()
            listener?.onCloseOcr()
            return
        }

        if (cameraManager == null) {
            cameraManager = OcrCameraManager(context)
        }
        if (pipeline == null) {
            pipeline = OcrPipeline(context)
        }

        previewView?.let { pv ->
            cameraManager?.startCamera(
                previewView = pv,
                onReady = {
                    isCameraStarted = true
                },
                onError = { e ->
                    Log.e(TAG, "Failed to start camera viewfinder", e)
                }
            )
        }
    }

    fun stopCamera() {
        cameraManager?.stopCamera()
        isCameraStarted = false
        flashBtn?.setImageResource(R.drawable.ic_flash_off)
    }

    fun release() {
        stopCamera()
        cameraManager?.release()
        cameraManager = null
        pipeline = null
    }

    private fun captureFromCamera() {
        if (!isCameraStarted) return
        showLoading(true)

        cameraManager?.capturePhoto(
            onCaptured = { bitmap ->
                pipeline?.processImage(
                    bitmap = bitmap,
                    onSuccess = { lines ->
                        showLoading(false)
                        listener?.onOcrTextExtracted(lines)
                    },
                    onError = { errorMsg ->
                        showLoading(false)
                        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onError = { e ->
                showLoading(false)
                Toast.makeText(context, "Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun openOcrSettings() {
        val intent = Intent().apply {
            setClass(context, SettingsActivity::class.java)
            putExtra("screen", SettingsDestination.OCR)
            putExtra("from_ime", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start OCR settings activity", e)
        }
    }

    fun applyColors(colors: Colors) {
        // Style controls bar if needed
    }
}
