package helium314.keyboard.keyboard.internal

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.utils.RecapitalizeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyboardStateTest {
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

    private fun functionalEvent(code: Int) = helium314.keyboard.event.Event.createSoftwareKeypressEvent(
        helium314.keyboard.event.Event.NOT_A_CODE_POINT,
        code,
        0,
        helium314.keyboard.latin.common.Constants.NOT_A_COORDINATE,
        helium314.keyboard.latin.common.Constants.NOT_A_COORDINATE,
        false,
    )

    private class RecordingSwitchActions : KeyboardState.SwitchActions {
        val customLayouts = mutableListOf<Int>()

        override fun setAlphabetKeyboard() = Unit
        override fun setAlphabetManualShiftedKeyboard() = Unit
        override fun setAlphabetAutomaticShiftedKeyboard() = Unit
        override fun setAlphabetShiftLockedKeyboard() = Unit
        override fun setAlphabetShiftLockShiftedKeyboard() = Unit
        override fun setEmojiKeyboard() = Unit
        override fun setClipboardKeyboard() = Unit
        override fun setNumpadKeyboard() = Unit
        override fun toggleNumpad(withSliding: Boolean, autoCapsFlags: Int, recapitalizeMode: RecapitalizeMode?, forceReturnToAlpha: Boolean) = Unit
        override fun setSymbolsKeyboard() = Unit
        override fun setSymbolsShiftedKeyboard() = Unit
        override fun setCustomKeyboard(customIndex: Int) { customLayouts += customIndex }
        override fun requestUpdatingShiftState(autoCapsFlags: Int, recapitalizeMode: RecapitalizeMode?) = Unit
        override fun startDoubleTapShiftKeyTimer() = Unit
        override val isInDoubleTapShiftKeyTimeout = false
        override fun cancelDoubleTapShiftKeyTimer() = Unit
        override fun setOneHandedModeEnabled(enabled: Boolean) = Unit
        override fun switchOneHandedMode() = Unit
        override fun toggleFloatingKeyboard() = Unit
    }
}
