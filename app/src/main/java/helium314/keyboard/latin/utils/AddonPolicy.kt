// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.os.Build
import helium314.keyboard.latin.BuildConfig

object AddonPolicy {
    fun allowsInAppDownloads(flavor: String = BuildConfig.FLAVOR): Boolean =
        flavor == "standard" || flavor == "standardfull"

    fun allowsOcrPlugins(
        buildType: String = BuildConfig.BUILD_TYPE,
        sdk: Int = Build.VERSION.SDK_INT,
    ): Boolean = buildType != "nouserlib" && sdk >= Build.VERSION_CODES.O
}
