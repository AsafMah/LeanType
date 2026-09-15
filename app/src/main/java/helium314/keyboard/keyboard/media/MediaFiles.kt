// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A finite receiver-read lease, not a provider copyright/retention permission.
 * Choose the lease only after the applicable provider policy and receiver acceptance tests agree.
 * Full stores reject new transfers instead of evicting unexpired receiver files.
 */
data class MediaRetentionPolicy(val receiverReadMillis: Long, val maxBytes: Long, val maxFiles: Int) {
    init {
        require(receiverReadMillis > 0 && receiverReadMillis <= 7 * 24 * 60 * 60 * 1000L)
        require(maxBytes in 1..64 * 1024 * 1024L)
        require(maxFiles in 1..64)
    }
}

/**
 * The null policy deliberately refuses staging: live-provider retention is not implicitly approved.
 * Closing an IME/session does not remove files or revoke receiver grants.
 * Android may reclaim cache storage; a lease is protection from our cleanup, not durable storage.
 */
class MediaFiles(
    context: Context,
    private val source: MediaSource,
    private val retention: MediaRetentionPolicy? = null,
    private val clock: () -> Long = System::currentTimeMillis
) : AutoCloseable {
    private val context = context.applicationContext
    private val directory get() = File(context.cacheDir, DIRECTORY)
    private val closed = AtomicBoolean()

    suspend fun stage(rendition: MediaRendition, description: String): StagedMedia {
        val policy = retention ?: throw MediaException(MediaError.UNAVAILABLE)
        checkOpen()
        if (!source.available) throw MediaException(MediaError.UNAVAILABLE)
        if ((rendition.byteSize ?: 0) > MediaLimits.MEDIA_BYTES ||
            (rendition.byteSize ?: 0) > policy.maxBytes) throw MediaException(MediaError.TOO_LARGE)
        val version = source.credentialVersion
        val bytes = source.download(rendition.copy(preview = false), MediaLimits.MEDIA_BYTES)
        return withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            val format = MediaFormat.inspect(bytes, rendition, preview = false)
            if (bytes.size > policy.maxBytes) throw MediaException(MediaError.TOO_LARGE)
            diskLock.withLock {
                checkOpen()
                if (version != source.credentialVersion) throw MediaException(MediaError.UNAVAILABLE)
                currentCoroutineContext().ensureActive()
                store(bytes, format, description, policy)
            }
        }
    }

    /** Safe to call separately from UI teardown. Never deletes an unexpired lease. */
    suspend fun cleanup() = withContext(Dispatchers.IO) {
        diskLock.withLock {
            val now = clock()
            try {
                cleanExpired(now)
            } catch (_: SecurityException) {
                throw MediaException(MediaError.STORAGE)
            } catch (_: IllegalArgumentException) {
                throw MediaException(MediaError.STORAGE)
            }
        }
    }

    override fun close() {
        closed.set(true)
    }

    private suspend fun store(
        bytes: ByteArray, format: MediaFormat.Image, description: String, policy: MediaRetentionPolicy
    ): StagedMedia {
        var pending: File? = null
        try {
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException()
            val now = clock()
            cleanExpired(now)
            val existing = directory.listFiles() ?: throw IOException()
            if (existing.size >= policy.maxFiles ||
                existing.sumOf { it.length() } > policy.maxBytes - bytes.size) throw MediaException(MediaError.STORAGE)
            if (now < 0 || now > Long.MAX_VALUE - policy.receiverReadMillis) throw IOException()
            val name = "${now + policy.receiverReadMillis}_${UUID.randomUUID()}"
            val file = File(directory, "$name.${format.extension}")
            pending = File(directory, "$name.pending")
            FileOutputStream(pending).use {
                it.write(bytes)
                it.fd.sync()
            }
            currentCoroutineContext().ensureActive()
            checkOpen()
            if (!pending.renameTo(file)) throw IOException()
            return StagedMedia(
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
                format.mimeType, description
            )
        } catch (error: MediaException) {
            throw error
        } catch (_: IOException) {
            throw MediaException(MediaError.STORAGE)
        } catch (_: SecurityException) {
            throw MediaException(MediaError.STORAGE)
        } catch (_: IllegalArgumentException) {
            throw MediaException(MediaError.STORAGE)
        } finally {
            pending?.delete()
        }
    }

    private fun cleanExpired(now: Long) {
        if (!directory.exists()) return
        val entries = directory.listFiles() ?: throw MediaException(MediaError.STORAGE)
        for (entry in entries) {
            if (!entry.isFile || !ENTRY.matches(entry.name)) continue
            val expires = entry.name.substringBefore('_').toLongOrNull() ?: continue
            if (expires > now) continue
            if (entry.extension != "pending") {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", entry)
                context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (!entry.delete()) throw MediaException(MediaError.STORAGE)
        }
    }

    private fun checkOpen() {
        if (closed.get()) throw MediaException(MediaError.UNAVAILABLE)
    }

    companion object {
        internal const val DIRECTORY = "media_staging"
        private val diskLock = Mutex()
        private val ENTRY = Regex("""\d+_[0-9a-f-]{36}\.(gif|webp|pending)""")
    }
}
