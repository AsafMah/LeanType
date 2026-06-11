// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryGestureWordSourceTest {

    @Test fun `frequency is the stored probability normalized by 255`() {
        val src = DictionaryGestureWordSource(mapOf("cat" to 255, "dog" to 128, "ox" to 0))
        assertEquals(1.0f, src.getFrequency("cat"), 0.0001f)
        assertEquals(128f / 255f, src.getFrequency("dog"), 0.0001f)
        assertEquals(0.0f, src.getFrequency("ox"), 0.0001f)
    }

    @Test fun `missing word has zero frequency`() {
        val src = DictionaryGestureWordSource(mapOf("cat" to 255))
        assertEquals(0.0f, src.getFrequency("missing"), 0.0001f)
    }

    @Test fun `getWords returns every lexicon key`() {
        val src = DictionaryGestureWordSource(mapOf("cat" to 1, "dog" to 2, "fish" to 3))
        assertEquals(setOf("cat", "dog", "fish"), src.getWords().toSet())
    }
}
