// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
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

        // 1. Close button (Leftmost)
        val closeBtn = createToolbarKey(context, ToolbarKey.CLOSE_HISTORY).apply {
            setOnClickListener {
                AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
                listener?.onClose()
            }
        }
        ocrStrip.addView(closeBtn)

        // 2. Retake Camera button
        val retakeBtn = createToolbarKey(context, ToolbarKey.OCR).apply {
            setOnClickListener {
                AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
                listener?.onRetake()
            }
        }
        ocrStrip.addView(retakeBtn)

        // 3. Select All button
        val selectAllBtn = createToolbarKey(context, ToolbarKey.SELECT_ALL).apply {
            setOnClickListener {
                AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
                resultEditText?.selectAll()
            }
        }
        ocrStrip.addView(selectAllBtn)

        // 4. Copy All button
        val copyBtn = createToolbarKey(context, ToolbarKey.COPY).apply {
            setOnClickListener {
                AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
                val text = resultEditText?.text?.toString() ?: ""
                if (text.isNotBlank()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("OCR Text", text)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, R.string.toast_msg_clipboard_copy, Toast.LENGTH_SHORT).show()
                }
            }
        }
        ocrStrip.addView(copyBtn)

        // 5. Insert Text button
        val insertBtn = createToolbarKey(context, ToolbarKey.PASTE).apply {
            setOnClickListener {
                AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
                val text = resultEditText?.text?.toString() ?: ""
                listener?.onInsertText(text)
            }
        }
        ocrStrip.addView(insertBtn)
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
