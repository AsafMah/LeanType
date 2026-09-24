// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.content.Context
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [HostAtomicFile::class])
class GiphyCacheGenerationTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        MediaLibraryStorage.clearAll(context)
    }

    @Test
    fun opaqueNamespaceSurvivesRestartAndChangesWithReplacementOrRemoval() {
        val prefs = context.getSharedPreferences("owned_generation_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = GiphyCredentialStore({ true }, { prefs })
        store.set(GiphyClientTest.KEY)
        val first = store.cacheIdentity()
        assertEquals(first, GiphyCredentialStore({ true }, { prefs }).cacheIdentity())
        store.set(GiphyClientTest.KEY)
        assertEquals(first, store.cacheIdentity())
        store.set("owned-replacement-not-a-real-key")
        val second = store.cacheIdentity()
        assertNotEquals(first, second)
        assertEquals(second, GiphyCredentialStore({ true }, { prefs }).cacheIdentity())
        store.set(null)
        assertNotEquals(second, store.cacheIdentity())
        assertFalse(first.contains(GiphyClientTest.KEY))
    }

    @Test
    fun keySetterSynchronouslyPurgesLiveWrapperCacheAndBookmarksButNotStaging() = runBlocking {
        val prefs = context.getSharedPreferences("owned_generation_purge_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = GiphyCredentialStore({ true }, { prefs }, { MediaLibraryStorage.clearAll(context) })
        val service = GiphyService(store, GiphyClient { FakeConnection(it) })
        service.setApiKey(GiphyClientTest.KEY)
        val library = MediaLibrary(context, service, { true })
        library.pin(MediaKind.GIF, MediaItem("owned", "Owned", "", "", "", emptyList()))
        val root = File(context.applicationContext.noBackupFilesDir, MediaLibraryStorage.DIRECTORY)
        assertTrue(File(root, "bookmarks.json").exists())
        val staging = File(context.cacheDir, MediaFiles.DIRECTORY).apply { mkdirs() }
        val lease = File(staging, "9223372036854775807_owned.gif").apply { writeBytes(OwnedMediaFixtures.gif()) }
        try {
            service.setApiKey("owned-replacement-not-a-real-key")
            assertFalse(root.exists()) // No wrapper call is needed to perform the purge.
            assertTrue(lease.exists())
            assertTrue(library.pins().isEmpty())
            service.setApiKey(null)
            assertFalse(root.exists())
            assertTrue(lease.exists())
        } finally {
            lease.delete()
        }
    }
}
