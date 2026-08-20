// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony

/**
 * Utility helper for detecting installed SMS apps and default SMS handlers.
 */
object SmsPackageProvider {

    val KNOWN_SMS_PACKAGES = setOf(
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        "org.thoughtcrime.securesms",
        "org.fossify.messages",
        "com.simplemobiletools.smsmessenger",
        "com.moez.QKSMS",
        "com.android.messaging"
    )

    fun getDefaultSmsPackage(context: Context): String? {
        return try {
            Telephony.Sms.getDefaultSmsPackage(context)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns a map of package names -> user-friendly app labels for installed SMS-capable applications.
     */
    fun getCandidateSmsPackages(context: Context): Map<String, String> {
        val candidates = LinkedHashMap<String, String>()
        val pm = context.packageManager

        val defaultPkg = getDefaultSmsPackage(context)
        if (!defaultPkg.isNullOrBlank()) {
            val label = getAppLabel(context, defaultPkg) ?: defaultPkg
            candidates[defaultPkg] = "$label (Default SMS App)"
        }

        // Query apps handling sms: and smsto:
        val intentSchemes = listOf("sms:", "smsto:")
        for (scheme in intentSchemes) {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(scheme))
            val resolveInfos = try {
                pm.queryIntentActivities(intent, 0)
            } catch (e: Exception) {
                emptyList()
            }
            for (info in resolveInfos) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (candidates.containsKey(pkg)) continue
                val label = getAppLabel(context, pkg) ?: pkg
                candidates[pkg] = label
            }
        }

        // Also check known SMS packages if installed
        for (pkg in KNOWN_SMS_PACKAGES) {
            if (candidates.containsKey(pkg)) continue
            val label = getAppLabel(context, pkg) ?: continue
            candidates[pkg] = label
        }

        return candidates
    }

    fun getAppLabel(context: Context, packageName: String): String? {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            null
        }
    }
}
