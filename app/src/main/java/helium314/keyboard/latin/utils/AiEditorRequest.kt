package helium314.keyboard.latin.utils

import android.os.Looper
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.RichInputConnection

/** Immutable editor/range ownership, checked again on Main immediately before delivery. */
class AiEditorRequest private constructor(
    private val ime: LatinIME,
    private val connection: InputConnection,
    private val generation: Long,
    private val state: State,
    val originalText: String,
    val hasSelection: Boolean,
) {
    fun isSameEditorSession(): Boolean = Looper.myLooper() == Looper.getMainLooper()
        && ime.inputSessionGeneration == generation
        && ime.currentInputConnection === connection

    fun isCurrent(): Boolean = isSameEditorSession() && read(connection) == state

    private data class State(val text: String, val start: Int, val end: Int)

    companion object {
        private fun read(connection: InputConnection): State? = try {
            val extracted = connection.getExtractedText(ExtractedTextRequest().apply {
                hintMaxChars = Int.MAX_VALUE
            }, 0)
            val text = extracted?.text?.toString()
            if (text == null || extracted.startOffset != 0 || extracted.partialStartOffset >= 0
                || extracted.selectionStart !in 0..text.length || extracted.selectionEnd !in 0..text.length
            ) null else State(text, extracted.selectionStart, extracted.selectionEnd)
        } catch (_: RuntimeException) {
            null
        }

        @JvmStatic fun capture(ime: LatinIME): AiEditorRequest? {
            val connection = ime.currentInputConnection ?: return null
            val state = read(connection) ?: return null
            return AiEditorRequest(ime, connection, ime.inputSessionGeneration, state, state.text,
                state.start != state.end)
        }

        @JvmStatic fun prepare(ime: LatinIME, richConnection: RichInputConnection, append: Boolean): AiEditorRequest? {
            val before = capture(ime) ?: return null
            val state = before.state
            val selected = state.start != state.end
            val source = if (selected) state.text.substring(minOf(state.start, state.end), maxOf(state.start, state.end))
                else state.text
            val start = if (append) {
                if (selected) state.end else state.text.length
            } else if (selected) state.start else 0
            val end = if (append) start else if (selected) state.end else state.text.length
            if (start != state.start || end != state.end) {
                val selectedRange = try {
                    richConnection.setSelection(start, end)
                } catch (_: RuntimeException) {
                    false
                }
                if (!selectedRange) return null
            }
            val expected = state.copy(start = start, end = end)
            val request = AiEditorRequest(ime, before.connection, before.generation, expected, source, selected)
            return request.takeIf { it.isCurrent() }
        }
    }
}
