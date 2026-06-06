// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.database

/**
 * Policy layer over [TouchModelDao]: turns a key's raw learned stats into a *capped*,
 * confidence- and strength-scaled landing offset that the input paths apply
 * (see docs/ADAPTIVE_TYPING.md). Pure functions — no Android / DB / threading — so the
 * caps and ramp are unit-testable and the input hot paths just consume the result.
 *
 * The cap is the safety guarantee: a learned bias can shift a key's effective center by at
 * most [MAX_SHIFT_FRACTION] of the key dimension, so a clearly-on-target press can never flip
 * to a neighbor. The bias also ramps in with sample count (no sudden jumps from sparse data)
 * and scales with the user's strength setting (0 = off).
 */
object TouchModelManager {
    /** Max center shift as a fraction of the key's width/height. */
    const val MAX_SHIFT_FRACTION = 0.25f
    /** Confidence reaches full strength at this many samples; it is 0 at/below MIN_CONFIDENT_SAMPLES. */
    const val FULL_CONFIDENCE_SAMPLES = 60

    /**
     * Capped, confidence- and strength-scaled landing offset for a key, in pixels.
     * @return a fresh {dx, dy}; {0, 0} when there is not enough data or strength is 0.
     */
    fun adjustedOffset(stat: TouchModelDao.Stat?, keyWidth: Int, keyHeight: Int,
                       strengthPercent: Int): FloatArray {
        if (stat == null || strengthPercent <= 0 || keyWidth <= 0 || keyHeight <= 0) {
            return floatArrayOf(0f, 0f)
        }
        val scale = confidence(stat.count) * (strengthPercent.coerceIn(0, 100) / 100f)
        if (scale <= 0f) return floatArrayOf(0f, 0f)
        val maxX = MAX_SHIFT_FRACTION * keyWidth
        val maxY = MAX_SHIFT_FRACTION * keyHeight
        val dx = (stat.meanDx * scale).coerceIn(-maxX, maxX)
        val dy = (stat.meanDy * scale).coerceIn(-maxY, maxY)
        return floatArrayOf(dx, dy)
    }

    /** 0 below [TouchModelDao.MIN_CONFIDENT_SAMPLES], ramps linearly to 1 at [FULL_CONFIDENCE_SAMPLES]. */
    fun confidence(count: Int): Float {
        val min = TouchModelDao.MIN_CONFIDENT_SAMPLES
        if (count <= min) return 0f
        if (count >= FULL_CONFIDENCE_SAMPLES) return 1f
        return (count - min).toFloat() / (FULL_CONFIDENCE_SAMPLES - min)
    }
}
