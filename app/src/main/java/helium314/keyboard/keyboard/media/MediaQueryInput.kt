// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.view.KeyEvent
import android.widget.EditText
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants

/** Routes query edits locally; never forwards a functional key to the application editor. */
internal class MediaQueryInput(
    private val field: EditText,
    private val submit: () -> Unit,
    private val layoutKey: (Int) -> Unit,
    private val rtl: () -> Boolean
) : KeyboardActionListener.Adapter() {
    private var deleteAnchor = -1

    override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) {
        when (primaryCode) {
            Constants.CODE_ENTER -> if (!isKeyRepeat) submit()
            KeyCode.DELETE -> delete()
            KeyCode.SHIFT, KeyCode.CAPS_LOCK, KeyCode.SYMBOL, KeyCode.SYMBOL_ALPHA, KeyCode.ALPHA ->
                layoutKey(primaryCode)
            else -> if (Character.isValidCodePoint(primaryCode) && primaryCode >= Constants.CODE_SPACE &&
                primaryCode !in 0xD800..0xDFFF) {
                onTextInput(String(Character.toChars(primaryCode)))
            }
        }
    }

    override fun onTextInput(text: String?) {
        if (text == null) return
        val start = field.selectionStart.coerceAtLeast(0)
        val end = field.selectionEnd.coerceAtLeast(0)
        field.text.replace(minOf(start, end), maxOf(start, end), text)
        field.setSelection(minOf(start, end) + text.length)
    }

    private fun delete() {
        val start = field.selectionStart.coerceAtLeast(0)
        val end = field.selectionEnd.coerceAtLeast(0)
        if (start != end) {
            field.text.delete(minOf(start, end), maxOf(start, end))
        } else if (start > 0) {
            val previous = Character.offsetByCodePoints(field.text, start, -1)
            field.text.delete(previous, start)
        }
    }

    override fun onHorizontalSpaceSwipe(steps: Int): Boolean {
        val delta = if (rtl()) -steps else steps
        var cursor = field.selectionStart.coerceAtLeast(0)
        repeat(kotlin.math.abs(delta).coerceAtMost(field.length())) {
            if (delta < 0 && cursor > 0) cursor = Character.offsetByCodePoints(field.text, cursor, -1)
            if (delta > 0 && cursor < field.length()) cursor = Character.offsetByCodePoints(field.text, cursor, 1)
        }
        field.setSelection(cursor)
        return true
    }

    override fun onMoveDeletePointer(steps: Int) {
        if (deleteAnchor < 0) deleteAnchor = field.selectionEnd.coerceAtLeast(0)
        val cursor = (field.selectionStart + steps).coerceIn(0, deleteAnchor)
        val safeCursor = if (cursor > 0 && cursor < field.length() &&
            Character.isLowSurrogate(field.text[cursor])) cursor - 1 else cursor
        field.setSelection(safeCursor, deleteAnchor)
    }

    override fun onUpWithDeletePointerActive() {
        if (field.selectionStart != field.selectionEnd) delete()
        deleteAnchor = -1
    }

    override fun onCancelInput() { deleteAnchor = -1 }
    override fun onReleaseKey(primaryCode: Int, withSliding: Boolean) { deleteAnchor = -1 }

    override fun onKeyDown(keyCode: Int, keyEvent: KeyEvent): Boolean {
        if ((keyEvent.isCtrlPressed && !keyEvent.isAltPressed) || keyEvent.isMetaPressed) {
            if (keyCode == KeyEvent.KEYCODE_A) field.selectAll()
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> if (keyEvent.repeatCount == 0) submit()
            KeyEvent.KEYCODE_DEL -> delete()
            KeyEvent.KEYCODE_DPAD_LEFT -> onHorizontalSpaceSwipe(if (rtl()) 1 else -1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> onHorizontalSpaceSwipe(if (rtl()) -1 else 1)
            else -> keyEvent.unicodeChar.takeIf { it >= Constants.CODE_SPACE && Character.isValidCodePoint(it) }
                ?.let { onTextInput(String(Character.toChars(it))) }
        }
        return true
    }
}
