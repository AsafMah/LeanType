// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class GiphyClient(
    private val clock: () -> Long = System::currentTimeMillis,
    private val openConnection: (URL) -> HttpURLConnection = {
        openPlatformConnection(it)
    }
) {
    suspend fun search(
        apiKey: String,
        kind: MediaKind,
        query: String,
        language: String,
        offset: Int,
        bypassCache: Boolean = false
    ): MediaPage {
        if (apiKey.isBlank()) throw MediaException(MediaError.MISSING_KEY)
        if (query.codePointCount(0, query.length) > MediaLimits.QUERY_CHARACTERS)
            throw MediaException(MediaError.QUERY_TOO_LONG)
        if (query.isBlank() || !Charsets.UTF_8.newEncoder().canEncode(query) ||
            offset !in 0..MediaLimits.MAX_OFFSET)
            throw MediaException(MediaError.INVALID_RESPONSE)
        val url = URL("https://api.giphy.com/v1/${kind.endpoint}/search" +
            "?api_key=${encode(apiKey)}&q=${encode(query)}&limit=${MediaLimits.PAGE_SIZE}" +
            "&offset=$offset&rating=r&lang=${encode(languageCode(language))}")
        val response = request(url, "application/json", MediaLimits.RESPONSE_BYTES, redirects = false, bypassCache)
        return GiphyResponseParser.parse(response.bytes, offset).copy(cacheInfo = response.cacheInfo)
    }

    suspend fun lookup(apiKey: String, kind: MediaKind, id: String): MediaLookup {
        if (apiKey.isBlank()) throw MediaException(MediaError.MISSING_KEY)
        MediaCacheValidation.id(id)
        // GIPHY's single-ID endpoint serves both GIFs and stickers.
        val url = URL("https://api.giphy.com/v1/gifs/${encode(id)}?api_key=${encode(apiKey)}")
        return try {
            val response = request(url, "application/json", MediaLimits.RESPONSE_BYTES, redirects = false, bypassCache = true)
            MediaLookup(GiphyResponseParser.parseLookup(response.bytes, kind, id), response.cacheInfo)
        } catch (error: MediaException) {
            if (error.httpStatus == 404) MediaLookup(null, MediaCacheInfo(storable = false)) else throw error
        }
    }

    suspend fun download(rendition: MediaRendition, maxBytes: Int): ByteArray =
        downloadResponse(rendition, maxBytes).bytes

    suspend fun downloadResponse(
        rendition: MediaRendition, maxBytes: Int, bypassCache: Boolean = false
    ): MediaDownload {
        if (rendition.mimeType !in setOf("image/gif", "image/webp") ||
            rendition.width <= 0 || rendition.height <= 0)
            throw MediaException(MediaError.UNSUPPORTED)
        if (maxBytes <= MediaLimits.PREVIEW_BYTES && (rendition.width > MediaLimits.PREVIEW_DIMENSION ||
                rendition.height > MediaLimits.PREVIEW_DIMENSION))
            throw MediaException(MediaError.TOO_LARGE)
        val cap = minOf(maxBytes, MediaLimits.MEDIA_BYTES)
        if (cap <= 0)
            throw MediaException(MediaError.TOO_LARGE)
        val url = mediaUrl(rendition.url)
        val response = request(url, rendition.mimeType, cap, redirects = true, bypassCache)
        // GIPHY may generate renditions dynamically, so metadata byteSize is only an estimate.
        // The transfer's Content-Length and actual read cap are enforced by request().
        GiphyMediaValidation.validate(response.bytes, rendition)
        return response
    }

    private suspend fun request(
        url: URL, accept: String, cap: Int, redirects: Boolean, bypassCache: Boolean = false
    ): MediaDownload =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                val active = AtomicReference<HttpURLConnection?>()
                val cancelled = AtomicBoolean(false)
                continuation.invokeOnCancellation {
                    cancelled.set(true)
                    active.getAndSet(null)?.disconnect()
                }
                fun checkCancelled() {
                    if (cancelled.get() || !continuation.isActive) throw CancellationException()
                }
                try {
                    var target = url
                    var result: MediaDownload? = null
                    var cacheInfo = MediaCacheInfo()
                    var redirectsFollowed = 0
                    while (result == null) {
                        checkCancelled()
                        val connection = openConnection(target)
                        active.set(connection)
                        try {
                            checkCancelled()
                            connection.requestMethod = "GET"
                            connection.instanceFollowRedirects = false
                            connection.useCaches = false
                            connection.connectTimeout = TIMEOUT_MILLIS
                            connection.readTimeout = TIMEOUT_MILLIS
                            connection.setRequestProperty("Accept", accept)
                            connection.setRequestProperty("Accept-Encoding", "identity")
                            connection.setRequestProperty("User-Agent", "LeanTypeDual")
                            if (bypassCache) {
                                connection.setRequestProperty("Cache-Control", "no-cache")
                                connection.setRequestProperty("Pragma", "no-cache")
                            }
                            val status = connection.responseCode
                            cacheInfo = GiphyHttpCache.combine(cacheInfo, GiphyHttpCache.read(connection, clock()))
                            checkCancelled()
                            if (status in REDIRECT_CODES) {
                                // Search redirects are never followed: their query contains the key.
                                if (!redirects || redirectsFollowed++ >= MAX_REDIRECTS)
                                    throw MediaException(MediaError.INVALID_RESPONSE)
                                val location = connection.getHeaderField("Location")
                                    ?: throw MediaException(MediaError.INVALID_RESPONSE)
                                target = mediaUrl(URL(target, location).toExternalForm())
                                continue
                            }
                            statusError(status)?.let {
                                // CDN URLs carry no API key; denial there does not invalidate the user's key.
                                throw MediaException(if (redirects && it == MediaError.INVALID_KEY)
                                    MediaError.INVALID_RESPONSE else it, status)
                            }
                            if (status != HttpURLConnection.HTTP_OK)
                                throw MediaException(MediaError.INVALID_RESPONSE, status)
                            val contentType = connection.getHeaderField("Content-Type")
                                ?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
                            if (contentType != null && contentType != accept &&
                                !(redirects && contentType == "application/octet-stream"))
                                throw MediaException(MediaError.INVALID_RESPONSE)
                            val encoding = connection.getHeaderField("Content-Encoding")
                            if (encoding != null && !encoding.equals("identity", ignoreCase = true))
                                throw MediaException(MediaError.INVALID_RESPONSE)
                            val length = connection.getHeaderField("Content-Length")?.let {
                                it.toLongOrNull()?.takeIf { size -> size >= 0 }
                                    ?: throw MediaException(MediaError.INVALID_RESPONSE)
                            }
                            if (length != null && length > cap) throw MediaException(MediaError.TOO_LARGE)
                            val bytes = connection.inputStream.use { input ->
                                val output = ByteArrayOutputStream(minOf(cap, 8192))
                                val buffer = ByteArray(8192)
                                while (true) {
                                    checkCancelled()
                                    val count = input.read(buffer, 0, minOf(buffer.size, cap - output.size() + 1))
                                    if (count < 0) break
                                    if (count == 0) continue
                                    if (output.size() + count > cap) throw MediaException(MediaError.TOO_LARGE)
                                    output.write(buffer, 0, count)
                                }
                                output.toByteArray()
                            }
                            if (length != null && length != bytes.size.toLong())
                                throw MediaException(MediaError.INVALID_RESPONSE)
                            result = MediaDownload(bytes, cacheInfo)
                        } finally {
                            active.compareAndSet(connection, null)
                            connection.disconnect()
                        }
                    }
                    checkCancelled()
                    continuation.resume(result)
                } catch (error: CancellationException) {
                    continuation.cancel(error)
                } catch (error: Exception) {
                    if (continuation.isActive)
                        continuation.resumeWithException(
                            if (error is MediaException) error else MediaException(MediaError.NETWORK))
                }
            }
        }

    companion object {
        private const val TIMEOUT_MILLIS = 15_000
        private const val MAX_REDIRECTS = 3
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val MEDIA_HOSTS = setOf("media.giphy.com", "media0.giphy.com", "media1.giphy.com",
            "media2.giphy.com", "media3.giphy.com", "media4.giphy.com", "i.giphy.com")
        // https://developers.giphy.com/docs/optional-settings/#language-support
        private val LANGUAGES = setOf("en", "es", "pt", "id", "fr", "ar", "tr", "th", "vi",
            "de", "it", "ja", "ru", "ko", "pl", "nl", "ro", "hu", "sv", "cs", "hi", "bn",
            "da", "fa", "tl", "fi", "he", "ms", "no", "uk")

        internal fun openPlatformConnection(url: URL): HttpURLConnection =
            url.openConnection() as HttpURLConnection

        internal fun languageCode(language: String): String {
            val tag = language.replace('_', '-').lowercase(Locale.ROOT)
            val parts = tag.split('-')
            if (parts.first() == "zh") return when {
                "hant" in parts -> "zh-TW"
                "hans" in parts -> "zh-CN"
                parts.any { it in setOf("tw", "hk", "mo") } -> "zh-TW"
                else -> "zh-CN"
            }
            val base = when (val code = parts.first()) {
                "in" -> "id"
                "iw" -> "he"
                else -> code
            }
            return base.takeIf { it in LANGUAGES } ?: "en"
        }

        internal fun mediaUrl(value: String): URL {
            try {
                MediaCacheValidation.url(value, asset = true)
            } catch (_: MediaException) {
                throw MediaException(MediaError.UNSUPPORTED)
            }
            val url = try { URL(value) } catch (_: Exception) {
                throw MediaException(MediaError.UNSUPPORTED)
            }
            if (url.protocol != "https" || url.host.lowercase(Locale.ROOT) !in MEDIA_HOSTS ||
                url.userInfo != null || url.port !in setOf(-1, 443) || url.ref != null)
                throw MediaException(MediaError.UNSUPPORTED)
            return url
        }

        internal fun statusError(status: Int): MediaError? = when (status) {
            401, 403 -> MediaError.INVALID_KEY
            429 -> MediaError.QUOTA
            in 500..599, 408 -> MediaError.NETWORK
            else -> null
        }

        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    }
}
