// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SmsPackageProvider
import helium314.keyboard.latin.utils.prefs

/**
 * Service that reads incoming OTPs strictly from SMS notifications of allowed SMS messaging apps.
 * Requires zero SMS reading permissions in AndroidManifest.xml.
 */
class OtpNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val sbnNonNull = sbn ?: return
        val pkg = sbnNonNull.packageName ?: return

        // 1. Check if feature is enabled
        val autoReadEnabled = try {
            prefs().getBoolean(Settings.PREF_AUTO_READ_OTP, false)
        } catch (e: Exception) {
            false
        }
        if (!autoReadEnabled) return

        // 2. Package filtering: check specific allowed package or fallback allowlist
        val allowedPackage = try {
            prefs().getString(Settings.PREF_OTP_ALLOWED_SMS_PACKAGE, null)
        } catch (e: Exception) {
            null
        }

        if (!allowedPackage.isNullOrBlank()) {
            if (pkg != allowedPackage) return
        } else {
            if (pkg !in SmsPackageProvider.KNOWN_SMS_PACKAGES) return
        }

        // 3. Extract notification text safely
        val extras = sbnNonNull.notification?.extras ?: return
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString(" ")
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: textLines
            ?: return

        val otp = OtpSuggestionManager.extractOtp(text) ?: return
        Log.i(TAG, "OTP detected from notification")
        OtpSuggestionManager.onOtpReceived(otp)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            prefs().edit().putBoolean(PREF_NLS_DISCONNECTED, false).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update NLS connected state", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            prefs().edit().putBoolean(PREF_NLS_DISCONNECTED, true).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update NLS disconnected state", e)
        }
    }

    companion object {
        private const val TAG = "OtpNotificationListener"
        const val PREF_NLS_DISCONNECTED = "nls_disconnected"
    }
}
