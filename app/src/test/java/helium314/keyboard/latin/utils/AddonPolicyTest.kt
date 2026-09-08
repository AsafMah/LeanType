// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonPolicyTest {
    @Test
    fun onlyCloudFlavorsDownloadAddonsInApp() {
        assertTrue(AddonPolicy.allowsInAppDownloads("standard"))
        assertTrue(AddonPolicy.allowsInAppDownloads("standardfull"))
        assertFalse(AddonPolicy.allowsInAppDownloads("offline"))
        assertFalse(AddonPolicy.allowsInAppDownloads("offlinelite"))
        assertFalse(AddonPolicy.allowsInAppDownloads("future-offline-tier"))
    }

    @Test
    fun noUserLibCannotLoadOcrEvenOnSupportedAndroid() {
        assertFalse(AddonPolicy.allowsOcrPlugins("nouserlib", 33))
    }

    @Test
    fun localOcrImportsRemainAvailableOnSupportedAndroid() {
        for (buildType in listOf("debug", "release", "experimental", "runTests")) {
            assertTrue(AddonPolicy.allowsOcrPlugins(buildType, 26))
            assertFalse(AddonPolicy.allowsOcrPlugins(buildType, 25))
        }
    }
}
