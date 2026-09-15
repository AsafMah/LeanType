// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.widget.EditText
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaQueryInputTest {
    @Test fun enterSubmitsWithoutInsertingNewline() {
        val field = EditText(RuntimeEnvironment.getApplication())
        var submitted = 0
        val input = MediaQueryInput(field, { submitted++ }, {}, { false })
        input.onTextInput("cat")
        input.onCodeInput(Constants.CODE_ENTER, 0, 0, false)
        input.onCodeInput(Constants.CODE_ENTER, 0, 0, true)
        input.onKeyDown(android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent(
            0, 0, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER, 1
        ))
        assertEquals(1, submitted)
        assertEquals("cat", field.text.toString())
    }

    @Test fun selectionReplacementWorksInEitherDirection() {
        val field = EditText(RuntimeEnvironment.getApplication())
        val input = MediaQueryInput(field, {}, {}, { false })
        for (reverse in listOf(false, true)) {
            field.setText("abcdef")
            field.setSelection(if (reverse) 4 else 1, if (reverse) 1 else 4)
            input.onTextInput("X")
            assertEquals("aXef", field.text.toString())
            assertEquals(2, field.selectionStart)
        }
    }

    @Test fun codePointInputAndDeletionNeverSplitSurrogates() {
        val field = EditText(RuntimeEnvironment.getApplication())
        val input = MediaQueryInput(field, {}, {}, { false })
        input.onTextInput("x")
        input.onCodeInput(0x1F600, 0, 0, false)
        assertEquals(3, field.length())
        input.onCodeInput(KeyCode.DELETE, 0, 0, false)
        assertEquals("x", field.text.toString())
    }

    @Test fun functionalKeysCannotEditHostAndLanguageKeysAreIgnored() {
        val field = EditText(RuntimeEnvironment.getApplication())
        val layouts = mutableListOf<Int>()
        var submits = 0
        val input = MediaQueryInput(field, { submits++ }, { layouts.add(it) }, { false })
        listOf(KeyCode.LANGUAGE_SWITCH, KeyCode.CUSTOM1, KeyCode.CLIPBOARD, KeyCode.VOICE_INPUT)
            .forEach { input.onCodeInput(it, 0, 0, false) }
        assertEquals("", field.text.toString())
        assertEquals(0, submits)
        assertEquals(emptyList(), layouts)
        input.onCodeInput(KeyCode.SHIFT, 0, 0, false)
        assertEquals(listOf(KeyCode.SHIFT), layouts)
    }
}
