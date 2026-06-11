/*
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Pure-JVM tests for the gesture decoder controller's caching / rebuild logic (the new glue around
 * the already-tested StatisticalSwipeDecoder). Uses the engine-agnostic `internal decode` overload
 * so no live Keyboard / InputPointers is needed.
 */
package helium314.keyboard.keyboard.internal.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureDecoderControllerTest {

    private fun key(c: Char, x: Float, y: Float) = GestureKey(c.code, x, y, 100f, 100f)

    private val keys = listOf(
        key('c', 100f, 100f), key('a', 250f, 100f), key('t', 400f, 100f),
        key('o', 250f, 200f), key('b', 400f, 200f), key('d', 100f, 200f), key('g', 400f, 300f),
    )

    /** Trace through the given letters' key centers, interpolating along each segment. */
    private fun trace(vararg letters: Char, perSegment: Int = 24): Pair<FloatArray, FloatArray> {
        val centers = letters.map { c -> keys.first { it.code == c.code } }
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        for (i in 0 until centers.size - 1) {
            val a = centers[i]; val b = centers[i + 1]
            for (s in 0 until perSegment) {
                val f = s.toFloat() / perSegment
                xs.add(a.centerX + (b.centerX - a.centerX) * f)
                ys.add(a.centerY + (b.centerY - a.centerY) * f)
            }
        }
        xs.add(centers.last().centerX); ys.add(centers.last().centerY)
        return xs.toFloatArray() to ys.toFloatArray()
    }

    @Test fun `decodes through the controller and serves cache hits`() {
        val controller = GestureDecoderController()
        val (xs, ys) = trace('c', 'a', 't')
        val lexicon = mapOf("cat" to 200, "cab" to 100)
        assertEquals("cat", controller.decode("layoutA", keys, xs, ys, lexicon, lexicon, 5).firstOrNull())
        // Same layout + same lexicon instance: cache hit must still decode correctly.
        assertEquals("cat", controller.decode("layoutA", keys, xs, ys, lexicon, lexicon, 5).firstOrNull())
    }

    @Test fun `lexicon identity change rebuilds the word source`() {
        val controller = GestureDecoderController()
        val (xs, ys) = trace('c', 'a', 't')
        val lex1 = mapOf("cat" to 200)
        assertEquals("cat", controller.decode("L", keys, xs, ys, lex1, lex1, 5).firstOrNull())
        // A new lexicon instance without "cat": the stale word source must not be reused.
        val lex2 = mapOf("cab" to 200)
        assertFalse("cat".let { controller.decode("L", keys, xs, ys, lex2, lex2, 5).contains(it) })
    }

    @Test fun `empty keys, path, or lexicon yields no suggestions`() {
        val controller = GestureDecoderController()
        val (xs, ys) = trace('c', 'a', 't')
        assertTrue(controller.decode("L", emptyList(), xs, ys, mapOf("cat" to 1), "k", 5).isEmpty())
        assertTrue(controller.decode("L", keys, FloatArray(0), FloatArray(0), mapOf("cat" to 1), "k", 5).isEmpty())
        assertTrue(controller.decode("L", keys, xs, ys, emptyMap(), "k", 5).isEmpty())
    }
}
