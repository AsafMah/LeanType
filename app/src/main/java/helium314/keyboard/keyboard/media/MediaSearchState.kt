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
            submitted = ""
            items = emptyList()
            nextOffset = null
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
        if (!more) {
            tab.submitted = query
            tab.language = language
            tab.items = emptyList()
            tab.nextOffset = null
        }
        return MediaRequest(kind, query, tab.language, offset, generation)
    }

    fun accept(request: MediaRequest, page: MediaPage): Boolean {
        if (request.generation != generation || request.kind != kind) return false
        tab.items = (tab.items + page.items).takeLast(MediaLimits.RETAINED_ITEMS)
        tab.nextOffset = page.nextOffset?.takeIf { it > request.offset && it <= MediaLimits.MAX_OFFSET }
        return true
    }

    fun reset() {
        invalidate()
        tabs.values.forEach {
            it.draft = ""
            it.submitted = ""
            it.items = emptyList()
            it.nextOffset = null
        }
    }
}
