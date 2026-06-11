// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [Suggest]'s gesture-backend selection (the built-in decoder vs the native library). */
class SuggestBatchSourceTest {

    @Test fun `built-in selected and it produced results uses built-in regardless of native lib`() {
        assertEquals(GestureBatchSource.BUILTIN, chooseGestureBatchSource(useBuiltinDecoder = true, builtinProducedResults = true, haveNativeGestureLib = false))
        assertEquals(GestureBatchSource.BUILTIN, chooseGestureBatchSource(useBuiltinDecoder = true, builtinProducedResults = true, haveNativeGestureLib = true))
    }

    @Test fun `built-in selected, no results, no native lib does NOT fall back (empty)`() {
        assertEquals(GestureBatchSource.EMPTY, chooseGestureBatchSource(useBuiltinDecoder = true, builtinProducedResults = false, haveNativeGestureLib = false))
    }

    @Test fun `built-in selected, no results, native lib present falls back to native`() {
        assertEquals(GestureBatchSource.NATIVE, chooseGestureBatchSource(useBuiltinDecoder = true, builtinProducedResults = false, haveNativeGestureLib = true))
    }

    @Test fun `built-in not selected always uses native`() {
        assertEquals(GestureBatchSource.NATIVE, chooseGestureBatchSource(useBuiltinDecoder = false, builtinProducedResults = false, haveNativeGestureLib = false))
        assertEquals(GestureBatchSource.NATIVE, chooseGestureBatchSource(useBuiltinDecoder = false, builtinProducedResults = true, haveNativeGestureLib = true))
    }
}
