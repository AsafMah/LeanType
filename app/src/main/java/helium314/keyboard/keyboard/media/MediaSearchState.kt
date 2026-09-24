// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

internal data class MediaRequest(
    val kind: MediaKind,
    val query: String,
    val language: String,
    val offset: Int,
    val generation: Long
)

internal class MediaSearchState {
    class Tab {
        var draft = ""
        var submitted = ""
        var language = ""
        var items: List<MediaItem> = emptyList()
        var nextOffset: Int? = null
        var scrollPosition = 0
        var scrollOffset = 0
        var requiresRefresh = false
    }

    private val tabs = MediaKind.entries.associateWith { Tab() }
    var kind = MediaKind.GIF
        private set
    var generation = 0L
        private set
    val tab: Tab get() = tabs.getValue(kind)

    fun select(kind: MediaKind) {
        invalidate()
        this.kind = kind
    }

    fun invalidate() { generation++ }

    fun clearQuery() {
        invalidate()
        tabs.getValue(kind).apply {
            draft = ""
        }
    }

    fun request(language: String, more: Boolean): MediaRequest? {
        val query = if (more) tab.submitted else tab.draft
        if (query.isBlank()) return null
        if (query.codePointCount(0, query.length) > MediaLimits.QUERY_CHARACTERS)
            throw MediaException(MediaError.QUERY_TOO_LONG)
        val offset = if (more) tab.nextOffset ?: return null else 0
        if (offset !in 0..MediaLimits.MAX_OFFSET) return null
        invalidate()
        return MediaRequest(kind, query, if (more) tab.language else language, offset, generation)
    }

    fun accept(request: MediaRequest, page: MediaPage): Boolean {
        if (request.generation != generation || request.kind != kind) return false
        val previous = if (request.offset == 0) emptyList() else tab.items
        tab.items = (previous + page.items).takeLast(MediaLimits.RETAINED_ITEMS)
        tab.submitted = request.query
        tab.language = request.language
        tab.requiresRefresh = false
        if (request.offset == 0) {
            tab.scrollPosition = 0
            tab.scrollOffset = 0
        }
        tab.nextOffset = page.nextOffset?.takeIf { it > request.offset && it <= MediaLimits.MAX_OFFSET }
        return true
    }

    fun snapshot(): MediaSnapshot = MediaSnapshot(kind, tab.draft, tab.submitted, tab.language,
        tab.items, tab.nextOffset, tab.scrollPosition, tab.scrollOffset, tab.requiresRefresh)

    fun restore(history: MediaHistory) {
        history.tabs.forEach { saved ->
            tabs.getValue(saved.kind).apply {
                draft = saved.draft
                submitted = saved.submitted.orEmpty()
                language = saved.language
                items = saved.items
                nextOffset = saved.nextOffset
                scrollPosition = saved.scrollPosition
                scrollOffset = saved.scrollOffset
                requiresRefresh = saved.requiresRefresh
            }
        }
    }

    fun reset() {
        invalidate()
        tabs.values.forEach {
            it.draft = ""
            it.submitted = ""
            it.items = emptyList()
            it.nextOffset = null
            it.scrollPosition = 0
            it.scrollOffset = 0
            it.requiresRefresh = false
        }
    }
}
