package helium314.keyboard.latin.utils

import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.KeyboardCodesSet
import helium314.keyboard.keyboard.internal.PopupKeySpec
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import java.util.EnumMap
import java.util.Locale

object SourceKeyActionTargets {
    enum class SourceKey { ACTION_KEY, PERIOD, COMMA }

    private var cachedSpec: String? = null
    private var cachedTargets: Map<SourceKey, List<ToolbarKey>> = emptyMap()

    @JvmStatic
    fun hasTargetsForSource(code: Int, spec: String): Boolean {
        val source = sourceKeyForCode(code) ?: return false
        return targets(spec)[source]?.isNotEmpty() == true
    }

    @JvmStatic
    fun popupKeysForSource(code: Int, spec: String, locale: Locale): Array<PopupKeySpec>? {
        val source = sourceKeyForCode(code) ?: return null
        val keys = targets(spec)[source].orEmpty()
        if (keys.isEmpty()) return null
        return keys.map { toolbarKey ->
            val keyName = toolbarKeyStrings[toolbarKey] ?: toolbarKey.name.lowercase(Locale.US)
            PopupKeySpec(
                "${KeyboardIconsSet.PREFIX_ICON}$keyName|${KeyboardCodesSet.PREFIX_CODE}${getCodeForToolbarKey(toolbarKey)}",
                false,
                locale,
            )
        }.toTypedArray()
    }

    fun parse(spec: String): Map<SourceKey, List<ToolbarKey>> = targets(spec)

    @JvmStatic
    fun clearCache() {
        cachedSpec = null
        cachedTargets = emptyMap()
    }

    private fun targets(spec: String): Map<SourceKey, List<ToolbarKey>> {
        if (spec == cachedSpec) return cachedTargets
        val parsed = EnumMap<SourceKey, MutableList<ToolbarKey>>(SourceKey::class.java)
        spec.split(';').forEach { entry ->
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) return@forEach
            val source = sourceKeyForName(trimmed.substringBefore('=', missingDelimiterValue = "")) ?: return@forEach
            val actions = trimmed.substringAfter('=', missingDelimiterValue = "")
                .split(',')
                .mapNotNull { it.enumValueOrNull<ToolbarKey>() }
            if (actions.isNotEmpty()) parsed[source] = actions.toMutableList()
        }
        cachedSpec = spec
        cachedTargets = parsed
        return cachedTargets
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

    private inline fun <reified T : Enum<T>> String.enumValueOrNull(): T? =
        runCatching { enumValueOf<T>(trim().uppercase()) }.getOrNull()
}
