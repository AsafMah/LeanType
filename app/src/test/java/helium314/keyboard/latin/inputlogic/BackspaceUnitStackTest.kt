// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.inputlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Direct unit tests for [BackspaceUnitStack] (#31): the fragment- / whole-word-backspace
 * length math, previously only exercised indirectly through InputLogic. Pure logic, no
 * Robolectric needed.
 */
class BackspaceUnitStackTest {

    private fun stackWith(vararg boundaries: Int) = BackspaceUnitStack().apply {
        boundaries.forEach { recordComposingBoundary(it) }
    }

    // ---- recordComposingBoundary ----

    @Test fun `record ignores non-positive lengths`() {
        val s = BackspaceUnitStack()
        s.recordComposingBoundary(0)
        s.recordComposingBoundary(-3)
        assertFalse(s.hasComposingBoundaries())
    }

    @Test fun `record dedups the current top`() {
        val s = stackWith(3, 3, 3)
        // Three identical records collapse to one boundary -> one pop empties it.
        assertEquals(0, s.popComposingFragment(3))
        assertEquals(-1, s.popComposingFragment(0))
    }

    @Test fun `clearComposing drops boundaries`() {
        val s = stackWith(2, 5)
        assertTrue(s.hasComposingBoundaries())
        s.clearComposing()
        assertFalse(s.hasComposingBoundaries())
        assertEquals(-1, s.popComposingFragment(5))
    }

    // ---- popComposingFragment ----

    @Test fun `pop on empty returns -1`() {
        assertEquals(-1, BackspaceUnitStack().popComposingFragment(4))
    }

    @Test fun `pop single fragment shrinks to zero`() {
        val s = stackWith(5)
        assertEquals(0, s.popComposingFragment(5))
    }

    @Test fun `pop multi-fragment shrinks to previous boundary`() {
        // Two gesture fragments: "tech"(4) + "nology"(->10). Pop the second -> back to "tech".
        val s = stackWith(4, 10)
        assertEquals(4, s.popComposingFragment(10))
        // Pop again -> empties.
        assertEquals(0, s.popComposingFragment(4))
        assertEquals(-1, s.popComposingFragment(0))
    }

    @Test fun `pop trims stale boundaries past current length`() {
        // Boundary 10 is stale (word already shrank to 7 by other means): trimmed, then the
        // remaining top (4) != currentLen(7) so the defensive fallback returns 4.
        val s = stackWith(4, 10)
        assertEquals(4, s.popComposingFragment(7))
    }

    @Test fun `pop falls back to top boundary when current fragment end unrecorded`() {
        // Top boundary 4 but the word is length 6 (last 2 chars' boundary never recorded):
        // fallback returns the top boundary without popping it.
        val s = stackWith(4)
        assertEquals(4, s.popComposingFragment(6))
        // Boundary not consumed: a pop at the recorded length still empties it.
        assertEquals(0, s.popComposingFragment(4))
    }

    // ---- fragmentLengthsForCommit ----

    @Test fun `commit lengths are deltas between boundaries`() {
        val s = stackWith(4, 10)
        assertEquals(listOf(4, 6), s.fragmentLengthsForCommit(10))
    }

    @Test fun `commit lengths add a trailing tail past the last boundary`() {
        val s = stackWith(4)
        // "tech"(4) + 2 trailing chars with no recorded boundary -> [4, 2].
        assertEquals(listOf(4, 2), s.fragmentLengthsForCommit(6))
    }

    @Test fun `commit lengths for an untracked word are one whole fragment`() {
        assertEquals(listOf(5), BackspaceUnitStack().fragmentLengthsForCommit(5))
    }

    @Test fun `commit lengths for zero length are empty`() {
        assertEquals(emptyList<Int>(), stackWith(4).fragmentLengthsForCommit(0))
    }

    @Test fun `commit lengths ignore boundaries past current length`() {
        val s = stackWith(4, 10)
        // Committing at length 7: boundary 10 is out of range, tail = 7-4 = 3.
        assertEquals(listOf(4, 3), s.fragmentLengthsForCommit(7))
    }

    // ---- committed side ----

    @Test fun `setCommitted stores length and a defensive copy of fragments`() {
        val s = BackspaceUnitStack()
        val src = arrayListOf(4, 7)
        s.setCommitted(11, src)
        assertEquals(11, s.committedLength())
        assertEquals(listOf(4, 7), s.copyCommittedFragmentLengths())
        // Mutating the source after the call must not leak into the stack.
        src.add(99)
        assertEquals(listOf(4, 7), s.copyCommittedFragmentLengths())
        // The returned copy is defensive too.
        s.copyCommittedFragmentLengths().add(99)
        assertEquals(listOf(4, 7), s.copyCommittedFragmentLengths())
    }

    @Test fun `setCommittedFragmentLengths replaces fragments but keeps length`() {
        val s = BackspaceUnitStack()
        s.setCommitted(11, listOf(4, 7))
        s.setCommittedFragmentLengths(listOf(4)) // popped the trailing fragment off the editor
        assertEquals(listOf(4), s.copyCommittedFragmentLengths())
        assertEquals(11, s.committedLength())
    }

    @Test fun `clearCommitted resets length and fragments`() {
        val s = BackspaceUnitStack()
        s.setCommitted(11, listOf(4, 7))
        s.clearCommitted()
        assertEquals(0, s.committedLength())
        assertEquals(emptyList<Int>(), s.copyCommittedFragmentLengths())
    }

    @Test fun `composing and committed sides are independent`() {
        val s = stackWith(4, 10)
        s.setCommitted(11, listOf(4, 7))
        s.clearComposing()
        // Clearing composing left the committed side intact.
        assertEquals(11, s.committedLength())
        assertEquals(listOf(4, 7), s.copyCommittedFragmentLengths())
    }
}
