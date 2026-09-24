// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@Serializable
internal data class CachedResult(
    val kind: MediaKind, val query: String, val language: String, val offset: Int,
    val page: MediaPage, val created: Long, val expires: Long, val used: Long,
    val lookupId: String? = null
)

@Serializable
internal data class CachedAsset(
    val file: String, val rendition: MediaRendition, val size: Int, val digest: String,
    val created: Long, val expires: Long, val used: Long
)

@Serializable
internal data class MediaCacheIndex(
    val schema: Int = 1,
    val results: List<CachedResult> = emptyList(),
    val assets: List<CachedAsset> = emptyList()
)

@Serializable
internal data class SavedTab(val snapshot: MediaSnapshot, val created: Long, val expires: Long)

@Serializable
internal data class SavedHistory(val schema: Int = 1, val kind: MediaKind, val tabs: List<SavedTab>)

@Serializable
internal data class SavedBookmarks(val schema: Int = 1, val items: List<MediaBookmark> = emptyList())

/**
 * Public app-private storage, excluded by noBackupFilesDir and the settings export allowlist.
 * With defaultToDeviceProtectedStorage this is device-protected, not credential-encrypted.
 * MediaLibrary's unlock/access guard runs before any read or write; this is an application
 * access boundary, not encryption of cached media. Credential changes purge this separate tree.
 */
internal class MediaLibraryStorage(context: Context, private val policy: MediaCachePolicy) {
    private val context = context
    private val root get() = root(context)

    fun prepare(identity: String) = storage {
        if (!identity.matches(Regex("[a-zA-Z0-9:_-]{1,128}"))) invalid()
        if (!root.exists() && !root.mkdirs()) throw IOException()
        checkRoot()
        val previous = try {
            read("generation", 128)?.decodeToString(throwOnInvalidSequence = true)?.also {
                if (!it.matches(Regex("[a-zA-Z0-9:_-]{1,128}"))) invalid()
            }
        } catch (_: Exception) {
            clearAll(context)
            invalid()
        }
        if (previous != identity) {
            if (previous != null || (root.listFiles() ?: throw IOException()).isNotEmpty()) clearAll(context)
            if (!root.mkdirs() && !root.isDirectory) throw IOException()
            write("generation", identity.toByteArray(), 128)
        }
        val entries = root.listFiles() ?: throw IOException()
        if (entries.size > policy.maxFiles + 12) {
            clearAll(context)
            invalid()
        }
        for (entry in entries) {
            val base = entry.name.removeSuffix(".bak").removeSuffix(".new")
            if (base !in setOf("generation", "index.json", "history.json", "bookmarks.json") &&
                !BLOB.matches(base)) {
                deleteEntry(entry)
                invalid()
            }
        }
    }

    fun index(): MediaCacheIndex = decode("index.json", policy.metadataBytes, MediaCacheIndex()) { value ->
        if (value.schema != 1 || value.results.size > policy.maxResults || value.assets.size > policy.maxFiles ||
            value.assets.sumOf { it.size.toLong() } > policy.maxBytes ||
            value.assets.map { it.file }.distinct().size != value.assets.size) invalid()
        value.results.forEach {
            MediaCacheValidation.query(it.query)
            MediaCacheValidation.language(it.language)
            if (it.offset !in 0..MediaLimits.MAX_OFFSET || it.page.items.size > MediaLimits.PAGE_SIZE ||
                it.page.nextOffset?.let { offset -> offset !in 0..MediaLimits.MAX_OFFSET } == true) invalid()
            it.lookupId?.let(MediaCacheValidation::id)
            lifetime(it.created, it.expires)
            if (it.used < 0 || !it.page.cacheInfo.storable ||
                it.page.cacheInfo.expiresAt?.let { expires -> expires < it.expires } == true) invalid()
            it.page.items.forEach(MediaCacheValidation::item)
        }
        value.assets.forEach {
            if (!BLOB.matches(it.file) || it.size !in 1..MediaLimits.MEDIA_BYTES ||
                !it.digest.matches(Regex("[a-f0-9]{64}")) || it.used < 0) invalid()
            lifetime(it.created, it.expires)
            MediaCacheValidation.rendition(it.rendition)
        }
    }

    fun history(): SavedHistory? = decode<SavedHistory?>("history.json", policy.snapshotBytes, null) { history ->
        history ?: return@decode
        if (history.schema != 1 || history.tabs.size > MediaKind.entries.size ||
            history.tabs.map { it.snapshot.kind }.distinct().size != history.tabs.size) invalid()
        history.tabs.forEach {
            MediaCacheValidation.snapshot(it.snapshot)
            lifetime(it.created, it.expires)
        }
    }

    fun bookmarks(): SavedBookmarks = decode("bookmarks.json", policy.metadataBytes, SavedBookmarks()) {
        if (it.schema != 1 || it.items.size > policy.maxBookmarks ||
            it.items.map { pin -> pin.kind to pin.id }.distinct().size != it.items.size) invalid()
        it.items.forEach(MediaCacheValidation::bookmark)
    }

    fun save(value: MediaCacheIndex) = write("index.json", Json.encodeToString(value).toByteArray(), policy.metadataBytes)
    fun fits(value: MediaCacheIndex): Boolean =
        Json.encodeToString(value).toByteArray().size <= policy.metadataBytes - file("bookmarks.json").length()
    fun save(value: SavedHistory) = write("history.json", Json.encodeToString(value).toByteArray(), policy.snapshotBytes)
    fun save(value: SavedBookmarks) = write("bookmarks.json", Json.encodeToString(value).toByteArray(), policy.metadataBytes)
    fun blob(name: String, cap: Int): ByteArray? {
        if (!BLOB.matches(name)) invalid()
        return read(name, minOf(cap, MediaLimits.MEDIA_BYTES))
    }
    fun saveBlob(name: String, bytes: ByteArray) {
        if (!BLOB.matches(name)) invalid()
        write(name, bytes, minOf(policy.maxBytes, MediaLimits.MEDIA_BYTES.toLong()).toInt())
    }

    fun delete(name: String) = storage {
        file(name).let { base ->
            listOf(base, File(base.path + ".bak"), File(base.path + ".new")).forEach {
                if (it.exists()) deleteEntry(it)
            }
        }
    }

    fun discardOrphans(assets: List<CachedAsset>) = storage {
        val retained = assets.map { it.file }.toSet()
        (root.listFiles() ?: throw IOException()).forEach {
            val name = it.name.removeSuffix(".bak").removeSuffix(".new")
            if (BLOB.matches(name) && name !in retained) deleteEntry(it)
        }
    }

    fun clearHistory() {
        delete("index.json")
        delete("history.json")
        discardOrphans(emptyList())
        revision++
    }

    private fun lifetime(created: Long, expires: Long) {
        if (created < 0 || expires < created || expires - created > policy.ttlMillis) invalid()
    }

    private inline fun <reified T> decode(name: String, cap: Int, fallback: T, validate: (T) -> Unit): T {
        val bytes = read(name, cap) ?: return fallback
        try {
            val text = bytes.decodeToString(throwOnInvalidSequence = true)
            MediaCacheValidation.nesting(text)
            return Json.decodeFromString<T>(text).also(validate)
        } catch (_: Exception) {
            delete(name)
            if (name == "index.json") discardOrphans(emptyList())
            invalid()
        }
    }

    private fun read(name: String, cap: Int): ByteArray? = storage {
        val base = file(name)
        if (!base.exists() && !File(base.path + ".bak").exists()) return@storage null
        try {
            listOf(base, File(base.path + ".bak"), File(base.path + ".new")).forEach {
                if (it.exists() && (!it.isFile || it.length() > cap)) invalid()
            }
            AtomicFile(base).openRead().use { input ->
                val output = ByteArrayOutputStream(minOf(cap, 8192))
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer, 0, minOf(buffer.size, cap - output.size() + 1))
                    if (count < 0) break
                    if (count == 0) continue
                    if (output.size() + count > cap) invalid()
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (error: MediaException) {
            delete(name)
            throw error
        }
    }

    private fun write(name: String, bytes: ByteArray, cap: Int) = storage {
        if (bytes.size > cap) throw MediaException(MediaError.TOO_LARGE)
        if (name == "index.json" || name == "bookmarks.json") {
            val other = file(if (name == "index.json") "bookmarks.json" else "index.json")
            if (bytes.size > policy.metadataBytes - other.length()) throw MediaException(MediaError.TOO_LARGE)
        }
        val atomic = AtomicFile(file(name))
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            atomic.finishWrite(output)
            if (read(name, cap)?.contentEquals(bytes) != true) throw MediaException(MediaError.STORAGE)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }

    private fun file(name: String): File {
        if (name !in setOf("generation", "index.json", "history.json", "bookmarks.json") && !BLOB.matches(name)) invalid()
        checkRoot()
        return File(root, name).also {
            if (it.canonicalFile.parentFile != root.canonicalFile) throw MediaException(MediaError.STORAGE)
            for (suffix in listOf(".bak", ".new")) {
                if (File(it.path + suffix).canonicalFile.parentFile != root.canonicalFile)
                    throw MediaException(MediaError.STORAGE)
            }
        }
    }

    private fun checkRoot() {
        if (!root.isDirectory || root.canonicalFile != root.absoluteFile) throw MediaException(MediaError.STORAGE)
    }

    companion object {
        const val DIRECTORY = "media_library"
        val lock = Any()
        @Volatile var revision = 0L
            private set
        private val BLOB = Regex("[a-f0-9-]{36}\\.blob")

        private fun root(context: Context): File {
            // The application defaults to device-protected storage; MediaLibrary gates all access on unlock.
            return File(context.applicationContext.noBackupFilesDir.canonicalFile, DIRECTORY)
        }

        /** Synchronous key-change hook; deliberately unrelated to media_staging or URI grants. */
        fun clearAll(context: Context) = synchronized(lock) {
            revision++
            storage {
                val directory = root(context)
                if (!directory.exists()) return@storage
                if (directory.canonicalFile != directory.absoluteFile) throw IOException()
                (directory.listFiles() ?: throw IOException()).forEach(::deleteEntry)
                if (!directory.delete()) throw IOException()
            }
        }

        private fun deleteEntry(entry: File) {
            // Never follow directories or links out of this one app-private cache tree.
            if (entry.isDirectory || entry.canonicalFile != entry.absoluteFile || !entry.delete())
                throw MediaException(MediaError.STORAGE)
        }

        fun digest(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        private fun invalid(): Nothing = MediaCacheValidation.invalid()

        private inline fun <T> storage(action: () -> T): T = try {
            action()
        } catch (error: MediaException) {
            throw error
        } catch (_: Exception) {
            throw MediaException(MediaError.STORAGE)
        }
    }
}
