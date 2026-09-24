// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal class EmojiPins(context: Context) {
    private val prefs = context.getSharedPreferences("emoji_picker_pins", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(String.serializer())

    fun items(): List<String> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        require(raw.length <= 16_384) { "Emoji pins exceed storage bounds" }
        return Json.decodeFromString(serializer, raw).also { values ->
            require(values.size <= 50 && values.all { it.isNotBlank() && it.length <= 64 })
        }
    }

    fun toggle(emoji: String) {
        require(emoji.isNotBlank() && emoji.length <= 64)
        val current = items()
        val updated = if (emoji in current) current - emoji else (listOf(emoji) + current).take(50)
        check(prefs.edit().putString("items", Json.encodeToString(serializer, updated)).commit()) {
            "Unable to save emoji pins"
        }
    }
}
