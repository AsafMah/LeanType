package helium314.keyboard.latin.utils

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.LatinIME
import android.view.inputmethod.InputConnection
import android.view.inputmethod.ExtractedText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [ProofreadHelperOwnershipTest.SwitcherShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class ProofreadHelperOwnershipTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val delivered = mutableListOf<String>()

    @Before fun clear() {
        ProofreadHelper.cancelCurrentOperation()
        shadowOf(Looper.getMainLooper()).idle()
        clearInvocations(KeyboardSwitcher.getInstance())
    }

    @Test fun cancelAfterWorkerFinishedInvalidatesAlreadyQueuedSuccess() {
        start(Result.success("obsolete"))
        ProofreadHelper.cancelCurrentOperation()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(emptyList<String>(), delivered)
    }

    @Test fun newerRequestOwnsDeliveryEvenWhenOldWorkerAlreadyFinished() {
        start(Result.success("obsolete"))
        start(Result.success("current"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("current"), delivered)
    }

    @Test fun cancelAfterWorkerFinishedInvalidatesAlreadyQueuedError() {
        start(Result.failure(IllegalStateException("obsolete error")))
        ProofreadHelper.cancelCurrentOperation()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(emptyList<String>(), delivered)
    }

    @Test fun cancellationResultIsNotAnError() {
        start(Result.failure(CancellationException("cancelled")))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(emptyList<String>(), delivered)
    }

    @Test fun pendingDeliveryIsStillAnOperation() {
        start(Result.success("current"))
        assertTrue(ProofreadHelper.isOperationInProgress)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("current"), delivered)
        assertFalse(ProofreadHelper.isOperationInProgress)
    }

    @Test fun cancelledPluginFailureCannotShowItsSeparatelyQueuedToast() {
        context.prefs().edit().clear().putString("pref_translation_engine", "plugin").commit()
        ProofreadHelper.translateAsync(context, "hello", true, { delivered.add(it) }, { delivered.add(it) })
        waitForWorker()
        ProofreadHelper.cancelCurrentOperation()
        shadowOf(Looper.getMainLooper()).idle()
        verify(KeyboardSwitcher.getInstance(), never()).showToast(anyString(), anyBoolean())
        assertEquals(emptyList<String>(), delivered)
    }

    @Test fun editorChangeSuppressesQueuedErrorAndAllItsUiFeedback() {
        val ime = mock(LatinIME::class.java)
        val editor = mock(InputConnection::class.java)
        `when`(ime.inputSessionGeneration).thenReturn(1L)
        `when`(ime.currentInputConnection).thenReturn(editor)
        `when`(editor.getExtractedText(any(), anyInt())).thenReturn(ExtractedText().apply {
            text = "original"
            partialStartOffset = -1
            selectionEnd = 8
        })
        start(Result.failure(IllegalStateException("old editor error")), ime)
        `when`(ime.inputSessionGeneration).thenReturn(2L)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(emptyList<String>(), delivered)
        verifyNoInteractions(KeyboardSwitcher.getInstance())
    }

    @Test fun queuedSuccessAfterSameEditorTextMutationSettlesLoadingWithoutDelivery() =
        assertSameEditorMutationSettles(Result.success("corrected")) { it.text = "modified" }

    @Test fun queuedSuccessAfterSameEditorSelectionMutationSettlesLoadingWithoutDelivery() =
        assertSameEditorMutationSettles(Result.success("corrected")) { it.selectionStart = 8 }

    @Test fun queuedErrorAfterSameEditorTextMutationSettlesLoadingWithoutDelivery() =
        assertSameEditorMutationSettles(Result.failure(IllegalStateException("obsolete error"))) { it.text = "modified" }

    @Test fun queuedErrorAfterSameEditorSelectionMutationSettlesLoadingWithoutDelivery() =
        assertSameEditorMutationSettles(Result.failure(IllegalStateException("obsolete error"))) { it.selectionStart = 8 }

    @Test fun obsoleteCompletionCannotHideOrClearNewerRequestLoading() {
        val (ime, _) = editorContext()
        val oldResult = launchWithLoading(ime)
        oldResult.complete(Result.success("obsolete"))
        waitForWorker()
        val nextResult = CompletableDeferred<Result<String>>()
        invokeOperation(ime) { nextResult.await() }
        shadowOf(Looper.getMainLooper()).idle()
        verify(KeyboardSwitcher.getInstance(), never()).hideLoadingAnimation()
        assertTrue(ProofreadHelper.isOperationInProgress)
        assertEquals(emptyList<String>(), delivered)
        nextResult.complete(Result.success("current"))
        waitForWorker()
        shadowOf(Looper.getMainLooper()).idle()
        verify(KeyboardSwitcher.getInstance()).hideLoadingAnimation()
        assertEquals(listOf("current"), delivered)
        assertFalse(ProofreadHelper.isOperationInProgress)
    }

    @Test fun differentInputSessionCannotCleanUpAnotherEditorsUi() {
        val (ime, _) = editorContext()
        val result = launchWithLoading(ime)
        result.complete(Result.success("obsolete"))
        waitForWorker()
        `when`(ime.inputSessionGeneration).thenReturn(2L)
        shadowOf(Looper.getMainLooper()).idle()
        verify(KeyboardSwitcher.getInstance(), never()).hideLoadingAnimation()
        assertEquals(emptyList<String>(), delivered)
    }

    @Test fun differentInputConnectionCannotCleanUpAnotherEditorsUi() {
        val (ime, _) = editorContext()
        val result = launchWithLoading(ime)
        result.complete(Result.success("obsolete"))
        waitForWorker()
        `when`(ime.currentInputConnection).thenReturn(mock(InputConnection::class.java))
        shadowOf(Looper.getMainLooper()).idle()
        verify(KeyboardSwitcher.getInstance(), never()).hideLoadingAnimation()
        assertEquals(emptyList<String>(), delivered)
    }

    private fun assertSameEditorMutationSettles(result: Result<String>, mutate: (ExtractedText) -> Unit) {
        val (ime, extracted) = editorContext()
        val pending = launchWithLoading(ime)
        pending.complete(result)
        waitForWorker()
        mutate(extracted)
        shadowOf(Looper.getMainLooper()).idle()
        verify(KeyboardSwitcher.getInstance()).hideLoadingAnimation()
        verify(KeyboardSwitcher.getInstance(), never()).showToast(anyString(), anyBoolean())
        assertEquals(emptyList<String>(), delivered)
        assertFalse(ProofreadHelper.isOperationInProgress)
    }

    private fun editorContext(): Pair<LatinIME, ExtractedText> {
        val ime = mock(LatinIME::class.java)
        val editor = mock(InputConnection::class.java)
        val extracted = ExtractedText().apply {
            text = "original"
            partialStartOffset = -1
            selectionEnd = 8
        }
        `when`(ime.inputSessionGeneration).thenReturn(1L)
        `when`(ime.currentInputConnection).thenReturn(editor)
        `when`(editor.getExtractedText(any(), anyInt())).thenReturn(extracted)
        return ime to extracted
    }

    private fun launchWithLoading(ime: LatinIME): CompletableDeferred<Result<String>> {
        val pending = CompletableDeferred<Result<String>>()
        invokeOperation(ime) { pending.await() }
        shadowOf(Looper.getMainLooper()).idle()
        verify(KeyboardSwitcher.getInstance()).showLoadingAnimation()
        return pending
    }

    private fun start(result: Result<String>, requestContext: Context = context) {
        invokeOperation(requestContext) { result }
        waitForWorker()
    }

    private fun invokeOperation(requestContext: Context, call: suspend (ProofreadService) -> Result<String>) {
        val success: (String) -> Unit = { delivered.add(it) }
        val error: (String) -> Unit = { delivered.add("error:$it") }
        val method = ProofreadHelper::class.java.declaredMethods.single { it.name == "performAsyncOperation" }
        method.isAccessible = true
        val args = if (method.parameterCount == 8) {
            arrayOf(requestContext, "original", R.string.proofread_no_text, R.string.proofread_error, true, call, success, error)
        } else {
            arrayOf(requestContext, "original", R.string.proofread_no_text, R.string.proofread_error, call, success, error, false, true)
        }
        method.invoke(ProofreadHelper, *args)
    }

    private fun waitForWorker() {
        val job = ProofreadHelper::class.java.getDeclaredField("currentJob").apply { isAccessible = true }
            .get(null) as Job
        runBlocking { job.join() }
    }

    @Implements(KeyboardSwitcher::class)
    class SwitcherShadow {
        companion object {
            private val switcher = mock(KeyboardSwitcher::class.java)
            @Implementation @JvmStatic fun getInstance(): KeyboardSwitcher = switcher
        }
    }
}
