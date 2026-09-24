// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.net.URL
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GiphyCacheTest {
    private val time = 1_700_000_000_000L

    @Test fun repeatedHeadersCannotHideNoStoreOrConflictingFreshness() {
        fun headers(values: Map<String, List<String>>) = object : java.net.HttpURLConnection(URL("https://media.giphy.com/owned.gif")) {
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy() = false
            override fun getHeaderFields() = values
            override fun getHeaderField(name: String) = values.entries.firstOrNull { it.key.equals(name, true) }?.value?.lastOrNull()
        }
        assertFalse(GiphyHttpCache.read(headers(mapOf("Cache-Control" to listOf("no-store", "max-age=3600"))), time).storable)
        assertFalse(GiphyHttpCache.read(headers(mapOf("Pragma" to listOf("no-cache", "other"))), time).storable)
        assertFalse(GiphyHttpCache.read(headers(mapOf("Age" to listOf("1", "2"))), time).storable)
        assertFalse(GiphyHttpCache.read(headers(mapOf("Cache-Control" to listOf("public"), "cache-control" to listOf("no-store"))), time).storable)
    }

    @Test
    fun cacheControlNoStoreNoCachePrivateVaryCookiesAndMalformedAgesAreNotStored() {
        for (headers in listOf(
            mapOf("Cache-Control" to "no-store"),
            mapOf("Cache-Control" to "max-age=100, No-Cache=\"field\""),
            mapOf("Cache-Control" to "private, max-age=300"),
            mapOf("Cache-Control" to "max-age=-1"),
            mapOf("Cache-Control" to "max-age=abc"),
            mapOf("Cache-Control" to "max-age=12,max-age=13"),
            mapOf("Cache-Control" to "max-age=1", "Age" to "bad"),
            mapOf("Expires" to "0"),
            mapOf("Pragma" to "no-cache"),
            mapOf("Vary" to "*"),
            mapOf("Set-Cookie" to "owned=fixture")
        )) {
            assertFalse(info(headers).storable, headers.keys.joinToString())
        }
    }

    @Test
    fun expiryHonorsAgeDateExpiresAndOneHourUpperBound() {
        assertEquals(time + 20_000, info(mapOf("Cache-Control" to "public, max-age=30", "Age" to "10")).expiresAt)
        assertEquals(time, info(mapOf("Cache-Control" to "max-age=10", "Age" to "10")).expiresAt)
        assertEquals(time + 3_600_000, info(mapOf("Cache-Control" to "max-age=9999999")).expiresAt)
        assertEquals(time + 20_000, info(mapOf("Date" to "Tue, 14 Nov 2023 22:13:10 GMT",
            "Expires" to "Tue, 14 Nov 2023 22:13:40 GMT")).expiresAt)
        assertEquals(time, info(mapOf("Expires" to "Tue, 14 Nov 2023 22:13:00 GMT")).expiresAt)
        assertEquals(MediaCacheInfo(), info(emptyMap()))
    }

    @Test
    fun searchAndBytesExposeOnlySanitizedExpiryAndRefreshSendsNoCacheHeaders() = runBlocking {
        lateinit var connection: FakeConnection
        val client = GiphyClient(clock = { time }) {
            FakeConnection(it, headers = mapOf("Cache-Control" to "max-age=10")).also { connection = it }
        }
        val result = client.search(GiphyClientTest.KEY, MediaKind.GIF, "owned", "en", 0, bypassCache = true)
        assertEquals(time + 10_000, result.cacheInfo.expiresAt)
        assertEquals("no-cache", connection.getRequestProperty("Cache-Control"))
        assertEquals("no-cache", connection.getRequestProperty("Pragma"))
        val gif = OwnedMediaFixtures.gif()
        val rendition = OwnedMediaFixtures.rendition(gif).copy(url = "https://media.giphy.com/owned.gif?cid=provider&rid=owned.gif")
        val bytes = GiphyClient(clock = { time }) {
            assertEquals(rendition.url, it.toExternalForm())
            FakeConnection(it, gif, headers = mapOf("Cache-Control" to "max-age=10", "Content-Type" to "image/gif"))
                .also { connection = it }
        }.downloadResponse(rendition, MediaLimits.MEDIA_BYTES, bypassCache = true)
        assertContentEquals(gif, bytes.bytes)
        assertEquals(time + 10_000, bytes.cacheInfo.expiresAt)
        assertEquals("no-cache", connection.getRequestProperty("Cache-Control"))
        assertFalse(connection.useCaches)
    }

    @Test
    fun redirectNoStoreCannotBeOverriddenByFinalCacheableResponse() = runBlocking {
        var calls = 0
        val client = GiphyClient(clock = { time }) {
            calls++
            if (calls == 1) FakeConnection(it, status = 302, headers = mapOf(
                "Location" to "https://media1.giphy.com/owned.gif", "Cache-Control" to "no-store"))
            else FakeConnection(it, OwnedMediaFixtures.gif(), headers = mapOf("Cache-Control" to "max-age=100"))
        }
        val rendition = OwnedMediaFixtures.rendition().copy(url = "https://media.giphy.com/owned.gif")
        assertFalse(client.downloadResponse(rendition, MediaLimits.MEDIA_BYTES).cacheInfo.storable)
    }

    @Test
    fun lookupUsesIdEndpointNeverSearchAndMissingIdsReturnNull() = runBlocking {
        val paths = mutableListOf<String>()
        val client = GiphyClient {
            paths += it.path
            assertEquals("api_key=${GiphyClientTest.KEY}", it.query)
            FakeConnection(it, lookupBody())
        }
        assertEquals("owned", client.lookup(GiphyClientTest.KEY, MediaKind.GIF, "owned").item!!.id)
        assertEquals("owned", client.lookup(GiphyClientTest.KEY, MediaKind.STICKER, "owned").item!!.id)
        assertEquals(listOf("/v1/gifs/owned", "/v1/gifs/owned"), paths)
        assertNull(GiphyClient { FakeConnection(it, status = 404) }
            .lookup(GiphyClientTest.KEY, MediaKind.GIF, "missing").item)
        assertNull(GiphyResponseParser.parseLookup("""{"meta":{"status":200},"data":null}""".toByteArray(),
            MediaKind.GIF, "missing"))
        assertEquals(MediaError.INVALID_RESPONSE, assertFailsWith<MediaException> {
            client.lookup(GiphyClientTest.KEY, MediaKind.GIF, "../search")
        }.reason)
    }

    @Test
    fun credentialBearingAssetParametersAreRejectedBeforeOpeningConnection() = runBlocking {
        val rendition = OwnedMediaFixtures.rendition().copy(url = "https://media.giphy.com/owned.gif?api%5Fkey=not-a-key")
        val client = GiphyClient { error("Must not open a credential-bearing asset URL") }
        assertEquals(MediaError.UNSUPPORTED, assertFailsWith<MediaException> {
            client.download(rendition, MediaLimits.MEDIA_BYTES)
        }.reason)
        val error = assertFailsWith<MediaException> {
            GiphyClient { FakeConnection(it, status = 403) }
                .download(rendition.copy(url = "https://media.giphy.com/owned.gif"), MediaLimits.MEDIA_BYTES)
        }
        assertEquals(403, error.httpStatus)
        assertEquals(MediaError.INVALID_RESPONSE, error.reason)
    }

    private fun info(headers: Map<String, String>) =
        GiphyHttpCache.read(FakeConnection(URL("https://media.giphy.com/owned.gif"), headers = headers), time)

    private fun lookupBody() = """{"meta":{"status":200},"data":{"id":"owned","title":"Owned",
        "url":"https://giphy.com/gifs/owned","is_sticker":1,"images":{"original":{
        "url":"https://media.giphy.com/owned.gif","width":"2","height":"1"}}}}""".toByteArray()
}
