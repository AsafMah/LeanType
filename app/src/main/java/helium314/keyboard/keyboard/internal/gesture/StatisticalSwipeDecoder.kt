/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * LeanType's native-free statistical swipe decoder (SHARK^2-style template matching).
 * Derived from FlorisBoard's StatisticalGlideTypingClassifier
 * (Copyright 2025 The FlorisBoard Contributors, Apache-2.0), itself based on
 * Etienne Desticourt's gesture classifier for AnySoftKeyboard (PR #1870) and the
 * SHARK^2 algorithm (Kristensson & Zhai, UIST 2004).
 *
 * Adapted for LeanType:
 *  - Decoupled from FlorisBoard/Android types -> pure JVM, unit-testable without the native engine.
 *  - Decodes a full path in one call (supports accumulated multi-stroke paths), instead of the
 *    stateful add-point API, so two-thumb sequential strokes can be merged before decoding.
 *  - Adds two extension points for LeanType features: n-gram rescoring and tap-anchor pruning.
 */
package helium314.keyboard.keyboard.internal.gesture

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Decodes a swipe path into ranked word candidates by comparing it against each lexicon word's
 * "ideal gesture" on two channels:
 *  - **shape**: how similar the normalized curves are (size/position independent),
 *  - **location**: how close the un-normalized paths are (anchors the word on the right keys),
 * combined with a unigram-frequency prior. Cheap pruning (extremities + length) runs first.
 *
 * Stateless across [decode] calls except for the layout/lexicon set via [setKeys]/[setWordSource].
 * Engine-agnostic: the integration layer adapts HeliBoard keys + dictionary into [GestureKey] /
 * [GestureWordSource].
 */
class StatisticalSwipeDecoder {

    private var keys: List<GestureKey> = emptyList()
    private var keysByCharacter: Map<Int, GestureKey> = emptyMap()
    private var wordSource: GestureWordSource? = null
    private var pruner: GesturePruner? = null

    /** Sets/updates the keyboard geometry. Rebuilds the pruner if a word source is present. */
    fun setKeys(keys: List<GestureKey>) {
        this.keys = keys
        this.keysByCharacter = keys.associateBy { it.code }
        rebuildPrunerIfReady()
    }

    /** Sets/updates the lexicon + frequencies. Rebuilds the pruner if keys are present. */
    fun setWordSource(source: GestureWordSource) {
        this.wordSource = source
        rebuildPrunerIfReady()
    }

    private fun rebuildPrunerIfReady() {
        val src = wordSource ?: return
        if (keys.isEmpty()) return
        pruner = GesturePruner(PRUNING_LENGTH_THRESHOLD, src.getWords(), keysByCharacter)
    }

    /**
     * Decodes one swipe path into up to [maxSuggestions] ranked words (best first).
     *
     * @param pathX x coordinates of the (possibly multi-stroke, already-merged) swipe path.
     * @param pathY y coordinates; must be the same length as [pathX].
     * @param maxSuggestions cap on returned candidates.
     * @param anchors committed taps that occurred during the swipe (two-thumb typing); each must be
     *   present in any returned word. Empty for a plain single-finger swipe.
     */
    fun decode(
        pathX: FloatArray,
        pathY: FloatArray,
        maxSuggestions: Int,
        anchors: List<GestureAnchor> = emptyList(),
    ): List<String> {
        val source = wordSource ?: return emptyList()
        val pruner = this.pruner ?: return emptyList()
        if (keys.isEmpty() || pathX.isEmpty() || pathX.size != pathY.size) return emptyList()

        val gesture = SwipeGesture()
        for (i in pathX.indices) gesture.addPoint(pathX[i], pathY[i])

        val radius = keys.first().radius
        var remaining: List<String> = pruner.pruneByExtremities(gesture, keys)
        remaining = pruner.pruneByLength(gesture, remaining, keysByCharacter, keys)
        remaining = pruneByAnchors(remaining, anchors)
        if (remaining.isEmpty()) return emptyList()

        val userResampled = gesture.resample(SAMPLING_POINTS)
        val userNormalized = userResampled.normalizeByBoxSide()

        // Best (lowest) confidence per word; lower confidence == better match.
        val best = HashMap<String, Float>()
        for (word in remaining) {
            for (ideal in SwipeGesture.generateIdealGestures(word, keysByCharacter)) {
                val wordResampled = ideal.resample(SAMPLING_POINTS)
                val wordNormalized = wordResampled.normalizeByBoxSide()
                val shapeDistance = calcShapeDistance(wordNormalized, userNormalized)
                val locationDistance = calcLocationDistance(wordResampled, userResampled)
                val shapeProbability = gaussian(shapeDistance, 0f, SHAPE_STD)
                val locationProbability = gaussian(locationDistance, 0f, LOCATION_STD * radius)
                val frequency = 255f * source.getFrequency(word)
                val denom = shapeProbability * locationProbability * frequency
                val confidence = if (denom <= 0f) Float.MAX_VALUE else 1.0f / denom
                val prev = best[word]
                if (prev == null || confidence < prev) best[word] = confidence
            }
        }

        val ranked = best.entries.sortedBy { it.value }.map { it.key }.take(maxSuggestions)
        return rescoreWithNgram(ranked)
    }

    // ----- scoring channels -----

    private fun calcShapeDistance(a: SwipeGesture, b: SwipeGesture): Float {
        var total = 0f
        for (i in 0 until SAMPLING_POINTS) {
            total += SwipeGesture.distance(a.getX(i), a.getY(i), b.getX(i), b.getY(i))
        }
        return total
    }

    private fun calcLocationDistance(a: SwipeGesture, b: SwipeGesture): Float {
        var total = 0f
        for (i in 0 until SAMPLING_POINTS) {
            total += abs(a.getX(i) - b.getX(i)) + abs(a.getY(i) - b.getY(i))
        }
        return total / SAMPLING_POINTS / 2
    }

    private fun gaussian(value: Float, mean: Float, standardDeviation: Float): Float {
        if (standardDeviation <= 0f) return 0f
        val factor = 1.0 / (standardDeviation * sqrt(2 * PI))
        val exponent = ((value - mean) / standardDeviation).toDouble().pow(2.0)
        return (factor * exp(-0.5 * exponent)).toFloat()
    }

    // ----- LeanType extension points -----

    /**
     * Tap-anchor pruning for two-thumb typing. Basic, functional cut: keeps only words that contain
     * every anchored code point. This already makes tap-while-swiping deterministic for the common
     * case (the swipe narrows candidates; the taps hard-filter them).
     *
     * TODO(two-thumb): make it position-aware (respect [GestureAnchor.index]) and fold in key
     * equivalence (e.g. Hebrew final/regular forms) so an anchored tap on one form also matches the
     * other. Those refinements need the layout's equivalence table and are tracked separately.
     */
    private fun pruneByAnchors(words: List<String>, anchors: List<GestureAnchor>): List<String> {
        if (anchors.isEmpty()) return words
        return words.filter { word ->
            anchors.all { anchor ->
                word.any { Character.toLowerCase(it).code == anchor.code }
            }
        }
    }

    /**
     * N-gram rescoring hook. STUB: currently returns the geometric ranking unchanged.
     *
     * TODO(ngram): combine each candidate's geometric confidence with its contextual n-gram
     * probability from the previous word(s). LeanType already carries this context natively
     * (`NgramContext` in the C++ engine); the integration layer will pass a previous-word signal in
     * and reorder candidates here. This is the single biggest accuracy win still on the table and is
     * intentionally left as a seam per the agreed scope.
     */
    private fun rescoreWithNgram(candidates: List<String>): List<String> = candidates

    companion object {
        /** Number of points each gesture is resampled to before comparison. */
        const val SAMPLING_POINTS = 200

        /** Allowed swipe-length variance (in key radii) before a word is pruned. */
        const val PRUNING_LENGTH_THRESHOLD = 8.42

        /** Std-dev of shape-channel distance between two gestures of the same word (normalized). */
        const val SHAPE_STD = 22.08f

        /** Std-dev of location-channel distance, as a factor of key radius (un-normalized). */
        const val LOCATION_STD = 0.5109f
    }
}
