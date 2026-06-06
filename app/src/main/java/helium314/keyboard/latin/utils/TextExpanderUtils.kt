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
    const val PREF_DATA = "pref_text_expander_data"

    fun isEnabled(context: Context): Boolean {
        return context.prefs().getBoolean(PREF_ENABLED, false)
    }

    fun getPrefix(context: Context): String {
        return context.prefs().getString(PREF_PREFIX, "") ?: ""
    }

    fun getShortcuts(context: Context): Map<String, String> {
        val jsonStr = context.prefs().getString(PREF_DATA, "{}") ?: "{}"
        val map = mutableMapOf<String, String>()
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = json.getString(key)
            }
        } catch (e: Exception) {
            // fallback
        }
        return map
    }

    fun saveShortcuts(context: Context, map: Map<String, String>) {
        try {
            val json = JSONObject()
            for ((key, value) in map) {
                json.put(key, value)
            }
            context.prefs().edit().putString(PREF_DATA, json.toString()).apply()
        } catch (e: Exception) {
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
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipText = try {
                if (clipboard?.hasPrimaryClip() == true) {
                    clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
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
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val level = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val batteryStr = if (level != -1) "$level%" else ""
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

    fun getExpandedWord(word: String?, context: Context): String? {
        if (word == null || !isEnabled(context)) return null
        
        val prefix = getPrefix(context)
        if (prefix.isNotEmpty() && !word.startsWith(prefix)) return null
        
        val shortcut = if (prefix.isNotEmpty()) word.substring(prefix.length) else word
        if (shortcut.isEmpty()) return null
        
        val shortcuts = getShortcuts(context)
        // Check exact match or lowercase match
        val template = shortcuts[shortcut] ?: shortcuts[shortcut.lowercase(Locale.getDefault())] ?: return null
        
        return expand(template, context)
    }
}
