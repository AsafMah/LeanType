// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.text.Selection
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.settings.SpacingAndPunctuations
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class RichInputConnectionCapsModeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val spacing get() = SpacingAndPunctuations(context.resources, false)
    private val sentenceType = InputType.TYPE_CLASS_TEXT or TextUtils.CAP_MODE_SENTENCES
    private val allCapsModes = TextUtils.CAP_MODE_CHARACTERS or
        TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES

    @Test fun `native null context does not imply sentence start`() {
        assertUnavailableContextDoesNotCapitalize(web = false, response = ContextResponse.NULL)
    }

    @Test fun `web null context does not imply sentence start`() {
        assertUnavailableContextDoesNotCapitalize(web = true, response = ContextResponse.NULL)
    }

    @Test fun `native empty context at nonzero cursor does not imply sentence start`() {
        assertUnavailableContextDoesNotCapitalize(web = false, response = ContextResponse.EMPTY)
    }

    @Test fun `web empty context at nonzero cursor does not imply sentence start`() {
        assertUnavailableContextDoesNotCapitalize(web = true, response = ContextResponse.EMPTY)
    }

    private fun assertUnavailableContextDoesNotCapitalize(web: Boolean, response: ContextResponse) {
        val (connection, editor) = fixture(web, "hello there", response)
        assertEquals("hello there", editor.editable.toString())
        assertEquals(11, Selection.getSelectionStart(editor.editable))
        assertEquals(0, connection.getCursorCapsMode(sentenceType, spacing, false))
        assertEquals(listOf(TextUtils.CAP_MODE_SENTENCES), editor.capsRequests)
        if (response == ContextResponse.NULL && !web)
            assertEquals(-1, connection.expectedSelectionStart)
    }

    @Test fun `null web context falls back even with a stale zero cursor`() {
        val (connection, editor) = fixture(true, "hello there", ContextResponse.EMPTY, expectedCursor = 0)
        editor.response = ContextResponse.NULL
        assertEquals(0, connection.expectedSelectionStart)
        assertEquals(0, connection.getCursorCapsMode(sentenceType, spacing, false))
        assertEquals(listOf(TextUtils.CAP_MODE_SENTENCES), editor.capsRequests)
    }

    @Test fun `empty context with unknown cursor falls back`() {
        for (web in listOf(false, true)) {
            val (connection, editor) = fixture(web, "hello there", ContextResponse.EMPTY, expectedCursor = -1)
            assertEquals(0, connection.getCursorCapsMode(sentenceType, spacing, false))
            assertEquals(listOf(TextUtils.CAP_MODE_SENTENCES), editor.capsRequests)
        }
    }

    @Test fun `failed reload loses position without manufacturing caps and can recover`() {
        val (connection, editor) = fixture(false, "hello there", ContextResponse.EMPTY)
        assertEquals(11, connection.expectedSelectionStart)
        editor.response = ContextResponse.NULL
        assertEquals(0, connection.getCursorCapsMode(sentenceType, spacing, false))
        assertEquals(-1, connection.expectedSelectionStart)
        editor.response = ContextResponse.EMPTY
        assertEquals(0, connection.getCursorCapsMode(sentenceType, spacing, false))
        editor.response = ContextResponse.AVAILABLE
        assertEquals(0, connection.getCursorCapsMode(sentenceType, spacing, false))
        assertEquals(2, editor.capsRequests.size)
        assertEquals("hello there", editor.editable.toString())
    }

    @Test fun `fallback preserves editor confirmed sentence start`() {
        for (web in listOf(false, true)) {
            val (connection, editor) = fixture(web, "Hello. ", ContextResponse.NULL)
            assertEquals(TextUtils.CAP_MODE_SENTENCES, connection.getCursorCapsMode(sentenceType, spacing, false))
            assertEquals(listOf(TextUtils.CAP_MODE_SENTENCES), editor.capsRequests)
        }
    }

    @Test fun `fallback preserves requested word capitalization at a real word boundary`() {
        for (web in listOf(false, true)) {
            val (connection, editor) = fixture(web, "hello ", ContextResponse.NULL)
            val inputType = InputType.TYPE_CLASS_TEXT or TextUtils.CAP_MODE_WORDS
            assertEquals(TextUtils.CAP_MODE_WORDS, connection.getCursorCapsMode(inputType, spacing, false))
            assertEquals(listOf(TextUtils.CAP_MODE_WORDS), editor.capsRequests)
        }
    }

    @Test fun `fallback does not capitalize words inside an existing word`() {
        for (web in listOf(false, true)) {
            val (connection, _) = fixture(web, "hello there", ContextResponse.NULL)
            val inputType = InputType.TYPE_CLASS_TEXT or TextUtils.CAP_MODE_WORDS
            assertEquals(0, connection.getCursorCapsMode(inputType, spacing, false))
        }
    }

    @Test fun `fallback only requests and accepts requested caps bits`() {
        for (web in listOf(false, true)) {
            val (connection, editor) = fixture(web, "hello there", ContextResponse.NULL)
            editor.capsModeOverride = allCapsModes or InputType.TYPE_CLASS_TEXT
            assertEquals(TextUtils.CAP_MODE_SENTENCES, connection.getCursorCapsMode(sentenceType, spacing, false))
            assertEquals(listOf(TextUtils.CAP_MODE_SENTENCES), editor.capsRequests)
        }
    }

    @Test fun `zero editor caps response still preserves explicit character capitalization`() {
        for (web in listOf(false, true)) {
            val (connection, editor) = fixture(web, "hello there", ContextResponse.NULL)
            editor.capsModeOverride = 0
            val inputType = sentenceType or TextUtils.CAP_MODE_CHARACTERS
            assertEquals(TextUtils.CAP_MODE_CHARACTERS, connection.getCursorCapsMode(inputType, spacing, false))
        }
    }

    @Test fun `context independent caps modes need no fallback IPC`() {
        for (web in listOf(false, true)) {
            val (connection, editor) = fixture(web, "hello there", ContextResponse.NULL)
            editor.capsModeOverride = 0
            assertEquals(0, connection.getCursorCapsMode(InputType.TYPE_CLASS_TEXT, spacing, false))
            assertEquals(TextUtils.CAP_MODE_CHARACTERS, connection.getCursorCapsMode(
                InputType.TYPE_CLASS_TEXT or TextUtils.CAP_MODE_CHARACTERS, spacing, false
            ))
            assertTrue(editor.capsRequests.isEmpty())
        }
    }

    @Test fun `phantom space preserves requested word caps without inventing sentence caps`() {
        for (web in listOf(false, true)) {
            val (connection, _) = fixture(web, "hello there", ContextResponse.NULL)
            assertEquals(TextUtils.CAP_MODE_WORDS, connection.getCursorCapsMode(
                sentenceType or TextUtils.CAP_MODE_WORDS, spacing, true
            ))
            assertEquals(0, connection.getCursorCapsMode(sentenceType, spacing, true))
        }
    }

    @Test fun `unavailable punctuation before phantom space cannot establish sentence start`() {
        for (web in listOf(false, true)) {
            val (connection, _) = fixture(web, "Hello.", ContextResponse.NULL)
            assertEquals(0, connection.getCursorCapsMode(sentenceType, spacing, true))
        }
    }

    @Test fun `available punctuation before phantom space still establishes sentence start`() {
        for (web in listOf(false, true)) {
            val (connection, editor) = fixture(web, "Hello.")
            assertEquals(TextUtils.CAP_MODE_SENTENCES, connection.getCursorCapsMode(sentenceType, spacing, true))
            assertTrue(editor.capsRequests.isEmpty())
        }
    }

    @Test fun `actual empty field and start of nonempty field still capitalize`() {
        for (web in listOf(false, true)) {
            for (text in listOf("", "hello there")) {
                val (connection, editor) = fixture(web, text, cursor = 0)
                editor.beforeCursorRequests = 0
                assertEquals(allCapsModes, connection.getCursorCapsMode(
                    InputType.TYPE_CLASS_TEXT or allCapsModes, spacing, false
                ))
                assertTrue(editor.capsRequests.isEmpty())
                assertEquals(if (web) 1 else 0, editor.beforeCursorRequests)
            }
        }
    }

    @Test fun `healthy native cache needs no editor reads or caps IPC`() {
        for ((text, expected) in listOf("hello there" to 0, "Hello. " to TextUtils.CAP_MODE_SENTENCES)) {
            val (connection, editor) = fixture(false, text)
            editor.beforeCursorRequests = 0
            editor.response = ContextResponse.NULL
            assertEquals(expected, connection.getCursorCapsMode(sentenceType, spacing, false))
            assertEquals(0, editor.beforeCursorRequests)
            assertTrue(editor.capsRequests.isEmpty())
        }
    }

    @Test fun `composing text keeps the early caps decision without editor IPC`() {
        for (web in listOf(false, true)) {
            val (connection, editor) = fixture(web, "")
            connection.setComposingText("hello", 1)
            editor.response = ContextResponse.NULL
            editor.beforeCursorRequests = 0
            val inputType = InputType.TYPE_CLASS_TEXT or allCapsModes
            assertEquals(TextUtils.CAP_MODE_CHARACTERS, connection.getCursorCapsMode(inputType, spacing, false))
            assertEquals(TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS,
                connection.getCursorCapsMode(inputType, spacing, true))
            assertEquals(0, editor.beforeCursorRequests)
            assertTrue(editor.capsRequests.isEmpty())
        }
    }

    @Test fun `nonempty partial surrounding text still uses local sentence rules`() {
        for (web in listOf(false, true)) {
            val (connection, editor) = fixture(web, "Earlier text. Hello. ")
            editor.maxContextLength = 7
            connection.resetCachesUponCursorMoveAndReturnSuccess(20, 20, false)
            assertEquals(TextUtils.CAP_MODE_SENTENCES, connection.getCursorCapsMode(sentenceType, spacing, false))
            assertTrue(editor.capsRequests.isEmpty())
        }
    }

    @Test
    @Config(qualifiers = "de")
    fun `available German comma newline keeps language specific caps rules`() {
        assertTrue(spacing.mUsesGermanRules)
        for (web in listOf(false, true)) {
            val (connection, editor) = fixture(web, "Hallo,\n")
            assertEquals(0, connection.getCursorCapsMode(sentenceType, spacing, false))
            assertTrue(editor.capsRequests.isEmpty())
        }
    }

    @Test fun `finished connection never queries editor caps`() {
        val (connection, editor) = fixture(false, "hello there", ContextResponse.NULL)
        connection.onFinishInput()
        assertEquals(0, connection.getCursorCapsMode(sentenceType, spacing, false))
        assertTrue(editor.capsRequests.isEmpty())
    }

    private fun fixture(
        web: Boolean,
        text: String,
        response: ContextResponse = ContextResponse.AVAILABLE,
        cursor: Int = text.length,
        expectedCursor: Int = cursor
    ): Pair<RichInputConnection, EditorConnection> {
        val editor = EditorConnection(context, text, cursor).apply { this.response = response }
        val service = Mockito.mock(InputMethodService::class.java)
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                if (web) InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT else InputType.TYPE_TEXT_VARIATION_NORMAL
        }
        Mockito.`when`(service.currentInputConnection).thenReturn(editor)
        Mockito.`when`(service.currentInputEditorInfo).thenReturn(info)
        val connection = RichInputConnection(service)
        connection.onStartInput()
        connection.resetCachesUponCursorMoveAndReturnSuccess(expectedCursor, expectedCursor, false)
        assertTrue(connection.isConnected())
        return connection to editor
    }

    private enum class ContextResponse { AVAILABLE, NULL, EMPTY }

    private class EditorConnection(context: Context, text: String, cursor: Int) :
        BaseInputConnection(View(context), true) {
        var response = ContextResponse.AVAILABLE
        var maxContextLength = Int.MAX_VALUE
        var beforeCursorRequests = 0
        val capsRequests = mutableListOf<Int>()
        var capsModeOverride: Int? = null

        init {
            val buffer = requireNotNull(editable)
            buffer.append(text)
            Selection.setSelection(buffer, cursor)
        }

        override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence? {
            beforeCursorRequests++
            return when (response) {
                ContextResponse.AVAILABLE -> super.getTextBeforeCursor(minOf(length, maxContextLength), flags)
                ContextResponse.NULL -> null
                ContextResponse.EMPTY -> ""
            }
        }

        override fun getCursorCapsMode(reqModes: Int): Int {
            capsRequests.add(reqModes)
            return capsModeOverride ?: super.getCursorCapsMode(reqModes)
        }
    }
}
