// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.content.Context
import android.os.UserManager
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [HostAtomicFile::class])
class MediaLibraryTest {
    private lateinit var context: Context
    private lateinit var source: Source
    private var time = 10_000L
    private var access = true
    private val root get() = File(context.applicationContext.noBackupFilesDir, MediaLibraryStorage.DIRECTORY)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        synchronized(MediaLibraryStorage.lock) { MediaLibraryStorage.clearAll(context) }
        source = Source()
        time = 10_000
        access = true
    }

    private fun library(policy: MediaCachePolicy = MediaCachePolicy(ttlMillis = 1000)) =
        MediaLibrary(context, source, { access }, policy) { time }

    private suspend fun accept(library: MediaLibrary, query: String = "owned", kind: MediaKind = MediaKind.GIF): MediaPage {
        val page = library.search(kind, query, "en")
        library.remember(MediaSnapshot(kind, query, query, "en", page.items, page.nextOffset))
        return page
    }

    @Test
    fun atomicFileReplacementKeepsRealWritesAndReadback() {
        val atomic = AtomicFile(File(context.noBackupFilesDir, "owned_atomic_replacement"))
        try {
            for (value in listOf("first", "replacement")) {
                val output = atomic.startWrite()
                output.write(value.toByteArray())
                atomic.finishWrite(output)
                assertEquals(value, atomic.openRead().use { it.readBytes().decodeToString() },
                    ShadowLog.getLogsForTag("AtomicFile").joinToString { it.msg })
            }
            val interrupted = atomic.startWrite()
            interrupted.write("interrupted".toByteArray())
            atomic.failWrite(interrupted)
            assertEquals("replacement", atomic.openRead().use { it.readBytes().decodeToString() })
        } finally {
            atomic.delete()
        }
    }

    @Test
    fun warmResultsAndActualGifWebpBytesSurviveRestartWithoutNetwork() = runBlocking {
        val first = library()
        val page = accept(first)
        val gif = page.items.single().renditions.single()
        val webp = gif.copy(url = "https://media.giphy.com/owned.webp", mimeType = "image/webp", width = 3,
            byteSize = null)
        assertContentEquals(OwnedMediaFixtures.gif(), first.download(gif, MediaLimits.MEDIA_BYTES))
        assertContentEquals(OwnedMediaFixtures.animatedWebp(), first.download(webp, MediaLimits.MEDIA_BYTES))
        first.rememberPosition(MediaKind.GIF, "draft", 4, 12)
        val second = library()
        assertEquals(page, second.search(MediaKind.GIF, "owned", "en"))
        assertContentEquals(OwnedMediaFixtures.gif(), second.download(gif, MediaLimits.MEDIA_BYTES))
        assertContentEquals(OwnedMediaFixtures.animatedWebp(), second.download(webp, MediaLimits.MEDIA_BYTES))
        val restored = assertNotNull(second.restore()).tabs.single()
        assertEquals("draft", restored.draft)
        assertEquals("owned", restored.submitted)
        assertEquals(4, restored.scrollPosition)
        assertEquals(12, restored.scrollOffset)
        assertEquals(page.items, restored.items)
        assertEquals(1, source.searches)
        assertEquals(2, source.downloads)
        assertEquals(2, root.listFiles()!!.count { it.extension == "blob" })
    }

    @Test
    fun exactTtlBoundaryDropsUrlsButKeepsQueryAndPositionWithoutRenewingOnHide() = runBlocking {
        val library = library()
        val page = accept(library)
        library.download(page.items.single().renditions.single(), MediaLimits.MEDIA_BYTES)
        time += 999
        library.rememberPosition(MediaKind.GIF, "draft", 7, 3)
        assertFalse(library.restore()!!.tabs.single().requiresRefresh)
        time++
        val tab = library.restore()!!.tabs.single()
        assertTrue(tab.requiresRefresh)
        assertTrue(tab.items.isEmpty())
        assertEquals("owned", tab.submitted)
        assertEquals("draft", tab.draft)
        assertEquals(7, tab.scrollPosition)
        assertEquals(0, root.listFiles()!!.count { it.extension == "blob" })
        assertEquals(1, source.searches)
        library.search(MediaKind.GIF, "owned", "en")
        assertEquals(2, source.searches)
    }

    @Test
    fun resultAndLookupRecordsUseFiniteLru() = runBlocking {
        val library = library(MediaCachePolicy(ttlMillis = 1000, maxResults = 2))
        library.search(MediaKind.GIF, "one", "en")
        library.search(MediaKind.GIF, "two", "en")
        library.search(MediaKind.GIF, "one", "en")
        library.search(MediaKind.GIF, "three", "en")
        library.search(MediaKind.GIF, "one", "en")
        assertEquals(3, source.searches)
        library.search(MediaKind.GIF, "two", "en")
        assertEquals(4, source.searches)
    }

    @Test
    fun assetByteAndCountBoundsEvictLeastRecentlyUsedNotStagingLeases() = runBlocking {
        val gif = source.item("one").renditions.single()
        val two = gif.copy(url = "https://media.giphy.com/two.gif")
        val three = gif.copy(url = "https://media.giphy.com/three.gif")
        val budget = OwnedMediaFixtures.gif().size * 2L
        val library = library(MediaCachePolicy(ttlMillis = 1000, maxFiles = 2, maxBytes = budget))
        val staging = File(context.cacheDir, MediaFiles.DIRECTORY).apply { mkdirs() }
        val lease = File(staging, "9223372036854775807_owned.gif").apply { writeBytes(OwnedMediaFixtures.gif()) }
        try {
            library.download(gif, MediaLimits.MEDIA_BYTES)
            library.download(two, MediaLimits.MEDIA_BYTES)
            library.download(gif, MediaLimits.MEDIA_BYTES)
            library.download(three, MediaLimits.MEDIA_BYTES)
            library.download(gif, MediaLimits.MEDIA_BYTES)
            assertEquals(3, source.downloads)
            assertEquals(2, root.listFiles()!!.count { it.extension == "blob" })
            assertTrue(root.listFiles()!!.filter { it.extension == "blob" }.sumOf { it.length() } <= budget)
            library.download(two, MediaLimits.MEDIA_BYTES)
            assertEquals(4, source.downloads)
            library.clearHistory()
            assertTrue(lease.exists())
            assertContentEquals(OwnedMediaFixtures.gif(), lease.readBytes())
        } finally {
            lease.delete()
        }
    }

    @Test
    fun byteBudgetSmallerThanOneAssetDoesNotPersistIt() = runBlocking {
        val library = library(MediaCachePolicy(ttlMillis = 1000, maxBytes = 1))
        val rendition = source.item("one").renditions.single()
        repeat(2) { library.download(rendition, MediaLimits.MEDIA_BYTES) }
        assertEquals(2, source.downloads)
        assertEquals(0, root.listFiles()!!.count { it.extension == "blob" })
    }

    @Test
    fun noStoreResultsRetainOnlyUserQueryAndNoStoreBytesNeverPersist() = runBlocking {
        source.info = MediaCacheInfo(storable = false)
        val library = library()
        val page = accept(library)
        val rendition = page.items.single().renditions.single()
        repeat(2) { library.download(rendition, MediaLimits.MEDIA_BYTES) }
        val restored = library().restore()!!.tabs.single()
        assertEquals("owned", restored.submitted)
        assertTrue(restored.items.isEmpty())
        assertTrue(restored.requiresRefresh)
        library.search(MediaKind.GIF, "owned", "en")
        assertEquals(2, source.searches)
        assertEquals(2, source.downloads)
        assertEquals(0, root.listFiles()!!.count { it.extension == "blob" })
    }

    @Test
    fun explicitRefreshAndByteBypassReachUpstreamAndFailedRefreshPreservesSuccess() = runBlocking {
        val library = library()
        val page = accept(library)
        val rendition = page.items.single().renditions.single()
        library.download(rendition, MediaLimits.MEDIA_BYTES)
        source.searchFailure = MediaException(MediaError.NETWORK)
        assertFailsWith<MediaException> { library.refresh(MediaKind.GIF, "owned", "en") }
        assertEquals(page, library.search(MediaKind.GIF, "owned", "en"))
        assertEquals(page.items, library.restore()!!.tabs.single().items)
        source.searchFailure = null
        library.downloadResponse(rendition, MediaLimits.MEDIA_BYTES, bypassCache = true)
        assertEquals(2, source.downloads)
        assertTrue(source.lastBypass)
        library.refresh(MediaKind.GIF, "owned", "en")
        assertEquals(2, source.refreshes)
        assertEquals(0, root.listFiles()!!.count { it.extension == "blob" })
    }

    @Test
    fun deniedPrivateLockedAndOfflineContextsDoNotReadWriteOrNetwork() = runBlocking {
        val library = library()
        val page = accept(library)
        library.pin(MediaKind.GIF, page.items.single())
        val before = root.walkTopDown().filter { it.isFile }.associate { it.name to it.readBytes().toList() }
        access = false
        assertNull(library.restore())
        assertTrue(library.pins().isEmpty())
        assertFailsWith<MediaException> { library.search(MediaKind.GIF, "blocked", "en") }
        assertFailsWith<MediaException> { library.rememberPosition(MediaKind.GIF, "blocked", 0, 0) }
        assertFailsWith<MediaException> { library.clearHistory() }
        assertEquals(before, root.walkTopDown().filter { it.isFile }.associate { it.name to it.readBytes().toList() })
        access = true
        shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        try {
            assertNull(library.restore())
            assertTrue(library.pins().isEmpty())
        } finally {
            shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        }
        source.available = false
        assertNull(library.restore())
        assertTrue(library.pins().isEmpty())
        assertEquals(1, source.searches)
    }

    @Test
    fun keyGenerationChangesClearBookmarksAndHistoryAcrossNewWrappers() = runBlocking {
        val first = library()
        val page = accept(first)
        first.pin(MediaKind.GIF, page.items.single())
        first.download(page.items.single().renditions.single(), MediaLimits.MEDIA_BYTES)
        source.cacheIdentity = "replacement-generation"
        source.credentialVersion = 0 // A process restart must not make the old namespace valid.
        val restarted = library()
        assertNull(restarted.restore())
        assertTrue(restarted.pins().isEmpty())
        assertEquals(0, root.listFiles()!!.count { it.extension == "blob" })
    }

    @Test
    fun lateOldKeyAndPrivateResponsesCannotWriteCache() = runBlocking {
        val library = library()
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        source.beforeSearch = { entered.complete(Unit); finish.await() }
        val pending = async { library.search(MediaKind.GIF, "owned", "en") }
        entered.await()
        source.cacheIdentity = "new-generation"
        source.credentialVersion++
        finish.complete(Unit)
        assertFailsWith<CancellationException> { pending.await() }
        assertNull(library.restore())
        assertTrue(library.pins().isEmpty())
    }

    @Test
    fun clearingHistoryFencesAnInFlightDownloadButKeepsPins() = runBlocking {
        val library = library()
        library.pin(MediaKind.GIF, source.item("one"))
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        source.beforeDownload = { entered.complete(Unit); finish.await() }
        val pending = async { library.download(source.item("one").renditions.single(), MediaLimits.MEDIA_BYTES) }
        entered.await()
        library.clearHistory()
        finish.complete(Unit)
        assertFailsWith<CancellationException> { pending.await() }
        assertEquals(listOf("one"), library.pins().map { it.id })
        assertEquals(0, root.listFiles()!!.count { it.extension == "blob" })
    }

    @Test
    fun bookmarksAreIdOnlyNewestFirstDuplicateStableBoundedAndExplicitlyRemoved() = runBlocking {
        val library = library(MediaCachePolicy(ttlMillis = 1000, maxBookmarks = 2))
        library.pin(MediaKind.GIF, source.item("one"))
        library.pin(MediaKind.GIF, source.item("two"))
        library.pin(MediaKind.GIF, source.item("one"))
        assertEquals(listOf("two", "one"), library.pins().map { it.id })
        library.pin(MediaKind.GIF, source.item("three"))
        assertEquals(listOf("three", "two"), library.pins().map { it.id })
        assertFalse(File(root, "bookmarks.json").readText().contains("media.giphy.com"))
        library.clearHistory()
        assertEquals(listOf("three", "two"), library().pins().map { it.id })
        library.unpin(MediaKind.GIF, "two")
        assertEquals(listOf("three"), library.pins().map { it.id })
    }

    @Test
    fun pinsResolveOnlyOnExplicitActionWithIdLookupAndRespectTtlAndMissingIds() = runBlocking {
        val library = library()
        library.pin(MediaKind.STICKER, source.item("one"))
        val pin = library().pins().single()
        assertEquals(0, source.lookups)
        assertNotNull(library.resolve(pin))
        assertNotNull(library.resolve(pin))
        assertEquals(1, source.lookups)
        time += 1000
        source.missing = true
        assertNull(library.resolve(pin))
        assertEquals(2, source.lookups)
        assertEquals(0, source.searches)
        assertEquals(listOf(pin), library.pins())
    }

    @Test
    fun defaultUnsupportedLookupDoesNotSearch() = runBlocking {
        val upstream = object : MediaSource by source {
            override suspend fun lookup(kind: MediaKind, id: String): MediaItem? = null
            override suspend fun lookupResponse(kind: MediaKind, id: String) = MediaLookup(null)
        }
        val library = MediaLibrary(context, upstream, { true })
        library.pin(MediaKind.GIF, source.item("one"))
        assertNull(library.resolve(library.pins().single()))
        assertEquals(0, source.searches)
    }

    @Test
    fun corruptMetadataAndBlobAreDeletedAndSurfaceTypedFailures() = runBlocking {
        val library = library()
        val page = accept(library)
        val rendition = page.items.single().renditions.single()
        library.download(rendition, MediaLimits.MEDIA_BYTES)
        val blob = root.listFiles()!!.single { it.extension == "blob" }
        blob.writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(MediaError.INVALID_RESPONSE,
            assertFailsWith<MediaException> { library.download(rendition, MediaLimits.MEDIA_BYTES) }.reason)
        assertFalse(blob.exists())
        File(root, "history.json").writeText("{broken")
        assertEquals(MediaError.INVALID_RESPONSE, assertFailsWith<MediaException> { library.restore() }.reason)
        assertFalse(File(root, "history.json").exists())
        File(root, "index.json").writeText("[".repeat(100))
        assertEquals(MediaError.INVALID_RESPONSE,
            assertFailsWith<MediaException> { library.search(MediaKind.GIF, "owned", "en") }.reason)
        assertFalse(File(root, "index.json").exists())
    }

    @Test
    fun metadataAndSnapshotBudgetsAreFiniteAndCredentialBearingUrlsAreRejected() = runBlocking {
        val library = library(MediaCachePolicy(ttlMillis = 1000, metadataBytes = 300))
        assertEquals("owned", library.search(MediaKind.GIF, "owned", "en").items.single().id)
        library.search(MediaKind.GIF, "owned", "en")
        assertEquals(2, source.searches, "A valid result too large to cache must still be usable")
        val unsafe = source.item("one").renditions.single().copy(url = "https://media.giphy.com/one.gif?api%5fkey=fixture")
        assertEquals(MediaError.INVALID_RESPONSE,
            assertFailsWith<MediaException> { library.download(unsafe, MediaLimits.MEDIA_BYTES) }.reason)
        assertEquals(0, source.downloads)
    }

    @Test fun metadataBudgetEvictsOlderRecordsRatherThanFailingNewSearches() = runBlocking {
        val upstream = object : MediaSource by source {
            override suspend fun search(kind: MediaKind, query: String, language: String, offset: Int): MediaPage {
                source.searches++
                return MediaPage(listOf(source.item(query).copy(title = "x".repeat(900))), null)
            }
        }
        val library = MediaLibrary(context, upstream, { true }, MediaCachePolicy(metadataBytes = 2000)) { time }
        for (query in listOf("one", "two", "three")) {
            assertEquals(query, library.search(MediaKind.GIF, query, "en").items.single().id)
        }
        assertEquals(3, source.searches)
        library.search(MediaKind.GIF, "three", "en")
        assertEquals(3, source.searches)
        assertTrue(File(root, "index.json").length() <= 2000)
    }

    @Test fun noStorePinPersistsOnlyTheExplicitIdAndKind() = runBlocking {
        val library = library()
        source.info = MediaCacheInfo(storable = false)
        val item = library.search(MediaKind.GIF, "owned", "en").items.single()
        library.pin(MediaKind.GIF, item)
        val pin = library.pins().single()
        assertEquals("owned", pin.id)
        assertTrue(listOf(pin.title, pin.pageUrl, pin.sourceName, pin.sourceUrl).all { it.isEmpty() })
        val stored = File(root, "bookmarks.json").readText()
        assertFalse(stored.contains(item.pageUrl))
        assertFalse(stored.contains(item.title))
    }
    @Test
    fun snapshotBudgetRejectsOversizedHistoryWithoutReplacingSuccessRecords() = runBlocking {
        val library = library(MediaCachePolicy(ttlMillis = 1000, snapshotBytes = 100))
        val page = library.search(MediaKind.GIF, "owned", "en")
        assertEquals(MediaError.TOO_LARGE, assertFailsWith<MediaException> {
            library.remember(MediaSnapshot(MediaKind.GIF, "owned", "owned", "en", page.items, null))
        }.reason)
        assertFalse(File(root, "history.json").exists())
        assertEquals(page, library.search(MediaKind.GIF, "owned", "en"))
        assertEquals(1, source.searches)
    }

    @Test
    fun responseExpiryCanShortenButNeverExtendLocalTtlAndRollbackDoesNotReviveResults() = runBlocking {
        val library = library()
        source.info = MediaCacheInfo(expiresAt = time + 10)
        accept(library)
        time += 10
        assertTrue(library.restore()!!.tabs.single().items.isEmpty())
        source.info = MediaCacheInfo(expiresAt = time + 100_000)
        accept(library)
        time += 1000
        assertTrue(library.restore()!!.tabs.single().items.isEmpty())
        source.info = MediaCacheInfo()
        accept(library)
        time--
        assertTrue(library.restore()!!.tabs.single().items.isEmpty())
    }

    @Test
    fun aSupersededSearchCannotReplaceNewSuccessOrBecomeHistory() = runBlocking {
        val library = library()
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        source.beforeSearch = { entered.complete(Unit); finish.await() }
        val old = async { library.search(MediaKind.GIF, "old", "en") }
        entered.await()
        source.beforeSearch = {}
        val accepted = accept(library, "new")
        finish.complete(Unit)
        assertFailsWith<CancellationException> { old.await() }
        assertEquals("new", library.restore()!!.tabs.single().submitted)
        assertEquals(accepted, library.search(MediaKind.GIF, "new", "en"))
    }

    @Test
    fun cdn403InvalidatesIdResultAndAssetWithoutRemovingExplicitBookmark() = runBlocking {
        val library = library()
        val page = accept(library)
        val item = page.items.single()
        library.pin(MediaKind.GIF, item)
        val rendition = item.renditions.single()
        library.download(rendition, MediaLimits.MEDIA_BYTES)
        source.downloadFailure = MediaException(MediaError.INVALID_RESPONSE, 403)
        assertFailsWith<MediaException> { library.downloadResponse(rendition, MediaLimits.MEDIA_BYTES, true) }
        assertTrue(library.restore()!!.tabs.single().items.isEmpty())
        assertTrue(library.search(MediaKind.GIF, "owned", "en").items.isEmpty())
        assertEquals(1, library.pins().size)
        assertEquals(0, root.listFiles()!!.count { it.extension == "blob" })
    }

    private class Source : MediaSource {
        override var available = true
        override var credentialVersion = 1L
        override var cacheIdentity = "owned-generation"
        override fun hasApiKey() = true
        var searches = 0
        var refreshes = 0
        var downloads = 0
        var lookups = 0
        var lastBypass = false
        var missing = false
        var info = MediaCacheInfo()
        var searchFailure: MediaException? = null
        var downloadFailure: MediaException? = null
        var beforeSearch: suspend () -> Unit = {}
        var beforeDownload: suspend () -> Unit = {}
        override suspend fun search(kind: MediaKind, query: String, language: String, offset: Int): MediaPage {
            searches++
            beforeSearch()
            searchFailure?.let { throw it }
            return MediaPage(listOf(item(query)), null, info)
        }
        override suspend fun refresh(kind: MediaKind, query: String, language: String, offset: Int): MediaPage {
            refreshes++
            return search(kind, query, language, offset)
        }
        override suspend fun download(rendition: MediaRendition, maxBytes: Int) =
            downloadResponse(rendition, maxBytes).bytes
        override suspend fun downloadResponse(rendition: MediaRendition, maxBytes: Int, bypassCache: Boolean): MediaDownload {
            downloads++
            lastBypass = bypassCache
            beforeDownload()
            downloadFailure?.let { throw it }
            val bytes = if (rendition.mimeType == "image/webp") OwnedMediaFixtures.animatedWebp() else OwnedMediaFixtures.gif()
            return MediaDownload(bytes, info)
        }
        override suspend fun lookupResponse(kind: MediaKind, id: String): MediaLookup {
            lookups++
            return MediaLookup(if (missing) null else item(id), info)
        }
        fun item(id: String) = MediaItem(id, "Owned animation", "https://giphy.com/gifs/$id",
            "Owned source", "https://example.invalid/owned", listOf(MediaRendition(
                "https://media.giphy.com/$id.gif", "image/gif", 2, 1, preview = true)))
    }
}
