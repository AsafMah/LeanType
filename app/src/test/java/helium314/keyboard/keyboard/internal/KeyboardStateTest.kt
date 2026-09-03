package helium314.keyboard.keyboard.internal

import android.os.SystemClock
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.utils.RecapitalizeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito

class KeyboardStateTest {
    @Test
    fun fastDoubleTapShiftLocksCaps() = withFakeClock { clock ->
        val actions = RecordingSwitchActions(clock)
        val state = loadedKeyboardState(actions)

        tapShift(state)
        clock.uptimeMillis += 50
        tapShift(state)

        assertEquals(
            listOf(KeyboardSelection.MANUAL_SHIFTED, KeyboardSelection.SHIFT_LOCKED),
            actions.keyboardSelections,
        )
        assertEquals(listOf(true), actions.shiftLockTransitions)
    }

    @Test
    fun ordinaryDoubleTapShiftWithinTimerLocksCaps() = withFakeClock { clock ->
        val actions = RecordingSwitchActions(clock)
        val state = loadedKeyboardState(actions)

        tapShift(state)
        clock.uptimeMillis += 150
        tapShift(state)

        assertEquals(
            listOf(KeyboardSelection.MANUAL_SHIFTED, KeyboardSelection.SHIFT_LOCKED),
            actions.keyboardSelections,
        )
        assertEquals(listOf(true), actions.shiftLockTransitions)
    }

    @Test
    fun singleTapShiftStaysTemporarilyShifted() = withFakeClock { clock ->
        val actions = RecordingSwitchActions(clock)
        val state = loadedKeyboardState(actions)

        tapShift(state)

        assertEquals(listOf(KeyboardSelection.MANUAL_SHIFTED), actions.keyboardSelections)
        assertEquals(emptyList(), actions.shiftLockTransitions)
    }

    // Regression coverage for LeanBitLab/LeanType#186 and #188: duplicate press delivery
    // must not recreate the single-tap Caps Lock bug that the 100 ms delay tried to mask.
    @Test
    fun duplicateShiftPressWithoutReleaseDoesNotLockCaps() = withFakeClock { clock ->
        val actions = RecordingSwitchActions(clock)
        val state = loadedKeyboardState(actions)

        state.onPressKey(KeyCode.SHIFT, true, 0, null)
        clock.uptimeMillis += 150
        state.onPressKey(KeyCode.SHIFT, true, 0, null)

        assertEquals(listOf(KeyboardSelection.MANUAL_SHIFTED), actions.keyboardSelections)
        assertEquals(emptyList(), actions.shiftLockTransitions)
    }

    @Test
    fun duplicateShiftPressWhilePressingOnShiftedDoesNotLockCaps() = withFakeClock { clock ->
        val actions = RecordingSwitchActions(clock)
        val state = loadedKeyboardState(actions)

        tapShift(state)
        clock.uptimeMillis += TimerHandler.DOUBLE_TAP_SHIFT_KEY_TIMEOUT_MILLIS
        state.onPressKey(KeyCode.SHIFT, true, 0, null)
        clock.uptimeMillis += 150
        state.onPressKey(KeyCode.SHIFT, true, 0, null)

        assertEquals(listOf(KeyboardSelection.MANUAL_SHIFTED), actions.keyboardSelections)
        assertEquals(emptyList(), actions.shiftLockTransitions)
    }

    @Test
    fun shiftTapAfterChordingStartsANewDoubleTapWindow() = withFakeClock { clock ->
        val actions = RecordingSwitchActions(clock)
        val state = loadedKeyboardState(actions)

        state.onPressKey(KeyCode.SHIFT, true, 0, null)
        state.onPressKey('a'.code, false, 0, null)
        state.onReleaseKey('a'.code, false, 0, null)
        state.onReleaseKey(KeyCode.SHIFT, false, 0, null)
        clock.uptimeMillis += 50
        tapShift(state)

        assertEquals(
            listOf(
                KeyboardSelection.MANUAL_SHIFTED,
                KeyboardSelection.ALPHABET,
                KeyboardSelection.MANUAL_SHIFTED,
            ),
            actions.keyboardSelections,
        )
        assertEquals(emptyList(), actions.shiftLockTransitions)
    }

    @Test
    fun shiftTapAfterDoubleTapWindowUnlocksCaps() = withFakeClock { clock ->
        val actions = RecordingSwitchActions(clock)
        val state = loadedKeyboardState(actions)

        tapShift(state)
        clock.uptimeMillis += 50
        tapShift(state)
        clock.uptimeMillis += TimerHandler.DOUBLE_TAP_SHIFT_KEY_TIMEOUT_MILLIS
        tapShift(state)

        assertEquals(
            listOf(
                KeyboardSelection.MANUAL_SHIFTED,
                KeyboardSelection.SHIFT_LOCKED,
                KeyboardSelection.SHIFT_LOCK_SHIFTED,
                KeyboardSelection.ALPHABET,
            ),
            actions.keyboardSelections,
        )
        assertEquals(listOf(true, false), actions.shiftLockTransitions)
    }

    @Test
    fun customLayoutRestoresAfterSymbolsAndKeyboardReload() {
        val actions = RecordingSwitchActions()
        val state = KeyboardState(actions)
        state.onLoadKeyboard(0, null, false)
        actions.customLayouts.clear()

        state.onEvent(functionalEvent(KeyCode.CUSTOM2), 0, null)
        state.onPressKey(KeyCode.SYMBOL_ALPHA, true, 0, null)
        state.onReleaseKey(KeyCode.SYMBOL_ALPHA, false, 0, null)
        state.onSaveKeyboardState()

        state.onLoadKeyboard(0, null, false)
        state.onPressKey(KeyCode.SYMBOL_ALPHA, true, 0, null)

        assertEquals(listOf(2, 2), actions.customLayouts)
    }

    private fun loadedKeyboardState(actions: RecordingSwitchActions) = KeyboardState(actions).also {
        it.onLoadKeyboard(0, null, false)
        actions.keyboardSelections.clear()
        actions.shiftLockTransitions.clear()
    }

    private fun tapShift(state: KeyboardState) {
        state.onPressKey(KeyCode.SHIFT, true, 0, null)
        state.onReleaseKey(KeyCode.SHIFT, false, 0, null)
    }

    private fun withFakeClock(block: (FakeClock) -> Unit) {
        val clock = FakeClock()
        Mockito.mockStatic(SystemClock::class.java).use { systemClock ->
            systemClock.`when`<Long> { SystemClock.uptimeMillis() }.thenAnswer { clock.uptimeMillis }
            block(clock)
        }
    }

    private fun functionalEvent(code: Int) = helium314.keyboard.event.Event.createSoftwareKeypressEvent(
        helium314.keyboard.event.Event.NOT_A_CODE_POINT,
        code,
        0,
        helium314.keyboard.latin.common.Constants.NOT_A_COORDINATE,
        helium314.keyboard.latin.common.Constants.NOT_A_COORDINATE,
        false,
    )

    private class FakeClock(var uptimeMillis: Long = 1_000)

    private enum class KeyboardSelection {
        ALPHABET,
        MANUAL_SHIFTED,
        AUTOMATIC_SHIFTED,
        SHIFT_LOCKED,
        SHIFT_LOCK_SHIFTED,
    }

    private class RecordingSwitchActions(
        private val clock: FakeClock = FakeClock(),
    ) : KeyboardState.SwitchActions {
        val customLayouts = mutableListOf<Int>()
        val keyboardSelections = mutableListOf<KeyboardSelection>()
        val shiftLockTransitions = mutableListOf<Boolean>()
        private var doubleTapTimerDeadline: Long? = null
        private var isShiftLocked = false

        override fun setAlphabetKeyboard() {
            keyboardSelections += KeyboardSelection.ALPHABET
            if (isShiftLocked) shiftLockTransitions += false
            isShiftLocked = false
        }
        override fun setAlphabetManualShiftedKeyboard() {
            keyboardSelections += KeyboardSelection.MANUAL_SHIFTED
        }
        override fun setAlphabetAutomaticShiftedKeyboard() {
            keyboardSelections += KeyboardSelection.AUTOMATIC_SHIFTED
        }
        override fun setAlphabetShiftLockedKeyboard() {
            keyboardSelections += KeyboardSelection.SHIFT_LOCKED
            if (!isShiftLocked) shiftLockTransitions += true
            isShiftLocked = true
        }
        override fun setAlphabetShiftLockShiftedKeyboard() {
            keyboardSelections += KeyboardSelection.SHIFT_LOCK_SHIFTED
        }
        override fun setEmojiKeyboard() = Unit
        override fun setClipboardKeyboard() = Unit
        override fun setNumpadKeyboard() = Unit
        override fun toggleNumpad(withSliding: Boolean, autoCapsFlags: Int, recapitalizeMode: RecapitalizeMode?, forceReturnToAlpha: Boolean) = Unit
        override fun setSymbolsKeyboard() = Unit
        override fun setSymbolsShiftedKeyboard() = Unit
        override fun setCustomKeyboard(customIndex: Int) { customLayouts += customIndex }
        override fun requestUpdatingShiftState(autoCapsFlags: Int, recapitalizeMode: RecapitalizeMode?) = Unit
        override fun startDoubleTapShiftKeyTimer() {
            doubleTapTimerDeadline =
                clock.uptimeMillis + TimerHandler.DOUBLE_TAP_SHIFT_KEY_TIMEOUT_MILLIS
        }
        override val isInDoubleTapShiftKeyTimeout
            get() = doubleTapTimerDeadline?.let { clock.uptimeMillis < it } == true
        override fun cancelDoubleTapShiftKeyTimer() {
            doubleTapTimerDeadline = null
        }
        override fun setOneHandedModeEnabled(enabled: Boolean) = Unit
        override fun switchOneHandedMode() = Unit
        override fun toggleFloatingKeyboard() = Unit
    }
}
