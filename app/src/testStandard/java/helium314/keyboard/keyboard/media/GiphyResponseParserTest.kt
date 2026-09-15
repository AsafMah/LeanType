// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GiphyResponseParserTest {
    @Test
    fun preservesOrderUnavailableEntriesAttributionAndExactMediaUrls() {
        val page = parse("""
            {"id":"unavailable","url":"https://giphy.com/gifs/unavailable","images":{}},
            {"id":"available","title":"Owned test result","url":"https://giphy.com/gifs/available",
             "source_tld":"example.org","source":"https://example.org/source",
             "user":{"display_name":"Fixture Artist","username":"fixture","profile_url":"https://giphy.com/fixture"},
             "images":{
              "fixed_width_small":{"width":"100","height":"80","url":"https://media1.giphy.com/unit/small.gif?cid=complete&ct=g","size":"1000",
                 "webp":"https://media1.giphy.com/unit/small.webp?cid=complete","webp_size":"700"},
              "original":{"width":"640","height":"480","url":"https://media.giphy.com/unit/full.gif?rid=giphy.gif","size":"9000"},
              "original_still":{"width":"640","height":"480","url":"https://media.giphy.com/unit/still.gif","size":"10"},
              "downsized_small":{"width":"200","height":"150","mp4":"https://media.giphy.com/unit/video.mp4","mp4_size":"500"}
             }},
            {"id":"unsupported","url":"https://giphy.com/gifs/unsupported",
             "images":{"original":{"width":"100","height":"100","url":"https://example.invalid/image.gif","size":"100"}}}
        """, count = 3, total = 4)
        assertEquals(listOf("unavailable", "available", "unsupported"), page.items.map { it.id })
        assertTrue(page.items[0].renditions.isEmpty())
        assertTrue(page.items[2].renditions.isEmpty())
        val item = page.items[1]
        assertEquals("Fixture Artist · fixture · example.org", item.sourceName)
        assertEquals("https://example.org/source", item.sourceUrl)
        assertEquals("https://giphy.com/gifs/available", item.pageUrl)
        assertEquals(3, item.renditions.size)
        assertEquals(MediaRendition("https://media1.giphy.com/unit/small.gif?cid=complete&ct=g",
            "image/gif", 100, 80, 1000, true), item.renditions[0])
        assertEquals("image/webp", item.renditions[1].mimeType)
        assertEquals(700L, item.renditions[1].byteSize)
        assertFalse(item.renditions[2].preview)
        assertEquals(3, page.nextOffset)
    }

    @Test
    fun pagingEndsOnlyAtProviderEndOrMaximumOffset() {
        assertEquals(11, parse(item, count = 1, total = 50, offset = 10).nextOffset)
        assertEquals(4999, parse(item, count = 1, total = 8000, offset = 4998).nextOffset)
        assertNull(parse(item, count = 1, total = 8000, offset = 4999).nextOffset)
        assertNull(parse(item, count = 1, total = 11, offset = 10).nextOffset)
        assertNull(parse("", count = 0, total = 0).nextOffset)
    }

    @Test
    fun omitsTrimmedAndGridOnlyRenditionsWithoutDroppingOrReorderingItems() {
        val excluded = listOf("preview", "preview_gif", "preview_webp",
            "fixed_width_downsampled", "fixed_height_downsampled")
        val retained = listOf("original", "fixed_width", "fixed_height", "fixed_width_small",
            "fixed_height_small", "downsized", "downsized_medium", "downsized_large")
        fun images(names: List<String>) = names.joinToString(",") { name ->
            """"$name":{"width":"100","height":"80","size":"1000","webp_size":"700",
                "url":"https://media.giphy.com/unit/$name.gif",
                "webp":"https://media.giphy.com/unit/$name.webp"}"""
        }
        val page = parse("""
            {"id":"grid-only","url":"https://giphy.com/gifs/grid-only","images":{${images(excluded)}}},
            {"id":"sendable","url":"https://giphy.com/gifs/sendable","images":{${images(excluded + retained)}}}
        """, count = 2, total = 3)
        assertEquals(listOf("grid-only", "sendable"), page.items.map { it.id })
        assertTrue(page.items.first().renditions.isEmpty())
        val urls = page.items.last().renditions.map { it.url }
        assertEquals(retained.flatMap { name ->
            listOf("https://media.giphy.com/unit/$name.gif", "https://media.giphy.com/unit/$name.webp")
        }, urls)
        assertEquals(urls.distinct(), urls)
        assertEquals(2, page.nextOffset)
    }

    @Test
    fun malformedResponseCannotBecomeSuccessfulEmptyPage() {
        for (json in listOf("{}", "[]", "{not json", """{"meta":{"status":200},"data":[]}""",
            """{"meta":{"status":200},"data":{},"pagination":{"count":0,"offset":0,"total_count":0}}""",
            """{"meta":{"status":200},"data":[null],"pagination":{"count":1,"offset":0,"total_count":1}}""",
            """{"meta":{"status":200},"data":[],"pagination":{"count":0,"offset":0,"total_count":20}}""",
            """{"meta":{"status":200},"data":[],"pagination":{"count":0,"offset":1,"total_count":0}}""")) {
            assertEquals(MediaError.INVALID_RESPONSE,
                assertFailsWith<MediaException> { GiphyResponseParser.parse(json.toByteArray(), 0) }.reason)
        }
        assertEquals(MediaError.INVALID_RESPONSE,
            assertFailsWith<MediaException> { parse(item, count = 0, total = 1) }.reason)
        assertEquals(MediaError.INVALID_RESPONSE,
            assertFailsWith<MediaException> { parse("""{"id":"id","url":"https://giphy.com/id"}""", 1, 1) }.reason)
    }

    @Test
    fun embeddedProviderErrorsAreNotSuccessShapedFallbacks() {
        for ((status, error) in listOf(401 to MediaError.INVALID_KEY, 403 to MediaError.INVALID_KEY,
            429 to MediaError.QUOTA, 503 to MediaError.NETWORK)) {
            assertEquals(error, assertFailsWith<MediaException> {
                GiphyResponseParser.parse("""{"meta":{"status":$status,"msg":"private response"}}""".toByteArray(), 0)
            }.reason)
        }
    }

    @Test
    fun refusesInvalidUtf8AndUnboundedPage() {
        assertEquals(MediaError.INVALID_RESPONSE, assertFailsWith<MediaException> {
            GiphyResponseParser.parse(byteArrayOf(-61, 40), 0)
        }.reason)
        assertEquals(MediaError.INVALID_RESPONSE, assertFailsWith<MediaException> {
            parse(List(21) { item }.joinToString(","), 21, 21)
        }.reason)
    }

    companion object {
        private const val item = """{"id":"id","url":"https://giphy.com/gifs/id","images":{}}"""
        private fun parse(data: String, count: Int, total: Int, offset: Int = 0) =
            GiphyResponseParser.parse("""
                {"meta":{"status":200},"data":[$data],
                 "pagination":{"count":$count,"offset":$offset,"total_count":$total}}
            """.trimIndent().toByteArray(), offset)
    }
}
