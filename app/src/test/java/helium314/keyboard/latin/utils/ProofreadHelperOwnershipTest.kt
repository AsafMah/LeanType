package helium314.keyboard.latin.utils

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
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

    private fun start(result: Result<String>) {
        val call: suspend (ProofreadService) -> Result<String> = { result }
        val success: (String) -> Unit = { delivered.add(it) }
        val error: (String) -> Unit = { delivered.add("error:$it") }
        val method = ProofreadHelper::class.java.declaredMethods.single { it.name == "performAsyncOperation" }
        method.isAccessible = true
        val args = if (method.parameterCount == 8) {
            arrayOf(context, "original", R.string.proofread_no_text, R.string.proofread_error, true, call, success, error)
        } else {
            arrayOf(context, "original", R.string.proofread_no_text, R.string.proofread_error, call, success, error, false, true)
        }
        method.invoke(ProofreadHelper, *args)
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
