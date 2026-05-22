// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.ClipboardDao

class ClipboardClipEditActivity : Activity() {
    private var clipId: Long = INVALID_CLIP_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        clipId = intent.getLongExtra(EXTRA_CLIP_ID, INVALID_CLIP_ID)
        if (clipId == INVALID_CLIP_ID) {
            finish()
            return
        }

        val dao = ClipboardDao.getInstance(this)
        val entry = try {
            dao?.get(clipId)
        } catch (_: NoSuchElementException) {
            null
        }
        if (dao == null || entry == null || entry.imageUri != null) {
            finish()
            return
        }

        val editor = buildEditor(entry.text)
        setContentView(buildContentView(editor, dao))

        editor.requestFocus()
        editor.post {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun buildContentView(editor: EditText, dao: ClipboardDao): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val cancel = Button(this).apply {
            text = getString(android.R.string.cancel)
            setOnClickListener { closeAndReturnToClipboard() }
        }
        val title = TextView(this).apply {
            text = getString(R.string.clipboard_edit_dialog_title)
            gravity = Gravity.CENTER
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val save = Button(this).apply {
            text = getString(R.string.save)
            setOnClickListener {
                dao.updateText(clipId, editor.text?.toString().orEmpty())
                closeAndReturnToClipboard()
            }
        }

        toolbar.addView(cancel)
        toolbar.addView(title)
        toolbar.addView(save)
        root.addView(toolbar)
        root.addView(editor)
        return root
    }

    private fun buildEditor(text: String): EditText {
        return EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(280)
            )
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(16), dp(8), dp(16), dp(16))
            minLines = 8
            setSingleLine(false)
            setHorizontallyScrolling(false)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
            hint = getString(R.string.clipboard_edit_dialog_hint)
            setText(text)
            setSelection(length())
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun closeAndReturnToClipboard() {
        finish()
        Handler(Looper.getMainLooper()).post {
            KeyboardSwitcher.getInstance().setClipboardKeyboard()
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_CLIP_ID = "helium314.keyboard.clipboard.CLIP_ID"
        private const val INVALID_CLIP_ID = -1L

        fun createIntent(context: Context, clipId: Long): Intent {
            return Intent(context, ClipboardClipEditActivity::class.java)
                .putExtra(EXTRA_CLIP_ID, clipId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
