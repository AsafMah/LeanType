// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.inputlogic

import helium314.keyboard.latin.inputlogic.SpacingGateModel.Gate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [SpacingGateModel.decide] and [SpacingGateDecision].
 *
 * The model is pure/stateless — every case is table-driven with explicit inputs; no Android
 * dependencies, no InputLogic instantiation required.
 */
class SpacingGateModelTest {

    private val LOW = 0.1f   // default threshold from Defaults.PREF_SPACING_LOW_THRESHOLD

    // ---- policy-disabled fast path ----

    @Test fun `policy off always returns NONE regardless of signals`() {
        assertEquals(Gate.NONE, decide(off = true, complete = true,  score = 0.0f))
        assertEquals(Gate.NONE, decide(off = true, complete = true,  score = 0.5f))
        assertEquals(Gate.NONE, decide(off = true, complete = false, score = 0.0f))
        assertEquals(Gate.NONE, decide(off = true, complete = false, score = 1.0f))
    }

    // ---- incomplete word (neither gate) ----

    @Test fun `incomplete word returns NONE even if policy is on`() {
        assertEquals(Gate.NONE, decide(complete = false, score = 0.0f))
        assertEquals(Gate.NONE, decide(complete = false, score = LOW))
        assertEquals(Gate.NONE, decide(complete = false, score = 1.0f))
    }

    // ---- Gate A: complete AND score <= threshold ----

    @Test fun `zero prefix-richness triggers Gate A (instant commit)`() {
        assertEquals(Gate.INSTANT, decide(complete = true, score = 0.0f))
    }

    @Test fun `score exactly at threshold triggers Gate A`() {
        assertEquals(Gate.INSTANT, decide(complete = true, score = LOW))
    }

    @Test fun `score just below threshold triggers Gate A`() {
        val just = LOW - 0.001f
        assertEquals(Gate.INSTANT, decide(complete = true, score = just))
    }

    // ---- Gate B: complete AND score > threshold ----

    @Test fun `score just above threshold triggers Gate B (pause)`() {
        val just = LOW + 0.001f
        assertEquals(Gate.PAUSE, decide(complete = true, score = just))
    }

    @Test fun `fully prefix-rich complete word triggers Gate B`() {
        assertEquals(Gate.PAUSE, decide(complete = true, score = 1.0f))
    }

    @Test fun `mid-range prefix-rich complete word triggers Gate B`() {
        assertEquals(Gate.PAUSE, decide(complete = true, score = 0.5f))
    }

    // ---- threshold sensitivity ----

    @Test fun `custom high threshold raises the bar for Gate B`() {
        // threshold = 0.5: score 0.5 still INSTANT; score 0.51 → PAUSE
        assertEquals(Gate.INSTANT, decide(complete = true, score = 0.5f,  threshold = 0.5f))
        assertEquals(Gate.PAUSE,   decide(complete = true, score = 0.51f, threshold = 0.5f))
    }

    @Test fun `threshold zero means only zero score is Gate A`() {
        assertEquals(Gate.INSTANT, decide(complete = true, score = 0.0f,  threshold = 0.0f))
        assertEquals(Gate.PAUSE,   decide(complete = true, score = 0.001f, threshold = 0.0f))
    }

    @Test fun `threshold one means all complete words are Gate A`() {
        assertEquals(Gate.INSTANT, decide(complete = true, score = 0.0f, threshold = 1.0f))
        assertEquals(Gate.INSTANT, decide(complete = true, score = 0.5f, threshold = 1.0f))
        assertEquals(Gate.INSTANT, decide(complete = true, score = 1.0f, threshold = 1.0f))
    }

    // ---- boundary: incomplete + zero score still NONE ----

    @Test fun `incomplete word with zero prefix-richness stays NONE`() {
        assertEquals(Gate.NONE, decide(complete = false, score = 0.0f))
    }

    // ---- SpacingGateDecision.evaluate mirrors decide ----

    @Test fun `SpacingGateDecision evaluate matches decide for all gates`() {
        for ((complete, score, threshold, expected) in gateTable()) {
            val decision = SpacingGateDecision.evaluate(
                policyEnabled = true, complete = complete,
                prefixRichScore = score, lowThreshold = threshold
            )
            assertEquals("complete=$complete score=$score threshold=$threshold",
                expected, decision.gate)
            assertEquals(complete,   decision.complete)
            assertEquals(score,      decision.prefixRichScore, 1e-7f)
            assertEquals(threshold,  decision.lowThreshold,    1e-7f)
        }
    }

    @Test fun `SpacingGateDecision evaluate with policy off returns NONE`() {
        val d = SpacingGateDecision.evaluate(
            policyEnabled = false, complete = true, prefixRichScore = 0.0f, lowThreshold = LOW
        )
        assertEquals(Gate.NONE, d.gate)
    }

    @Test fun `SpacingGateDecision captures all inputs faithfully`() {
        val d = SpacingGateDecision.evaluate(
            policyEnabled = true, complete = true, prefixRichScore = 0.3f, lowThreshold = 0.2f
        )
        assertNotNull(d)
        assertEquals(Gate.PAUSE, d.gate)
        assertEquals(true,  d.complete)
        assertEquals(0.3f,  d.prefixRichScore, 1e-7f)
        assertEquals(0.2f,  d.lowThreshold,    1e-7f)
    }

    // ---- helpers ----

    /** Delegate with named parameters for readability; `off` inverts `policyEnabled`. */
    private fun decide(
        complete: Boolean,
        score: Float,
        threshold: Float = LOW,
        off: Boolean = false,
    ): Gate = SpacingGateModel.decide(
        policyEnabled = !off,
        complete = complete,
        prefixRichScore = score,
        lowThreshold = threshold,
    )

    /** Table of (complete, score, threshold, expectedGate) for the evaluate-roundtrip test. */
    private fun gateTable(): List<Triple4> = listOf(
        Triple4(false, 0.0f,  LOW,  Gate.NONE),
        Triple4(false, 0.5f,  LOW,  Gate.NONE),
        Triple4(true,  0.0f,  LOW,  Gate.INSTANT),
        Triple4(true,  LOW,   LOW,  Gate.INSTANT),
        Triple4(true,  LOW + 0.01f, LOW, Gate.PAUSE),
        Triple4(true,  1.0f,  LOW,  Gate.PAUSE),
    )

    private data class Triple4(
        val complete: Boolean,
        val score: Float,
        val threshold: Float,
        val gate: Gate,
    )
}
