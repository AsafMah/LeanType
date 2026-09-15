// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

internal object GiphyResponseParser {
    fun parse(bytes: ByteArray, requestedOffset: Int): MediaPage {
        if (bytes.size > MediaLimits.RESPONSE_BYTES) throw MediaException(MediaError.TOO_LARGE)
        try {
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            checkNesting(text)
            val root = Json.parseToJsonElement(text) as? JsonObject ?: invalid()
            val status = root.obj("meta").number("status")
            GiphyClient.statusError(status)?.let { throw MediaException(it) }
            if (status != 200) invalid()
            val data = root["data"] as? JsonArray ?: invalid()
            val paging = root.obj("pagination")
            val count = paging.number("count")
            val offset = paging.number("offset")
            val total = paging.number("total_count")
            if (count != data.size || count !in 0..MediaLimits.PAGE_SIZE || offset != requestedOffset ||
                total < 0 || (count > 0 && offset.toLong() + count > total) ||
                (count == 0 && offset < total)) invalid()
            val items = data.map { item(it as? JsonObject ?: invalid()) }
            val next = offset.toLong() + count
            return MediaPage(items, next.toInt().takeIf { count > 0 && next < total && next <= MediaLimits.MAX_OFFSET })
        } catch (error: MediaException) {
            throw error
        } catch (_: Exception) {
            throw MediaException(MediaError.INVALID_RESPONSE)
        }
    }

    // The byte cap alone does not protect the JSON tree reader's recursive array path.
    private fun checkNesting(text: String) {
        var depth = 0
        var quoted = false
        var escaped = false
        for (character in text) {
            if (quoted) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> quoted = false
                }
            } else {
                when (character) {
                    '"' -> quoted = true
                    '{', '[' -> if (++depth > 64) invalid()
                    '}', ']' -> if (--depth < 0) invalid()
                }
            }
        }
        if (depth != 0 || quoted) invalid()
    }

    private fun item(value: JsonObject): MediaItem {
        val id = value.string("id").takeIf { it.isNotBlank() } ?: invalid()
        val user = value["user"] as? JsonObject
        val names = listOfNotNull(user?.optionalString("display_name"), user?.optionalString("username"),
            value.optionalString("source_tld")).filter { it.isNotBlank() }.distinct()
        val sourceUrl = listOfNotNull(value.optionalString("source_post_url"), value.optionalString("source"),
            user?.optionalString("profile_url")).firstOrNull { it.isNotBlank() }.orEmpty()
        val images = value["images"] as? JsonObject ?: invalid()
        val renditions = buildList<MediaRendition> {
            for ((name, raw) in images) {
                // Every exposed rendition can be selected for sending. Omit trimmed previews
                // and six-frame grid-only variants, as well as stills and video conversions.
                if (name.endsWith("_still") || name.endsWith("_downsampled") ||
                    name.startsWith("preview_") || name in setOf("preview", "looping", "hd")) continue
                val image = raw as? JsonObject ?: continue
                val width = image.optionalNumber("width") ?: continue
                val height = image.optionalNumber("height") ?: continue
                if (width <= 0 || height <= 0) continue
                val preview = width <= MediaLimits.PREVIEW_DIMENSION &&
                    height <= MediaLimits.PREVIEW_DIMENSION
                addImage(image, "url", "size", width, height, preview)
                addImage(image, "webp", "webp_size", width, height, preview, "image/webp")
            }
        }.distinctBy { Triple(it.url, it.mimeType, it.preview) }
        return MediaItem(id, value.optionalString("title").orEmpty(), value.string("url"),
            names.joinToString(" · "), sourceUrl, renditions)
    }

    private fun MutableList<MediaRendition>.addImage(
        image: JsonObject, field: String, sizeField: String, width: Int, height: Int,
        preview: Boolean, knownMime: String? = null
    ) {
        val value = image.optionalString(field)?.takeIf { it.isNotBlank() } ?: return
        val url = try { GiphyClient.mediaUrl(value) } catch (_: MediaException) { return }
        val path = url.path.lowercase(Locale.ROOT)
        val mime = knownMime ?: when {
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".webp") -> "image/webp"
            else -> return
        }
        val size = image.optionalString(sizeField)?.takeIf { it.isNotBlank() }?.let {
            it.toLongOrNull()?.takeIf { size -> size > 0 } ?: return
        }
        add(MediaRendition(value, mime, width, height, size,
            preview = preview && (size == null || size <= MediaLimits.PREVIEW_BYTES)))
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject ?: invalid()
    private fun JsonObject.string(key: String) = optionalString(key) ?: invalid()
    private fun JsonObject.optionalString(key: String): String? {
        val value = this[key] ?: return null
        if (value == kotlinx.serialization.json.JsonNull) return null
        return (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid()
    }
    private fun JsonObject.number(key: String) = optionalNumber(key) ?: invalid()
    private fun JsonObject.optionalNumber(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.toIntOrNull()
    private fun invalid(): Nothing = throw MediaException(MediaError.INVALID_RESPONSE)
}
