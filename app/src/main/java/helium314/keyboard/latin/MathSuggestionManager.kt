// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.core.view.isGone
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.calculator.CalculatorHistoryManager
import helium314.keyboard.latin.calculator.MathEvaluator
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.databinding.OtpSuggestionBinding
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.ToolbarKey

class MathSuggestionManager(private val latinIME: LatinIME) {

    private var mathSuggestionView: View? = null
    private var lastDismissedExpression: String? = null
    private val historyManager by lazy { CalculatorHistoryManager.getInstance(latinIME) }

    fun getMathSuggestionView(parent: ViewGroup?): View? {
        mathSuggestionView?.isGone = true
        mathSuggestionView = null
        if (parent == null) return null
        if (!isEligible()) return null

        val connection = latinIME.mInputLogic?.connection ?: return null
        val editor = latinIME.currentInputEditorInfo ?: return null
        val inputConnection = latinIME.currentInputConnection ?: return null
        val attributes = latinIME.mSettings.current.mInputAttributes
        val sessionGeneration = latinIME.inputSessionGeneration
        val editorIdentity = EditorIdentity(editor)
        fun isCurrentEditor() = isEligible()
            && latinIME.inputSessionGeneration == sessionGeneration
            && latinIME.currentInputEditorInfo === editor
            && EditorIdentity(editor) == editorIdentity
            && latinIME.currentInputConnection === inputConnection
            && latinIME.mInputLogic?.connection === connection
            && latinIME.mSettings.current.mInputAttributes === attributes
        val snapshot = readSnapshot(inputConnection, connection) ?: return null
        if (!isCurrentEditor()) return null
        val textBefore = snapshot.textBefore
        if (!textBefore.contains('=')) return null

        val incognito = latinIME.mSettings.current.mIncognitoModeEnabled
        val previousAnswer = if (incognito) null else historyManager.getLastAnswer()?.let {
            runCatching { java.math.BigDecimal(it) }.getOrNull()
        }
        val match = MathEvaluator.evaluateInline(textBefore, previousAnswer) ?: return null
        // A bounded read may begin halfway through an operand. Require a known left boundary.
        if (match.startIndex == 0 && snapshot.cursor > textBefore.length) return null
        if (match.expression == lastDismissedExpression) return null

        val binding = OtpSuggestionBinding.inflate(LayoutInflater.from(latinIME), parent, false)
        val textView = binding.otpSuggestionText
        latinIME.mSettings.getCustomTypeface()?.let { textView.typeface = it }
        
        textView.text = match.resultFormatted
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)

        textView.setOnClickListener {
            // The strip can outlive an editor, a settings reload, or a pending selection update.
            // Check privacy and ownership before making any new editor reads.
            if (mathSuggestionView !== binding.root || !isCurrentEditor()
                || readSnapshot(inputConnection, connection) != snapshot || !isCurrentEditor()) {
                binding.root.isGone = true
                if (mathSuggestionView === binding.root) mathSuggestionView = null
                return@setOnClickListener
            }

            // Use the normal input-state/cache machinery and a checked selection, rather than
            // deleting an unchecked number of characters from whichever editor is now active.
            latinIME.mInputLogic.finishInput()
            connection.beginBatchEdit()
            try {
                val start = snapshot.cursor - textBefore.length + match.startIndex
                if (!connection.setSelection(start, snapshot.cursor)) {
                    connection.setSelection(snapshot.cursor, snapshot.cursor)
                    return@setOnClickListener
                }
                if (inputConnection.getSelectedText(0)?.toString() != match.fullMatchedText) {
                    connection.setSelection(snapshot.cursor, snapshot.cursor)
                    return@setOnClickListener
                }
                if (!isCurrentEditor()) return@setOnClickListener
                val replacement = match.resultFormatted + match.trailingWhitespace
                latinIME.onTextInput(replacement)
                if (!isCurrentEditor()) return@setOnClickListener
                val applied = readSnapshot(inputConnection, connection)
                val expectedBefore = (textBefore.take(match.startIndex) + replacement).takeLast(60)
                if (applied?.cursor != start + replacement.length
                    || !applied.textBefore.endsWith(expectedBefore)) {
                    // RichInputConnection optimistically updates its cache even if an editor
                    // refuses commitText. Resync it, and never record an unapplied calculation.
                    val actual = inputConnection.getExtractedText(ExtractedTextRequest(), 0)
                    if (actual != null && isCurrentEditor()) {
                        connection.resetCachesUponCursorMoveAndReturnSuccess(
                            actual.startOffset + actual.selectionStart,
                            actual.startOffset + actual.selectionEnd, false)
                    }
                    return@setOnClickListener
                }
            } finally {
                connection.endBatchEdit()
                binding.root.isGone = true
                if (mathSuggestionView === binding.root) mathSuggestionView = null
            }
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS
            )
            if (!incognito && !latinIME.mSettings.current.mIncognitoModeEnabled) {
                historyManager.addEntry(match.expression, match.resultFormatted)
            }
            lastDismissedExpression = match.expression
        }

        val closeButton = binding.otpSuggestionClose
        closeButton.setImageDrawable(latinIME.mKeyboardSwitcher.keyboard?.mIconsSet?.getIconDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase()))
        closeButton.setOnClickListener {
            lastDismissedExpression = match.expression
            removeMathSuggestion()
        }

        val colors = latinIME.mSettings.current.mColors
        textView.setTextColor(colors.get(ColorType.KEY_TEXT))
        colors.setColor(closeButton, ColorType.REMOVE_SUGGESTION_ICON)
        colors.setBackground(binding.root, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)

        mathSuggestionView = binding.root
        return mathSuggestionView
    }

    fun removeMathSuggestion() {
        val view = mathSuggestionView ?: return
        mathSuggestionView = null
        if (view.parent != null && !view.isGone) {
            latinIME.setNeutralSuggestionStrip()
            latinIME.mHandler.postResumeSuggestions(false)
        }
        view.isGone = true
    }

    private fun isEligible(): Boolean {
        if (!latinIME.currentInputStarted) return false
        val editor = latinIME.currentInputEditorInfo ?: return false
        val settings = latinIME.mSettings.current
        if (!settings.mInlineMathCalculation) return false
        val type = editor.inputType
        val inputClass = type and InputType.TYPE_MASK_CLASS
        return (inputClass == InputType.TYPE_CLASS_TEXT || inputClass == InputType.TYPE_CLASS_NUMBER)
            && !InputTypeUtils.isPasswordInputType(type)
            && !InputTypeUtils.isVisiblePasswordInputType(type)
            && !InputTypeUtils.isUriOrEmailType(type)
            && (inputClass != InputType.TYPE_CLASS_TEXT
                || type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0)
            && editor.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
            && !settings.mInputAttributes.mIsPasswordField
            && !settings.mInputAttributes.mNoLearning
    }

    private data class EditorIdentity(
        val packageName: String?,
        val fieldId: Int,
        val fieldName: String?,
        val inputType: Int,
        val imeOptions: Int,
        val privateImeOptions: String?
    ) {
        constructor(editor: EditorInfo) : this(editor.packageName, editor.fieldId, editor.fieldName,
            editor.inputType, editor.imeOptions, editor.privateImeOptions)
    }

    private data class Snapshot(val cursor: Int, val textBefore: String)

    private fun readSnapshot(raw: InputConnection, connection: RichInputConnection): Snapshot? {
        // Like RichInputConnection.reloadCursorPosition, request selection metadata only.
        // Cached text alone cannot detect edits or cursor moves whose callbacks are still pending.
        val extracted = raw.getExtractedText(ExtractedTextRequest(), 0) ?: return null
        if (extracted.selectionStart < 0 || extracted.selectionStart != extracted.selectionEnd) return null
        val cursor = extracted.startOffset + extracted.selectionStart
        if (cursor != connection.expectedSelectionStart
            || cursor != connection.expectedSelectionEnd) return null
        val before = raw.getTextBeforeCursor(60, 0)?.toString() ?: return null
        if (before.length > cursor) return null
        return Snapshot(cursor, before)
    }
}
