// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.UserManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import java.util.UUID

class GiphyService internal constructor(
    private val credentials: GiphyCredentialStore,
    private val client: GiphyClient
) : MediaSource {
    constructor(context: Context) : this(credentials(context), GiphyClient())

    override val available = true
    override val credentialVersion: Long get() = credentials.version
    override val cacheIdentity: String get() = credentials.cacheIdentity()
    override fun hasApiKey() = getApiKey() != null
    fun getApiKey(): String? = credentials.get()
    fun setApiKey(apiKey: String?) = credentials.set(apiKey)

    override suspend fun search(kind: MediaKind, query: String, language: String, offset: Int): MediaPage =
        withContext(Dispatchers.IO) {
            val version = credentialVersion
            val key = getApiKey() ?: throw MediaException(MediaError.MISSING_KEY)
            client.search(key, kind, query, language, offset).also { checkVersion(version) }
        }

    override suspend fun refresh(kind: MediaKind, query: String, language: String, offset: Int): MediaPage =
        withContext(Dispatchers.IO) {
            val version = credentialVersion
            val key = getApiKey() ?: throw MediaException(MediaError.MISSING_KEY)
            client.search(key, kind, query, language, offset, bypassCache = true).also { checkVersion(version) }
        }

    override suspend fun lookup(kind: MediaKind, id: String): MediaItem? = lookupResponse(kind, id).item

    override suspend fun lookupResponse(kind: MediaKind, id: String): MediaLookup = withContext(Dispatchers.IO) {
        val version = credentialVersion
        val key = getApiKey() ?: throw MediaException(MediaError.MISSING_KEY)
        client.lookup(key, kind, id).also { checkVersion(version) }
    }

    override suspend fun download(rendition: MediaRendition, maxBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        val version = credentialVersion
        if (!hasApiKey()) throw MediaException(MediaError.MISSING_KEY)
        client.download(rendition, maxBytes).also { checkVersion(version) }
    }

    override suspend fun downloadResponse(
        rendition: MediaRendition, maxBytes: Int, bypassCache: Boolean
    ): MediaDownload = withContext(Dispatchers.IO) {
        val version = credentialVersion
        if (!hasApiKey()) throw MediaException(MediaError.MISSING_KEY)
        client.downloadResponse(rendition, maxBytes, bypassCache).also { checkVersion(version) }
    }

    private fun checkVersion(version: Long) {
        if (version != credentialVersion) throw CancellationException("Media credentials changed")
        // A lock transition must not deliver an already-started result either.
        credentials.checkUnlocked()
    }

    companion object {
        internal const val CREDENTIAL_PREFS_NAME = "giphy_credentials"
        @Volatile private var sharedCredentials: GiphyCredentialStore? = null

        private fun credentials(context: Context): GiphyCredentialStore =
            sharedCredentials ?: synchronized(this) {
                sharedCredentials ?: run {
                    val app = context.applicationContext
                    // EncryptedSharedPreferences uses applicationContext internally. With
                    // defaultToDeviceProtectedStorage=true, its file is device-protected
                    // ciphertext, not a credential-encrypted file. The unlock guard runs
                    // before opening preferences or creating/accessing the Keystore key.
                    GiphyCredentialStore(
                        unlocked = {
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
                                app.getSystemService(UserManager::class.java)?.isUserUnlocked == true
                        },
                        openPreferences = {
                            val masterKey = MasterKey.Builder(app)
                                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                            EncryptedSharedPreferences.create(app, CREDENTIAL_PREFS_NAME, masterKey,
                                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
                        },
                        onChanged = { MediaLibraryStorage.clearAll(app) }
                    ).also { sharedCredentials = it }
                }
            }
    }
}

/** Deliberately separate from both default preference stores and the backup export allowlist. */
internal class GiphyCredentialStore(
    private val unlocked: () -> Boolean,
    private val openPreferences: () -> SharedPreferences,
    private val onChanged: () -> Unit = {}
) {
    private val changes = AtomicLong()
    private var preferences: SharedPreferences? = null
    private var writeFailed = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY || key == null) changes.incrementAndGet()
    }
    val version: Long get() = changes.get()

    fun checkUnlocked() {
        val isUnlocked = try { unlocked() } catch (_: Exception) { false }
        if (!isUnlocked) throw MediaException(MediaError.LOCKED)
    }

    @Synchronized
    fun get(): String? = secure {
        if (writeFailed) throw MediaException(MediaError.STORAGE)
        prefs().getString(KEY, null)?.takeIf { it.isNotBlank() }
    }

    @Synchronized
    fun cacheIdentity(): String = secure {
        if (writeFailed) throw MediaException(MediaError.STORAGE)
        val prefs = prefs()
        prefs.getString(GENERATION, null)?.let {
            if (!it.matches(Regex("[a-f0-9-]{36}"))) throw MediaException(MediaError.STORAGE)
            return@secure it
        }
        UUID.randomUUID().toString().also {
            writeFailed = true
            if (!prefs.edit().putString(GENERATION, it).commit()) throw MediaException(MediaError.STORAGE)
            writeFailed = false
        }
    }

    fun set(value: String?) = synchronized(MediaLibraryStorage.lock) {
        synchronized(this) {
            secure {
                val key = value?.trim()?.takeIf { it.isNotEmpty() }
                val prefs = prefs()
                if (!writeFailed && prefs.getString(KEY, null) == key) return@secure
                // Cache writers take the same outer lock: no old-generation write can race this purge.
                changes.incrementAndGet()
                writeFailed = true
                onChanged()
                val editor = prefs.edit()
                editor.putString(GENERATION, UUID.randomUUID().toString())
                if (key == null) editor.remove(KEY) else editor.putString(KEY, key)
                if (!editor.commit()) throw MediaException(MediaError.STORAGE)
                writeFailed = false
            }
        }
    }

    private fun prefs(): SharedPreferences = preferences ?: openPreferences().also {
        it.registerOnSharedPreferenceChangeListener(listener)
        preferences = it
    }

    private fun <T> secure(block: () -> T): T {
        checkUnlocked()
        try {
            return block()
        } catch (error: MediaException) {
            throw error
        } catch (_: Exception) {
            // Never expose an exception that could contain decrypted values, and never fall back to plaintext.
            throw MediaException(MediaError.STORAGE)
        }
    }

    companion object {
        internal const val KEY = "personal_api_key"
        internal const val GENERATION = "cache_generation"
    }
}
