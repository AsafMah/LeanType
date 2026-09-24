// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.content.Context

class GiphyService(@Suppress("UNUSED_PARAMETER") context: Context) : MediaSource {
    override val available = false
    override val credentialVersion = 0L
    override val cacheIdentity = "offline"
    override fun hasApiKey() = false
    fun getApiKey(): String? = throw MediaException(MediaError.UNAVAILABLE)
    fun setApiKey(@Suppress("UNUSED_PARAMETER") apiKey: String?): Unit =
        throw MediaException(MediaError.UNAVAILABLE)
    override suspend fun search(kind: MediaKind, query: String, language: String, offset: Int): MediaPage =
        throw MediaException(MediaError.UNAVAILABLE)
    override suspend fun download(rendition: MediaRendition, maxBytes: Int): ByteArray =
        throw MediaException(MediaError.UNAVAILABLE)
    override suspend fun refresh(kind: MediaKind, query: String, language: String, offset: Int): MediaPage =
        throw MediaException(MediaError.UNAVAILABLE)
    override suspend fun lookup(kind: MediaKind, id: String): MediaItem? =
        throw MediaException(MediaError.UNAVAILABLE)
    override suspend fun lookupResponse(kind: MediaKind, id: String): MediaLookup =
        throw MediaException(MediaError.UNAVAILABLE)
    override suspend fun downloadResponse(
        rendition: MediaRendition, maxBytes: Int, bypassCache: Boolean
    ): MediaDownload = throw MediaException(MediaError.UNAVAILABLE)
}
