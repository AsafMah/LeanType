// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class VoiceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val statusTextView: TextView
    private val progressBar: ProgressBar
    private val doneButton: Button
    private val cancelButton: Button

    private var onDoneListener: Runnable? = null
    private var onCancelListener: Runnable? = null

    init {
        isClickable = true
        isFocusable = true

        val backgroundDrawable = GradientDrawable().apply {
            setColor(Color.parseColor("#EE1E1E24")) // Dark semi-opaque background
            cornerRadius = 0f
        }
        background = backgroundDrawable

        val containerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 24)
        }

        statusTextView = TextView(context).apply {
            text = "Listening..."
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                16
            ).apply {
                topMargin = 16
                bottomMargin = 16
            }
            layoutParams = lp
        }

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        cancelButton = Button(context).apply {
            text = "Cancel"
            setOnClickListener { onCancelListener?.run() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = 16
            }
            layoutParams = lp
        }

        doneButton = Button(context).apply {
            text = "Done"
            setOnClickListener { onDoneListener?.run() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }

        buttonRow.addView(cancelButton)
        buttonRow.addView(doneButton)

        containerLayout.addView(statusTextView)
        containerLayout.addView(progressBar)
        containerLayout.addView(buttonRow)

        val frameParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.CENTER
        }
        addView(containerLayout, frameParams)
    }

    fun setOnDoneListener(listener: Runnable?) {
        this.onDoneListener = listener
    }

    fun setOnCancelListener(listener: Runnable?) {
        this.onCancelListener = listener
    }

    fun updateState(state: VoiceInputManager.VoiceState) {
        when (state) {
            VoiceInputManager.VoiceState.IDLE -> {
                visibility = GONE
            }
            VoiceInputManager.VoiceState.CONNECTING_PLUGIN -> {
                visibility = VISIBLE
                statusTextView.text = "Connecting to Voice Plugin..."
                progressBar.visibility = VISIBLE
                doneButton.isEnabled = false
            }
            VoiceInputManager.VoiceState.STARTING_SESSION -> {
                visibility = VISIBLE
                statusTextView.text = "Starting dictation..."
                progressBar.visibility = VISIBLE
                doneButton.isEnabled = false
            }
            VoiceInputManager.VoiceState.RECORDING -> {
                visibility = VISIBLE
                statusTextView.text = "Listening... Speak now"
                progressBar.visibility = VISIBLE
                doneButton.isEnabled = true
            }
            VoiceInputManager.VoiceState.PROCESSING_FINAL -> {
                visibility = VISIBLE
                statusTextView.text = "Processing transcription..."
                progressBar.visibility = VISIBLE
                doneButton.isEnabled = false
            }
            VoiceInputManager.VoiceState.ERROR -> {
                visibility = VISIBLE
                statusTextView.text = "Voice error"
                progressBar.visibility = GONE
                doneButton.isEnabled = false
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        super.onTouchEvent(event)
        return true // Consumes background touches to block keyboard keys
    }
}
