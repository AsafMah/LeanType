/*
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Drives the native-free statistical swipe decoder from the live gesture pipeline.
 */
package helium314.keyboard.keyboard.internal.gesture

import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.common.InputPointers

/**
 * Stateful bridge between the gesture input path and [StatisticalSwipeDecoder]. Held by `Suggest`
 * and used only when the user selects the built-in decoder. Caches the (expensive) decoder setup:
 *  - re-extracts keys when the keyboard layout changes (`Keyboard.mId`),
 *  - rebuilds the word source when the lexicon identity changes.
 *
 * Runs on the background suggestion thread (the batch-input path), so the one-off pruner build does
 * not block the UI thread.
 */
class GestureDecoderController {
    private val decoder = StatisticalSwipeDecoder()
    private var keyLayoutId: Any? = null
    private var lexiconId: Any? = null

    /**
     * Decodes one completed gesture into ranked word candidates, or an empty list if the decoder is
     * not usable yet (no letter keys / empty lexicon / empty path).
     *
     * @param keyboard the active keyboard (key geometry source).
     * @param inputPointers the aggregated gesture points (same coordinate space as the keys).
     * @param lexicon `word -> probability (0..255)` for the active locale.
     * @param lexiconKey an identity token for [lexicon]; when it changes the word source is rebuilt.
     * @param maxSuggestions cap on returned candidates.
     */
    fun decode(
        keyboard: Keyboard,
        inputPointers: InputPointers,
        lexicon: Map<String, Int>,
        lexiconKey: Any,
        maxSuggestions: Int,
    ): List<String> {
        val n = inputPointers.pointerSize
        if (n <= 0) return emptyList()
        val xc = inputPointers.xCoordinates
        val yc = inputPointers.yCoordinates
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        for (i in 0 until n) {
            xs[i] = xc[i].toFloat()
            ys[i] = yc[i].toFloat()
        }
        return decode(keyboard.mId, KeyboardGestureAdapter.toGestureKeys(keyboard), xs, ys, lexicon, lexiconKey, maxSuggestions)
    }

    /**
     * Engine-agnostic core (unit-testable without a live [Keyboard] / [InputPointers]). Rebuilds the
     * decoder's keys when [layoutKey] changes and its word source when [lexiconKey] changes \u2014 the
     * latter by identity, since the facilitator returns a stable cached lexicon instance until it is
     * invalidated, making the check O(1) instead of an expensive map content comparison.
     */
    internal fun decode(
        layoutKey: Any,
        keys: List<GestureKey>,
        pathX: FloatArray,
        pathY: FloatArray,
        lexicon: Map<String, Int>,
        lexiconKey: Any,
        maxSuggestions: Int,
    ): List<String> {
        if (keys.isEmpty() || lexicon.isEmpty() || pathX.isEmpty() || pathX.size != pathY.size) return emptyList()
        if (layoutKey != keyLayoutId) {
            decoder.setKeys(keys)
            keyLayoutId = layoutKey
        }
        if (lexiconKey !== lexiconId) {
            decoder.setWordSource(DictionaryGestureWordSource(lexicon))
            lexiconId = lexiconKey
        }
        return decoder.decode(pathX, pathY, maxSuggestions)
    }
}
