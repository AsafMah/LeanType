// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.databinding.OtpSuggestionBinding
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ToolbarKey

/**
 * Optional, opt-in helper that surfaces one-time passcodes (OTPs) from incoming SMS as a
 * suggestion-strip chip the user can tap to insert (similar to the clipboard/screenshot
 * suggestions, see [ClipboardHistoryManager.getClipboardSuggestionView]).
 *
 * Privacy: OTPs are extracted strictly from SMS app notifications via [OtpNotificationListenerService]
 * when enabled in Settings. No SMS reading permissions are required.
 */
class OtpSuggestionManager(private val latinIME: LatinIME) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var otpSuggestionView: View? = null
    private var dontShowCurrentSuggestion = false

    fun start() {
        activeInstance = this
    }

    fun stop() {
        if (activeInstance === this) {
            activeInstance = null
        }
    }

    /**
     * Build the OTP suggestion chip if a recent code is available, else null.
     * Called from [LatinIME.tryShowOtpSuggestion].
     */
    fun getOtpSuggestionView(parent: ViewGroup?): View? {
        otpSuggestionView = null
        if (parent == null) return null
        if (!latinIME.mSettings.current.mAutoReadOtp) return null
        if (dontShowCurrentSuggestion) return null
        val otp = latestOtp ?: return null
        if (System.currentTimeMillis() - latestOtpTimestamp > RECENT_OTP_MILLIS) return null

        val binding = OtpSuggestionBinding.inflate(LayoutInflater.from(latinIME), parent, false)
        val textView = binding.otpSuggestionText
        latinIME.mSettings.getCustomTypeface()?.let { textView.typeface = it }
        textView.text = otp
        val icon = latinIME.mKeyboardSwitcher.keyboard?.mIconsSet?.getIconDrawable(ToolbarKey.NUMPAD.name.lowercase())
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        textView.setOnClickListener {
            dontShowCurrentSuggestion = true
            latinIME.onTextInput(otp)
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
            binding.root.isGone = true
        }
        val closeButton = binding.otpSuggestionClose
        closeButton.setImageDrawable(latinIME.mKeyboardSwitcher.keyboard?.mIconsSet?.getIconDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase()))
        closeButton.setOnClickListener { removeOtpSuggestion() }

        val colors = latinIME.mSettings.current.mColors
        textView.setTextColor(colors.get(ColorType.KEY_TEXT))
        icon?.let { colors.setColor(it, ColorType.KEY_ICON) }
        colors.setColor(closeButton, ColorType.REMOVE_SUGGESTION_ICON)
        colors.setBackground(binding.root, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)

        otpSuggestionView = binding.root
        return otpSuggestionView
    }

    private fun removeOtpSuggestion() {
        dontShowCurrentSuggestion = true
        val view = otpSuggestionView ?: return
        if (view.parent != null && !view.isGone) {
            latinIME.setNeutralSuggestionStrip()
            latinIME.mHandler.postResumeSuggestions(false)
        }
        view.isGone = true
    }

    companion object {
        private const val TAG = "OtpSuggestionManager"
        private const val RECENT_OTP_MILLIS = 60 * 1000L // OTP chip is offered for 60s after arrival
        private val codeRegex = Regex("\\b\\d{4,8}\\b")
        private val otpKeywordRegex = Regex(
            "otp|code|passcode|password|pin|verification|verify|one[- ]?time|2fa|auth",
            RegexOption.IGNORE_CASE
        )

        @Volatile private var latestOtp: String? = null
        @Volatile private var latestOtpTimestamp: Long = 0L
        @Volatile private var activeInstance: OtpSuggestionManager? = null

        /**
         * Called by [OtpNotificationListenerService] when an OTP is detected from an SMS notification.
         */
        fun onOtpReceived(otp: String) {
            latestOtp = otp
            latestOtpTimestamp = System.currentTimeMillis()
            val instance = activeInstance ?: return
            instance.dontShowCurrentSuggestion = false
            instance.mainHandler.post {
                if (instance.latinIME.isInputViewShown) {
                    instance.latinIME.setNeutralSuggestionStrip()
                }
            }
        }

        /**
         * Extract an OTP from notification or SMS body text. Keyword-gated to limit false positives:
         * a 4-8 digit group is only treated as a code when the message mentions a code-like keyword,
         * or when it is the single such group in the message.
         */
        fun extractOtp(body: String): String? {
            if (body.isBlank()) return null
            val groups = codeRegex.findAll(body).map { it.value }.toList()
            if (groups.isEmpty()) return null
            return if (otpKeywordRegex.containsMatchIn(body) || groups.size == 1) groups.first() else null
        }
    }
}
