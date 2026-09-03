// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
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
        val colors = Settings.getValues().mColors
        colors.setBackground(ocrStrip, ColorType.STRIP_BACKGROUND)
        ocrStrip.removeAllViews()

        val stripHeight = ResourceUtils.getSuggestionsStripHeight(context.resources)
        val defaultStripHeight = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        val defaultEdgeWidth = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width)
        val ratio = if (defaultStripHeight > 0) defaultEdgeWidth.toFloat() / defaultStripHeight else 0.9f
        val keyDimension = (stripHeight * ratio).toInt().coerceAtLeast(1)

        fun createKey(key: ToolbarKey, onClick: () -> Unit) = createToolbarKey(context, key).apply {
            layoutParams = LinearLayout.LayoutParams(keyDimension, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            setOnClickListener {
                AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
                onClick()
            }
        }

        // 1. Close / Back to Keyboard
        ocrStrip.addView(createKey(ToolbarKey.CLOSE_HISTORY) {
            listener?.onClose()
        })

        // 2. Retake / Back to Camera
        ocrStrip.addView(createKey(ToolbarKey.OCR) {
            listener?.onRetake()
        })

        // 3. Select All Text
        ocrStrip.addView(createKey(ToolbarKey.SELECT_ALL) {
            resultEditText?.selectAll()
        })

        // 4. Copy All / Selected Text
        ocrStrip.addView(createKey(ToolbarKey.COPY) {
            val text = resultEditText?.text?.toString() ?: ""
            if (text.isNotBlank()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("OCR Text", text)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(context, R.string.toast_msg_clipboard_copy, Toast.LENGTH_SHORT).show()
            }
        })

        // 5. Cut Selected Text
        ocrStrip.addView(createKey(ToolbarKey.CUT) {
            val edit = resultEditText ?: return@createKey
            val selStart = edit.selectionStart
            val selEnd = edit.selectionEnd
            if (selStart in 0 until selEnd) {
                val selectedText = edit.text.substring(selStart, selEnd)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("OCR Text", selectedText))
                edit.text.delete(selStart, selEnd)
                Toast.makeText(context, R.string.toast_msg_clipboard_copy, Toast.LENGTH_SHORT).show()
            }
        })

        // 6. Clear All Text
        ocrStrip.addView(createKey(ToolbarKey.CLEAR_CLIPBOARD) {
            resultEditText?.setText("")
        })

        // 7. Insert / Paste into Target Field
        ocrStrip.addView(createKey(ToolbarKey.PASTE) {
            val text = resultEditText?.text?.toString() ?: ""
            listener?.onInsertText(text)
        })
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
        val ocrStrip = KeyboardSwitcher.getInstance().ocrStrip
        if (ocrStrip != null) {
            colors.setBackground(ocrStrip, ColorType.STRIP_BACKGROUND)
        }
    }
}
