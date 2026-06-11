/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Pure-JVM tests for LeanType's statistical swipe decoder. No Robolectric / native engine needed:
 * the decoder is engine-agnostic, which is what lets us cover gesture recognition in unit tests
 * (previously a coverage gap, since the old path required the native library).
 */
package helium314.keyboard.keyboard.internal.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticalSwipeDecoderTest {

    // A small keyboard. Letters needed by the test lexicon, on a coarse grid (100x100 keys).
    private val keys = listOf(
        key('c', 100f, 100f), key('a', 250f, 100f), key('t', 400f, 100f), key('g', 550f, 100f),
        key('d', 100f, 200f), key('o', 250f, 200f), key('r', 400f, 200f), key('b', 550f, 200f),
        key('l', 250f, 300f),
    )

    private val lexicon = listOf("cat", "cot", "car", "cab", "dog", "all")

    private val wordSource = object : GestureWordSource {
        override fun getWords() = lexicon
        // Equal frequency so the geometry channels decide the ranking.
        override fun getFrequency(word: String) = 0.5f
    }

    private fun decoder(): StatisticalSwipeDecoder = StatisticalSwipeDecoder().apply {
        setKeys(keys)
        setWordSource(wordSource)
    }

    private fun key(c: Char, x: Float, y: Float) = GestureKey(c.code, x, y, 100f, 100f)

    /** A realistic swipe: trace through the given letters' centers, interpolating along segments. */
    private fun trace(vararg letters: Char, perSegment: Int = 24): Pair<FloatArray, FloatArray> {
        val centers = letters.map { c -> keys.first { it.code == c.code } }
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        for (i in 0 until centers.size - 1) {
            val a = centers[i]; val b = centers[i + 1]
            for (s in 0 until perSegment) {
                val f = s.toFloat() / perSegment
                xs.add(a.centerX + (b.centerX - a.centerX) * f)
                ys.add(a.centerY + (b.centerY - a.centerY) * f)
            }
        }
        val last = centers.last()
        xs.add(last.centerX); ys.add(last.centerY)
        return xs.toFloatArray() to ys.toFloatArray()
    }

    @Test fun `straight c-a-t swipe decodes to cat over cot`() {
        val (xs, ys) = trace('c', 'a', 't')
        val out = decoder().decode(xs, ys, maxSuggestions = 5)
        assertTrue("expected suggestions, got none", out.isNotEmpty())
        assertEquals("cat", out.first())
        assertTrue("cot should still be a candidate", out.contains("cot"))
    }

    @Test fun `V-shaped c-o-t swipe decodes to cot over cat`() {
        val (xs, ys) = trace('c', 'o', 't')
        val out = decoder().decode(xs, ys, maxSuggestions = 5)
        assertEquals("cot", out.first())
    }

    @Test fun `tap anchor excludes words missing the anchored letter`() {
        val (xs, ys) = trace('c', 'a', 't') // geometry favors "cat"
        // Anchor a committed tap on 'o': only c..t words containing 'o' survive -> "cot", not "cat".
        val out = decoder().decode(xs, ys, maxSuggestions = 5, anchors = listOf(GestureAnchor('o'.code)))
        assertFalse("cat lacks the anchored 'o' and must be pruned", out.contains("cat"))
        assertTrue("cot contains the anchored 'o'", out.contains("cot"))
    }

    @Test fun `anchor on absent letter yields no candidates`() {
        val (xs, ys) = trace('c', 'a', 't')
        val out = decoder().decode(xs, ys, maxSuggestions = 5, anchors = listOf(GestureAnchor('z'.code)))
        assertTrue("no lexicon word contains 'z'", out.isEmpty())
    }

    @Test fun `ideal gesture has one variant per word and two for a doubled letter`() {
        val keysByChar = keys.associateBy { it.code }
        assertEquals(1, SwipeGesture.generateIdealGestures("cat", keysByChar).size)
        // "all" repeats 'l' -> a plain variant plus a with-loops variant.
        assertEquals(2, SwipeGesture.generateIdealGestures("all", keysByChar).size)
    }

    @Test fun `normalizeByBoxSide centers the gesture and bounds its longest side`() {
        val g = SwipeGesture()
        g.addPoint(100f, 100f); g.addPoint(300f, 100f); g.addPoint(300f, 200f)
        val n = g.normalizeByBoxSide()
        var maxAbs = 0f
        for (i in 0 until 3) maxAbs = maxOf(maxAbs, kotlin.math.abs(n.getX(i)), kotlin.math.abs(n.getY(i)))
        // Longest side maps to ~1 unit, centered on the origin -> coordinates stay within ~[-1, 1].
        assertTrue("normalized coords should be bounded, was $maxAbs", maxAbs <= 1.0f)
    }

    @Test fun `empty path returns no suggestions`() {
        val out = decoder().decode(FloatArray(0), FloatArray(0), 5)
        assertTrue(out.isEmpty())
    }
}
