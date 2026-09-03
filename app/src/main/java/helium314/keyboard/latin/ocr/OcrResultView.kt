// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.createToolbarKey
import helium314.keyboard.latin.utils.setToolbarButtonActivatedState

class OcrResultView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    interface OcrResultListener {
        fun onInsertText(text: String)
        fun onRetake()
        fun onClose()
    }

    private var resultEditText: EditText? = null
    private var listener: OcrResultListener? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        resultEditText = findViewById(R.id.ocr_result_edittext)
    }

    fun setListener(listener: OcrResultListener) {
        this.listener = listener
        setupToolbarStrip()
    }

    fun setupToolbarStrip() {
        val ocrStrip = KeyboardSwitcher.getInstance().ocrStrip ?: return
        val ocrScrollView = KeyboardSwitcher.getInstance().ocrStripScrollView
        val colors = Settings.getValues().mColors
        if (ocrScrollView != null) {
            colors.setBackground(ocrScrollView, ColorType.STRIP_BACKGROUND)
        }
        colors.setBackground(ocrStrip, ColorType.STRIP_BACKGROUND)
        ocrStrip.removeAllViews()

        val stripHeight = ResourceUtils.getSuggestionsStripHeight(context.resources)
        val defaultStripHeight = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        val defaultEdgeWidth = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width)
        val ratio = if (defaultStripHeight > 0) defaultEdgeWidth.toFloat() / defaultStripHeight else 0.9f
        val keyDimension = (stripHeight * ratio).toInt().coerceAtLeast(1)

        val keys = mutableListOf<View>()

        fun addKey(key: ToolbarKey, onClick: () -> Unit) {
            val button = createToolbarKey(context, key).apply {
                setOnClickListener {
                    AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
                    onClick()
                }
            }
            setToolbarButtonActivatedState(button)
            ocrStrip.addView(button)
            keys.add(button)
        }

        // 1. Close / Return to keyboard
        addKey(ToolbarKey.CLOSE_HISTORY) {
            listener?.onClose()
        }

        // 2. Retake / Camera Viewfinder
        addKey(ToolbarKey.OCR) {
            listener?.onRetake()
        }

        // 3. Select All text
        addKey(ToolbarKey.SELECT_ALL) {
            resultEditText?.selectAll()
        }

        // 4. Copy text to clipboard
        addKey(ToolbarKey.COPY) {
            val edit = resultEditText ?: return@addKey
            val selStart = edit.selectionStart
            val selEnd = edit.selectionEnd
            val textToCopy = if (selStart in 0 until selEnd) {
                edit.text.substring(selStart, selEnd)
            } else {
                edit.text?.toString() ?: ""
            }
            if (textToCopy.isNotBlank()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("OCR Text", textToCopy))
                Toast.makeText(context, R.string.toast_msg_clipboard_copy, Toast.LENGTH_SHORT).show()
            }
        }

        // 5. Cut selected text
        addKey(ToolbarKey.CUT) {
            val edit = resultEditText ?: return@addKey
            val selStart = edit.selectionStart
            val selEnd = edit.selectionEnd
            if (selStart in 0 until selEnd) {
                val selectedText = edit.text.substring(selStart, selEnd)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("OCR Text", selectedText))
                edit.text.delete(selStart, selEnd)
                Toast.makeText(context, R.string.toast_msg_clipboard_copy, Toast.LENGTH_SHORT).show()
            }
        }

        // 6. Clear all text
        addKey(ToolbarKey.CLEAR_CLIPBOARD) {
            resultEditText?.setText("")
        }

        // 7. Insert / Paste text into input field
        addKey(ToolbarKey.PASTE) {
            val text = resultEditText?.text?.toString() ?: ""
            listener?.onInsertText(text)
        }

        fun applyKeyLayout() {
            val containerWidth = (ocrScrollView?.width?.takeIf { it > 0 }
                ?: ocrStrip.width.takeIf { it > 0 }
                ?: ResourceUtils.getKeyboardWidth(context, Settings.getValues())).coerceAtLeast(0)

            val count = keys.size
            if (count == 0) return

            val isAutoSpan = Settings.getValues().mAutoSpanToolbarKeys
            val minSpannedKeyWidth = (keyDimension * 1.25f).toInt()
            val canSpan = containerWidth > 0 && (containerWidth / count >= minSpannedKeyWidth)
            val useEqualSpacing = isAutoSpan && canSpan

            val alignmentGravity = when (Settings.getValues().mToolbarKeysAlignment) {
                "left" -> Gravity.START or Gravity.CENTER_VERTICAL
                "center" -> Gravity.CENTER
                else -> Gravity.END or Gravity.CENTER_VERTICAL
            }
            ocrStrip.gravity = if (useEqualSpacing) Gravity.NO_GRAVITY else alignmentGravity

            val spannedLayoutParams = LinearLayout.LayoutParams(0, keyDimension, 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            val standardLayoutParams = LinearLayout.LayoutParams(keyDimension, keyDimension).apply {
                gravity = Gravity.CENTER_VERTICAL
            }

            for (keyView in keys) {
                keyView.layoutParams = if (useEqualSpacing) spannedLayoutParams else standardLayoutParams
            }
        }

        applyKeyLayout()
        ocrStrip.post { applyKeyLayout() }
    }

    fun setResultText(lines: List<String>) {
        val fullText = lines.joinToString("\n")
        resultEditText?.setText(fullText)
        resultEditText?.setSelection(fullText.length)
        setupToolbarStrip()
    }

    fun applyColors(colors: Colors) {
        colors.setBackground(this, ColorType.MAIN_BACKGROUND)
        resultEditText?.let {
            it.setTextColor(colors.get(ColorType.KEY_TEXT))
            it.setHintTextColor(colors.get(ColorType.KEY_HINT_TEXT))
        }
        val ocrScrollView = KeyboardSwitcher.getInstance().ocrStripScrollView
        if (ocrScrollView != null) {
            colors.setBackground(ocrScrollView, ColorType.STRIP_BACKGROUND)
        }
        val ocrStrip = KeyboardSwitcher.getInstance().ocrStrip
        if (ocrStrip != null) {
            colors.setBackground(ocrStrip, ColorType.STRIP_BACKGROUND)
        }
    }
}
