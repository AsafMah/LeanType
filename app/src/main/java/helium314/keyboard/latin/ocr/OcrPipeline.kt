// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ocr

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OcrPipeline(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "OcrPipeline"
    }

    fun processImage(
        bitmap: Bitmap,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            val recognizer = OcrPluginLoader.getRecognizer(context)
            if (recognizer == null) {
                withContext(Dispatchers.Main) {
                    onError("OCR plugin is not installed or active")
                }
                bitmap.recycle()
                return@launch
            }

            val keepLineBreaks = context.prefs().getBoolean(OcrPluginLoader.PREF_OCR_KEEP_LINE_BREAKS, true)
            val trimWhitespace = context.prefs().getBoolean(OcrPluginLoader.PREF_OCR_TRIM_WHITESPACE, true)
            val autoCopy = context.prefs().getBoolean(OcrPluginLoader.PREF_OCR_AUTO_COPY, false)

            try {
                var lines = recognizer.recognize(bitmap, keepLineBreaks) ?: emptyList()
                if (trimWhitespace) {
                    lines = lines.map { it.trim() }.filter { it.isNotEmpty() }
                }
                if (autoCopy && lines.isNotEmpty()) {
                    val fullText = lines.joinToString(if (keepLineBreaks) "\n" else " ")
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("OCR Extracted Text", fullText))
                }
                withContext(Dispatchers.Main) {
                    if (lines.isEmpty()) {
                        onError("No text recognized in image")
                    } else {
                        onSuccess(lines)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "OCR recognition error", e)
                withContext(Dispatchers.Main) {
                    onError("OCR failed: ${e.message ?: "Unknown error"}")
                }
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }
}
