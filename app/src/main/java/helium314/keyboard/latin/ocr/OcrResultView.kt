// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils

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

    private var headerText: TextView? = null
    private var resultEditText: EditText? = null
    private var copyBtn: ImageButton? = null
    private var closeBtn: ImageButton? = null
    private var retakeBtn: Button? = null
    private var insertBtn: Button? = null

    private var listener: OcrResultListener? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        headerText = findViewById(R.id.ocr_result_header)
        resultEditText = findViewById(R.id.ocr_result_edittext)
        copyBtn = findViewById(R.id.btn_ocr_copy)
        closeBtn = findViewById(R.id.btn_ocr_result_close)
        retakeBtn = findViewById(R.id.btn_ocr_retake)
        insertBtn = findViewById(R.id.btn_ocr_insert)

        copyBtn?.setOnClickListener {
            val text = resultEditText?.text?.toString() ?: ""
            if (text.isNotBlank()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("OCR Text", text)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(context, R.string.toast_msg_clipboard_copy, Toast.LENGTH_SHORT).show()
            }
        }

        closeBtn?.setOnClickListener {
            listener?.onClose()
        }

        retakeBtn?.setOnClickListener {
            listener?.onRetake()
        }

        insertBtn?.setOnClickListener {
            val text = resultEditText?.text?.toString() ?: ""
            listener?.onInsertText(text)
        }
    }

    fun setListener(listener: OcrResultListener) {
        this.listener = listener
    }

    fun setResultText(lines: List<String>) {
        val fullText = lines.joinToString("\n")
        resultEditText?.setText(fullText)
        resultEditText?.setSelection(fullText.length)
    }

    fun applyColors(colors: Colors) {
        colors.setBackground(this, ColorType.MAIN_BACKGROUND)
        headerText?.setTextColor(colors.get(ColorType.KEY_TEXT))
        resultEditText?.let {
            it.setTextColor(colors.get(ColorType.KEY_TEXT))
            it.setHintTextColor(colors.get(ColorType.KEY_HINT_TEXT))
        }
        copyBtn?.let { colors.setColor(it, ColorType.ACTION_KEY_ICON) }
        closeBtn?.let { colors.setColor(it, ColorType.ACTION_KEY_ICON) }
        insertBtn?.let {
            colors.setBackground(it, ColorType.ACTION_KEY_BACKGROUND)
            it.setTextColor(colors.get(ColorType.ACTION_KEY_ICON))
        }
        retakeBtn?.let {
            it.setTextColor(colors.get(ColorType.KEY_TEXT))
        }
    }
}
