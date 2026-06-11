/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Part of LeanType's native-free statistical swipe decoder.
 * Derived from FlorisBoard's StatisticalGlideTypingClassifier
 * (Copyright 2025 The FlorisBoard Contributors, Apache-2.0), itself based on
 * Etienne Desticourt's gesture classifier for AnySoftKeyboard (PR #1870) and the
 * SHARK^2 algorithm (Kristensson & Zhai, UIST 2004).
 *
 * Adapted for LeanType: decoupled from FlorisBoard/Android types so the decoder is
 * pure JVM logic (engine-agnostic and unit-testable without the native engine).
 */
package helium314.keyboard.keyboard.internal.gesture

/**
 * Minimal, engine-agnostic view of a keyboard key, used only by the swipe decoder.
 *
 * The integration layer (a HeliBoard [helium314.keyboard.keyboard.Key] adapter) maps real keys to
 * this so the decoder never depends on the view layer or the native engine.
 *
 * @param code the primary code point produced by the key (e.g. 'a'.code).
 * @param centerX center x of the key in the same coordinate space as gesture points.
 * @param centerY center y of the key.
 * @param width key width in the same units (used as a length/radius scale).
 * @param height key height.
 */
data class GestureKey(
    val code: Int,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
) {
    val radius: Float get() = minOf(width, height)
}

/**
 * Supplies the lexicon and unigram frequencies the decoder scores candidates against.
 *
 * The integration layer backs this with LeanType's `DictionaryFacilitator` (main + user-history +
 * per-locale dictionaries). Kept as an interface so unit tests can feed a toy dictionary.
 */
interface GestureWordSource {
    /** All candidate words for the active locale, lowercased. */
    fun getWords(): List<String>

    /** Unigram frequency of [word], normalized to [0, 1]; higher means more frequent. */
    fun getFrequency(word: String): Float
}

/**
 * A committed tap that occurred during a swipe (two-thumb typing): a *known* letter the decoded
 * word must contain. Anchors shrink the candidate set to words that provably pass through the
 * tapped key, which is the mechanism that makes tap-while-swiping deterministic.
 *
 * @param code code point of the tapped key.
 * @param index optional position hint within the word (0-based); negative means "anywhere".
 */
data class GestureAnchor(val code: Int, val index: Int = -1)
