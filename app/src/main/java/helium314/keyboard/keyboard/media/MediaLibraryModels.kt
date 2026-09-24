// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** Local bounds, not a statement of a provider's retention permissions. */
data class MediaCachePolicy(
    val ttlMillis: Long = 60 * 60 * 1000L,
    val maxBytes: Long = 64 * 1024 * 1024L,
    val maxFiles: Int = 64,
    val maxResults: Int = 20,
    val maxBookmarks: Int = 200,
    val metadataBytes: Int = 1024 * 1024,
    val snapshotBytes: Int = 1024 * 1024
) {
    init {
        require(ttlMillis in 1..60 * 60 * 1000L)
        require(maxBytes in 1..64 * 1024 * 1024L && maxFiles in 1..64)
        require(maxResults in 1..20 && maxBookmarks in 1..200)
        require(metadataBytes in 1..1024 * 1024 && snapshotBytes in 1..1024 * 1024)
    }
}

@Serializable
data class MediaBookmark(
    val kind: MediaKind,
    val id: String,
    val title: String,
    val pageUrl: String,
    val sourceName: String,
    val sourceUrl: String
)

@Serializable
data class MediaSnapshot(
    val kind: MediaKind,
    val draft: String,
    val submitted: String?,
    val language: String,
    val items: List<MediaItem>,
    val nextOffset: Int?,
    val scrollPosition: Int = 0,
    val scrollOffset: Int = 0,
    val requiresRefresh: Boolean = false
)

@Serializable
data class MediaHistory(val kind: MediaKind, val tabs: List<MediaSnapshot>)

fun snapshot(
    kind: MediaKind, draft: String, submitted: String?, language: String,
    items: List<MediaItem>, nextOffset: Int?, scrollPosition: Int = 0, scrollOffset: Int = 0
) = MediaSnapshot(kind, draft, submitted, language, items, nextOffset, scrollPosition, scrollOffset)

internal object MediaCacheValidation {
    private val hosts = setOf("media.giphy.com", "media0.giphy.com", "media1.giphy.com",
        "media2.giphy.com", "media3.giphy.com", "media4.giphy.com", "i.giphy.com")
    private val sensitiveParameters = setOf("api_key", "apikey", "api-key", "key", "token",
        "access_token", "authorization", "password", "secret")
    private val idPattern = Regex("[a-zA-Z0-9_-]{1,128}")

    fun id(value: String) {
        if (!idPattern.matches(value)) invalid()
    }

    fun query(value: String) {
        if (value.codePointCount(0, value.length) > MediaLimits.QUERY_CHARACTERS ||
            !Charsets.UTF_8.newEncoder().canEncode(value)) invalid()
    }

    fun language(value: String) {
        if (value.length > 64 || !value.all { it.isLetterOrDigit() || it in "_-" }) invalid()
    }

    fun url(value: String, asset: Boolean = false) {
        if (!asset && value.isEmpty()) return
        if (value.length > 4096) invalid()
        val uri = try { URI(value) } catch (_: Exception) { invalid() }
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("https", "http") ||
            uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.rawFragment != null ||
            (asset && (uri.scheme != "https" || uri.host.lowercase(Locale.ROOT) !in hosts ||
                uri.port !in setOf(-1, 443)))) invalid()
        uri.rawQuery?.split('&', ';')?.forEach {
            val name = try { URLDecoder.decode(it.substringBefore('='), "UTF-8") }
                catch (_: Exception) { invalid() }
            if (name.lowercase(Locale.ROOT) in sensitiveParameters) invalid()
        }
    }

    fun rendition(value: MediaRendition) {
        url(value.url, asset = true)
        if (value.mimeType !in setOf("image/gif", "image/webp") || value.width <= 0 ||
            value.height <= 0 || (value.byteSize != null && value.byteSize <= 0)) invalid()
    }

    fun item(value: MediaItem) {
        bookmark(MediaBookmark(MediaKind.GIF, value.id, value.title, value.pageUrl, value.sourceName, value.sourceUrl))
        if (value.renditions.size > 100) invalid()
        value.renditions.forEach(::rendition)
    }

    fun bookmark(value: MediaBookmark) {
        id(value.id)
        if (value.title.length > 1024 || value.sourceName.length > 1024) invalid()
        url(value.pageUrl)
        url(value.sourceUrl)
    }

    fun snapshot(value: MediaSnapshot) {
        query(value.draft)
        value.submitted?.let(::query)
        language(value.language)
        if (value.items.size > MediaLimits.RETAINED_ITEMS || value.scrollPosition < 0 ||
            value.scrollOffset < 0 || value.nextOffset?.let { it !in 0..MediaLimits.MAX_OFFSET } == true) invalid()
        value.items.forEach(::item)
    }

    fun nesting(text: String) {
        var depth = 0
        var quoted = false
        var escaped = false
        for (character in text) {
            if (quoted) when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> quoted = false
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> if (++depth > 32) invalid()
                '}', ']' -> if (--depth < 0) invalid()
            }
        }
        if (depth != 0 || quoted) invalid()
    }

    fun invalid(): Nothing = throw MediaException(MediaError.INVALID_RESPONSE)
}
