// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLDecoder
import java.net.URLStreamHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class GiphyClientTest {
    @Test
    fun exactQueryEndpointParametersAndNoTrackingOrCaching() = runBlocking {
        val query = " שלום + &?#/漢 😀 "
        for (kind in MediaKind.entries) {
            lateinit var connection: FakeConnection
            val client = GiphyClient { url ->
                FakeConnection(url, emptyPage(20)).also { connection = it }
            }
            assertEquals(MediaPage(emptyList(), null), client.search(KEY, kind, query, "he-IL", 20))
            assertEquals("https", connection.url.protocol)
            assertEquals("api.giphy.com", connection.url.host)
            assertEquals("/v1/${kind.endpoint}/search", connection.url.path)
            val parameters = connection.url.query.split('&').associate {
                val parts = it.split('=', limit = 2)
                URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
            }
            assertEquals(mapOf("api_key" to KEY, "q" to query, "limit" to "20",
                "offset" to "20", "rating" to "r", "lang" to "he"), parameters)
            assertEquals("GET", connection.requestMethod)
            assertEquals("application/json", connection.getRequestProperty("Accept"))
            assertEquals("identity", connection.getRequestProperty("Accept-Encoding"))
            assertEquals("LeanTypeDual", connection.getRequestProperty("User-Agent"))
            assertEquals(setOf("Accept", "Accept-Encoding", "User-Agent"), connection.requestProperties.keys)
            assertFalse(connection.useCaches)
            assertFalse(connection.instanceFollowRedirects)
            assertEquals(15_000, connection.connectTimeout)
            assertEquals(15_000, connection.readTimeout)
            assertTrue(connection.disconnects.get() > 0)
        }
    }

    @Test
    fun unicodeLimitCountsCodePointsWithoutTruncating() = runBlocking {
        var calls = 0
        val client = GiphyClient { FakeConnection(it, emptyPage()).also { calls++ } }
        client.search(KEY, MediaKind.GIF, "😀".repeat(50), "en", 0)
        assertEquals(1, calls)
        expectError(MediaError.QUERY_TOO_LONG) { client.search(KEY, MediaKind.GIF, "😀".repeat(51), "en", 0) }
        expectError(MediaError.INVALID_RESPONSE) { client.search(KEY, MediaKind.GIF, " ", "en", 0) }
        expectError(MediaError.INVALID_RESPONSE) { client.search(KEY, MediaKind.GIF, "\uD800", "en", 0) }
        expectError(MediaError.INVALID_RESPONSE) { client.search(KEY, MediaKind.GIF, "query", "en", -1) }
        expectError(MediaError.INVALID_RESPONSE) { client.search(KEY, MediaKind.GIF, "query", "en", 5000) }
        expectError(MediaError.MISSING_KEY) { client.search("", MediaKind.GIF, "query", "en", 0) }
        assertEquals(1, calls)
    }

    @Test
    fun languageUsesDocumentedCodesAndEnglishFallback() {
        mapOf("he" to "he", "iw_IL" to "he", "ms-MY" to "ms", "no-NO" to "no", "uk-UA" to "uk",
            "unknown" to "en", "pt_BR" to "pt",
            "zh-Hant" to "zh-TW", "zh_HK" to "zh-TW", "zh-CN" to "zh-CN", "zh" to "zh-CN",
            "zh-Hant-CN" to "zh-TW", "zh-Hans-HK" to "zh-CN", "zh_Hans_TW" to "zh-CN",
            "in_ID" to "id", "fr_CA" to "fr", "en-US" to "en").forEach { (input, expected) ->
            assertEquals(expected, GiphyClient.languageCode(input))
        }
    }

    @Test
    fun platformConnectionDoesNotForceAProxyOverride() {
        var normalOpens = 0
        val url = URL(null, "https://media.giphy.com/unit/owned.gif", object : URLStreamHandler() {
            override fun openConnection(url: URL): HttpURLConnection {
                normalOpens++
                return FakeConnection(url)
            }
            override fun openConnection(url: URL, proxy: Proxy): HttpURLConnection =
                fail("The client must leave proxy selection to the platform")
        })
        val connection = GiphyClient.openPlatformConnection(url)
        assertEquals(1, normalOpens)
        assertEquals(url, connection.url)
        connection.disconnect()
    }

    @Test
    fun errorsAreTypedWithoutReadingOrExposingResponseBodies() = runBlocking {
        for ((status, reason) in mapOf(401 to MediaError.INVALID_KEY, 403 to MediaError.INVALID_KEY,
            429 to MediaError.QUOTA, 500 to MediaError.NETWORK, 408 to MediaError.NETWORK,
            404 to MediaError.INVALID_RESPONSE)) {
            val connection = FakeConnection(URL("https://api.giphy.com"), status = status,
                input = { fail("An error response body must not be read") })
            expectError(reason) {
                GiphyClient { connection }.search(KEY, MediaKind.GIF, "private query", "en", 0)
            }
            assertTrue(connection.disconnects.get() > 0)
        }
        expectError(MediaError.NETWORK) {
            GiphyClient { throw SocketTimeoutException("Unsafe $KEY private query") }
                .search(KEY, MediaKind.GIF, "private query", "en", 0)
        }
    }

    @Test
    fun metadataRedirectsNeverSendKeyToAnotherHost() = runBlocking {
        var calls = 0
        val client = GiphyClient {
            calls++
            FakeConnection(it, status = 302, headers = mapOf("Location" to "https://example.invalid/collect"))
        }
        expectError(MediaError.INVALID_RESPONSE) { client.search(KEY, MediaKind.GIF, "query", "en", 0) }
        assertEquals(1, calls)
    }

    @Test
    fun mediaAccessDenialDoesNotMisreportAnInvalidApiKey() = runBlocking {
        for (status in listOf(401, 403)) {
            val client = GiphyClient { FakeConnection(it, status = status) }
            expectError(MediaError.INVALID_RESPONSE) { client.download(fixtureRendition(), 100) }
        }
    }

    @Test
    fun metadataHasDeclaredAndActualOneMebibyteBound() = runBlocking {
        val valid = emptyPage()
        val padded = valid + ByteArray(MediaLimits.RESPONSE_BYTES - valid.size) { 32 }
        GiphyClient { FakeConnection(it, padded) }.search(KEY, MediaKind.GIF, "query", "en", 0)
        expectError(MediaError.TOO_LARGE) {
            GiphyClient { FakeConnection(it, padded + byteArrayOf(32)) }
                .search(KEY, MediaKind.GIF, "query", "en", 0)
        }
        expectError(MediaError.TOO_LARGE) {
            GiphyClient { FakeConnection(it, headers = mapOf("Content-Length" to "${MediaLimits.RESPONSE_BYTES + 1}"),
                input = { fail("Oversized declared body must not be read") }) }
                .search(KEY, MediaKind.GIF, "query", "en", 0)
        }
        expectError(MediaError.INVALID_RESPONSE) {
            GiphyClient { FakeConnection(it, valid, headers = mapOf("Content-Length" to "${valid.size + 1}")) }
                .search(KEY, MediaKind.GIF, "query", "en", 0)
        }
    }

    @Test
    fun mediaUrlValidationAndRedirectsPreserveFullProviderUrl() = runBlocking {
        val urls = mutableListOf<String>()
        val target = "https://media1.giphy.com/media/unit/giphy.gif?cid=provider-value&rid=giphy.gif&ct=g"
        val rendition = fixtureRendition()
        val client = GiphyClient {
            urls += it.toExternalForm()
            if (urls.size == 1) FakeConnection(it, status = 302, headers = mapOf("Location" to target))
            else FakeConnection(it, GIF, headers = mapOf("Content-Type" to "image/gif"))
        }
        assertContentEquals(GIF, client.download(rendition, 100))
        assertEquals(listOf(rendition.url, target), urls)
        for (invalid in listOf("http://media.giphy.com/a.gif", "https://giphy.com.example.invalid/a.gif",
            "https://example.invalid/a.gif", "https://user@media.giphy.com/a.gif",
            "https://api.giphy.com/a.gif", "https://media.giphy.com:444/a.gif")) {
            expectError(MediaError.UNSUPPORTED) {
                GiphyClient { fail("Forbidden host must not be opened") }
                    .download(rendition.copy(url = invalid), 100)
            }
        }
        var redirects = 0
        expectError(MediaError.UNSUPPORTED) {
            GiphyClient {
                redirects++
                FakeConnection(it, status = 307, headers = mapOf("Location" to "https://example.invalid/a.gif"))
            }.download(rendition, 100)
        }
        assertEquals(1, redirects)
        redirects = 0
        expectError(MediaError.INVALID_RESPONSE) {
            GiphyClient {
                redirects++
                FakeConnection(it, status = 302, headers = mapOf("Location" to rendition.url))
            }.download(rendition, 100)
        }
        assertEquals(4, redirects)
    }

    @Test
    fun downloadVerifiesMimeDimensionsLengthsAndCaps() = runBlocking {
        val client = GiphyClient { FakeConnection(it, GIF) }
        assertContentEquals(GIF, client.download(fixtureRendition(), GIF.size))
        expectError(MediaError.TOO_LARGE) { client.download(fixtureRendition(), GIF.size - 1) }
        expectError(MediaError.INVALID_RESPONSE) {
            client.download(fixtureRendition().copy(width = 2), 100)
        }
        expectError(MediaError.INVALID_RESPONSE) {
            client.download(fixtureRendition().copy(mimeType = "image/webp"), 100)
        }
        expectError(MediaError.UNSUPPORTED) {
            client.download(fixtureRendition().copy(mimeType = "video/mp4"), 100)
        }
        expectError(MediaError.TOO_LARGE) {
            client.download(fixtureRendition().copy(preview = true, width = 513), 100)
        }
        for ((preview, cap) in listOf(true to MediaLimits.PREVIEW_BYTES, false to MediaLimits.MEDIA_BYTES)) {
            expectError(MediaError.TOO_LARGE) {
                GiphyClient { FakeConnection(it, headers = mapOf("Content-Length" to "${cap + 1}"),
                    input = { fail("Must reject declared media larger than cap") }) }
                    .download(fixtureRendition().copy(preview = preview), cap)
            }
            expectError(MediaError.TOO_LARGE) {
                GiphyClient { FakeConnection(it, ByteArray(cap + 1)) }
                    .download(fixtureRendition().copy(preview = preview), cap)
            }
        }
        expectError(MediaError.TOO_LARGE) {
            GiphyClient { FakeConnection(it, headers = mapOf("Content-Length" to "${MediaLimits.MEDIA_BYTES + 1}"),
                input = { fail("The hard media limit cannot be raised by a caller") }) }
                .download(fixtureRendition(), Int.MAX_VALUE)
        }
    }

    @Test
    fun dynamicRenditionSizeEstimatesDoNotOverrideActualTransferValidation() = runBlocking {
        val actualSize = GIF.size.toLong()
        for (estimate in listOf(actualSize - 1, actualSize + 1, MediaLimits.MEDIA_BYTES + 1L)) {
            val client = GiphyClient {
                FakeConnection(it, GIF, headers = mapOf("Content-Length" to "$actualSize"))
            }
            assertContentEquals(GIF, client.download(fixtureRendition().copy(byteSize = estimate), GIF.size))
        }
        val wrongHttpLength = GiphyClient {
            FakeConnection(it, GIF, headers = mapOf("Content-Length" to "${actualSize + 1}"))
        }
        expectError(MediaError.INVALID_RESPONSE) { wrongHttpLength.download(fixtureRendition(), 100) }
        val underestimated = fixtureRendition().copy(byteSize = 1)
        expectError(MediaError.TOO_LARGE) {
            GiphyClient { FakeConnection(it, GIF) }.download(underestimated, GIF.size - 1)
        }
    }

    @Test
    fun selectedPreviewRenditionUsesTheExplicitSelectedTransferLimit() = runBlocking {
        // Padding isolates transport policy from the renderer's structural validation.
        val bytes = GIF + ByteArray(MediaLimits.PREVIEW_BYTES)
        val rendition = fixtureRendition().copy(preview = true, byteSize = bytes.size.toLong())
        val client = GiphyClient { FakeConnection(it, bytes) }
        assertContentEquals(bytes, client.download(rendition, MediaLimits.MEDIA_BYTES))
        expectError(MediaError.TOO_LARGE) { client.download(rendition, MediaLimits.PREVIEW_BYTES) }
    }

    @Test
    fun cancellationDisconnectsBlockedReadPromptly() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val connection = FakeConnection(URL("https://api.giphy.com"), input = {
            object : InputStream() {
                override fun read(): Int {
                    entered.countDown()
                    assertTrue(released.await(5, TimeUnit.SECONDS), "Cancellation did not disconnect")
                    throw SocketTimeoutException("cancelled")
                }
            }
        }, onDisconnect = { released.countDown() })
        val job = launch(Dispatchers.Default) {
            GiphyClient { connection }.search(KEY, MediaKind.GIF, "query", "en", 0)
            fail("Cancelled requests must not deliver a page")
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(connection.disconnects.get() > 0)
    }

    companion object {
        internal const val KEY = "unit-test-not-a-real-api-key"
        // An owned, single transparent pixel; no downloaded/provider content.
        internal val GIF = byteArrayOf(71, 73, 70, 56, 57, 97, 1, 0, 1, 0, -128, 0, 0,
            0, 0, 0, -1, -1, -1, 33, -7, 4, 1, 0, 0, 0, 0, 44, 0, 0, 0, 0, 1, 0, 1, 0,
            0, 2, 2, 68, 1, 0, 59)
        internal fun fixtureRendition() =
            MediaRendition("https://media.giphy.com/media/unit/giphy.gif", "image/gif", 1, 1)
        internal fun emptyPage(offset: Int = 0) =
            """{"meta":{"status":200},"data":[],"pagination":{"offset":$offset,"count":0,"total_count":0}}"""
                .toByteArray()
        internal suspend fun expectError(reason: MediaError, action: suspend () -> Unit) {
            try {
                action()
                fail("Expected $reason")
            } catch (error: MediaException) {
                assertEquals(reason, error.reason)
                assertEquals(reason.name, error.message)
                assertEquals(null, error.cause)
            }
        }
    }
}

internal class FakeConnection(
    url: URL,
    private val body: ByteArray = GiphyClientTest.emptyPage(),
    private val status: Int = 200,
    private val headers: Map<String, String> = emptyMap(),
    private val input: () -> InputStream = { ByteArrayInputStream(body) },
    private val onDisconnect: () -> Unit = {}
) : HttpURLConnection(url) {
    val disconnects = AtomicInteger()
    override fun connect() {}
    override fun disconnect() { disconnects.incrementAndGet(); onDisconnect() }
    override fun usingProxy() = false
    override fun getResponseCode() = status
    override fun getInputStream() = input()
    override fun getHeaderField(name: String?) = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
}
