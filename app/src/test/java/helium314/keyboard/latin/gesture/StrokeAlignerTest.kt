package helium314.keyboard.latin.gesture

import helium314.keyboard.latin.common.InputPointers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [StrokeAligner]'s merge contract.
 *
 * The invariants asserted here are not stylistic — each maps to a measured property of the native
 * decoder (`jni/tests/replay/two_pointer_track_test.cpp`, `docs/TWO_THUMB_TEMPORAL_ALIGNMENT.md`):
 *
 *  - track 0 must be non-empty or `Suggest::initializeSearch` returns zero suggestions;
 *  - only pointer ids 0 and 1 reach a decoder track at all;
 *  - timestamps must be globally monotonic, because the decoder's speed/beeline features walk the
 *    raw arrays across the track boundary and a decreasing timestamp yields a negative duration.
 */
class StrokeAlignerTest {

    private fun pointers(vararg triples: Triple<Int, Int, Int>, id: Int = 0) =
        InputPointers(16).apply {
            triples.forEach { (x, y, t) -> addPointer(x, y, id, t) }
        }

    private fun base() = pointers(
        Triple(10, 10, 0),
        Triple(20, 12, 0),
        Triple(30, 14, 0), // tap-sourced coords carry a time=0 sentinel
    )

    private fun current() = pointers(
        Triple(100, 50, 1000),
        Triple(120, 55, 1025),
        Triple(140, 60, 1050),
    )

    private fun InputPointers.idsList() = pointerIds.take(pointerSize)
    private fun InputPointers.timesList() = times.take(pointerSize)
    private fun InputPointers.xsList() = xCoordinates.take(pointerSize)

    // ---- shared invariants -------------------------------------------------

    @Test
    fun `connector mode keeps every point on track zero`() {
        val out = InputPointers(16)
        StrokeAligner.merge(out, base(), current(),
            StrokeAligner.Params(StrokeAligner.Mode.CONNECTOR, 25, 60))

        assertEquals(6, out.pointerSize)
        assertTrue(out.idsList().all { it == 0 }, "connector mode must not split tracks")
    }

    @Test
    fun `dual pointer mode puts the base on track zero and the new stroke on track one`() {
        val out = InputPointers(16)
        StrokeAligner.merge(out, base(), current(),
            StrokeAligner.Params(StrokeAligner.Mode.DUAL_POINTER, 25, 60))

        assertEquals(listOf(0, 0, 0, 1, 1, 1), out.idsList())
    }

    @Test
    fun `no mode ever emits a pointer id the decoder would discard`() {
        for (mode in StrokeAligner.Mode.entries) {
            val out = InputPointers(16)
            StrokeAligner.merge(out, base(), current(), StrokeAligner.Params(mode, 25, 60))
            assertTrue(out.idsList().all { it == 0 || it == 1 },
                "$mode emitted an id outside [0,1]; those reach no decoder track")
        }
    }

    @Test
    fun `track zero is always populated so the search does not bail out`() {
        for (mode in StrokeAligner.Mode.entries) {
            val out = InputPointers(16)
            StrokeAligner.merge(out, base(), current(), StrokeAligner.Params(mode, 25, 60))
            assertTrue(out.idsList().contains(0),
                "$mode left track 0 empty; Suggest::initializeSearch would return no suggestions")
        }
    }

    @Test
    fun `timestamps are globally monotonic in every mode`() {
        for (mode in StrokeAligner.Mode.entries) {
            val out = InputPointers(16)
            StrokeAligner.merge(out, base(), current(), StrokeAligner.Params(mode, 25, 60))
            val times = out.timesList()
            times.zipWithNext().forEach { (a, b) ->
                assertTrue(b >= a, "$mode produced a decreasing timestamp ($a -> $b); " +
                        "the decoder's speed features would compute a negative duration")
            }
        }
    }

    @Test
    fun `base timestamps are re-synthesised to land just before the new stroke`() {
        val out = InputPointers(16)
        StrokeAligner.merge(out, base(), current(),
            StrokeAligner.Params(StrokeAligner.Mode.CONNECTOR, 25, 60))

        // current starts at 1000, gap 60 => base ends at 940, interval 25 over 3 points.
        assertEquals(listOf(890, 915, 940, 1000, 1025, 1050), out.timesList())
    }

    @Test
    fun `geometry is preserved and ordered base-then-current`() {
        val out = InputPointers(16)
        StrokeAligner.merge(out, base(), current(),
            StrokeAligner.Params(StrokeAligner.Mode.DUAL_POINTER, 25, 60))

        assertEquals(listOf(10, 20, 30, 100, 120, 140), out.xsList())
    }

    // ---- knobs -------------------------------------------------------------

    @Test
    fun `interval and gap knobs move the base timeline in dual pointer mode`() {
        val out = InputPointers(16)
        StrokeAligner.merge(out, base(), current(),
            StrokeAligner.Params(StrokeAligner.Mode.DUAL_POINTER, 10, 100))

        // gap 100 => base ends at 900; interval 10 over 3 points.
        assertEquals(listOf(880, 890, 900, 1000, 1025, 1050), out.timesList())
    }

    @Test
    fun `non-positive knobs are clamped so the base cannot go non-monotonic`() {
        val params = StrokeAligner.Params(StrokeAligner.Mode.CONNECTOR, 0, -5)
        assertTrue(params.basePointIntervalMs >= 1)
        assertTrue(params.gapBeforeNewMs >= 1)

        val out = InputPointers(16)
        StrokeAligner.merge(out, base(), current(), params)
        out.timesList().zipWithNext().forEach { (a, b) -> assertTrue(b >= a) }
    }

    @Test
    fun `defaults reproduce the historical connector behaviour`() {
        val defaults = StrokeAligner.Params.defaults()
        assertEquals(StrokeAligner.Mode.CONNECTOR, defaults.mode)
        assertEquals(25, defaults.basePointIntervalMs)
        assertEquals(60, defaults.gapBeforeNewMs)
    }

    @Test
    fun `unknown or missing pref values fall back to connector`() {
        assertEquals(StrokeAligner.Mode.CONNECTOR, StrokeAligner.Mode.fromPrefValue("connector"))
        assertEquals(StrokeAligner.Mode.CONNECTOR, StrokeAligner.Mode.fromPrefValue("nonsense"))
        assertEquals(StrokeAligner.Mode.CONNECTOR, StrokeAligner.Mode.fromPrefValue(null))
        assertEquals(StrokeAligner.Mode.DUAL_POINTER,
            StrokeAligner.Mode.fromPrefValue("dual_pointer"))
    }

    // ---- degenerate inputs -------------------------------------------------

    @Test
    fun `mode changes ids only, never geometry or timing`() {
        // The Java SwipeGestureEngine fallback (used when the native lib is absent, e.g. the
        // offlinelite flavor) flattens all points into one path and ignores pointer ids, so
        // DUAL_POINTER must be invisible to it.
        val connector = InputPointers(16)
        val dual = InputPointers(16)
        StrokeAligner.merge(connector, base(), current(),
            StrokeAligner.Params(StrokeAligner.Mode.CONNECTOR, 25, 60))
        StrokeAligner.merge(dual, base(), current(),
            StrokeAligner.Params(StrokeAligner.Mode.DUAL_POINTER, 25, 60))

        assertEquals(connector.pointerSize, dual.pointerSize)
        assertEquals(connector.xsList(), dual.xsList())
        assertEquals(connector.ys().take(connector.pointerSize), dual.ys().take(dual.pointerSize))
        assertEquals(connector.timesList(), dual.timesList())
        assertTrue(connector.idsList() != dual.idsList(), "only the ids should differ")
    }

    private fun InputPointers.ys() = yCoordinates.toList()

    @Test
    fun `connector mode ignores the timing knobs so it always means historical behaviour`() {
        // The sliders are only shown for DUAL_POINTER; tuning them there and switching back must
        // not silently change what "one joined trail" does.
        val tuned = InputPointers(16)
        StrokeAligner.merge(tuned, base(), current(),
            StrokeAligner.Params(StrokeAligner.Mode.CONNECTOR, 5, 200))

        assertEquals(listOf(890, 915, 940, 1000, 1025, 1050), tuned.timesList())
    }

    @Test
    fun `dual pointer preserves an already multi-pointer current stroke`() {
        // A genuinely simultaneous two-thumb stroke already occupies both decoder tracks;
        // flattening it onto track 1 would destroy that structure.
        val simultaneous = InputPointers(8).apply {
            addPointer(100, 50, 0, 1000)
            addPointer(300, 50, 1, 1005)
            addPointer(120, 55, 0, 1025)
            addPointer(320, 55, 1, 1030)
        }
        val out = InputPointers(16)
        StrokeAligner.merge(out, base(), simultaneous,
            StrokeAligner.Params(StrokeAligner.Mode.DUAL_POINTER, 25, 60))

        assertEquals(listOf(0, 0, 0, 0, 1, 0, 1), out.idsList())
        assertTrue(out.idsList().all { it == 0 || it == 1 })
        out.timesList().zipWithNext().forEach { (a, b) -> assertTrue(b >= a) }
    }

    @Test
    fun `an empty base copies the current stroke through untouched`() {
        // This is the ordinary single-swipe path, including genuinely simultaneous two-thumb
        // input, whose real MotionEvent pointer ids must survive unchanged.
        val simultaneous = InputPointers(16).apply {
            addPointer(1, 1, 0, 10)
            addPointer(2, 2, 1, 12)
        }
        val out = InputPointers(16)
        StrokeAligner.merge(out, InputPointers(4), simultaneous,
            StrokeAligner.Params(StrokeAligner.Mode.DUAL_POINTER, 25, 60))

        assertEquals(2, out.pointerSize)
        assertEquals(listOf(0, 1), out.idsList())
        assertEquals(listOf(10, 12), out.timesList())
    }

    @Test
    fun `an empty current stroke yields just the base`() {
        val out = InputPointers(16)
        StrokeAligner.merge(out, base(), InputPointers(4),
            StrokeAligner.Params(StrokeAligner.Mode.DUAL_POINTER, 25, 60))

        assertEquals(3, out.pointerSize)
        assertTrue(out.idsList().contains(0))
    }

    @Test
    fun `merging into a non-empty output resets it first`() {
        val out = InputPointers(16).apply { addPointer(999, 999, 1, 999) }
        StrokeAligner.merge(out, base(), current(), StrokeAligner.Params.defaults())

        assertEquals(6, out.pointerSize, "stale points must not survive the merge")
        assertEquals(10, out.xsList().first())
    }

    @Test
    fun `null params behave as defaults`() {
        val withNull = InputPointers(16)
        val withDefaults = InputPointers(16)
        StrokeAligner.merge(withNull, base(), current(), null)
        StrokeAligner.merge(withDefaults, base(), current(), StrokeAligner.Params.defaults())

        assertEquals(withDefaults.timesList(), withNull.timesList())
        assertEquals(withDefaults.idsList(), withNull.idsList())
    }

    /**
     * Invariant 4: a given raw index must not change track as the stroke grows, because
     * `checkAndReturnIsContinuousSuggestionPossible` compares x/y/time but not pointer ids.
     */
    @Test
    fun `pointer ids for existing points are stable as the stroke grows`() {
        val params = StrokeAligner.Params(StrokeAligner.Mode.DUAL_POINTER, 25, 60)
        val first = InputPointers(16)
        StrokeAligner.merge(first, base(), current(), params)

        val grown = current().apply { addPointer(160, 65, 0, 1075) }
        val second = InputPointers(16)
        StrokeAligner.merge(second, base(), grown, params)

        val firstIds = first.idsList()
        val secondIds = second.idsList()
        assertEquals(firstIds, secondIds.take(firstIds.size),
            "an already-seen point changed decoder track mid-gesture")
        assertEquals(first.timesList(), second.timesList().take(firstIds.size),
            "an already-seen point was re-timed mid-gesture")
    }
}
