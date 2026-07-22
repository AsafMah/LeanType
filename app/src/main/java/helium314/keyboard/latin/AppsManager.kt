// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import helium314.keyboard.latin.utils.prefs

class AppsManager(val context: Context) : BroadcastReceiver() {
    private val mPackageManager: PackageManager = context.packageManager
    private var listener: AppsChangedListener? = null

    /**
     * Returns all app labels associated with a launcher icon, sorted arbitrarily.
     */
    fun getNames(): HashSet<String> {
        val filter = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        // activities with an entry/icon for the launcher
        val launcherApps: List<ResolveInfo> = mPackageManager.queryIntentActivities(filter, 0)

        return launcherApps.mapTo(HashSet(launcherApps.size)) {
            it.activityInfo.loadLabel(mPackageManager).toString()
        }
    }

    private var isRegistered = false

    fun registerForUpdates(listener: AppsChangedListener) {
        this.listener = listener
        val useApps = context.prefs().getBoolean(helium314.keyboard.latin.settings.Settings.PREF_USE_APPS, helium314.keyboard.latin.settings.Defaults.PREF_USE_APPS)
        if (!useApps) return
        val packageFilter = IntentFilter()
        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED)
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        packageFilter.addDataScheme("package")
        context.registerReceiver(this, packageFilter)
        isRegistered = true
    }

    fun close() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(this)
            } catch (e: Exception) {
                // ignore
            }
            isRegistered = false
        }
        listener = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false))
            listener?.onAppsChanged()
    }

    interface AppsChangedListener {
        fun onAppsChanged()
    }
}
