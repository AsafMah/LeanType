package helium314.keyboard.latin.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.settings.Settings
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.nehuatl.llamacpp.LlamaAndroid
import org.nehuatl.llamacpp.LlamaHelper
import org.nehuatl.llamacpp.LlamaContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLog
import kotlin.coroutines.CoroutineContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [OfflineGenerationTest.NativeShadow::class, OfflineGenerationTest.ContextShadow::class])
class OfflineGenerationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val native = mock(LlamaAndroid::class.java)
    private val helper = mock(LlamaHelper::class.java)
    private val service = ProofreadService(context, Dispatchers.Unconfined)
    private var emitted = 0
    private var generated = listOf("corrected")
    private var fail = false
    private var prompt = ""

    @Before fun setUp() {
        context.prefs().edit().clear().putBoolean(Settings.PREF_OFFLINE_KEEP_MODEL_LOADED, true).commit()
        context.prefs().edit().putString("offline_model_path", "content://fake/model.gguf").commit()
        service.setSystemPrompt("Correct {text}")
        field("llama\$delegate", lazy { native })
        field("currentContext", 1)
        `when`(helper.scope).thenReturn(CoroutineScope(object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
        }))
        ProofreadService.ModelHolder.llamaHelper = helper
        ProofreadService.ModelHolder.currentModelPath = service.getModelPath()
        ProofreadService.ModelHolder.isModelLoaded = true
        doAnswer {
            prompt = (it.getArgument<Map<String, Any>>(1)["prompt"] as String)
            if (fail) throw IllegalStateException("PRIVATE_BACKEND_ERROR")
            generated.forEach { word ->
                ProofreadService.ModelHolder.onNativeToken(helper, word)
                emitted++
            }
            mapOf("text" to generated.joinToString(""))
        }.`when`(native).launchCompletion(anyInt(), anyMap())
    }

    @Test fun immediateDoneCannotBeLostBeforeCollectionStarts() = runBlocking {
        val result = async(Dispatchers.Unconfined) { service.proofread("original") }
        try {
            assertEquals(1, emitted)
            assertTrue("Native completion returned; result must not wait for a lost event", result.isCompleted)
            assertEquals("corrected", result.await().getOrThrow())
        } finally { result.cancelAndJoin() }
    }

    @Test fun immediateErrorCannotBeLostBeforeCollectionStarts() = runBlocking {
        fail = true
        val result = async(Dispatchers.Unconfined) { service.proofread("original") }
        try {
            assertTrue("Native error must terminate the request", result.isCompleted)
            assertTrue(result.await().isFailure)
        } finally { result.cancelAndJoin() }
    }

    @Test fun fastProducerRetainsMoreThan64Tokens() = runBlocking {
        generated = List(1000) { "word$it " }
        val queue = QueueDispatcher()
        val consumer = QueueDispatcher()
        `when`(helper.scope).thenReturn(CoroutineScope(queue))
        val result = async(Dispatchers.Unconfined) {
            ProofreadService(context, consumer).proofread("original")
        }
        try {
            consumer.drain()
            queue.drain()
            consumer.drain()
            assertEquals(1000, emitted)
            assertTrue(result.isCompleted)
            assertEquals(generated.joinToString("").trim(), result.await().getOrThrow())
        } finally { result.cancelAndJoin() }
    }

    @Test fun generationSeamDoesNotLogInputPromptOrOutput() = runBlocking {
        val queue = QueueDispatcher()
        `when`(helper.scope).thenReturn(CoroutineScope(queue))
        generated = listOf("PRIVATE_GENERATED_OUTPUT")
        ShadowLog.clear()
        val result = async(Dispatchers.Unconfined) { service.proofread("PRIVATE_EDITOR_INPUT") }
        try {
            queue.drain()
            assertEquals("PRIVATE_GENERATED_OUTPUT", result.await().getOrThrow())
            val logs = ShadowLog.getLogs().joinToString("\n") { it.msg + (it.throwable?.message ?: "") }
            assertFalse(logs.contains("PRIVATE_EDITOR_INPUT"))
            assertFalse(logs.contains("PRIVATE_GENERATED_OUTPUT"))
        } finally { result.cancelAndJoin() }
    }

    @Test fun translationPromptUsesPersistedToolbarLanguage() = runBlocking {
        val queue = QueueDispatcher()
        `when`(helper.scope).thenReturn(CoroutineScope(queue))
        service.setTargetLanguage("de")
        service.setTranslateSystemPrompt("Translate {text} into {lang}.")
        val result = async(Dispatchers.Unconfined) { service.translate("hello") }
        try {
            queue.drain()
            assertEquals("corrected", result.await().getOrThrow())
            assertEquals("Translate hello into German.", prompt)
        } finally { result.cancelAndJoin() }
    }

    @Test fun cancelledNativeCallDrainsBeforeNextRequestAndCannotPublishIntoIt() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        doAnswer {
            if (calls.incrementAndGet() == 1) {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                ProofreadService.ModelHolder.onNativeToken(helper, "obsolete")
            } else {
                ProofreadService.ModelHolder.onNativeToken(helper, "current")
            }
            mapOf("text" to "")
        }.`when`(native).launchCompletion(anyInt(), anyMap())
        val first = async(Dispatchers.IO) { ProofreadService(context).proofread("first") }
        var second: Deferred<Result<String>>? = null
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            first.cancel()
            second = async(Dispatchers.Unconfined) { service.proofread("second") }
            assertFalse(second.isCompleted)
            assertEquals(1, calls.get())
            release.countDown()
            first.join()
            assertEquals("current", second.await().getOrThrow())
            assertTrue(first.isCancelled)
            assertEquals(2, calls.get())
        } finally {
            release.countDown()
            first.cancelAndJoin()
            second?.cancelAndJoin()
        }
    }

    @Test fun modelLoadWaitsUntilActiveNativeGenerationReturns() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        doAnswer {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            ProofreadService.ModelHolder.onNativeToken(helper, "complete")
            mapOf("text" to "complete")
        }.`when`(native).launchCompletion(anyInt(), anyMap())
        val generation = async(Dispatchers.IO) { ProofreadService(context).proofread("first") }
        var load: Deferred<Boolean>? = null
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            load = async(Dispatchers.Unconfined) {
                ProofreadService.ModelHolder.loadModel(context, service.getModelPath()!!)
            }
            assertFalse(load.isCompleted)
            release.countDown()
            assertEquals("complete", generation.await().getOrThrow())
            assertTrue(load.await())
        } finally {
            release.countDown()
            generation.cancelAndJoin()
            load?.cancelAndJoin()
        }
    }

    @Test fun modelUnloadCannotReleaseAnActiveNativeContext() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        doAnswer {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            ProofreadService.ModelHolder.onNativeToken(helper, "complete")
            mapOf("text" to "complete")
        }.`when`(native).launchCompletion(anyInt(), anyMap())
        val generation = async(Dispatchers.IO) { ProofreadService(context).proofread("first") }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val unload = async(Dispatchers.Unconfined) { ProofreadService.ModelHolder.unloadModelAndWait() }
            assertFalse(unload.isCompleted)
            verify(helper, never()).release()
            release.countDown()
            assertEquals("complete", generation.await().getOrThrow())
            unload.join()
            verify(helper).release()
            assertFalse(ProofreadService.ModelHolder.isModelLoaded)
        } finally {
            release.countDown()
            generation.cancelAndJoin()
        }
    }

    @Test fun failedGenerationDoesNotLogBackendExceptionContent() = runBlocking {
        fail = true
        ShadowLog.clear()
        assertTrue(service.proofread("PRIVATE_EDITOR_INPUT").isFailure)
        val logs = ShadowLog.getLogs().joinToString("\n") { it.msg + (it.throwable?.message ?: "") }
        assertFalse(logs.contains("PRIVATE_EDITOR_INPUT"))
        assertFalse(logs.contains("PRIVATE_BACKEND_ERROR"))
    }

    @Test fun nullNativeCompletionIsAnErrorNotOriginalTextSuccess() = runBlocking {
        doReturn(null).`when`(native).launchCompletion(anyInt(), anyMap())
        assertTrue(service.proofread("original").isFailure)
    }

    @Test fun dependencyWrapperCannotLogGenerationPrompt() = runBlocking {
        val wrapper = LlamaAndroid(context.contentResolver)
        val nativeContext = mock(LlamaContext::class.java)
        val contexts = LlamaAndroid::class.java.getDeclaredField("contexts").apply { isAccessible = true }
            .get(wrapper) as MutableMap<Int, LlamaContext>
        contexts[1] = nativeContext
        field("llama\$delegate", lazy { wrapper })
        `when`(nativeContext.completion(anyMap())).thenAnswer {
            assertTrue((it.getArgument<Map<String, Any>>(0)["prompt"] as String).contains("PRIVATE_EDITOR_INPUT"))
            ProofreadService.ModelHolder.onNativeToken(helper, "PRIVATE_GENERATED_OUTPUT")
            mapOf("text" to "PRIVATE_GENERATED_OUTPUT")
        }
        ShadowLog.clear()
        assertEquals("PRIVATE_GENERATED_OUTPUT", service.proofread("PRIVATE_EDITOR_INPUT").getOrThrow())
        val logs = ShadowLog.getLogs().joinToString("\n") { it.msg + (it.throwable?.message ?: "") }
        assertFalse(logs.contains("PRIVATE_EDITOR_INPUT"))
        assertFalse(logs.contains("PRIVATE_GENERATED_OUTPUT"))
    }

    private fun field(name: String, value: Any) {
        LlamaHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
    }

    private class QueueDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    @Implements(LlamaAndroid::class)
    class NativeShadow {
        companion object {
            @Implementation @JvmStatic fun __staticInitializer__() = Unit
        }
    }

    @Implements(LlamaContext::class)
    class ContextShadow {
        companion object {
            @Implementation @JvmStatic fun __staticInitializer__() = Unit
        }
    }
}
