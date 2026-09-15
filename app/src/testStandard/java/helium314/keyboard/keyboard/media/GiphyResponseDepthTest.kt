// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GiphyResponseDepthTest {
    @Test fun deeplyNestedArraysFailAsInvalidResponseNotStackOverflow() {
        val json = "[".repeat(20_000) + "0" + "]".repeat(20_000)
        val failure = assertFailsWith<MediaException> { GiphyResponseParser.parse(json.toByteArray(), 0) }
        assertEquals(MediaError.INVALID_RESPONSE, failure.reason)
    }

    @Test fun bracketsAndEscapedQuotesInStringsDoNotCountAsNesting() {
        val value = JsonPrimitive("\\\" " + "[]{}".repeat(20_000)).toString()
        assertEquals(MediaPage(emptyList(), null), GiphyResponseParser.parse(page(value), 0))
    }

    @Test fun nestingBoundaryIncludesTheRootObject() {
        assertEquals(MediaPage(emptyList(), null),
            GiphyResponseParser.parse(page("[".repeat(63) + "0" + "]".repeat(63)), 0))
        val failure = assertFailsWith<MediaException> {
            GiphyResponseParser.parse(page("[".repeat(64) + "0" + "]".repeat(64)), 0)
        }
        assertEquals(MediaError.INVALID_RESPONSE, failure.reason)
    }

    private fun page(extra: String) =
        """{"data":[],"meta":{"status":200},"pagination":{"count":0,"offset":0,"total_count":0},"extra":$extra}"""
            .toByteArray()
}
