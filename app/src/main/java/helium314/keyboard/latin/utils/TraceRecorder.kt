// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET
import helium314.keyboard.latin.common.InputPointers
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Debug recorder for gesture input sessions.
 *
 * When [helium314.keyboard.latin.settings.DebugSettings.PREF_RECORD_INPUT_TRACES] is enabled,
 * each completed gesture/batch-input session is written to a JSON file under
 * `<context.filesDir>/input_traces/trace-<epochMillis>.json`.
 *
 * All file I/O runs on a single-threaded background executor so the input path is never blocked.
 *
 * ## JSON schema (version 1)
 * ```json
 * {
 *   "version": 1,
 *   "createdAt": <epochMillis>,
 *   "keyboard": {
 *     "width":      <int px>,     // Keyboard.mOccupiedWidth
 *     "height":     <int px>,     // Keyboard.mOccupiedHeight
 *     "mainLayout": "<string>",   // subtype KEYBOARD_LAYOUT_SET extra value, or ""
 *     "locale":     "<BCP-47>"    // keyboard.mId.getLocale().toLanguageTag(), or ""
 *   },
 *   "committedWord": "<string>",  // composedText from onUpdateTailBatchInputCompleted, or ""
 *   "pointers": [
 *     { "id": <int>, "x": <int>, "y": <int>, "t": <int ms> },
 *     ...
 *   ]
 * }
 * ```
 *
 * `pointers` is the [InputPointers] array flattened in index order (0 until
 * [InputPointers.getPointerSize]): `getXCoordinates()[i]`, `getYCoordinates()[i]`,
 * `getPointerIds()[i]`, `getTimes()[i]`.
 *
 * For multi-part gestures (two-thumb / combining mode) the pointers reflect whatever
 * was in [helium314.keyboard.latin.WordComposer.getInputPointers] at the moment
 * [helium314.keyboard.latin.inputlogic.InputLogic.onUpdateTailBatchInputCompleted] computed
 * `composedText`; that may be a merged trail rather than a raw single-finger stroke.
 */
object TraceRecorder {

    private val executor: Executor = Executors.newSingleThreadExecutor()

    /**
     * Capture one gesture session and schedule an async write to disk.
     *
     * Must be called from the UI thread after `composedText` is known.
     * Copies all mutable data synchronously before returning so the caller's
     * arrays/objects can be mutated freely afterwards.
     *
     * @param context       used only for [Context.getFilesDir]; must not be null.
     * @param batchPointers the gesture trace (WordComposer.getInputPointers() snapshot).
     * @param committedWord the word that will be committed for this batch, or "" if unknown.
     * @param keyboard      the active keyboard at commit time; may be null (geometry will be 0/empty).
     */
    fun record(
        context: Context,
        batchPointers: InputPointers,
        committedWord: String,
        keyboard: Keyboard?,
    ) {
        val size = batchPointers.pointerSize
        // Copy arrays immediately on the calling thread — InputPointers is not thread-safe
        // and the caller may reset it right after we return.
        val xs = batchPointers.xCoordinates.copyOf(size)
        val ys = batchPointers.yCoordinates.copyOf(size)
        val ids = batchPointers.pointerIds.copyOf(size)
        val ts = batchPointers.times.copyOf(size)

        val now = System.currentTimeMillis()
        val kbWidth = keyboard?.mOccupiedWidth ?: 0
        val kbHeight = keyboard?.mOccupiedHeight ?: 0
        val mainLayout = keyboard?.mId?.mSubtype?.getExtraValueOf(KEYBOARD_LAYOUT_SET) ?: ""
        val locale = keyboard?.mId?.getLocale()?.toLanguageTag() ?: ""
        // Capture the application context so we never leak the IME service.
        val appContext = context.applicationContext

        executor.execute {
            try {
                val dir = File(appContext.filesDir, "input_traces")
                dir.mkdirs()
                val file = File(dir, "trace-$now.json")
                file.writeText(
                    buildJson(now, kbWidth, kbHeight, mainLayout, locale, committedWord,
                              size, xs, ys, ids, ts)
                )
            } catch (_: Exception) {
                // Best-effort — never propagate exceptions back onto the input path.
            }
        }
    }

    private fun buildJson(
        now: Long,
        width: Int,
        height: Int,
        mainLayout: String,
        locale: String,
        committedWord: String,
        size: Int,
        xs: IntArray,
        ys: IntArray,
        ids: IntArray,
        ts: IntArray,
    ): String {
        val sb = StringBuilder(64 + size * 40)
        sb.append("{\"version\":1")
        sb.append(",\"createdAt\":").append(now)
        sb.append(",\"keyboard\":{")
        sb.append("\"width\":").append(width)
        sb.append(",\"height\":").append(height)
        sb.append(",\"mainLayout\":\"").append(jsonEscape(mainLayout)).append('"')
        sb.append(",\"locale\":\"").append(jsonEscape(locale)).append('"')
        sb.append('}')
        sb.append(",\"committedWord\":\"").append(jsonEscape(committedWord)).append('"')
        sb.append(",\"pointers\":[")
        for (i in 0 until size) {
            if (i > 0) sb.append(',')
            sb.append("{\"id\":").append(ids[i])
            sb.append(",\"x\":").append(xs[i])
            sb.append(",\"y\":").append(ys[i])
            sb.append(",\"t\":").append(ts[i])
            sb.append('}')
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun jsonEscape(s: String): String {
        if (s.none { it == '"' || it == '\\' || it.code < 0x20 }) return s
        val sb = StringBuilder(s.length + 4)
        for (c in s) {
            when {
                c == '"'      -> sb.append("\\\"")
                c == '\\'     -> sb.append("\\\\")
                c == '\n'     -> sb.append("\\n")
                c == '\r'     -> sb.append("\\r")
                c == '\t'     -> sb.append("\\t")
                c.code < 0x20 -> sb.append("\\u%04x".format(c.code))
                else          -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
