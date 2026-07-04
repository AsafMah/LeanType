// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.databinding.OtpSuggestionBinding
import helium314.keyboard.latin.permissions.PermissionsUtil
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ToolbarKey

/**
 * Optional, opt-in helper that surfaces one-time passcodes (OTPs) from incoming SMS as a
 * suggestion-strip chip the user can tap to insert (similar to the clipboard/screenshot
 * suggestions, see [ClipboardHistoryManager.getClipboardSuggestionView]).
 *
 * Privacy: this never reads the existing SMS inbox. A [BroadcastReceiver] is registered only
 * while the keyboard input view is shown and only when the feature is enabled and the
 * RECEIVE_SMS permission has been granted, so the keyboard only ever sees messages that arrive
 * while the user is actively typing.
 */
class OtpSuggestionManager(private val latinIME: LatinIME) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var otpSuggestionView: View? = null
    private var dontShowCurrentSuggestion = false

    private var latestOtp: String? = null
    private var latestOtpTimestamp = 0L

    private var isRegistered = false
    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
            val body = try {
                Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    ?.joinToString(separator = "") { it.messageBody ?: it.displayMessageBody ?: "" }
                    ?: return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read incoming SMS", e)
                return
            }
            val otp = extractOtp(body) ?: return
            latestOtp = otp
            latestOtpTimestamp = System.currentTimeMillis()
            dontShowCurrentSuggestion = false
            // Refresh the strip on the main thread so the chip appears immediately,
            // mirroring the screenshot-observer path in ClipboardHistoryManager.
            mainHandler.post {
                if (latinIME.isInputViewShown) latinIME.setNeutralSuggestionStrip()
            }
        }
    }

    /** Register the SMS receiver if the feature is enabled and the permission is granted. Idempotent. */
    fun start() {
        if (isRegistered) return
        if (!latinIME.mSettings.current.mAutoReadOtp) return
        if (!PermissionsUtil.checkAllPermissionsGranted(latinIME, Manifest.permission.RECEIVE_SMS)) return
        try {
            ContextCompat.registerReceiver(
                latinIME,
                smsReceiver,
                IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION),
                // EXPORTED is required: SMS_RECEIVED is delivered by the system/telephony process
                // (an external sender), so a NOT_EXPORTED receiver never receives it. This is safe
                // because SMS_RECEIVED is a protected broadcast that only the system can send.
                ContextCompat.RECEIVER_EXPORTED
            )
            isRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Could not register SMS receiver", e)
        }
    }

    /** Unregister the receiver. Idempotent. Called when the input view is hidden or the IME is destroyed. */
    fun stop() {
        if (!isRegistered) return
        try {
            latinIME.unregisterReceiver(smsReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Could not unregister SMS receiver", e)
        }
        isRegistered = false
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

    /**
     * Extract an OTP from an SMS body. Keyword-gated to limit false positives: a 4-8 digit group is
     * only treated as a code when the message mentions a code-like keyword, or when it is the single
     * such group in the message.
     */
    private fun extractOtp(body: String): String? {
        if (body.isBlank()) return null
        val groups = codeRegex.findAll(body).map { it.value }.toList()
        if (groups.isEmpty()) return null
        return if (otpKeywordRegex.containsMatchIn(body) || groups.size == 1) groups.first() else null
    }

    companion object {
        private const val TAG = "OtpSuggestionManager"
        private const val RECENT_OTP_MILLIS = 60 * 1000L // OTP chip is offered for 60s after arrival
        private val codeRegex = Regex("\\b\\d{4,8}\\b")
        private val otpKeywordRegex = Regex(
            "otp|code|passcode|password|pin|verification|verify|one[- ]?time|2fa|auth",
            RegexOption.IGNORE_CASE
        )
    }
}
