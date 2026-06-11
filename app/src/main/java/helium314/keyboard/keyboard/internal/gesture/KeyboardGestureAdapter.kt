/*
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Bridges LeanType's keyboard/dictionary types to the engine-agnostic swipe decoder.
 */
package helium314.keyboard.keyboard.internal.gesture

import helium314.keyboard.keyboard.Keyboard

/** Adapts a live [Keyboard] into the decoder's [GestureKey] list (letter keys only). */
object KeyboardGestureAdapter {

    /**
     * Returns one [GestureKey] per letter key, keyed by lowercase code point, with centers in the
     * same coordinate space as gesture [helium314.keyboard.latin.common.InputPointers] (both are
     * relative to the keyboard view's top-left, so no transform is needed).
     *
     * Functional / modifier / multi-char keys are skipped: only keys whose primary code is a single
     * letter code point participate in gesture decoding.
     */
    fun toGestureKeys(keyboard: Keyboard): List<GestureKey> {
        val out = ArrayList<GestureKey>()
        for (key in keyboard.sortedKeys) {
            val code = key.code
            if (code <= 0 || key.isModifier || !Character.isLetter(code)) continue
            out.add(
                GestureKey(
                    code = Character.toLowerCase(code),
                    centerX = key.x + key.width / 2f,
                    centerY = key.y + key.height / 2f,
                    width = key.width.toFloat(),
                    height = key.height.toFloat(),
                )
            )
        }
        return out
    }
}

/** [GestureWordSource] backed by a precomputed `word -> probability (0..255)` lexicon. */
class DictionaryGestureWordSource(private val lexicon: Map<String, Int>) : GestureWordSource {
    private val words: List<String> = lexicon.keys.toList()
    override fun getWords(): List<String> = words
    override fun getFrequency(word: String): Float = (lexicon[word] ?: 0) / 255f
}
