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

        // Resolve %day_short%
        if (result.contains("%day_short%")) {
            val dayShortStr = SimpleDateFormat("EEE", Locale.getDefault()).format(Date())
            result = result.replace("%day_short%", dayShortStr)
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
