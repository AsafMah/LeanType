// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import java.io.ByteArrayOutputStream

/** Shared JVM/device fixtures generated from pixel indices, not copied provider media. */
internal object OwnedMediaFixtures {
    /** Lossless encoded 3x1 red/blue frames with a transparent center pixel, generated locally. */
    fun animatedWebp(): ByteArray = byteArrayOf(
        82, 73, 70, 70, -120, 0, 0, 0, 87, 69, 66, 80, 86, 80, 56, 88, 10, 0,
        0, 0, 18, 0, 0, 0, 2, 0, 0, 0, 0, 0, 65, 78, 73, 77, 6, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 65, 78, 77, 70, 42, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 100, 0, 0, 2, 86, 80, 56, 76,
        17, 0, 0, 0, 47, 2, 0, 0, 16, 15, 16, -13, 31, -13, 31, -116, 22, 68,
        -12, 63, 0, 0, 65, 78, 77, 70, 42, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        2, 0, 0, 0, 0, 0, 100, 0, 0, 0, 86, 80, 56, 76, 17, 0, 0, 0,
        47, 2, 0, 0, 16, 15, 16, 49, -1, -13, 31, -116, 22, 68, -12, 63, 0, 0
    )

    fun gif(frames: Int = 2, width: Int = 2, height: Int = 1, paddingBytes: Int = 0): ByteArray {
        val output = ByteArrayOutputStream()
        fun byte(value: Int) = output.write(value)
        fun word(value: Int) { byte(value and 255); byte(value shr 8) }
        fun text(value: String) = output.write(value.toByteArray(Charsets.US_ASCII))
        text("GIF89a")
        word(width); word(height)
        byte(0x81); byte(0); byte(0)
        output.write(byteArrayOf(0, 0, 0, -1, 0, 0, 0, 0, -1, -1, -1, -1))
        byte(0x21); byte(0xff); byte(11); text("NETSCAPE2.0")
        byte(3); byte(1); word(0); byte(0)
        repeat(frames) { frame ->
            byte(0x21); byte(0xf9); byte(4); byte(9); word(10); byte(0); byte(0)
            byte(0x2c); word(0); word(0); word(width); word(height); byte(0)
            byte(2)
            val encoded = ByteArrayOutputStream()
            var bits = 0
            var count = 0
            fun code(value: Int) {
                bits = bits or (value shl count)
                count += 3
                while (count >= 8) {
                    encoded.write(bits and 255)
                    bits = bits shr 8
                    count -= 8
                }
            }
            repeat(width * height) { pixel ->
                code(4) // Clear keeps every following palette index a three-bit LZW code.
                code(if (pixel % 2 == 1) 0 else 1 + frame % 2)
            }
            code(5)
            if (count > 0) encoded.write(bits)
            val data = encoded.toByteArray()
            var position = 0
            while (position < data.size) {
                val length = minOf(255, data.size - position)
                byte(length); output.write(data, position, length)
                position += length
            }
            byte(0)
        }
        if (paddingBytes > 0) {
            byte(0x21); byte(0xfe)
            var left = paddingBytes
            while (left > 0) {
                val length = minOf(255, left)
                byte(length); output.write(ByteArray(length))
                left -= length
            }
            byte(0)
        }
        byte(0x3b)
        return output.toByteArray()
    }

    /** RIFF container fixture for parser tests, not a valid compressed WebP bitstream. */
    fun webpContainer(width: Int = 2, height: Int = 1, frames: Int = 2): ByteArray {
        fun word24(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte())
        fun word32(value: Int) = word24(value) + (value shr 24).toByte()
        fun chunk(tag: String, data: ByteArray) =
            tag.toByteArray(Charsets.US_ASCII) + word32(data.size) + data + ByteArray(data.size and 1)
        val body = ByteArrayOutputStream()
        body.write("WEBP".toByteArray())
        body.write(chunk("VP8X", byteArrayOf(2, 0, 0, 0) + word24(width - 1) + word24(height - 1)))
        body.write(chunk("ANIM", ByteArray(6)))
        repeat(frames) {
            body.write(chunk("ANMF", word24(0) + word24(0) + word24(width - 1) + word24(height - 1) +
                word24(100) + byteArrayOf(0) + chunk("VP8L", byteArrayOf(0x2f, 1, 0, 0, 0))))
        }
        return "RIFF".toByteArray() + word32(body.size()) + body.toByteArray()
    }

    fun rendition(bytes: ByteArray = gif(), mime: String = "image/gif", width: Int = 2, height: Int = 1) =
        MediaRendition("https://fixture.invalid/owned", mime, width, height, bytes.size.toLong(), preview = true)

    fun item(renditions: List<MediaRendition> = listOf(rendition())) =
        MediaItem("owned", "Owned animation", "", "", "", renditions)
}
