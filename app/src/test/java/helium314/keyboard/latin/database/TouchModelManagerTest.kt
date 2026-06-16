// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Unit tests for the capped/confidence/strength policy in [TouchModelManager]. */
class TouchModelManagerTest {

    private fun stat(meanDx: Float, meanDy: Float, count: Int) =
        TouchModelDao.Stat(0x65 /* 'e' */, "qwerty", 0, meanDx, meanDy, 0f, 0f, count, 0L)

    @Test fun nullStatGivesNoOffset() {
        val o = TouchModelManager.adjustedOffset(null, 100, 120, 100)
        assertEquals(0f, o[0]); assertEquals(0f, o[1])
    }

    @Test fun belowConfidenceThresholdGivesNoOffset() {
        // count <= MIN_CONFIDENT_SAMPLES => confidence 0 => no bias even with a large mean
        val o = TouchModelManager.adjustedOffset(stat(40f, -30f, TouchModelDao.MIN_CONFIDENT_SAMPLES), 100, 120, 100)
        assertEquals(0f, o[0]); assertEquals(0f, o[1])
    }

    @Test fun zeroStrengthGivesNoOffset() {
        val o = TouchModelManager.adjustedOffset(stat(40f, -30f, 1000), 100, 120, 0)
        assertEquals(0f, o[0]); assertEquals(0f, o[1])
    }

    @Test fun largeOffsetIsCappedToKeyFraction() {
        // Full confidence + full strength, but a huge mean must be clamped to +/-25% of the key.
        val o = TouchModelManager.adjustedOffset(stat(9999f, -9999f, 1000), 100, 120, 100)
        assertEquals(TouchModelManager.MAX_SHIFT_FRACTION * 100, o[0], 0.001f)   // +25
        assertEquals(-TouchModelManager.MAX_SHIFT_FRACTION * 120, o[1], 0.001f)  // -30
    }

    @Test fun moderateOffsetPassesThroughWithinCap() {
        // count=60 => confidence 1; strength 100% => full mean, well within the cap.
        val o = TouchModelManager.adjustedOffset(stat(5f, -4f, TouchModelManager.FULL_CONFIDENCE_SAMPLES), 100, 120, 100)
        assertEquals(5f, o[0], 0.001f)
        assertEquals(-4f, o[1], 0.001f)
    }

    @Test fun strengthScalesTheOffset() {
        val full = TouchModelManager.adjustedOffset(stat(10f, 0f, 1000), 100, 120, 100)[0]
        val half = TouchModelManager.adjustedOffset(stat(10f, 0f, 1000), 100, 120, 50)[0]
        assertEquals(full / 2f, half, 0.001f)
    }

    @Test fun confidenceRampIsMonotonic() {
        val c20 = TouchModelManager.confidence(20)
        val c40 = TouchModelManager.confidence(40)
        val c60 = TouchModelManager.confidence(60)
        val c100 = TouchModelManager.confidence(100)
        assertEquals(0f, c20, 0.001f)
        assertTrue(c40 > c20 && c40 < c60)
        assertEquals(1f, c60, 0.001f)
        assertEquals(1f, c100, 0.001f)
    }

    @Test fun decayFactorHalvesAtHalfLife() {
        assertEquals(1f, TouchModelManager.decayFactor(0L, 1000L), 0.001f)
        assertEquals(0.5f, TouchModelManager.decayFactor(1000L, 1000L), 0.01f)
        assertEquals(0.25f, TouchModelManager.decayFactor(2000L, 1000L), 0.01f)
        assertEquals(1f, TouchModelManager.decayFactor(5000L, 0L), 0.001f)   // window off => no decay
        assertEquals(1f, TouchModelManager.decayFactor(-10L, 1000L), 0.001f) // future/zero elapsed => no decay
    }

    @Test fun forgetWindowFadesStaleLearning() {
        val hl = 1000L
        // count=60 => full confidence; updatedAt=0 (from the stat helper).
        val fresh = TouchModelManager.adjustedOffset(stat(10f, 0f, 60), 100, 120, 100, 0L, hl)[0]
        assertEquals(10f, fresh, 0.001f)
        // now = one half-life => effective count 30 => partial confidence => weaker but non-zero.
        val stale = TouchModelManager.adjustedOffset(stat(10f, 0f, 60), 100, 120, 100, hl, hl)[0]
        assertTrue(stale > 0f && stale < fresh, "stale must be weaker than fresh")
        // now = four half-lives => effective count well below the confidence floor => fully faded.
        val veryStale = TouchModelManager.adjustedOffset(stat(10f, 0f, 60), 100, 120, 100, 4L * hl, hl)[0]
        assertEquals(0f, veryStale, 0.001f)
    }
}
