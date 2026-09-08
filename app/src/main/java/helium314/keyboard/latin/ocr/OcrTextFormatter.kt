// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ocr

import android.content.Context
import helium314.keyboard.latin.utils.prefs
import java.util.Locale

object OcrTextFormatter {

    fun format(context: Context, rawLines: List<String>): List<String> {
        val prefs = context.prefs()
        val keepLineBreaks = prefs.getBoolean(OcrPluginLoader.PREF_OCR_KEEP_LINE_BREAKS, true)
        val trimWhitespace = prefs.getBoolean(OcrPluginLoader.PREF_OCR_TRIM_WHITESPACE, true)
        val dehyphenate = prefs.getBoolean(OcrPluginLoader.PREF_OCR_DEHYPHENATE, true)
        val normalizePunctuation = prefs.getBoolean(OcrPluginLoader.PREF_OCR_NORMALIZE_PUNCTUATION, false)
        val stripBullets = prefs.getBoolean(OcrPluginLoader.PREF_OCR_STRIP_BULLETS, false)
        val removeNoise = prefs.getBoolean(OcrPluginLoader.PREF_OCR_REMOVE_NOISE, true)
        val casing = prefs.getString(OcrPluginLoader.PREF_OCR_CASING, "as_is") ?: "as_is"
        val joinFormat = prefs.getString(OcrPluginLoader.PREF_OCR_LINE_JOIN_FORMAT, "newline") ?: "newline"

        return formatLines(
            rawLines = rawLines,
            keepLineBreaks = keepLineBreaks,
            trimWhitespace = trimWhitespace,
            casing = casing,
            joinFormat = joinFormat,
            dehyphenate = dehyphenate,
            normalizePunctuation = normalizePunctuation,
            stripBullets = stripBullets,
            removeNoise = removeNoise
        )
    }

    fun formatLines(
        rawLines: List<String>,
        keepLineBreaks: Boolean,
        trimWhitespace: Boolean,
        casing: String,
        joinFormat: String,
        dehyphenate: Boolean,
        normalizePunctuation: Boolean,
        stripBullets: Boolean,
        removeNoise: Boolean
    ): List<String> {
        var lines = rawLines
        if (removeNoise) {
            lines = lines.filter { line ->
                val trimmed = line.trim()
                if (trimmed.length <= 1 && !trimmed.all { it.isLetterOrDigit() }) return@filter false
                true
            }
        }
        if (stripBullets) {
            val bulletRegex = Regex("""^(\s*[-*•–—►▪▫]|\[[ xX]?\]|\d+[\.\)]|[a-zA-Z][\.\)])\s*""")
            lines = lines.map { it.replace(bulletRegex, "") }
        }
        if (trimWhitespace) {
            lines = lines.map { it.trim() }.filter { it.isNotEmpty() }
        }
        if (dehyphenate && lines.size > 1) {
            val result = mutableListOf<String>()
            var carry: String? = null
            for (line in lines) {
                if (carry != null) {
                    val merged = carry.dropLast(1) + line.trimStart()
                    result.add(merged)
                    carry = null
                } else if (line.endsWith("-") || line.endsWith("—") || line.endsWith("–")) {
                    carry = line
                } else {
                    result.add(line)
                }
            }
            if (carry != null) result.add(carry)
            lines = result
        }
        if (normalizePunctuation) {
            lines = lines.map { line ->
                line.replace('“', '"')
                    .replace('”', '"')
                    .replace('‘', '\'')
                    .replace('’', '\'')
                    .replace(" ,", ",")
                    .replace(" .", ".")
                    .replace(" !", "!")
                    .replace(" ?", "?")
                    .replace(" ;", ";")
                    .replace(" :", ":")
            }
        }
        lines = when (casing) {
            "sentence" -> lines.map { line ->
                line.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
            "lower" -> lines.map { it.lowercase(Locale.getDefault()) }
            "upper" -> lines.map { it.uppercase(Locale.getDefault()) }
            "title" -> lines.map { line ->
                line.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            }
            else -> lines
        }
        lines = when (joinFormat) {
            "bullet" -> lines.map { "• $it" }
            "numbered" -> lines.mapIndexed { idx, it -> "${idx + 1}. $it" }
            "comma" -> listOf(lines.joinToString(", "))
            "space" -> listOf(lines.joinToString(" "))
            else -> lines
        }
        return lines
    }
}
