// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ocr

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.MainThread
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@MainThread
class OcrPipeline(
    private val context: Context,
    private val getRecognizer: () -> ITextRecognizer? = { OcrPluginLoader.getRecognizer(context) }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var requestJob: Job? = null
    @Volatile private var generation = 0L
    private var released = false

    companion object {
        private const val TAG = "OcrPipeline"
    }

    fun processImage(
        bitmap: Bitmap,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit,
        isRequestCurrent: () -> Boolean = { true },
        onInsertText: ((String) -> Unit)? = null
    ) {
        stop()
        if (released || !isRequestCurrent()) {
            bitmap.recycle()
            return
        }
        val request = generation
        requestJob = scope.launch {
            try {
                coroutineContext.ensureActive()
                if (request != generation) return@launch
                val recognizer = getRecognizer()
                // Plugin initialization and native recognition may ignore coroutine cancellation.
                coroutineContext.ensureActive()
                if (request != generation) return@launch
                if (recognizer == null) {
                    withContext(Dispatchers.Main) {
                        if (request == generation && isRequestCurrent()) {
                            onError("OCR plugin is not installed or active")
                        }
                    }
                    return@launch
                }
                val keepLineBreaks = context.prefs().getBoolean(OcrPluginLoader.PREF_OCR_KEEP_LINE_BREAKS, true)
                val autoCopy = context.prefs().getBoolean(OcrPluginLoader.PREF_OCR_AUTO_COPY, false)
                val rawLines = recognizer.recognize(bitmap, keepLineBreaks) ?: emptyList()
                coroutineContext.ensureActive()
                if (request != generation) return@launch
                val lines = OcrTextFormatter.format(context, rawLines)
                withContext(Dispatchers.Main) {
                    if (request != generation || !isRequestCurrent()) return@withContext
                    if (autoCopy && lines.isNotEmpty()) {
                        val fullText = lines.joinToString(if (keepLineBreaks) "\n" else " ")
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("OCR Extracted Text", fullText))
                    }
                    if (request != generation || !isRequestCurrent()) return@withContext
                    if (lines.isEmpty()) {
                        onError("No text recognized in image")
                    } else if (onInsertText != null &&
                        context.prefs().getBoolean(OcrPluginLoader.PREF_OCR_AUTO_INSERT, false)) {
                        onInsertText(lines.joinToString(if (keepLineBreaks) "\n" else " "))
                    } else {
                        onSuccess(lines)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "OCR recognition error", e)
                withContext(Dispatchers.Main) {
                    if (request == generation && isRequestCurrent()) {
                        onError("OCR failed: ${e.message ?: "Unknown error"}")
                    }
                }
            }
        }.also { job ->
            // Also dispose a bitmap when a queued coroutine is cancelled before it ever starts.
            job.invokeOnCompletion { if (!bitmap.isRecycled) bitmap.recycle() }
        }
    }

    fun stop() {
        generation++
        requestJob?.cancel()
        requestJob = null
    }

    fun release() {
        stop()
        released = true
        scope.cancel()
    }
}
