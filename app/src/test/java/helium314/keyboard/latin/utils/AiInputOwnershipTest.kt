package helium314.keyboard.latin.utils

import android.content.Context
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.RichInputConnection
import helium314.keyboard.latin.R
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.inputlogic.InputLogic
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [AiInputOwnershipTest.HelperShadow::class, ProofreadHelperOwnershipTest.SwitcherShadow::class])
class AiInputOwnershipTest {
    private val ime = mock(LatinIME::class.java)
    private val connection = mock(RichInputConnection::class.java)
    private val editor = mock(InputConnection::class.java)
    private val logic = mock(InputLogic::class.java, CALLS_REAL_METHODS)
    private var text = "original text"
    private var start = 0
    private var end = text.length
    private var generation = 1L
    private val commits = mutableListOf<String>()

    @Before fun setUp() {
        HelperShadow.callbacks.clear()
        clearInvocations(KeyboardSwitcher.getInstance())
        field("mLatinIME", ime)
        field("mConnection", connection)
        `when`(ime.inputSessionGeneration).thenAnswer { generation }
        `when`(ime.currentInputConnection).thenReturn(editor)
        `when`(editor.getExtractedText(any(), anyInt())).thenAnswer {
            ExtractedText().apply {
                text = this@AiInputOwnershipTest.text
                startOffset = 0
                partialStartOffset = -1
                partialEndOffset = -1
                selectionStart = start
                selectionEnd = end
            }
        }
        `when`(connection.hasSelection()).thenAnswer { start != end }
        `when`(connection.getSelectedText(0)).thenAnswer { text.substring(minOf(start, end), maxOf(start, end)) }
        `when`(connection.getTextBeforeCursor(anyInt(), anyInt())).thenAnswer { text.substring(0, minOf(start, end)) }
        `when`(connection.getTextAfterCursor(anyInt(), anyInt())).thenAnswer { text.substring(maxOf(start, end)) }
        `when`(connection.expectedSelectionStart).thenAnswer { start }
        `when`(connection.expectedSelectionEnd).thenAnswer { end }
        doAnswer { commits.add(it.getArgument<CharSequence>(0).toString()); null }
            .`when`(connection).commitText(any(CharSequence::class.java), anyInt())
        doAnswer { start = it.getArgument(0); end = it.getArgument(1); true }
            .`when`(connection).setSelection(anyInt(), anyInt())
        doAnswer { start = 0; end = text.length; null }.`when`(connection).selectAll()
        doAnswer { commits.add(it.getArgument(0)); null }.`when`(ime).onTextInput(anyString())
        val context = ApplicationProvider.getApplicationContext<Context>()
        `when`(ime.getString(anyInt())).thenAnswer { context.getString(it.getArgument(0)) }
        `when`(ime.createDeviceProtectedStorageContext()).thenReturn(context)
        `when`(ime.applicationInfo).thenReturn(context.applicationInfo)
        `when`(ime.getSharedPreferences(anyString(), anyInt())).thenAnswer {
            context.getSharedPreferences(it.getArgument(0), it.getArgument(1))
        }
        DeviceProtectedUtils.getSharedPreferences(ime).edit()
            .putString("pref_custom_ai_prompt_1", "#append Continue").commit()
    }

    @Test fun proofreadRejectsReusedEditorAfterGenerationChanges() {
        request("handleProofread")
        generation++
        complete()
        assertEquals(emptyList<String>(), commits)
    }

    @Test fun translateRejectsDifferentConnectionWithIdenticalText() {
        request("handleTranslate")
        `when`(ime.currentInputConnection).thenReturn(mock(InputConnection::class.java))
        complete()
        assertEquals(emptyList<String>(), commits)
    }

    @Test fun proofreadRejectsChangedTextAtSameRange() {
        request("handleProofread")
        text = "modified text"
        complete()
        assertEquals(emptyList<String>(), commits)
    }

    @Test fun translateRejectsMovedSelectionAndDoesNotDeselect() {
        request("handleTranslate")
        start = end
        clearInvocations(connection)
        complete("changed translation")
        verify(connection, never()).setSelection(anyInt(), anyInt())
        assertEquals(emptyList<String>(), commits)
    }

    @Test fun unavailableSourceFailsClosedBeforeStartingBackend() {
        `when`(editor.getExtractedText(any(), anyInt())).thenReturn(null)
        request("handleProofread")
        assertEquals(0, HelperShadow.callbacks.size)
        verify(connection, never()).setSelection(anyInt(), anyInt())
        assertEquals(emptyList<String>(), commits)
    }

    @Test fun proofreadNotifiesUnsupportedEditor() = assertUnsupportedEditor("handleProofread")
    @Test fun translateNotifiesUnsupportedEditor() = assertUnsupportedEditor("handleTranslate")
    @Test fun customNotifiesUnsupportedEditor() = assertUnsupportedEditor("handleCustomAIKey")

    @Test fun proofreadNotifiesPartialSnapshot() = assertPartialSnapshot("handleProofread")
    @Test fun translateNotifiesPartialSnapshot() = assertPartialSnapshot("handleTranslate")
    @Test fun customNotifiesPartialSnapshot() = assertPartialSnapshot("handleCustomAIKey")

    @Test fun proofreadNotifiesFailedSelection() = assertFailedSelection("handleProofread")
    @Test fun translateNotifiesFailedSelection() = assertFailedSelection("handleTranslate")
    @Test fun customNotifiesFailedSelection() = assertFailedSelection("handleCustomAIKey")

    @Test fun refusedSelectionCannotBeAcceptedEvenWhenSnapshotReportsRequestedRange() {
        start = 4
        end = 4
        doAnswer { start = it.getArgument(0); end = it.getArgument(1); false }
            .`when`(connection).setSelection(anyInt(), anyInt())
        request("handleProofread")
        assertNotifiedWithoutBackend()
    }

    private fun assertUnsupportedEditor(method: String) {
        `when`(editor.getExtractedText(any(), anyInt())).thenReturn(null)
        request(method)
        assertNotifiedWithoutBackend()
        verify(connection, never()).setSelection(anyInt(), anyInt())
    }

    private fun assertPartialSnapshot(method: String) {
        `when`(editor.getExtractedText(any(), anyInt())).thenReturn(ExtractedText().apply {
            text = this@AiInputOwnershipTest.text
            partialStartOffset = 2
            partialEndOffset = 5
        })
        request(method)
        assertNotifiedWithoutBackend()
        verify(connection, never()).setSelection(anyInt(), anyInt())
    }

    private fun assertFailedSelection(method: String) {
        start = 4
        end = 4
        doReturn(false).`when`(connection).setSelection(anyInt(), anyInt())
        request(method)
        assertNotifiedWithoutBackend()
        verify(connection).setSelection(anyInt(), anyInt())
    }

    private fun assertNotifiedWithoutBackend() {
        assertEquals(0, HelperShadow.callbacks.size)
        assertEquals(emptyList<String>(), commits)
        verify(KeyboardSwitcher.getInstance()).showToast(ime.getString(R.string.ai_editor_unavailable), true)
    }

    @Test fun repeatedSelectedTextElsewhereIsNotTheOriginalRange() {
        text = "same same"
        end = 4
        request("handleProofread")
        start = 5
        end = 9
        complete()
        assertEquals(emptyList<String>(), commits)
    }

    @Test fun newerRequestSupersedesOlderOriginalAndCallback() {
        request("handleTranslate")
        request("handleTranslate")
        HelperShadow.callbacks.first().onSuccess("obsolete")
        assertEquals(emptyList<String>(), commits)
        complete()
        assertEquals(listOf("corrected text"), commits)
    }

    @Test fun unchangedSelectionStillAcceptsProofread() {
        request("handleProofread")
        complete()
        assertEquals(listOf("corrected text"), commits)
    }

    @Test fun wholeFieldProofreadStillSelectsAndReplaces() {
        start = 4
        end = 4
        request("handleProofread")
        assertEquals(0, start)
        assertEquals(text.length, end)
        complete()
        assertEquals(listOf("corrected text"), commits)
    }

    @Test fun customAppendKeepsSourceAndInsertsAtSelectionEnd() {
        request("handleCustomAIKey")
        assertEquals(text.length, start)
        complete(" continuation")
        assertEquals(listOf(" continuation"), commits)
        assertEquals("original text", text)
    }

    @Test fun customAppendRejectsChangedSourceBeforeInsertionPoint() {
        request("handleCustomAIKey")
        text = "modified text"
        complete()
        assertEquals(emptyList<String>(), commits)
    }

    private fun complete(result: String = "corrected text") = HelperShadow.callbacks.last().onSuccess(result)

    private fun request(name: String) {
        if (name == "handleCustomAIKey") {
            InputLogic::class.java.getDeclaredMethod(name, Int::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(logic, 1)
        } else {
            InputLogic::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(logic)
        }
    }

    private fun field(name: String, value: Any) {
        InputLogic::class.java.getDeclaredField(name).apply { isAccessible = true }.set(logic, value)
    }

    @Implements(ProofreadHelper::class)
    class HelperShadow {
        companion object {
            val callbacks = mutableListOf<ProofreadHelper.ProofreadCallback>()
            @Implementation @JvmStatic fun isOperationInProgress() = false
            @Implementation @JvmStatic fun proofreadAsync(
                context: Context, text: String, selected: Boolean, callback: ProofreadHelper.ProofreadCallback
            ) { callbacks.add(callback) }
            @Implementation @JvmStatic fun translateAsync(
                context: Context, text: String, selected: Boolean, callback: ProofreadHelper.ProofreadCallback
            ) { callbacks.add(callback) }
            @Implementation @JvmStatic fun customAsync(
                context: Context, text: String, prompt: String, selected: Boolean, thinking: Boolean,
                callback: ProofreadHelper.ProofreadCallback
            ) { callbacks.add(callback) }
        }
    }
}
