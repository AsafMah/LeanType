// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.settings.SettingsContainer
import helium314.keyboard.settings.SettingsWithoutKey
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class OfflineGiphyServiceTest {
    @Test
    fun unavailableServiceHasNoCredentialOrNetworkBehavior() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = GiphyService(context)
        assertFalse(service.available)
        assertFalse(service.hasApiKey())
        assertEquals(0L, service.credentialVersion)
        assertEquals(MediaError.UNAVAILABLE, assertFailsWith<MediaException> { service.getApiKey() }.reason)
        assertEquals(MediaError.UNAVAILABLE,
            assertFailsWith<MediaException> { service.setApiKey("unit-test-not-a-real-api-key") }.reason)
        assertEquals(MediaError.UNAVAILABLE,
            assertFailsWith<MediaException> { service.search(MediaKind.GIF, "query", "en") }.reason)
        assertEquals(MediaError.UNAVAILABLE, assertFailsWith<MediaException> {
            service.download(MediaRendition("https://media.giphy.com/owned.gif", "image/gif", 1, 1), 100)
        }.reason)
        val settings = SettingsContainer(context)
        assertNull(settings[SettingsWithoutKey.GIPHY_API_KEY])
        assertTrue(settings.filter("GIPHY").isEmpty())
    }
}
