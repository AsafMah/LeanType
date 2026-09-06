package helium314.keyboard.latin

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.KeyboardActionListenerImpl
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.inputlogic.InputLogic
import helium314.keyboard.latin.ocr.OcrCameraView
import helium314.keyboard.latin.ocr.OcrPipeline
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class])
class OcrSwitcherTest {
    private lateinit var ime: LatinIME
    private lateinit var switcher: KeyboardSwitcher

    @Before fun setUp() {
        ime = Robolectric.setupService(LatinIME::class.java)
        switcher = KeyboardSwitcher.getInstance()
    }

    @After fun tearDown() {
        KeyboardActionListenerImpl.sPersistentSelectionModeActive = false
        KeyboardActionListenerImpl.sPersistentTextEditModeActive = false
        ShadowInputMethodManager2.reset()
    }

    @Test fun cameraEntryAndExitDoNotLeaveArrowSelecting() = assertOcrTransition(false)
    @Test fun resultEntryAndExitDoNotLeaveArrowSelecting() = assertOcrTransition(true)

    private fun assertOcrTransition(result: Boolean) {
        for (name in listOf("mMainKeyboardFrame", "mKeyboardView", "mEmojiTabStripView",
            "mSuggestionStripView", "mStripContainer", "mClipboardStripScrollView",
            "mEmojiPalettesView", "mClipboardHistoryView")) {
            val field = KeyboardSwitcher::class.java.getDeclaredField(name).apply { isAccessible = true }
            field.set(switcher, mock(field.type))
        }
        for (name in listOf("mHandwritingView", "mOcrCameraView", "mOcrResultView", "mOcrStripScrollView")) {
            KeyboardSwitcher::class.java.getDeclaredField(name).apply { isAccessible = true }.set(switcher, null)
        }
        val transitions = spy(switcher)
        doNothing().`when`(transitions).setAlphabetKeyboard()
        val targetIme = mock(LatinIME::class.java)
        val inputLogic = mock(InputLogic::class.java)
        val connection = mock(RichInputConnection::class.java)
        `when`(inputLogic.connection).thenReturn(connection)
        val listener = KeyboardActionListenerImpl(targetIme, inputLogic)
        KeyboardActionListenerImpl.sPersistentTextEditModeActive = true
        KeyboardActionListenerImpl.sPersistentSelectionModeActive = true
        if (result) transitions.showOcrResult(listOf("text")) else transitions.showOcrCamera()
        assertPlainArrow(listener, targetIme, connection)
        transitions.hideOcrPanels()
        assertPlainArrow(listener, targetIme, connection)
    }

    private fun assertPlainArrow(listener: KeyboardActionListenerImpl, ime: LatinIME, connection: RichInputConnection) {
        clearInvocations(ime, connection)
        listener.onCodeInput(KeyCode.ARROW_RIGHT, 0, 0, false)
        verify(connection, never()).sendKeyEvent(org.mockito.ArgumentMatchers.any(KeyEvent::class.java))
        val event = ArgumentCaptor.forClass(Event::class.java)
        verify(ime).onEvent(event.capture())
        assertEquals(0, event.value.metaState and KeyEvent.META_SHIFT_ON)
        assertFalse(KeyboardActionListenerImpl.sPersistentSelectionModeActive)
    }

    @Test fun inputViewReplacementReleasesPreviousCameraOwner() {
        val old = mock(OcrCameraView::class.java)
        KeyboardSwitcher::class.java.getDeclaredField("mOcrCameraView").apply { isAccessible = true }.set(switcher, old)
        switcher.onCreateInputView(ime, false)
        verify(old).release()
    }

    @Test fun hiddenCameraStillStopsWhenWindowHides() {
        val old = mock(OcrCameraView::class.java)
        KeyboardSwitcher::class.java.getDeclaredField("mOcrCameraView").apply { isAccessible = true }.set(switcher, old)
        switcher.onHideWindow()
        verify(old).stopCamera()
    }

    @Test fun frameworkStartWithReusedEditorCancelsBothOcrOwners() {
        val editor = EditorInfo().apply { packageName = "ocr.editor"; inputType = android.text.InputType.TYPE_CLASS_TEXT }
        ime.onStartInput(editor, false)
        val session = ime.inputSessionGeneration
        val (camera, pipeline) = installPendingOwners()
        ime.onStartInput(editor, true)
        assertEquals(session + 1, ime.inputSessionGeneration)
        verify(camera, atLeastOnce()).stopCamera()
        verify(pipeline).release()
    }

    @Test fun frameworkFinishCancelsBothOcrOwnersWithoutViewFinish() {
        val (camera, pipeline) = installPendingOwners()
        val session = ime.inputSessionGeneration
        ime.onFinishInput()
        assertEquals(session + 1, ime.inputSessionGeneration)
        verify(camera, atLeastOnce()).stopCamera()
        verify(pipeline).release()
    }

    @Test fun frameworkWindowHiddenCancelsBothOcrOwners() {
        val (camera, pipeline) = installPendingOwners()
        ime.onWindowHidden()
        verify(camera, atLeastOnce()).stopCamera()
        verify(pipeline).release()
    }

    @Test fun frameworkViewFinishImmediatelyCancelsBothOcrOwners() {
        val (camera, pipeline) = installPendingOwners()
        ime.onFinishInputView(false)
        verify(camera, atLeastOnce()).stopCamera()
        verify(pipeline).release()
    }

    @Test fun frameworkDeferredStartInvalidatesBeforeHandlerRuns() {
        val handler = mock(LatinIME.UIHandler::class.java)
        LatinIME::class.java.getDeclaredField("mHandler").apply { isAccessible = true }.set(ime, handler)
        val (camera, pipeline) = installPendingOwners()
        val session = ime.inputSessionGeneration
        val editor = EditorInfo().apply { packageName = "deferred.editor" }
        ime.onStartInput(editor, true)
        assertEquals(session + 1, ime.inputSessionGeneration)
        verify(camera, atLeastOnce()).stopCamera()
        verify(pipeline).release()
        verify(handler).onStartInput(editor, true)
    }

    @Test fun imeShutdownReleasesCameraAndPipeline() {
        val (camera, pipeline) = installPendingOwners()
        ime.onDestroy()
        verify(camera, atLeastOnce()).release()
        verify(pipeline).release()
    }

    private fun installPendingOwners(): Pair<OcrCameraView, OcrPipeline> {
        val camera = mock(OcrCameraView::class.java)
        val pipeline = mock(OcrPipeline::class.java)
        KeyboardSwitcher::class.java.getDeclaredField("mOcrCameraView").apply { isAccessible = true }.set(switcher, camera)
        ClipboardHistoryManager::class.java.getDeclaredField("screenshotPipeline").apply { isAccessible = true }
            .set(ime.clipboardHistoryManager, pipeline)
        return camera to pipeline
    }
}
