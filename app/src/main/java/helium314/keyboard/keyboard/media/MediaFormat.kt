// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

/** Container validation before a decoder or receiver sees untrusted image bytes. */
internal object MediaFormat {
    data class Image(val mimeType: String, val extension: String, val width: Int, val height: Int, val frames: Int) {
        // Budget every frame as a full RGBA canvas, plus three working canvases.
        // This is an admission bound, not a measurement of a platform decoder's native heap.
        val frameMemoryBytes: Long get() = width.toLong() * height * 4 * (frames + 3L)
    }

    fun inspect(bytes: ByteArray, rendition: MediaRendition, preview: Boolean): Image {
        val maxBytes = if (preview) MediaLimits.PREVIEW_BYTES else MediaLimits.MEDIA_BYTES
        if (bytes.size > maxBytes || (rendition.byteSize ?: 0) > maxBytes) fail(MediaError.TOO_LARGE)
        if (bytes.isEmpty() || (rendition.byteSize ?: 0) < 0) fail()
        val input = Reader(bytes)
        val image = when {
            input.matches(0, "GIF87a") || input.matches(0, "GIF89a") -> gif(input)
            input.matches(0, "RIFF") && input.matches(8, "WEBP") -> webp(input)
            else -> fail(MediaError.UNSUPPORTED)
        }
        if (image.mimeType != rendition.mimeType ||
            image.width != rendition.width || image.height != rendition.height) fail()
        val dimension = if (preview) MediaLimits.PREVIEW_DIMENSION else 4096
        if (image.width !in 1..dimension || image.height !in 1..dimension) fail(MediaError.TOO_LARGE)
        return image
    }

    private fun gif(input: Reader): Image {
        input.skip(6)
        val width = input.u16()
        val height = input.u16()
        val packed = input.u8()
        input.skip(2)
        if (packed and 128 != 0) input.skip(3 * (1 shl ((packed and 7) + 1)))
        var frames = 0
        while (true) {
            when (input.u8()) {
                0x3b -> {
                    if (frames == 0 || input.remaining != 0) fail()
                    return Image("image/gif", "gif", width, height, frames)
                }
                0x21 -> {
                    val extension = input.u8()
                    if (extension == 0xf9) {
                        if (input.u8() != 4) fail()
                        input.skip(4)
                        if (input.u8() != 0) fail()
                    } else {
                        input.subBlocks()
                    }
                }
                0x2c -> {
                    val left = input.u16()
                    val top = input.u16()
                    val frameWidth = input.u16()
                    val frameHeight = input.u16()
                    if (frameWidth == 0 || frameHeight == 0 ||
                        left + frameWidth > width || top + frameHeight > height) fail()
                    val flags = input.u8()
                    if (flags and 128 != 0) input.skip(3 * (1 shl ((flags and 7) + 1)))
                    if (input.u8() !in 2..8) fail()
                    if (input.subBlocks() == 0) fail()
                    frames++
                }
                else -> fail()
            }
        }
    }

    private fun webp(input: Reader): Image {
        input.skip(4)
        if (input.u32() + 8 != input.size.toLong()) fail()
        input.skip(4)
        var width = 0
        var height = 0
        var animated = false
        var animationHeader = false
        var frames = 0
        while (input.remaining > 0) {
            val tag = input.tag()
            val length = input.chunkLength()
            val end = input.position + length
            when (tag) {
                "VP8X" -> {
                    if (length != 10 || width != 0) fail()
                    animated = input.u8() and 2 != 0
                    input.skip(3)
                    width = input.u24() + 1
                    height = input.u24() + 1
                }
                "ANIM" -> {
                    if (!animated || length != 6 || animationHeader) fail()
                    animationHeader = true
                }
                "ANMF" -> {
                    if (!animated || !animationHeader || length < 16) fail()
                    val left = input.u24() * 2
                    val top = input.u24() * 2
                    val frameWidth = input.u24() + 1
                    val frameHeight = input.u24() + 1
                    input.skip(4)
                    if (left.toLong() + frameWidth > width || top.toLong() + frameHeight > height) fail()
                    var imageChunks = 0
                    while (input.position < end) {
                        val frameTag = input.tag()
                        val frameLength = input.chunkLength()
                        if (frameLength > end - input.position) fail()
                        when (frameTag) {
                            "VP8 ", "VP8L" -> {
                                if (frameLength == 0) fail()
                                imageChunks++
                            }
                            "ALPH" -> if (frameLength == 0) fail()
                            else -> fail()
                        }
                        input.skip(frameLength + (frameLength and 1))
                    }
                    if (input.position != end || imageChunks != 1) fail()
                    frames++
                }
                "VP8 " -> {
                    if (animated || length < 10) fail()
                    if (width == 0) {
                        input.skip(3)
                        if (input.u8() != 0x9d || input.u8() != 1 || input.u8() != 0x2a) fail()
                        width = input.u16() and 0x3fff
                        height = input.u16() and 0x3fff
                    }
                    frames++
                }
                "VP8L" -> {
                    if (animated || length < 5 || input.u8() != 0x2f) fail()
                    if (width == 0) {
                        val bits = input.u32()
                        width = (bits and 0x3fff).toInt() + 1
                        height = ((bits shr 14) and 0x3fff).toInt() + 1
                    }
                    frames++
                }
            }
            input.position = end
            input.skip(length and 1)
        }
        if (width == 0 || height == 0 || frames == 0 || (!animated && frames != 1)) fail()
        return Image("image/webp", "webp", width, height, frames)
    }

    private class Reader(private val bytes: ByteArray) {
        val size get() = bytes.size
        var position = 0
        val remaining get() = size - position
        fun matches(offset: Int, text: String): Boolean =
            offset + text.length <= size && text.indices.all { bytes[offset + it].toInt() == text[it].code }
        fun skip(count: Int) {
            if (count < 0 || count > remaining) fail()
            position += count
        }
        fun u8(): Int {
            if (remaining < 1) fail()
            return bytes[position++].toInt() and 255
        }
        fun u16() = u8() or (u8() shl 8)
        fun u24() = u16() or (u8() shl 16)
        fun u32() = u24().toLong() or (u8().toLong() shl 24)
        fun tag(): String = (0..3).map { u8().toChar() }.joinToString("")
        fun chunkLength(): Int {
            val length = u32()
            if (length > remaining) fail()
            return length.toInt()
        }
        fun subBlocks(): Int {
            var total = 0
            while (true) {
                val length = u8()
                if (length == 0) return total
                skip(length)
                total += length
            }
        }
    }

    private fun fail(reason: MediaError = MediaError.INVALID_RESPONSE): Nothing = throw MediaException(reason)
}
