// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.content.Context
import android.os.Build
import android.os.UserManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Bounded, app-private provider results and bytes. Nothing here owns a receiver's staging lease.
 * Access is checked before opening storage and again after every upstream suspension.
 */
class MediaLibrary(
    private val context: Context,
    private val upstream: MediaSource,
    private val canAccess: () -> Boolean,
    private val policy: MediaCachePolicy = MediaCachePolicy(),
    private val clock: () -> Long = System::currentTimeMillis
) : MediaSource {
    private val storage = MediaLibraryStorage(context, policy)
    private var preparedIdentity: String? = null
    private var preparedRevision = -1L
    private val delivered = ArrayList<CachedResult>()
    private val requests = mutableMapOf<MediaKind, Long>()
    override val available get() = upstream.available
    override val credentialVersion get() = upstream.credentialVersion
    override val cacheIdentity get() = upstream.cacheIdentity
    override fun hasApiKey() = allowed()

    suspend fun restore(): MediaHistory? {
        if (!allowed()) return null
        return disk {
            prune(storage.index())
            val saved = storage.history() ?: return@disk null
            val time = now()
            MediaHistory(saved.kind, saved.tabs.map {
                if (fresh(it.created, it.expires, time)) it.snapshot
                else it.snapshot.copy(items = emptyList(), nextOffset = null,
                    requiresRefresh = !it.snapshot.submitted.isNullOrBlank())
            })
        }
    }

    /** Only a response delivered by this generation can become persisted UI history. */
    suspend fun remember(snapshot: MediaSnapshot): Unit = disk {
        MediaCacheValidation.snapshot(snapshot)
        val candidates = (delivered + storage.index().results).filter {
            it.lookupId == null && it.kind == snapshot.kind && it.query == snapshot.submitted &&
                it.language == snapshot.language
        }
        if (candidates.isEmpty() || snapshot.items.any { item ->
                candidates.none { result -> item in result.page.items }
            }) return@disk
        val time = now()
        val relevant = candidates.filter { it.page.items.isEmpty() || it.page.items.any(snapshot.items::contains) }
        val expires = relevant.minOfOrNull { it.expires } ?: time
        val canStore = relevant.all { it.page.cacheInfo.storable } && expires > time
        val value = if (canStore) snapshot else snapshot.copy(
            items = emptyList(), nextOffset = null, requiresRefresh = !snapshot.submitted.isNullOrBlank())
        val previous = storage.history()
        val tab = SavedTab(value, time, if (canStore) minOf(expires, deadline(time)) else time)
        storage.save(SavedHistory(kind = snapshot.kind,
            tabs = previous?.tabs.orEmpty().filterNot { it.snapshot.kind == snapshot.kind } + tab))
        delivered.removeAll { !it.page.cacheInfo.storable }
    }

    /** Draft/position edits intentionally neither replace accepted results nor renew their expiry. */
    suspend fun rememberPosition(kind: MediaKind, draft: String, scrollPosition: Int, scrollOffset: Int): Unit = disk {
        MediaCacheValidation.query(draft)
        if (scrollPosition < 0 || scrollOffset < 0) MediaCacheValidation.invalid()
        val previous = storage.history()
        val time = now()
        val existing = previous?.tabs.orEmpty()
        val tabs = if (existing.none { it.snapshot.kind == kind }) existing + SavedTab(
            MediaSnapshot(kind, draft, null, "", emptyList(), null, scrollPosition, scrollOffset), time, time)
        else existing.map {
            if (it.snapshot.kind != kind) it else it.copy(snapshot = it.snapshot.copy(
                draft = draft, scrollPosition = scrollPosition, scrollOffset = scrollOffset))
        }
        storage.save(SavedHistory(kind = kind, tabs = tabs))
    }

    suspend fun pins(): List<MediaBookmark> {
        if (!allowed()) return emptyList()
        return disk { storage.bookmarks().items }
    }

    suspend fun pin(kind: MediaKind, item: MediaItem): Unit = disk {
        val provenance = (delivered + storage.index().results).firstOrNull {
            it.kind == kind && item in it.page.items
        }
        // An explicit bookmark may retain identity, but cannot turn no-store response data into a cache.
        val descriptive = provenance?.page?.cacheInfo?.storable == true
        val pin = if (descriptive) MediaBookmark(kind, item.id, item.title, item.pageUrl, item.sourceName, item.sourceUrl)
            else MediaBookmark(kind, item.id, "", "", "", "")
        MediaCacheValidation.bookmark(pin)
        val pins = storage.bookmarks().items
        if (pins.any { it.kind == kind && it.id == item.id }) return@disk
        storage.save(SavedBookmarks(items = (listOf(pin) + pins).take(policy.maxBookmarks)))
    }

    suspend fun unpin(kind: MediaKind, id: String): Unit = disk {
        MediaCacheValidation.id(id)
        storage.save(SavedBookmarks(items = storage.bookmarks().items.filterNot { it.kind == kind && it.id == id }))
    }

    suspend fun resolve(bookmark: MediaBookmark): MediaItem? {
        MediaCacheValidation.bookmark(bookmark)
        return lookup(bookmark.kind, bookmark.id)
    }

    suspend fun clearHistory(): Unit = disk {
        storage.clearHistory()
        delivered.clear()
        preparedRevision = MediaLibraryStorage.revision
    }

    override suspend fun search(kind: MediaKind, query: String, language: String, offset: Int): MediaPage =
        searchPage(kind, query, language, offset, refresh = false)

    override suspend fun refresh(kind: MediaKind, query: String, language: String, offset: Int): MediaPage =
        searchPage(kind, query, language, offset, refresh = true)

    private suspend fun searchPage(
        kind: MediaKind, query: String, language: String, offset: Int, refresh: Boolean
    ): MediaPage {
        MediaCacheValidation.query(query)
        MediaCacheValidation.language(language)
        if (query.isBlank() || offset !in 0..MediaLimits.MAX_OFFSET) MediaCacheValidation.invalid()
        var hit: MediaPage? = null
        val token = disk {
            requests[kind] = (requests[kind] ?: 0) + 1
            val index = prune(storage.index())
            if (!refresh) index.results.firstOrNull {
                it.lookupId == null && it.kind == kind && it.query == query && it.language == language && it.offset == offset
            }?.let { result ->
                val updated = result.copy(used = nextUse(index))
                storage.save(index.copy(results = index.results.map { if (it == result) updated else it }))
                deliver(updated)
                hit = result.page
            }
            token(kind)
        }
        hit?.let { return it }
        val page = if (refresh) upstream.refresh(kind, query, language, offset)
            else upstream.search(kind, query, language, offset)
        return disk {
            check(token)
            validatePage(page)
            val time = now()
            val index = prune(storage.index())
            val result = CachedResult(kind, query, language, offset, page, time,
                expiry(page.cacheInfo, time), nextUse(index))
            var results = index.results
            // Refresh is a transaction: old successes survive a failed or superseded request.
            val replaced = results.filter {
                it.lookupId == null && it.kind == kind && it.query == query && it.language == language &&
                    (refresh && offset == 0 || it.offset == offset)
            }
            results = results - replaced.toSet()
            val obsoleteUrls = if (refresh) replaced.flatMap { it.page.items }.flatMap { it.renditions }
                .map { it.url }.toSet() else emptySet()
            val assets = index.assets.filterNot { it.rendition.url in obsoleteUrls }
            val updated = index.copy(results = results, assets = assets)
            saveResult(updated, result)
            index.assets.filterNot { it in assets }.forEach { storage.delete(it.file) }
            deliver(result)
            page
        }
    }

    override suspend fun lookup(kind: MediaKind, id: String): MediaItem? = lookupResponse(kind, id).item

    override suspend fun lookupResponse(kind: MediaKind, id: String): MediaLookup {
        MediaCacheValidation.id(id)
        var hit: MediaLookup? = null
        val token = disk {
            val index = prune(storage.index())
            index.results.firstOrNull { it.kind == kind && it.lookupId == id }?.let { result ->
                storage.save(index.copy(results = index.results.map {
                    if (it == result) it.copy(used = nextUse(index)) else it
                }))
                hit = MediaLookup(result.page.items.singleOrNull(), result.page.cacheInfo)
            }
            token()
        }
        hit?.let { return it }
        val response = try {
            upstream.lookupResponse(kind, id)
        } catch (error: MediaException) {
            if (error.httpStatus in setOf(403, 404)) disk { check(token); invalidateId(kind, id) }
            throw error
        }
        return disk {
            check(token)
            val item = response.item
            if (item == null) {
                invalidateId(kind, id)
            } else {
                MediaCacheValidation.item(item)
                if (item.id != id) MediaCacheValidation.invalid()
                val time = now()
                val index = prune(storage.index())
                val result = CachedResult(kind, "", "", 0, MediaPage(listOf(item), null, response.cacheInfo),
                    time, expiry(response.cacheInfo, time), nextUse(index), id)
                saveResult(index.copy(results = index.results.filterNot { it.kind == kind && it.lookupId == id }), result)
            }
            response
        }
    }

    override suspend fun download(rendition: MediaRendition, maxBytes: Int): ByteArray =
        downloadResponse(rendition, maxBytes).bytes

    override suspend fun downloadResponse(
        rendition: MediaRendition, maxBytes: Int, bypassCache: Boolean
    ): MediaDownload {
        MediaCacheValidation.rendition(rendition)
        val cap = minOf(maxBytes, MediaLimits.MEDIA_BYTES)
        if (cap <= 0) throw MediaException(MediaError.TOO_LARGE)
        var hit: MediaDownload? = null
        val token = disk {
            val index = prune(storage.index())
            if (!bypassCache) index.assets.firstOrNull { sameAsset(it.rendition, rendition) }?.let { asset ->
                if (asset.size > cap) throw MediaException(MediaError.TOO_LARGE)
                val bytes = storage.blob(asset.file, cap)
                if (bytes == null) {
                    storage.save(index.copy(assets = index.assets - asset))
                } else {
                    try {
                        if (bytes.size != asset.size || MediaLibraryStorage.digest(bytes) != asset.digest)
                            MediaCacheValidation.invalid()
                        validateBytes(bytes, rendition, cap)
                    } catch (error: MediaException) {
                        storage.delete(asset.file)
                        storage.save(index.copy(assets = index.assets - asset))
                        throw error
                    }
                    storage.save(index.copy(assets = index.assets.map {
                        if (it == asset) it.copy(used = nextUse(index)) else it
                    }))
                    hit = MediaDownload(bytes, MediaCacheInfo(expiresAt = asset.expires))
                }
            }
            token()
        }
        hit?.let { return it }
        val response = try {
            upstream.downloadResponse(rendition, cap, bypassCache)
        } catch (error: MediaException) {
            if (error.httpStatus in setOf(403, 404)) disk { check(token); invalidateUrl(rendition.url) }
            throw error
        }
        return disk {
            check(token)
            validateBytes(response.bytes, rendition, cap)
            val time = now()
            val expires = expiry(response.cacheInfo, time)
            val index = prune(storage.index())
            var assets = index.assets.filterNot { it.rendition.url == rendition.url }
            if (response.cacheInfo.storable && expires > time && response.bytes.size <= policy.maxBytes) {
                while (assets.size >= policy.maxFiles || assets.sumOf { it.size.toLong() } >
                    policy.maxBytes - response.bytes.size) {
                    assets = assets - assets.minBy { it.used }
                }
                // Remove evicted bytes before writing: even transient byte/file use stays bounded.
                index.assets.filterNot { it in assets }.forEach { storage.delete(it.file) }
                val asset = CachedAsset("${UUID.randomUUID()}.blob", rendition, response.bytes.size,
                    MediaLibraryStorage.digest(response.bytes), time, expires, nextUse(index))
                storage.saveBlob(asset.file, response.bytes)
                try {
                    storage.save(index.copy(assets = assets + asset))
                } catch (error: Exception) {
                    storage.delete(asset.file)
                    throw error
                }
            } else {
                storage.save(index.copy(assets = assets))
                index.assets.filterNot { it in assets }.forEach { storage.delete(it.file) }
            }
            response
        }
    }

    private fun saveResult(index: MediaCacheIndex, result: CachedResult) {
        var results = if (result.page.cacheInfo.storable && result.expires > result.created)
            (index.results + result).sortedByDescending { it.used }.take(policy.maxResults) else index.results
        while (results.isNotEmpty() && !storage.fits(index.copy(results = results))) {
            results = results - results.minBy { it.used }
        }
        // Caching is optional: an individually valid response can exceed our metadata admission budget.
        if (!storage.fits(index.copy(results = results))) return
        storage.save(index.copy(results = results))
    }

    private fun deliver(result: CachedResult) {
        delivered.removeAll { it.kind == result.kind && it.query == result.query &&
            it.language == result.language && it.offset == result.offset }
        delivered.add(0, result)
        while (delivered.size > policy.maxResults) delivered.removeAt(delivered.lastIndex)
    }

    private fun invalidateUrl(url: String) {
        val index = storage.index()
        val ids = index.results.flatMap { result ->
            result.page.items.filter { item -> item.renditions.any { it.url == url } }.map { result.kind to it.id }
        }.distinct()
        val assets = index.assets.filterNot { it.rendition.url == url }
        storage.save(index.copy(assets = assets))
        index.assets.filterNot { it in assets }.forEach { storage.delete(it.file) }
        ids.forEach { (kind, id) -> invalidateId(kind, id) }
    }

    private fun invalidateId(kind: MediaKind, id: String) {
        val index = storage.index()
        val urls = index.results.filter { it.kind == kind }.flatMap { it.page.items }
            .filter { it.id == id }.flatMap { it.renditions }.map { it.url }.toSet()
        val assets = index.assets.filterNot { it.rendition.url in urls }
        val results = index.results.filterNot { it.kind == kind && it.lookupId == id }.map {
            if (it.kind != kind) it else it.copy(page = it.page.copy(items = it.page.items.filterNot { item -> item.id == id }))
        }
        storage.save(index.copy(results = results, assets = assets))
        index.assets.filterNot { it in assets }.forEach { storage.delete(it.file) }
        delivered.removeAll { it.kind == kind && it.page.items.any { item -> item.id == id } }
        storage.history()?.let { history ->
            storage.save(history.copy(tabs = history.tabs.map {
                if (it.snapshot.kind != kind) it else it.copy(snapshot = it.snapshot.copy(
                    items = it.snapshot.items.filterNot { item -> item.id == id }))
            }))
        }
    }

    private fun prune(index: MediaCacheIndex): MediaCacheIndex {
        val time = now()
        val pruned = index.copy(results = index.results.filter { fresh(it.created, it.expires, time) },
            assets = index.assets.filter { fresh(it.created, it.expires, time) })
        if (pruned != index) storage.save(pruned)
        storage.discardOrphans(pruned.assets)
        storage.history()?.let { history ->
            val tabs = history.tabs.map {
                if (fresh(it.created, it.expires, time) || it.snapshot.items.isEmpty() && it.snapshot.nextOffset == null) it
                else it.copy(snapshot = it.snapshot.copy(items = emptyList(), nextOffset = null,
                    requiresRefresh = !it.snapshot.submitted.isNullOrBlank()))
            }
            if (tabs != history.tabs) storage.save(history.copy(tabs = tabs))
        }
        delivered.removeAll {
            time < it.created || time - it.created >= policy.ttlMillis ||
                !fresh(it.created, it.expires, time) && it.page.cacheInfo.storable
        }
        return pruned
    }

    private fun sameAsset(first: MediaRendition, second: MediaRendition) =
        first.url == second.url && first.mimeType == second.mimeType &&
            first.width == second.width && first.height == second.height

    private fun validateBytes(bytes: ByteArray, rendition: MediaRendition, cap: Int) {
        if (bytes.size > cap) throw MediaException(MediaError.TOO_LARGE)
        MediaFormat.inspect(bytes, rendition.copy(byteSize = null), preview = cap <= MediaLimits.PREVIEW_BYTES)
    }

    private fun validatePage(page: MediaPage) {
        if (page.items.size > MediaLimits.PAGE_SIZE ||
            page.nextOffset?.let { it !in 0..MediaLimits.MAX_OFFSET } == true) MediaCacheValidation.invalid()
        page.items.forEach(MediaCacheValidation::item)
    }

    private fun now(): Long = clock().also {
        if (it < 0 || it > Long.MAX_VALUE - policy.ttlMillis) throw MediaException(MediaError.STORAGE)
    }
    private fun deadline(time: Long) = time + policy.ttlMillis
    private fun expiry(info: MediaCacheInfo, time: Long) =
        if (!info.storable) time else minOf(deadline(time), info.expiresAt ?: deadline(time)).coerceAtLeast(time)
    private fun fresh(created: Long, expires: Long, time: Long) = time >= created && time < expires
    private fun nextUse(index: MediaCacheIndex): Long =
        maxOf(index.assets.maxOfOrNull { it.used } ?: 0, index.results.maxOfOrNull { it.used } ?: 0)
            .let { if (it == Long.MAX_VALUE) throw MediaException(MediaError.STORAGE) else it + 1 }

    private data class Token(val identity: String, val version: Long, val revision: Long,
        val kind: MediaKind?, val request: Long?)
    private fun token(kind: MediaKind? = null) =
        Token(cacheIdentity, credentialVersion, MediaLibraryStorage.revision, kind, requests[kind])
    private fun check(token: Token) {
        if (token.identity != cacheIdentity || token.version != credentialVersion ||
            token.revision != MediaLibraryStorage.revision ||
            token.kind != null && token.request != requests[token.kind])
            throw CancellationException("Media cache generation changed")
    }

    private fun allowed(): Boolean {
        if (!canAccess() || !upstream.available) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            context.getSystemService(UserManager::class.java)?.isUserUnlocked != true) return false
        return try { upstream.hasApiKey() } catch (error: MediaException) {
            if (error.reason in setOf(MediaError.LOCKED, MediaError.MISSING_KEY, MediaError.UNAVAILABLE)) false else throw error
        }
    }

    private suspend fun <T> disk(action: () -> T): T {
        if (!allowed()) throw MediaException(MediaError.UNAVAILABLE)
        val expectedVersion = credentialVersion
        val expectedIdentity = cacheIdentity
        val expectedRevision = MediaLibraryStorage.revision
        return withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            synchronized(MediaLibraryStorage.lock) {
                if (!allowed()) throw MediaException(MediaError.UNAVAILABLE)
                if (expectedVersion != credentialVersion || expectedIdentity != cacheIdentity ||
                    expectedRevision != MediaLibraryStorage.revision)
                    throw CancellationException("Media cache generation changed")
                storage.prepare(expectedIdentity)
                if (expectedIdentity != preparedIdentity || preparedRevision != MediaLibraryStorage.revision) {
                    delivered.clear()
                    preparedIdentity = expectedIdentity
                    preparedRevision = MediaLibraryStorage.revision
                }
                action()
            }
        }
    }
}
