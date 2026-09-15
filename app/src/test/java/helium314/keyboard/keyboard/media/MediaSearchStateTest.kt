// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class MediaSearchStateTest {
    @Test fun draftingDoesNotSubmit() {
        val state = MediaSearchState()
        state.tab.draft = "cat"
        assertEquals("", state.tab.submitted)
        assertEquals(0L, state.generation)
    }

    @Test fun blankQueryDoesNotProduceRequest() {
        val state = MediaSearchState()
        state.tab.draft = "  "
        assertNull(state.request("en", false))
    }

    @Test fun paginationUsesSubmittedQueryNotEdits() {
        val state = MediaSearchState()
        state.tab.draft = "original query"
        val first = assertNotNull(state.request("he", false))
        assertTrue(state.accept(first, MediaPage(listOf(item("a")), 20)))
        state.tab.draft = "unsubmitted edit"
        val next = assertNotNull(state.request("en", true))
        assertEquals("original query", next.query)
        assertEquals("he", next.language)
        assertEquals(20, next.offset)
    }

    @Test fun tabSwitchRejectsLateResponseAndKeepsIndependentDrafts() {
        val state = MediaSearchState()
        state.tab.draft = "gif"
        val request = assertNotNull(state.request("en", false))
        state.select(MediaKind.STICKER)
        state.tab.draft = "sticker"
        assertFalse(state.accept(request, MediaPage(listOf(item("old")), null)))
        assertTrue(state.tab.items.isEmpty())
        state.select(MediaKind.GIF)
        assertEquals("gif", state.tab.draft)
    }

    @Test fun laterSubmissionSupersedesOldResponse() {
        val state = MediaSearchState()
        state.tab.draft = "old"
        val old = assertNotNull(state.request("en", false))
        state.tab.draft = "new"
        val new = assertNotNull(state.request("en", false))
        assertFalse(state.accept(old, MediaPage(listOf(item("old")), null)))
        assertTrue(state.accept(new, MediaPage(listOf(item("new")), null)))
        assertEquals(listOf("new"), state.tab.items.map { it.id })
    }

    @Test fun retainedPagesAreBoundedAndKeepProviderOrder() {
        val state = MediaSearchState()
        state.tab.draft = "query"
        repeat(5) { page ->
            val request = assertNotNull(state.request("en", page > 0))
            state.accept(request, MediaPage((page * 20 until (page + 1) * 20).map { item("$it") }, (page + 1) * 20))
        }
        assertEquals(60, state.tab.items.size)
        assertEquals((40..99).map { "$it" }, state.tab.items.map { it.id })
    }

    @Test fun clearingQueryInvalidatesResultsWithoutSubmitting() {
        val state = MediaSearchState()
        state.tab.draft = "query"
        val pending = assertNotNull(state.request("en", false))
        state.clearQuery()
        assertEquals("", state.tab.draft)
        assertFalse(state.accept(pending, MediaPage(listOf(item("late")), 20)))
        assertNull(state.request("en", true))
    }

    @Test fun unicodeQueryLimitCountsCodePoints() {
        val state = MediaSearchState()
        state.tab.draft = String(Character.toChars(0x1F600)).repeat(50)
        assertNotNull(state.request("en", false))
        state.tab.draft += "x"
        assertEquals(MediaError.QUERY_TOO_LONG, assertFailsWith<MediaException> {
            state.request("en", false)
        }.reason)
    }

    @Test fun invalidPaginationCannotLoopOrExceedProviderLimit() {
        val state = MediaSearchState()
        state.tab.draft = "query"
        val request = assertNotNull(state.request("en", false))
        state.accept(request, MediaPage(emptyList(), 0))
        assertNull(state.request("en", true))
        val next = assertNotNull(state.request("en", false))
        state.accept(next, MediaPage(emptyList(), 5000))
        assertNull(state.request("en", true))
    }

    private fun item(id: String) = MediaItem(id, id, "", "", "", emptyList())
}
