// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MediaFormatTest {
    @Test fun identifiesBothFramesAndTransparentGifWithoutFlattening() {
        val bytes = OwnedMediaFixtures.gif()
        val image = MediaFormat.inspect(bytes, OwnedMediaFixtures.rendition(bytes), preview = true)
        assertEquals("image/gif", image.mimeType)
        assertEquals("gif", image.extension)
        assertEquals(2, image.width)
        assertEquals(1, image.height)
        assertEquals(2, image.frames)
        assertEquals(40, image.frameMemoryBytes)
        assertTrue(bytes.toList().windowed(4).any { it == listOf(0x21, 0xf9, 4, 9).map(Int::toByte) })
    }

    @Test fun validatesAnimatedWebpContainerAndRiffLength() {
        val bytes = OwnedMediaFixtures.webpContainer()
        val rendition = OwnedMediaFixtures.rendition(bytes, "image/webp")
        assertEquals(2, MediaFormat.inspect(bytes, rendition, true).frames)
        val corrupt = bytes.clone().apply { this[4] = 0 }
        assertError(MediaError.INVALID_RESPONSE) { MediaFormat.inspect(corrupt, rendition, true) }
        assertError(MediaError.INVALID_RESPONSE) { MediaFormat.inspect(bytes.copyOf(bytes.size - 1), rendition, true) }
    }

    @Test fun acceptsStaticStickerWithoutPretendingItIsAnimated() {
        val bytes = OwnedMediaFixtures.gif(frames = 1)
        assertEquals(1, MediaFormat.inspect(bytes, OwnedMediaFixtures.rendition(bytes), true).frames)
        assertEquals(1, MediaFormat.inspect(bytes, OwnedMediaFixtures.rendition(bytes), false).frames)
    }

    @Test fun rejectsCorruptionWrongMimeAndMisreportedDimensions() {
        val bytes = OwnedMediaFixtures.gif()
        val rendition = OwnedMediaFixtures.rendition(bytes)
        assertError(MediaError.INVALID_RESPONSE) { MediaFormat.inspect(bytes.copyOf(bytes.size - 1), rendition, true) }
        assertError(MediaError.UNSUPPORTED) { MediaFormat.inspect(byteArrayOf(1, 2), rendition, false) }
        assertError(MediaError.INVALID_RESPONSE) { MediaFormat.inspect(bytes, rendition.copy(mimeType = "image/webp"), false) }
        assertError(MediaError.INVALID_RESPONSE) { MediaFormat.inspect(bytes, rendition.copy(width = 3), false) }
        assertError(MediaError.INVALID_RESPONSE) { MediaFormat.inspect(bytes, rendition.copy(byteSize = -1), false) }
    }

    @Test fun rejectsAdvertisedAndActualByteOverflow() {
        val bytes = OwnedMediaFixtures.gif()
        val rendition = OwnedMediaFixtures.rendition(bytes)
        assertError(MediaError.TOO_LARGE) {
            MediaFormat.inspect(bytes, rendition.copy(byteSize = MediaLimits.PREVIEW_BYTES + 1L), true)
        }
        assertError(MediaError.TOO_LARGE) {
            MediaFormat.inspect(ByteArray(MediaLimits.PREVIEW_BYTES + 1), rendition.copy(byteSize = null), true)
        }
        assertError(MediaError.TOO_LARGE) {
            MediaFormat.inspect(ByteArray(MediaLimits.MEDIA_BYTES + 1), rendition.copy(byteSize = null), false)
        }
    }

    @Test fun acceptsExactAndBelowByteLimitsUsingOwnedGifCommentBlocks() {
        for (limit in listOf(MediaLimits.PREVIEW_BYTES, MediaLimits.MEDIA_BYTES)) {
            for (size in listOf(limit - 1, limit)) {
                val bytes = gifWithSize(size)
                assertEquals(size, bytes.size)
                val info = MediaFormat.inspect(bytes, OwnedMediaFixtures.rendition(bytes), limit == MediaLimits.PREVIEW_BYTES)
                assertEquals(2, info.frames)
            }
        }
    }

    @Test fun checksActualDimensionsBeforeDecodeAndBudgetsAllFrames() {
        val bytes = OwnedMediaFixtures.gif(width = 513)
        assertError(MediaError.TOO_LARGE) {
            MediaFormat.inspect(bytes, OwnedMediaFixtures.rendition(bytes, width = 513), true)
        }
        val many = OwnedMediaFixtures.gif(frames = 40, width = 64, height = 64)
        val info = MediaFormat.inspect(many, OwnedMediaFixtures.rendition(many, width = 64, height = 64), true)
        assertEquals(64L * 64 * 4 * 43, info.frameMemoryBytes)
    }

    private fun gifWithSize(size: Int): ByteArray {
        val base = OwnedMediaFixtures.gif()
        val output = java.io.ByteArrayOutputStream()
        output.write(base, 0, base.size - 1)
        var remaining = size - base.size
        while (remaining > 0) {
            if (remaining == 3) {
                output.write(byteArrayOf(0x21, 0xfe.toByte(), 0))
                remaining = 0
                continue
            }
            // A comment extension costs marker + label + length + terminator.
            val payload = minOf(255, remaining - 4)
            require(payload > 0)
            var length = payload
            val after = remaining - length - 4
            if (after in 1..4) length -= 5 - after
            output.write(byteArrayOf(0x21, 0xfe.toByte(), length.toByte()))
            output.write(ByteArray(length))
            output.write(0)
            remaining -= length + 4
        }
        output.write(0x3b)
        return output.toByteArray()
    }

    private fun assertError(reason: MediaError, action: () -> Unit) {
        assertEquals(reason, assertFailsWith<MediaException>(block = action).reason)
    }
}
