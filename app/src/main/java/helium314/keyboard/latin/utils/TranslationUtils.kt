package helium314.keyboard.latin.utils

import android.content.SharedPreferences

object TranslationUtils {
    fun getLanguageHistory(prefs: SharedPreferences): List<Pair<String, String>> {
        val historyString = prefs.getString("pref_translation_language_history", "") ?: ""
        if (historyString.isEmpty()) return emptyList()
        return historyString.split("\n").mapNotNull {
            val parts = it.split("|", limit = 2)
            if (parts.size == 2) parts[1] to parts[0] else null
        }
    }

    fun getRemovedLanguages(prefs: SharedPreferences): Set<String> {
        val str = prefs.getString("pref_removed_translation_languages", "") ?: ""
        if (str.isEmpty()) return emptySet()
        return str.split(",").toSet()
    }

    fun saveLanguageHistory(prefs: SharedPreferences, name: String, code: String) {
        val currentHistory = getLanguageHistory(prefs).toMutableList()
        currentHistory.removeAll { it.second.equals(code, ignoreCase = true) || it.first.equals(name, ignoreCase = true) }
        currentHistory.add(0, name to code)
        val serialized = currentHistory.joinToString("\n") { "${it.second}|${it.first}" }
        
        // Also un-remove if user explicitly saved it again
        val removed = getRemovedLanguages(prefs).toMutableSet()
        removed.remove(code.lowercase())
        removed.remove(name.lowercase())
        
        prefs.edit()
            .putString("pref_translation_language_history", serialized)
            .putString("pref_removed_translation_languages", removed.joinToString(","))
            .apply()
    }

    fun removeLanguageHistory(prefs: SharedPreferences, code: String) {
        val currentHistory = getLanguageHistory(prefs).toMutableList()
        currentHistory.removeAll { it.second.equals(code, ignoreCase = true) || it.first.equals(code, ignoreCase = true) }
        val serialized = currentHistory.joinToString("\n") { "${it.second}|${it.first}" }
        
        val removed = getRemovedLanguages(prefs).toMutableSet()
        removed.add(code.lowercase())
        
        prefs.edit()
            .putString("pref_translation_language_history", serialized)
            .putString("pref_removed_translation_languages", removed.joinToString(","))
            .apply()
    }

    fun isSameLanguage(p1: Pair<String, String>, p2: Pair<String, String>): Boolean {
        return p1.first.equals(p2.first, ignoreCase = true) ||
               p1.second.equals(p2.second, ignoreCase = true)
    }
}
