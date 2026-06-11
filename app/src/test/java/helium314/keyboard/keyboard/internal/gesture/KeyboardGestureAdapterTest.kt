// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal.gesture

import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

/** Verifies that only letter keys map to gesture keys, lowercased and centered. */
class KeyboardGestureAdapterTest {

    private fun mockKey(code: Int, x: Int, y: Int, w: Int = 100, h: Int = 100, modifier: Boolean = false): Key {
        val k = Mockito.mock(Key::class.java)
        Mockito.`when`(k.code).thenReturn(code)
        Mockito.`when`(k.x).thenReturn(x)
        Mockito.`when`(k.y).thenReturn(y)
        Mockito.`when`(k.width).thenReturn(w)
        Mockito.`when`(k.height).thenReturn(h)
        Mockito.`when`(k.isModifier).thenReturn(modifier)
        return k
    }

    @Test fun `keeps only letter keys, lowercased, with centered coordinates`() {
        val keys = listOf(
            mockKey('A'.code, 0, 0),                    // uppercase letter -> included as 'a'
            mockKey('b'.code, 100, 0),                  // letter
            mockKey('1'.code, 200, 0),                  // digit -> excluded (not a letter)
            mockKey(-5, 300, 0),                        // functional / negative code -> excluded
            mockKey('c'.code, 400, 0, modifier = true), // modifier -> excluded
        )
        val keyboard = Mockito.mock(Keyboard::class.java)
        Mockito.`when`(keyboard.sortedKeys).thenReturn(keys)

        val result = KeyboardGestureAdapter.toGestureKeys(keyboard)

        assertEquals(setOf('a'.code, 'b'.code), result.map { it.code }.toSet())
        val a = result.first { it.code == 'a'.code }
        assertEquals(50f, a.centerX, 0.001f) // x(0) + width(100)/2
        assertEquals(50f, a.centerY, 0.001f) // y(0) + height(100)/2
        assertEquals(100f, a.width, 0.001f)
    }
}
