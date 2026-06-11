/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Part of LeanType's native-free statistical swipe decoder.
 * Derived from FlorisBoard's StatisticalGlideTypingClassifier
 * (Copyright 2025 The FlorisBoard Contributors, Apache-2.0), itself based on
 * Etienne Desticourt's gesture classifier for AnySoftKeyboard (PR #1870) and the
 * SHARK^2 algorithm (Kristensson & Zhai, UIST 2004).
 *
 * Adapted for LeanType: decoupled from FlorisBoard/Android types (plain coordinate arrays and a
 * Map<Int, GestureKey> instead of TextKey/SparseArrayCompat).
 */
package helium314.keyboard.keyboard.internal.gesture

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A 2D point sequence: either the user's swipe path or a word's "ideal gesture" (the path through
 * the centers of its letters). The shape and location channels of [StatisticalSwipeDecoder] compare
 * a resampled, normalized user gesture against resampled ideal gestures.
 */
class SwipeGesture(
    private val xs: FloatArray = FloatArray(MAX_SIZE),
    private val ys: FloatArray = FloatArray(MAX_SIZE),
    private var size: Int = 0,
) {
    val isEmpty: Boolean get() = size == 0
    val length: Float
        get() {
            var len = 0f
            for (i in 1 until size) len += distance(xs[i - 1], ys[i - 1], xs[i], ys[i])
            return len
        }

    fun addPoint(x: Float, y: Float) {
        if (size >= MAX_SIZE) return
        xs[size] = x
        ys[size] = y
        size += 1
    }

    fun getX(i: Int): Float = xs.getOrElse(i) { 0f }
    fun getY(i: Int): Float = ys.getOrElse(i) { 0f }
    fun firstX(): Float = xs.getOrElse(0) { 0f }
    fun firstY(): Float = ys.getOrElse(0) { 0f }
    fun lastX(): Float = xs.getOrElse(size - 1) { 0f }
    fun lastY(): Float = ys.getOrElse(size - 1) { 0f }

    fun clear() { size = 0 }
    fun clone(): SwipeGesture = SwipeGesture(xs.clone(), ys.clone(), size)

    /**
     * Oversamples this gesture to exactly (roughly) [numPoints] evenly-spaced points so two gestures
     * can be compared point-for-point. Carries fractional error forward so resampling stays even.
     */
    fun resample(numPoints: Int): SwipeGesture {
        val out = SwipeGesture()
        if (size == 0) return out
        out.addPoint(xs[0], ys[0])
        if (size == 1) {
            for (i in 0 until numPoints) out.addPoint(xs[0], ys[0])
            return out
        }
        val interPointDistance = length / numPoints
        if (interPointDistance <= 0f) {
            for (i in 0 until numPoints) out.addPoint(xs[0], ys[0])
            return out
        }
        var lastX = xs[0]
        var lastY = ys[0]
        var cumulativeError = 0.0f
        for (i in 0 until size - 1) {
            var dx = xs[i + 1] - xs[i]
            var dy = ys[i + 1] - ys[i]
            val norm = sqrt(dx.pow(2.0f) + dy.pow(2.0f))
            if (norm == 0f) continue
            dx /= norm
            dy /= norm
            var numNewPoints = norm / interPointDistance
            cumulativeError += numNewPoints - numNewPoints.toInt()
            if (cumulativeError > 1) {
                numNewPoints = (numNewPoints.toInt() + cumulativeError.toInt()).toFloat()
                cumulativeError %= 1
            }
            for (j in 0 until numNewPoints.toInt()) {
                lastX += dx * interPointDistance
                lastY += dy * interPointDistance
                out.addPoint(lastX, lastY)
            }
        }
        return out
    }

    /**
     * Translates+scales the gesture so it is centered on its bounding box and sized by its longest
     * side. Makes the shape channel independent of keyboard/key size and position.
     */
    fun normalizeByBoxSide(): SwipeGesture {
        val out = SwipeGesture()
        var maxX = -1e9f; var maxY = -1e9f; var minX = 1e9f; var minY = 1e9f
        for (i in 0 until size) {
            maxX = max(xs[i], maxX); maxY = max(ys[i], maxY)
            minX = min(xs[i], minX); minY = min(ys[i], minY)
        }
        val width = maxX - minX
        val height = maxY - minY
        val longestSide = max(max(width, height), 0.00001f)
        val centroidX = (width / 2 + minX) / longestSide
        val centroidY = (height / 2 + minY) / longestSide
        for (i in 0 until size) {
            out.addPoint(xs[i] / longestSide - centroidX, ys[i] / longestSide - centroidY)
        }
        return out
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SwipeGesture || size != other.size) return false
        for (i in 0 until size) if (xs[i] != other.xs[i] || ys[i] != other.ys[i]) return false
        return true
    }

    override fun hashCode(): Int {
        var result = size
        for (i in 0 until size) {
            result = 31 * result + xs[i].toBits()
            result = 31 * result + ys[i].toBits()
        }
        return result
    }

    companion object {
        private const val MAX_SIZE = 500

        fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
            sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))

        /**
         * Builds the ideal gesture(s) for [word]: the path visiting each letter's key center. When a
         * letter repeats (e.g. "pool", "lull") a small diamond loop is added on the doubled key in a
         * second variant, so words that differ only by a doubled letter can still be told apart.
         *
         * @return one gesture, or two (plain + with-loops) when the word has a repeated letter.
         */
        fun generateIdealGestures(word: String, keysByCharacter: Map<Int, GestureKey>): List<SwipeGesture> {
            val ideal = SwipeGesture()
            val idealWithLoops = SwipeGesture()
            var previousLetter = '\u0000'
            var hasLoops = false
            for (c in word) {
                val lc = Character.toLowerCase(c)
                var key = keysByCharacter[lc.code]
                if (key == null) {
                    val base = Normalizer.normalize(lc.toString(), Normalizer.Form.NFD)[0]
                    key = keysByCharacter[base.code] ?: continue
                }
                val cx = key.centerX
                val cy = key.centerY
                if (previousLetter == lc) {
                    val qx = key.width / 4.0f
                    val qy = key.height / 4.0f
                    idealWithLoops.addPoint(cx + qx, cy + qy)
                    idealWithLoops.addPoint(cx + qx, cy - qy)
                    idealWithLoops.addPoint(cx - qx, cy - qy)
                    idealWithLoops.addPoint(cx - qx, cy + qy)
                    hasLoops = true
                    ideal.addPoint(cx, cy)
                } else {
                    ideal.addPoint(cx, cy)
                    idealWithLoops.addPoint(cx, cy)
                }
                previousLetter = lc
            }
            return if (hasLoops) listOf(ideal, idealWithLoops) else listOf(ideal)
        }
    }
}
