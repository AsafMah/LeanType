// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import java.util.Locale

object VoiceTextProcessor {

    enum class Action {
        NEW_LINE,
        NEW_PARAGRAPH,
        DELETE_LAST_WORD,
        CLEAR_ALL,
        SEND
    }

    sealed class Result {
        data class Command(val action: Action, val commandText: String) : Result()
        data class Text(val value: String, val isTerminal: Boolean) : Result()
    }

    // Pre-compile regex patterns at the object level to avoid main-thread allocations
    private val PUNCT_REGEX = Regex(
        """\b(question mark|exclamation mark|exclamation point|full stop|period|comma|semicolon|colon)\b""",
        RegexOption.IGNORE_CASE
    )
    private val SPACE_BEFORE_PUNCT = Regex("""\s+([,;.!?])""")
    private val SPACE_AFTER_PUNCT = Regex("""([,;.!?])(?=\w)""")

    private val COMMANDS = mapOf(
        "new line" to Action.NEW_LINE,
        "next line" to Action.NEW_LINE,
        "new paragraph" to Action.NEW_PARAGRAPH,
        "delete last word" to Action.DELETE_LAST_WORD,
        "delete word" to Action.DELETE_LAST_WORD,
        "clear all" to Action.CLEAR_ALL,
        "clear text" to Action.CLEAR_ALL,
        "send" to Action.SEND,
        "send it" to Action.SEND
    )

    private val PUNCT_MAP = mapOf(
        "question mark" to "?",
        "exclamation mark" to "!",
        "exclamation point" to "!",
        "full stop" to ".",
        "period" to ".",
        "comma" to ",",
        "semicolon" to ";",
        "colon" to ":"
    )

    fun process(
        raw: String,
        commandsEnabled: Boolean,
        smartPunctuationEnabled: Boolean,
        needsCapital: Boolean
    ): Result {
        val normalized = raw.trim()
        if (normalized.isEmpty()) return Result.Text("", false)

        val lower = normalized.lowercase(Locale.ROOT).replace(Regex("""[.,!?;:]+$"""), "").trim()
        if (commandsEnabled && COMMANDS.containsKey(lower)) {
            return Result.Command(COMMANDS[lower]!!, lower)
        }

        var text = normalized
        var isTerminal = false

        if (smartPunctuationEnabled) {
            text = PUNCT_REGEX.replace(text) { match ->
                val sym = PUNCT_MAP[match.value.lowercase(Locale.ROOT)] ?: match.value
                if (sym in ".!?") isTerminal = true
                sym
            }
            text = text.replace(SPACE_BEFORE_PUNCT, "$1").replace(SPACE_AFTER_PUNCT, "$1 ")
        }

        if (needsCapital && text.isNotEmpty()) {
            text = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }

        val lastChar = text.trimEnd().lastOrNull()
        if (lastChar != null && lastChar in ".!?") {
            isTerminal = true
        }

        return Result.Text(text, isTerminal)
    }
}
