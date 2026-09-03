package helium314.keyboard.keyboard.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Pins the pointer-id → decoder-track-slot mapping.
 *
 * The native gesture decoder keeps exactly two per-pointer tracks and seeds track *i* with pointer
 * id *i* (`jni/src/defines.h` MAX_POINTER_COUNT_G, `dic_traverse_session.cpp`). Two failure modes
 * follow from that in-tree preprocessing:
 *
 *  - no point carrying id 0 ⇒ track 0 unused ⇒ `Suggest::initializeSearch` returns early ⇒
 *    **zero suggestions**;
 *  - any id >= 2 reaches no track at all.
 *
 * Android hands out the lowest free pointer index, so a stroke's raw id is not guaranteed to be 0.
 */
class PointerIdNormalizerTest {

    @Test
    fun `single finger keeps slot zero`() {
        val n = PointerIdNormalizer()
        assertEquals(0, n.slotFor(0))
        assertEquals(0, n.slotFor(0))
        assertEquals(1, n.trackedPointerCount())
    }

    @Test
    fun `two thumbs in order map to slots zero and one`() {
        val n = PointerIdNormalizer()
        assertEquals(0, n.slotFor(0))
        assertEquals(1, n.slotFor(1))
        // Stable across repeated lookups — ids must not drift mid-gesture, because
        // checkAndReturnIsContinuousSuggestionPossible does not compare pointer ids.
        assertEquals(0, n.slotFor(0))
        assertEquals(1, n.slotFor(1))
        assertEquals(2, n.trackedPointerCount())
    }

    /**
     * The regression this class exists for: thumb A goes down (id 0), thumb B goes down (id 1),
     * thumb A lifts, and thumb B swipes on alone still carrying raw id 1. Before normalisation
     * every aggregated point carried id 1, track 0 stayed empty and the gesture produced no
     * suggestions at all.
     */
    @Test
    fun `gesture whose only pointer is raw id one still anchors track zero`() {
        val n = PointerIdNormalizer()
        val slot = n.slotFor(1)
        assertEquals(0, slot, "the first pointer to contribute must anchor track 0")
        assertNotEquals(1, slot)
    }

    @Test
    fun `first seen order decides the slot, not the raw id value`() {
        val n = PointerIdNormalizer()
        assertEquals(0, n.slotFor(3))
        assertEquals(1, n.slotFor(2))
        assertEquals(0, n.slotFor(3))
        assertEquals(1, n.slotFor(2))
    }

    /** A third finger still falls outside the decoder's two tracks — unchanged behaviour. */
    @Test
    fun `third distinct pointer gets a slot the decoder will ignore`() {
        val n = PointerIdNormalizer()
        n.slotFor(0)
        n.slotFor(1)
        assertEquals(2, n.slotFor(7))
        assertEquals(3, n.trackedPointerCount())
    }

    @Test
    fun `reset forgets the mapping so the next gesture starts at slot zero`() {
        val n = PointerIdNormalizer()
        n.slotFor(4)
        n.slotFor(9)
        assertEquals(2, n.trackedPointerCount())
        n.reset()
        assertEquals(0, n.trackedPointerCount())
        assertEquals(0, n.slotFor(9), "a fresh gesture must re-anchor track 0")
    }

    @Test
    fun `passes ids through once more than eight distinct pointers appear`() {
        val n = PointerIdNormalizer()
        for (i in 0 until 8) {
            assertEquals(i, n.slotFor(100 + i))
        }
        // Beyond the tracked capacity we stop renumbering; the decoder discards these anyway.
        assertEquals(500, n.slotFor(500))
        // Already-tracked ids keep their slots.
        assertEquals(0, n.slotFor(100))
    }
}
