// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

internal object GiphyMediaValidation {
    fun validate(bytes: ByteArray, rendition: MediaRendition) {
        val dimensions = when (rendition.mimeType) {
            "image/gif" -> {
                if (bytes.size < 13 || !(bytes.matches(0, "GIF87a") || bytes.matches(0, "GIF89a"))) invalid()
                bytes.le(6, 2) to bytes.le(8, 2)
            }
            "image/webp" -> {
                if (bytes.size < 25 || !bytes.matches(0, "RIFF") || !bytes.matches(8, "WEBP") ||
                    bytes.le(4, 4) + 8 != bytes.size.toLong()) invalid()
                when {
                    bytes.matches(12, "VP8X") -> {
                        if (bytes.size < 30 || bytes.le(16, 4) != 10L) invalid()
                        bytes.le(24, 3) + 1 to bytes.le(27, 3) + 1
                    }
                    bytes.matches(12, "VP8L") -> {
                        if (bytes[20].toInt() and 255 != 0x2f) invalid()
                        val bits = bytes.le(21, 4)
                        ((bits and 0x3fff) + 1) to (((bits shr 14) and 0x3fff) + 1)
                    }
                    bytes.matches(12, "VP8 ") -> {
                        if (bytes.size < 30 || bytes[23].toInt() and 255 != 0x9d ||
                            bytes[24].toInt() and 255 != 1 || bytes[25].toInt() and 255 != 0x2a) invalid()
                        (bytes.le(26, 2) and 0x3fff) to (bytes.le(28, 2) and 0x3fff)
                    }
                    else -> invalid()
                }
            }
            else -> throw MediaException(MediaError.UNSUPPORTED)
        }
        if (dimensions.first != rendition.width.toLong() || dimensions.second != rendition.height.toLong()) invalid()
    }

    private fun ByteArray.matches(offset: Int, value: String) =
        size >= offset + value.length && value.indices.all { this[offset + it].toInt() == value[it].code }
    private fun ByteArray.le(offset: Int, length: Int): Long =
        (0 until length).fold(0L) { result, i -> result or ((this[offset + i].toLong() and 255) shl (i * 8)) }
    private fun invalid(): Nothing = throw MediaException(MediaError.INVALID_RESPONSE)
}
