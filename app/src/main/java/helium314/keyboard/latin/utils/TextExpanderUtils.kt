/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.utils

import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TextExpanderUtils {
    const val PREF_ENABLED = "pref_text_expander_enabled"
    const val PREF_PREFIX = "pref_text_expander_prefix"
    const val PREF_IMMEDIATE = "pref_text_expander_immediate"
    const val PREF_BACKSPACE_REVERTS = "pref_text_expander_backspace_reverts"
    const val PREF_DATA = "pref_text_expander_data"
    const val REGEX_PREFIX = "__regex__:"

    fun isEnabled(context: Context): Boolean {
        return context.prefs().getBoolean(PREF_ENABLED, false)
    }

    fun isImmediateEnabled(context: Context): Boolean {
        return context.prefs().getBoolean(PREF_IMMEDIATE, false)
    }

    fun isBackspaceRevertsEnabled(context: Context): Boolean {
        return context.prefs().getBoolean(PREF_BACKSPACE_REVERTS, false)
    }



    data class ShortcutEntry(
        val template: String,
        val prefix: String = ""
    )

    data class ExpandedResult(
        val expandedText: String,
        val prefixLength: Int,
        val matchedString: String
    )

    private data class CompiledShortcut(
        val key: String,
        val cleanKey: String,
        val isRegex: Boolean,
        val regex: Regex?,
        val entry: ShortcutEntry
    )

    @Volatile
    private var cachedJsonStr: String? = null

    @Volatile
    private var cachedShortcutsMap: Map<String, ShortcutEntry>? = null

    @Volatile
    private var cachedCompiledList: List<CompiledShortcut>? = null

    fun clearCache() {
        cachedJsonStr = null
        cachedShortcutsMap = null
        cachedCompiledList = null
    }

    fun getShortcuts(context: Context): Map<String, ShortcutEntry> {
        val jsonStr = context.prefs().getString(PREF_DATA, "{}") ?: "{}"
        val currentJson = cachedJsonStr
        val currentMap = cachedShortcutsMap
        if (currentJson != null && currentJson == jsonStr && currentMap != null) {
            return currentMap
        }
        val map = mutableMapOf<String, ShortcutEntry>()
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val valueObj = json.get(key)
                if (valueObj is JSONObject) {
                    val template = valueObj.optString("template", "")
                    val prefix = valueObj.optString("prefix", "")
                    map[key] = ShortcutEntry(template, prefix)
                } else {
                    map[key] = ShortcutEntry(valueObj.toString(), "")
                }
            }
        } catch (e: java.lang.Exception) {
            // fallback
        }
        val compiled = map.map { (key, entry) ->
            val isRegex = key.startsWith(REGEX_PREFIX)
            val cleanKey = if (isRegex) key.substring(REGEX_PREFIX.length) else key
            val regex = if (isRegex) {
                try {
                    Regex(cleanKey, RegexOption.IGNORE_CASE)
                } catch (e: Exception) {
                    null
                }
            } else null
            CompiledShortcut(key, cleanKey, isRegex, regex, entry)
        }
        cachedJsonStr = jsonStr
        cachedShortcutsMap = map
        cachedCompiledList = compiled
        return map
    }

    fun saveShortcuts(context: Context, map: Map<String, ShortcutEntry>) {
        try {
            val json = JSONObject()
            for ((key, entry) in map) {
                val obj = JSONObject()
                obj.put("template", entry.template)
                obj.put("prefix", entry.prefix)
                json.put(key, obj)
            }
            val jsonStr = json.toString()
            context.prefs().edit().putString(PREF_DATA, jsonStr).commit()
            clearCache()
        } catch (e: java.lang.Exception) {
            // fail silently
        }
    }

    fun expand(template: String, context: Context): String {
        var result = template

        // Resolve %date%
        if (result.contains("%date%")) {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            result = result.replace("%date%", dateStr)
        }

        // Resolve %time%
        if (result.contains("%time%")) {
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            result = result.replace("%time%", timeStr)
        }

        // Resolve %clipboard%
        if (result.contains("%clipboard%")) {
            val clipText = try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipboard?.hasPrimaryClip() == true) {
                    val rawText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    if (rawText.length > 2000) rawText.substring(0, 2000) else rawText
                } else ""
            } catch (e: Exception) {
                ""
            }
            result = result.replace("%clipboard%", clipText)
        }

        // Resolve %day%
        if (result.contains("%day%")) {
            val dayStr = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
            result = result.replace("%day%", dayStr)
        }

        // Resolve %time12%
        if (result.contains("%time12%")) {
            val time12Str = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            result = result.replace("%time12%", time12Str)
        }

        // Resolve %month%
        if (result.contains("%month%")) {
            val monthStr = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())
            result = result.replace("%month%", monthStr)
        }

        // Resolve %year%
        if (result.contains("%year%")) {
            val yearStr = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
            result = result.replace("%year%", yearStr)
        }

        // Resolve %week%
        if (result.contains("%week%")) {
            val weekStr = SimpleDateFormat("w", Locale.getDefault()).format(Date())
            result = result.replace("%week%", weekStr)
        }

        // Resolve %battery%
        if (result.contains("%battery%")) {
            val batteryStr = try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                val level = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                if (level != -1) "$level%" else ""
            } catch (e: Exception) {
                ""
            }
            result = result.replace("%battery%", batteryStr)
        }

        // Resolve %language%
        if (result.contains("%language%")) {
            val imeManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            val activeSubtype = imeManager?.currentInputMethodSubtype
            val languageStr = activeSubtype?.getDisplayName(context, context.packageName, context.applicationInfo)?.toString()
                ?: Locale.getDefault().getDisplayName(Locale.getDefault())
            result = result.replace("%language%", languageStr)
        }

        // Resolve %greeting%
        if (result.contains("%greeting%")) {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val greeting = when (hour) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                in 17..21 -> "Good evening"
                else -> "Good night"
            }
            result = result.replace("%greeting%", greeting)
        }

        // Resolve %tomorrow%
        if (result.contains("%tomorrow%")) {
            val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
            val tomorrowStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            result = result.replace("%tomorrow%", tomorrowStr)
        }

        // Resolve %bullets% with optional count
        if (result.contains("%bullets")) {
            val bulletsRegex = Regex("%bullets(?:_(\\d+))?%")
            result = bulletsRegex.replace(result) { match ->
                val count = match.groups[1]?.value?.toIntOrNull() ?: 3
                if (count <= 0) ""
                else {
                    val sb = java.lang.StringBuilder()
                    sb.append("• %cursor%")
                    for (i in 2..count) {
                        sb.append("\n• ")
                    }
                    sb.toString()
                }
            }
        }

        // Resolve %list% with optional count
        if (result.contains("%list")) {
            val listRegex = Regex("%list(?:_(\\d+))?%")
            result = listRegex.replace(result) { match ->
                val count = match.groups[1]?.value?.toIntOrNull() ?: 3
                if (count <= 0) ""
                else {
                    val sb = java.lang.StringBuilder()
                    sb.append("1. %cursor%")
                    for (i in 2..count) {
                        sb.append("\n$i. ")
                    }
                    sb.toString()
                }
            }
        }

        return result
    }

    fun isPrefixOfNonRegexShortcut(
        word: String,
        textBeforeCursor: String,
        context: Context,
    ): Boolean =
        getShortcuts(context).any { (key, entry) ->
            if (key.startsWith(REGEX_PREFIX) || key.length < entry.prefix.length) {
                false
            } else {
                val shortcut = key.substring(entry.prefix.length)
                !shortcut.equals(word, ignoreCase = true) &&
                    shortcut.startsWith(word, ignoreCase = true) &&
                    textBeforeCursor.endsWith(entry.prefix + word, ignoreCase = true)
            }
        }

    fun getExpandedWordForTyped(word: String?, textBeforeCursor: String?, context: Context): ExpandedResult? {
        if (textBeforeCursor == null || !isEnabled(context)) return null
        getShortcuts(context)
        val compiledList = cachedCompiledList ?: return null
        val fullText = if (word != null && !textBeforeCursor.endsWith(word, ignoreCase = true)) {
            textBeforeCursor + word
        } else {
            textBeforeCursor
        }
        
        for (item in compiledList) {
            val entry = item.entry
            if (item.isRegex) {
                val regex = item.regex ?: continue
                val prefix = entry.prefix
                if (word != null) {
                    if (regex.matches(fullText)) {
                        try {
                            val replaced = regex.replace(fullText, entry.template)
                            val prefixLength = if (fullText.length > word.length) fullText.length - word.length else prefix.length
                            return ExpandedResult(expand(replaced, context), prefixLength, fullText)
                        } catch (e: java.lang.Exception) {
                            // ignore
                        }
                    }
                }
                try {
                    val match = regex.findAll(fullText).lastOrNull { it.range.last == fullText.length - 1 }
                    if (match != null && match.value.isNotEmpty()) {
                        val matchedString = match.value
                        val replaced = regex.replace(matchedString, entry.template)
                        val extraPrefix = if (word != null && matchedString.endsWith(word, ignoreCase = true)) {
                            matchedString.length - word.length
                        } else 0
                        val effectivePrefixLength = maxOf(prefix.length, extraPrefix)
                        return ExpandedResult(expand(replaced, context), effectivePrefixLength, matchedString)
                    }
                } catch (e: java.lang.Exception) {
                    // ignore
                }
            } else {
                val prefix = entry.prefix
                val expectedSuffix = item.cleanKey
                if (word == null || expectedSuffix.equals(prefix + word, ignoreCase = true)) {
                    if (textBeforeCursor.endsWith(expectedSuffix, ignoreCase = true)) {
                        return ExpandedResult(expand(entry.template, context), prefix.length, expectedSuffix)
                    }
                }
            }
        }
        return null
    }

    fun getExpandedWord(word: String?, context: Context): String? {
        if (word == null || !isEnabled(context)) return null
        val shortcuts = getShortcuts(context)
        val entry = shortcuts[word] ?: shortcuts[word.lowercase(Locale.getDefault())]
        if (entry != null && entry.prefix.isEmpty()) {
            return expand(entry.template, context)
        }
        return null
    }
}
