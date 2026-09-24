// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.net.Uri
import kotlinx.serialization.Serializable
import java.io.IOException

@Serializable
enum class MediaKind(val endpoint: String) {
    GIF("gifs"),
    STICKER("stickers")
}

@Serializable
data class MediaRendition(
    val url: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val byteSize: Long? = null,
    val preview: Boolean = false
)

@Serializable
data class MediaItem(
    val id: String,
    val title: String,
    val pageUrl: String,
    val sourceName: String,
    val sourceUrl: String,
    val renditions: List<MediaRendition>
)

/** Only sanitized response cache policy, never the credential-bearing request URL. */
@Serializable
data class MediaCacheInfo(val storable: Boolean = true, val expiresAt: Long? = null)

@Serializable
data class MediaPage(
    val items: List<MediaItem>,
    val nextOffset: Int?,
    val cacheInfo: MediaCacheInfo = MediaCacheInfo()
)

data class MediaDownload(val bytes: ByteArray, val cacheInfo: MediaCacheInfo = MediaCacheInfo())
data class MediaLookup(val item: MediaItem?, val cacheInfo: MediaCacheInfo = MediaCacheInfo())

data class StagedMedia(val uri: Uri, val mimeType: String, val description: String)

enum class MediaError {
    UNAVAILABLE, MISSING_KEY, LOCKED, INVALID_KEY, QUOTA, NETWORK,
    INVALID_RESPONSE, QUERY_TOO_LONG, TOO_LARGE, UNSUPPORTED, STORAGE
}

// Do not put credential-bearing request URLs or provider response bodies in errors.
class MediaException(val reason: MediaError, val httpStatus: Int? = null) : IOException(reason.name)

interface MediaSource {
    val available: Boolean
    val credentialVersion: Long
    /** An opaque credential generation, not a credential or a credential digest. */
    val cacheIdentity: String get() = "version:$credentialVersion"
    fun hasApiKey(): Boolean
    suspend fun search(kind: MediaKind, query: String, language: String, offset: Int = 0): MediaPage
    suspend fun refresh(kind: MediaKind, query: String, language: String, offset: Int = 0): MediaPage =
        search(kind, query, language, offset)
    suspend fun download(rendition: MediaRendition, maxBytes: Int): ByteArray
    suspend fun downloadResponse(
        rendition: MediaRendition, maxBytes: Int, bypassCache: Boolean = false
    ): MediaDownload = MediaDownload(download(rendition, maxBytes))
    suspend fun lookup(kind: MediaKind, id: String): MediaItem? = null
    suspend fun lookupResponse(kind: MediaKind, id: String): MediaLookup = MediaLookup(lookup(kind, id))
}

object MediaLimits {
    const val PAGE_SIZE = 20
    const val RETAINED_ITEMS = 60
    const val QUERY_CHARACTERS = 50
    const val MAX_OFFSET = 4999
    const val RESPONSE_BYTES = 1024 * 1024
    const val PREVIEW_BYTES = 2 * 1024 * 1024
    const val MEDIA_BYTES = 8 * 1024 * 1024
    const val PREVIEW_DIMENSION = 512
}
