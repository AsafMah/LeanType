// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.text.InputType
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.widget.EditText
import android.widget.FrameLayout
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.event.Event
import helium314.keyboard.latin.calculator.CalculatorHistoryManager
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowLocaleManagerCompat::class, ShadowInputMethodManager2::class])
class MathSuggestionManagerTest {
    private lateinit var ime: MathTestIme
    private lateinit var editor: EditText
    private lateinit var rawConnection: MathEditorConnection
    private lateinit var manager: MathSuggestionManager
    private lateinit var parent: FrameLayout
    private val history get() = CalculatorHistoryManager.getInstance(ime)

    @Before fun setUp() {
        ime = Robolectric.setupService(MathTestIme::class.java)
        editor = EditText(ime)
        rawConnection = MathEditorConnection(editor)
        ime.testConnection = rawConnection
        ime.testEditor = EditorInfo().apply {
            packageName = "math.test.editor"
            fieldId = 42
            inputType = InputType.TYPE_CLASS_TEXT
        }
        ime.started = true
        parent = FrameLayout(ime)
        manager = MathSuggestionManager(ime)
        history.clearHistory()
        setText("1+2=")
        loadSettings()
    }

    private fun loadSettings(incognito: Boolean = false) {
        ime.prefs().edit()
            .putBoolean(Settings.PREF_INLINE_MATH_CALCULATION, true)
            .putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, incognito).commit()
        val current = ime.mSettings.current
        ime.mSettings.loadSettings(ime, current.mLocale,
            InputAttributes(ime.testEditor, false, ime.packageName), current.mCurrentKeyboardScript)
    }

    private fun setText(text: String) {
        editor.setText(text)
        editor.setSelection(text.length)
        ime.mInputLogic.connection.resetCachesUponCursorMoveAndReturnSuccess(
            text.length, text.length, true)
        rawConnection.reads = 0
    }

    private fun show(): View = assertNotNull(manager.getMathSuggestionView(parent))
    private fun click(view: View) = view.findViewById<View>(R.id.otp_suggestion_text).performClick()

    @Test fun passwordVariantsNeverReadOrDisplayMath() {
        for (inputType in listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )) {
            ime.testEditor!!.inputType = inputType
            loadSettings()
            rawConnection.reads = 0
            assertNull(manager.getMathSuggestionView(parent), "inputType=$inputType")
            assertEquals(0, rawConnection.reads)
        }
        assertTrue(history.getHistory().isEmpty())
    }

    @Test fun sensitiveFieldNeverReadsOrDisplaysMath() {
        ime.testEditor!!.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        loadSettings()
        rawConnection.reads = 0
        assertNull(manager.getMathSuggestionView(parent))
        assertEquals(0, rawConnection.reads)
    }

    @Test fun noSuggestionsFieldNeverReadsOrDisplaysMath() {
        ime.testEditor!!.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        loadSettings()
        rawConnection.reads = 0
        assertNull(manager.getMathSuggestionView(parent))
        assertEquals(0, rawConnection.reads)
    }

    @Test fun incognitoDoesNotPersistHistory() {
        loadSettings(incognito = true)
        click(show())
        assertEquals("3", editor.text.toString())
        val lazyHistory = MathSuggestionManager::class.java.getDeclaredField("historyManager\$delegate")
            .apply { isAccessible = true }.get(manager) as Lazy<*>
        assertTrue(!lazyHistory.isInitialized(), "Incognito must not even load calculator history")
        assertTrue(history.getHistory().isEmpty())
    }

    @Test fun validClickPreservesDelimiterAndTrailingWhitespace() {
        setText("total;1+2= \t")
        click(show())
        assertEquals("total;3 \t", editor.text.toString())
        assertEquals(editor.selectionStart, ime.mInputLogic.connection.expectedSelectionStart)
        assertEquals("total;3 \t", ime.mInputLogic.connection.getTextBeforeCursor(60, 0).toString())
        assertEquals("1+2", history.getHistory().single().expression)
    }

    @Test fun changedTextDiscardsClickEvenBeforeCacheUpdate() {
        val view = show()
        editor.setText("4+5=")
        editor.setSelection(4)
        click(view)
        assertEquals("4+5=", editor.text.toString())
        assertTrue(history.getHistory().isEmpty())
    }

    @Test fun changedCursorDiscardsClickEvenWithIdenticalSuffix() {
        val repeated = "1+2=" + " ".repeat(60) + "1+2=" + " ".repeat(60)
        setText(repeated)
        editor.setSelection(repeated.length - 60)
        ime.mInputLogic.connection.resetCachesUponCursorMoveAndReturnSuccess(
            editor.selectionStart, editor.selectionEnd, true)
        val view = show()
        editor.setSelection(4)
        click(view)
        assertEquals(repeated, editor.text.toString())
        assertTrue(history.getHistory().isEmpty())
    }

    @Test fun changedEditorDiscardsClick() {
        val view = show()
        ime.testEditor = EditorInfo().apply {
            packageName = "other.editor"
            inputType = InputType.TYPE_CLASS_TEXT
            fieldId = 42
        }
        click(view)
        assertEquals("1+2=", editor.text.toString())
        assertTrue(history.getHistory().isEmpty())
    }

    @Test fun changedConnectionDiscardsClick() {
        val view = show()
        ime.testConnection = MathEditorConnection(editor)
        click(view)
        assertEquals("1+2=", editor.text.toString())
        assertTrue(history.getHistory().isEmpty())
    }

    @Test fun finishedSessionDiscardsClick() {
        val view = show()
        ime.started = false
        click(view)
        assertEquals("1+2=", editor.text.toString())
        assertTrue(history.getHistory().isEmpty())
    }

    @Test fun clickRechecksPrivacyBeforeReading() {
        val view = show()
        ime.testEditor!!.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        rawConnection.reads = 0
        click(view)
        assertEquals(0, rawConnection.reads)
        assertEquals("1+2=", editor.text.toString())
        assertTrue(history.getHistory().isEmpty())
    }

    @Test fun rejectedSelectionDoesNotChangeTextOrLeaveStaleCursorCache() {
        val view = show()
        rawConnection.rejectSelection = true
        click(view)
        assertEquals("1+2=", editor.text.toString())
        assertEquals(editor.selectionStart, ime.mInputLogic.connection.expectedSelectionStart)
        assertEquals(editor.selectionEnd, ime.mInputLogic.connection.expectedSelectionEnd)
        assertTrue(history.getHistory().isEmpty())
    }

    @Test fun rejectedCommitDoesNotSaveAnUnappliedCalculation() {
        val view = show()
        rawConnection.rejectCommit = true
        click(view)
        assertEquals("1+2=", editor.text.toString())
        assertTrue(history.getHistory().isEmpty())
    }

    @Test fun replacedSuggestionDiscardsOldClick() {
        val oldView = show()
        show()
        click(oldView)
        assertEquals("1+2=", editor.text.toString())
        assertTrue(history.getHistory().isEmpty())
    }

    @Test fun clearedSuggestionDiscardsOldClick() {
        val oldView = show()
        manager.removeMathSuggestion()
        click(oldView)
        assertEquals("1+2=", editor.text.toString())
        assertTrue(history.getHistory().isEmpty())
    }

    @Test fun aNewSessionForTheSameEditorDiscardsOldClick() {
        val oldView = show()
        loadSettings()
        click(oldView)
        assertEquals("1+2=", editor.text.toString())
        assertTrue(history.getHistory().isEmpty())
    }

    @Test fun restartedInputWithReusedEditorAndConnectionDiscardsOldClick() {
        val oldView = show()
        val attributes = ime.mSettings.current.mInputAttributes
        ime.onStartInput(ime.testEditor, true)
        assertTrue(attributes === ime.mSettings.current.mInputAttributes)
        rawConnection.reads = 0
        click(oldView)
        assertEquals("1+2=", editor.text.toString())
        assertEquals(0, rawConnection.reads)
        assertTrue(history.getHistory().isEmpty())
    }

    @Test fun truncatedExpressionIsNotOfferedAsACompleteCalculation() {
        setText("7".repeat(70) + "+2=")
        assertNull(manager.getMathSuggestionView(parent))
    }

    @Test fun boundedReadStillReplacesCompleteExpressionInALongDocument() {
        val prefix = "x".repeat(100) + ";"
        setText(prefix + "1+2= ")
        click(show())
        assertEquals(prefix + "3 ", editor.text.toString())
        assertEquals(1, history.getHistory().size)
    }
}

class MathTestIme : LatinIME() {
    var testConnection: InputConnection? = null
    var testEditor: EditorInfo? = null
    var started = false
    override fun getCurrentInputConnection() = testConnection
    override fun getCurrentInputEditorInfo() = testEditor
    override fun getCurrentInputStarted() = started
    override fun onTextInput(rawText: String) {
        mInputLogic.onTextInput(mSettings.current, Event.createSoftwareTextEvent(rawText, 0), 0, mHandler)
    }
}

private class MathEditorConnection(private val editor: EditText) : BaseInputConnection(editor, true) {
    var reads = 0
    var rejectSelection = false
    var rejectCommit = false
    override fun getEditable() = editor.editableText
    override fun setSelection(start: Int, end: Int): Boolean =
        !rejectSelection && super.setSelection(start, end)
    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean =
        !rejectCommit && super.commitText(text, newCursorPosition)
    override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence? {
        reads++
        return super.getTextBeforeCursor(length, flags)
    }
    override fun getTextAfterCursor(length: Int, flags: Int): CharSequence? {
        reads++
        return super.getTextAfterCursor(length, flags)
    }
    override fun getSelectedText(flags: Int): CharSequence? {
        reads++
        return super.getSelectedText(flags)
    }
    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText {
        reads++
        return ExtractedText().apply {
            startOffset = 0
            selectionStart = editor.selectionStart
            selectionEnd = editor.selectionEnd
        }
    }
}
