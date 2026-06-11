/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Part of LeanType's native-free statistical swipe decoder.
 * Derived from FlorisBoard's StatisticalGlideTypingClassifier
 * (Copyright 2025 The FlorisBoard Contributors, Apache-2.0), itself based on
 * Etienne Desticourt's gesture classifier for AnySoftKeyboard (PR #1870) and the
 * SHARK^2 algorithm (Kristensson & Zhai, UIST 2004).
 *
 * Adapted for LeanType: decoupled from FlorisBoard/Android types.
 */
package helium314.keyboard.keyboard.internal.gesture

import java.text.Normalizer
import kotlin.math.abs

/**
 * Cheaply discards the vast majority of the lexicon before the (expensive) shape/location scoring,
 * using two filters:
 *  1. **Extremities** — the word's first/last letter must be near the swipe's start/end points.
 *  2. **Length** — the word's ideal-gesture length must be within [lengthThreshold] key-radii of the
 *     user's swipe length.
 *
 * @param lengthThreshold allowed length difference (in key radii) before a word is pruned.
 * @param words the lexicon to index.
 * @param keysByCharacter map of code point -> key (for first/last-letter lookup).
 */
class GesturePruner(
    private val lengthThreshold: Double,
    words: List<String>,
    keysByCharacter: Map<Int, GestureKey>,
) {
    /** (firstKeyCode, lastKeyCode) -> words, for fast extremity lookup. */
    private val wordTree = HashMap<Pair<Int, Int>, ArrayList<String>>()
    private val cachedIdealLength = HashMap<String, Float>()

    init {
        for (word in words) {
            if (word.isEmpty()) continue
            val keyPair = firstKeyLastKey(word, keysByCharacter) ?: continue
            wordTree.getOrPut(keyPair) { arrayListOf() }.add(word)
        }
    }

    /** Words whose first/last letters sit near the swipe's first/last points (2 nearest each). */
    fun pruneByExtremities(userGesture: SwipeGesture, keys: List<GestureKey>): ArrayList<String> {
        val remaining = ArrayList<String>()
        val startKeys = nClosestKeyCodes(userGesture.firstX(), userGesture.firstY(), 2, keys)
        val endKeys = nClosestKeyCodes(userGesture.lastX(), userGesture.lastY(), 2, keys)
        for (s in startKeys) for (e in endKeys) {
            wordTree[s to e]?.let { remaining.addAll(it) }
        }
        return remaining
    }

    /** Of [words], keeps those whose ideal-gesture length is close to the user's swipe length. */
    fun pruneByLength(
        userGesture: SwipeGesture,
        words: List<String>,
        keysByCharacter: Map<Int, GestureKey>,
        keys: List<GestureKey>,
    ): ArrayList<String> {
        val remaining = ArrayList<String>()
        val key = keys.firstOrNull() ?: return remaining
        val radius = key.radius
        val userLength = userGesture.length
        for (word in words) {
            for (ideal in SwipeGesture.generateIdealGestures(word, keysByCharacter)) {
                val idealLength = cachedIdealLength.getOrPut(word) { ideal.length }
                if (abs(userLength - idealLength) < lengthThreshold * radius) {
                    remaining.add(word)
                    break
                }
            }
        }
        return remaining
    }

    companion object {
        private fun firstKeyLastKey(word: String, keysByCharacter: Map<Int, GestureKey>): Pair<Int, Int>? {
            val first = baseCode(word[0])
            val last = baseCode(word[word.length - 1])
            val firstKey = keysByCharacter[first] ?: return null
            val lastKey = keysByCharacter[last] ?: return null
            return firstKey.code to lastKey.code
        }

        private fun baseCode(c: Char): Int {
            val lc = Character.toLowerCase(c)
            return Normalizer.normalize(lc.toString(), Normalizer.Form.NFD)[0].code
        }

        private fun nClosestKeyCodes(x: Float, y: Float, n: Int, keys: List<GestureKey>): List<Int> =
            keys.sortedBy { SwipeGesture.distance(it.centerX, it.centerY, x, y) }
                .take(n)
                .map { it.code }
    }
}
