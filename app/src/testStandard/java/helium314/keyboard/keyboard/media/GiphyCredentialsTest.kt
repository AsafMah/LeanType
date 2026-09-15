// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.settings.SettingsContainer
import helium314.keyboard.settings.SettingsWithoutKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GiphyCredentialsTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("owned_unit_test_credential_fixture", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear().commit())
    }

    @Test
    fun setReplaceClearAreSharedAcrossServiceInstances() {
        val store = GiphyCredentialStore({ true }, { prefs })
        val first = GiphyService(store, GiphyClient { error("No live connection") })
        val second = GiphyService(store, GiphyClient { error("No live connection") })
        assertFalse(first.hasApiKey())
        var version = first.credentialVersion
        first.setApiKey("  ${GiphyClientTest.KEY}  ")
        assertTrue(second.credentialVersion > version)
        assertEquals(GiphyClientTest.KEY, second.getApiKey())
        version = second.credentialVersion
        second.setApiKey("unit-test-replacement-not-a-real-key")
        assertTrue(first.credentialVersion > version)
        assertEquals("unit-test-replacement-not-a-real-key", first.getApiKey())
        version = first.credentialVersion
        first.setApiKey(null)
        assertTrue(second.credentialVersion > version)
        assertFalse(second.hasApiKey())
        assertNull(second.getApiKey())
        assertFalse(prefs.contains(GiphyCredentialStore.KEY))
    }

    @Test
    fun externalPreferenceEditsInvalidateGeneration() {
        val store = GiphyCredentialStore({ true }, { prefs })
        store.get() // Attach the observer.
        val before = store.version
        assertTrue(prefs.edit().putString(GiphyCredentialStore.KEY, GiphyClientTest.KEY).commit())
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(store.version > before)
    }

    @Test
    fun lockingDoesNotOpenOrReadCredentialPreferences() {
        var opens = 0
        var unlocked = false
        val store = GiphyCredentialStore({ unlocked }, { opens++; prefs })
        assertEquals(MediaError.LOCKED, assertFailsWith<MediaException> { store.get() }.reason)
        assertEquals(MediaError.LOCKED, assertFailsWith<MediaException> { store.set(GiphyClientTest.KEY) }.reason)
        assertEquals(0, opens)
        unlocked = true
        store.set(GiphyClientTest.KEY)
        unlocked = false
        assertEquals(MediaError.LOCKED, assertFailsWith<MediaException> { store.get() }.reason)
        assertEquals(1, opens)
    }

    @Test
    fun productionServiceDoesNotInitializeEncryptedStorageInDirectBoot() {
        val manager = context.getSystemService(UserManager::class.java)
        shadowOf(manager).setUserUnlocked(false)
        try {
            val service = GiphyService(context.createDeviceProtectedStorageContext())
            assertTrue(service.available)
            assertEquals(MediaError.LOCKED, assertFailsWith<MediaException> { service.hasApiKey() }.reason)
        } finally {
            shadowOf(manager).setUserUnlocked(true)
        }
    }

    @Test
    fun failedSecureStorageHasNoPlaintextFallbackOrUnsanitizedError() {
        var attempts = 0
        val store = GiphyCredentialStore({ true }, {
            attempts++
            throw IllegalStateException("Unsafe ${GiphyClientTest.KEY}")
        })
        for (action in listOf<() -> Unit>({ store.get() }, { store.set(GiphyClientTest.KEY) })) {
            val error = assertFailsWith<MediaException> { action() }
            assertEquals(MediaError.STORAGE, error.reason)
            assertEquals("STORAGE", error.message)
            assertNull(error.cause)
        }
        assertEquals(2, attempts)
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun failedCommitCannotLeaveAnApparentlyUsableInMemoryKey() {
        var failCommit = true
        val faultyPrefs = object : SharedPreferences by prefs {
            override fun edit(): SharedPreferences.Editor {
                val delegate = prefs.edit()
                return object : SharedPreferences.Editor by delegate {
                    override fun commit(): Boolean {
                        val committed = delegate.commit()
                        return committed && !failCommit
                    }
                }
            }
        }
        val store = GiphyCredentialStore({ true }, { faultyPrefs })
        assertEquals(MediaError.STORAGE, assertFailsWith<MediaException> { store.set(GiphyClientTest.KEY) }.reason)
        assertEquals(MediaError.STORAGE, assertFailsWith<MediaException> { store.get() }.reason)
        failCommit = false
        store.set(GiphyClientTest.KEY)
        assertEquals(GiphyClientTest.KEY, store.get())
    }

    @Test
    fun credentialEditInvalidatesAnInFlightResult() = runBlocking {
        val store = GiphyCredentialStore({ true }, { prefs })
        store.set(GiphyClientTest.KEY)
        val service = GiphyService(store, GiphyClient {
            store.set("unit-test-replacement-not-a-real-key")
            FakeConnection(it)
        })
        assertFailsWith<CancellationException> { service.search(MediaKind.GIF, "query", "en", 0) }
        store.set(null)
        GiphyClientTest.expectError(MediaError.MISSING_KEY) { service.search(MediaKind.GIF, "query", "en", 0) }
        GiphyClientTest.expectError(MediaError.MISSING_KEY) { service.download(GiphyClientTest.fixtureRendition(), 100) }
    }

    @Test
    fun settingsAreSearchableAndBackupAllowlistCannotIncludeTheKeyStore() {
        val settings = SettingsContainer(context)
        assertEquals(listOf(SettingsWithoutKey.GIPHY_API_KEY), settings.filter("GIPHY").map { it.key })
        val exports = Class.forName("helium314.keyboard.settings.preferences.BackupRestorePreferenceKt")
            .getDeclaredMethod("auxiliaryPrefsToBackUp", Context::class.java).apply { isAccessible = true }
            .invoke(null, context) as Map<*, *>
        assertFalse(exports.keys.any { it.toString().contains("giphy", ignoreCase = true) })
    }
}
