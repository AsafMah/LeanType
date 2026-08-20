package helium314.keyboard.latin.gesture

import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.latin.common.InputPointers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the synthetic prefix trail, including the tap → micro-stroke promotion.
 *
 * Recognition *quality* is not testable here (there is no gesture policy in this tree), so these
 * assert the geometry contract only: a tap becomes a stroke with a vertex, multi-letter prefixes
 * are densified, and unbuildable inputs return null so the caller falls back to the raw trail.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class IdealPrefixTrailBuilderTest {

    /** A single row of 100x100 letter keys, laid out left to right. */
    private fun keyboardFor(letters: String): Keyboard {
        val params = KeyboardParams().apply {
            mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardId.ELEMENT_ALPHABET)
            mOccupiedWidth = letters.length * 100
            mOccupiedHeight = 100
            mBaseWidth = mOccupiedWidth
            mBaseHeight = mOccupiedHeight
            mMostCommonKeyWidth = 100
            mMostCommonKeyHeight = 100
            GRID_WIDTH = letters.length
            GRID_HEIGHT = 1
        }
        letters.forEachIndexed { index, letter ->
            params.onAddKey(Key(
                letter.toString(), null, letter.code, null, null,
                0, Key.BACKGROUND_TYPE_NORMAL,
                index * 100, 0, 100, 100, 0, 0,
            ))
        }
        return Keyboard(params)
    }

    private fun keyboard() = keyboardFor("techsplo")

    private fun InputPointers.xs() = xCoordinates.take(pointerSize)
    private fun InputPointers.ys() = yCoordinates.take(pointerSize)
    private fun InputPointers.ids() = pointerIds.take(pointerSize)

    @Test
    fun `null or empty input yields null so the caller keeps the raw trail`() {
        val kb = keyboard()
        assertNull(IdealPrefixTrailBuilder.build(null, kb))
        assertNull(IdealPrefixTrailBuilder.build("", kb))
        assertNull(IdealPrefixTrailBuilder.build("hello", null))
    }

    @Test
    fun `a word with no mappable letters yields null`() {
        assertNull(IdealPrefixTrailBuilder.build("123", keyboard()))
    }

    /** The tap-promotion claim: one letter must come back as a stroke, not a point. */
    @Test
    fun `a single letter prefix becomes an out-and-back micro-stroke`() {
        val trail = assertNotNull(IdealPrefixTrailBuilder.build("s", keyboard()))

        assertEquals(4, trail.pointerSize, "a tap must be promoted to a 4-point micro-stroke")
        val xs = trail.xs()
        val ys = trail.ys()
        // Out and back around the key centre: left, centre, right, centre.
        assertEquals(xs[1], xs[3], "the micro-stroke must return to the key centre")
        assertTrue(xs[0] < xs[1], "first point must sit left of centre")
        assertTrue(xs[2] > xs[1], "third point must sit right of centre")
        assertTrue(ys.all { it == ys[0] }, "the micro-arc stays on one row")
        // A real vertex, not a degenerate zero-length wiggle.
        assertTrue(xs[2] - xs[0] > 1, "the micro-stroke must have non-trivial extent")
    }

    @Test
    fun `a multi letter prefix is densified beyond one point per key`() {
        val trail = assertNotNull(IdealPrefixTrailBuilder.build("tech", keyboard()))
        assertTrue(trail.pointerSize > 4,
            "expected interpolated samples between key centres, got ${trail.pointerSize}")
    }

    @Test
    fun `every synthesised point is on the base track`() {
        for (word in listOf("s", "tech", "hello")) {
            val trail = assertNotNull(IdealPrefixTrailBuilder.build(word, keyboard()))
            assertTrue(trail.ids().all { it == StrokeAligner.BASE_POINTER_ID },
                "$word: the prefix trail must stay on decoder track 0")
        }
    }

    @Test
    fun `punctuation is skipped but an unmappable letter forces a fallback`() {
        // Apostrophes legitimately have no place on the trail.
        val withApostrophe = assertNotNull(IdealPrefixTrailBuilder.build("to'p", keyboard()))
        val without = assertNotNull(IdealPrefixTrailBuilder.build("top", keyboard()))
        assertEquals(without.pointerSize, withApostrophe.pointerSize)
        assertEquals(without.xs(), withApostrophe.xs())

        // A letter that isn't on this keyboard would leave a hole in the synthetic path, which is
        // worse than the raw trail — so the builder bails out and the caller falls back.
        assertNull(IdealPrefixTrailBuilder.build("tzch", keyboard()))
        assertNull(IdealPrefixTrailBuilder.build("téch", keyboard()))
    }

    @Test
    fun `case does not change the produced geometry`() {
        val lower = assertNotNull(IdealPrefixTrailBuilder.build("tech", keyboard()))
        val upper = assertNotNull(IdealPrefixTrailBuilder.build("TECH", keyboard()))
        assertEquals(lower.xs(), upper.xs())
        assertEquals(lower.ys(), upper.ys())
    }

    /** The whole point of the builder: feed StrokeAligner a stroke-like base. */
    @Test
    fun `the synthesised tap trail survives a StrokeAligner merge as a real stroke`() {
        val trail = assertNotNull(IdealPrefixTrailBuilder.build("s", keyboard()))
        val current = InputPointers(8).apply {
            addPointer(500, 100, 0, 1000)
            addPointer(520, 105, 0, 1025)
        }
        val out = InputPointers(16)
        StrokeAligner.merge(out, trail, current,
            StrokeAligner.Params(StrokeAligner.Mode.DUAL_POINTER, 25, 60))

        assertEquals(6, out.pointerSize)
        assertEquals(listOf(0, 0, 0, 0, 1, 1), out.ids())
        out.times.take(out.pointerSize).zipWithNext().forEach { (a, b) ->
            assertTrue(b >= a, "merged trail must stay monotonic in time")
        }
    }
}
