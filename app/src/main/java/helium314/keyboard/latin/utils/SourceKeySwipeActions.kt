package helium314.keyboard.latin.utils

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import java.util.EnumMap

object SourceKeySwipeActions {
    enum class SourceKey { ACTION_KEY, PERIOD, COMMA }
    enum class Direction { UP, DOWN, LEFT, RIGHT }

    private data class BindingKey(val sourceKey: SourceKey, val direction: Direction)

    private var cachedSpec: String? = null
    private var cachedBindings: Map<BindingKey, ToolbarKey> = emptyMap()

    @JvmStatic
    fun hasBindingForSource(code: Int, spec: String): Boolean {
        val sourceKey = sourceKeyForCode(code) ?: return false
        return bindings(spec).keys.any { it.sourceKey == sourceKey }
    }

    @JvmStatic
    fun codeForSwipe(sourceCode: Int, direction: Int, spec: String): Int {
        val sourceKey = sourceKeyForCode(sourceCode) ?: return KeyCode.UNSPECIFIED
        val swipeDirection = directionForCode(direction) ?: return KeyCode.UNSPECIFIED
        val toolbarKey = bindings(spec)[BindingKey(sourceKey, swipeDirection)] ?: return KeyCode.UNSPECIFIED
        return getCodeForToolbarKey(toolbarKey)
    }

    @JvmStatic
    fun clearCache() {
        cachedSpec = null
        cachedBindings = emptyMap()
    }

    fun parse(spec: String): Map<Pair<SourceKey, Direction>, ToolbarKey> =
        bindings(spec).mapKeys { it.key.sourceKey to it.key.direction }

    private fun bindings(spec: String): Map<BindingKey, ToolbarKey> {
        if (spec == cachedSpec) return cachedBindings
        val parsed = EnumMap<SourceKey, EnumMap<Direction, ToolbarKey>>(SourceKey::class.java)
        spec.split(';').forEach { entry ->
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) return@forEach
            val sourceAndDirection = trimmed.substringBefore('=', missingDelimiterValue = "")
            val actionName = trimmed.substringAfter('=', missingDelimiterValue = "")
            val sourceName = sourceAndDirection.substringBefore(':', missingDelimiterValue = "")
            val directionName = sourceAndDirection.substringAfter(':', missingDelimiterValue = "")
            val sourceKey = sourceKeyForName(sourceName) ?: return@forEach
            val direction = directionName.enumValueOrNull<Direction>() ?: return@forEach
            val action = actionName.enumValueOrNull<ToolbarKey>() ?: return@forEach
            parsed.getOrPut(sourceKey) { EnumMap(Direction::class.java) }[direction] = action
        }
        cachedSpec = spec
        cachedBindings = parsed.flatMap { sourceEntry ->
            sourceEntry.value.map { directionEntry ->
                BindingKey(sourceEntry.key, directionEntry.key) to directionEntry.value
            }
        }.toMap()
        return cachedBindings
    }

    private fun sourceKeyForName(name: String): SourceKey? = when (name.trim().uppercase()) {
        "ACTION", "ACTION_KEY", "ENTER" -> SourceKey.ACTION_KEY
        "PERIOD", "." -> SourceKey.PERIOD
        "COMMA", "," -> SourceKey.COMMA
        else -> null
    }

    private fun sourceKeyForCode(code: Int): SourceKey? = when (code) {
        Constants.CODE_ENTER, KeyCode.SHIFT_ENTER, KeyCode.ACTION_NEXT, KeyCode.ACTION_PREVIOUS -> SourceKey.ACTION_KEY
        Constants.CODE_PERIOD -> SourceKey.PERIOD
        ','.code -> SourceKey.COMMA
        else -> null
    }

    private fun directionForCode(direction: Int): Direction? = when (direction) {
        SWIPE_UP -> Direction.UP
        SWIPE_DOWN -> Direction.DOWN
        SWIPE_LEFT -> Direction.LEFT
        SWIPE_RIGHT -> Direction.RIGHT
        else -> null
    }

    private inline fun <reified T : Enum<T>> String.enumValueOrNull(): T? =
        runCatching { enumValueOf<T>(trim().uppercase()) }.getOrNull()

    const val SWIPE_UP = 1
    const val SWIPE_DOWN = 2
    const val SWIPE_LEFT = 3
    const val SWIPE_RIGHT = 4
}
