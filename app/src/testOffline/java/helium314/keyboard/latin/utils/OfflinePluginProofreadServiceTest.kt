// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.ai.IOfflineAiProvider
import helium314.keyboard.latin.ai.OfflineAiLoader
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.SettingsWithoutKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [OfflinePluginProofreadServiceTest.LoaderShadow::class])
class OfflinePluginProofreadServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val service = ProofreadService(context)
    private val provider = FakeProvider()
    private val modelUri = "content://test.documents/existing-model.gguf"

    @Before fun setUp() {
        ProofreadService.ModelHolder.cancelUnload()
        ProofreadService.ModelHolder.unloadModel()
        LoaderShadow.provider = provider
        LoaderShadow.requestedContexts.clear()
        context.prefs().edit().clear()
            .putBoolean(Settings.PREF_OFFLINE_KEEP_MODEL_LOADED, true)
            .putString("offline_model_path", modelUri).commit()
        service.setSystemPrompt("Correct {text}")
    }

    @After fun tearDown() {
        ProofreadService.ModelHolder.cancelUnload()
        ProofreadService.ModelHolder.unloadModel()
        LoaderShadow.provider = null
        LoaderShadow.requestedContexts.clear()
    }

    @Test fun proofreadLoadsTheProviderAndForwardsPromptAndSamplingSettings() = runBlocking {
        service.prefs.edit()
            .putFloat(Settings.PREF_OFFLINE_TEMP, 0.25f)
            .putFloat(Settings.PREF_OFFLINE_TOP_P, 0.5f)
            .putInt(Settings.PREF_OFFLINE_TOP_K, 17)
            .putFloat(Settings.PREF_OFFLINE_MIN_P, 0.125f)
            .putInt(Settings.PREF_OFFLINE_MAX_TOKENS, 321).commit()

        assertEquals("Corrected text.", service.proofread("teh text").getOrThrow())

        assertEquals(listOf(LoadRequest(
            context, modelUri, minOf(Runtime.getRuntime().availableProcessors(), 4), 2048
        )), provider.loads)
        assertEquals(listOf(GenerationRequest("Correct teh text", mapOf(
            "temperature" to 0.25,
            "top_p" to 0.5,
            "top_k" to 17,
            "min_p" to 0.125,
            "max_tokens" to 321
        ))), provider.generations)
        assertTrue(LoaderShadow.requestedContexts.isNotEmpty())
        assertTrue(LoaderShadow.requestedContexts.all { it === context })
        assertTrue(ProofreadService.ModelHolder.isModelLoaded)
        assertTrue(ProofreadService.ModelHolder.isModelAvailable)
        assertEquals(modelUri, ProofreadService.ModelHolder.currentModelPath)
    }

    @Test fun recreatedServiceReusesThePluginLoadedModel() = runBlocking {
        service.proofread("first").getOrThrow()
        ProofreadService(context).proofread("second").getOrThrow()

        assertEquals(1, provider.loads.size)
        assertEquals(2, provider.generations.size)
    }

    @Test fun hostDoesNotLogProofreadingInputOrOutput() = runBlocking {
        val input = "private-input-fixture"
        provider.output = "private-output-fixture"
        ShadowLog.clear()

        assertEquals(provider.output, service.proofread(input).getOrThrow())

        val messages = ShadowLog.getLogsForTag("LlamaProofreadService").map { it.msg }
        assertFalse(messages.any { input in it || provider.output in it })
    }

    @Test fun pluginLosingItsModelTriggersAReload() = runBlocking {
        service.proofread("first").getOrThrow()
        provider.loaded = false
        service.proofread("second").getOrThrow()

        assertEquals(listOf(modelUri, modelUri), provider.loads.map { it.path })
        assertEquals(2, provider.generations.size)
    }

    @Test fun changingTheSelectedModelLoadsTheNewUriThroughThePlugin() = runBlocking {
        service.proofread("first").getOrThrow()
        val secondUri = "content://test.documents/second-model.gguf"
        service.setModelPath(secondUri)
        service.proofread("second").getOrThrow()

        assertEquals(listOf(modelUri, secondUri), provider.loads.map { it.path })
        assertEquals(secondUri, service.getModelPath())
        assertEquals(2, provider.unloads)
    }

    @Test fun noSelectedModelDoesNotCallTheProvider() = runBlocking {
        service.setModelPath(null)
        val result = service.proofread("original")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("select a GGUF model"))
        assertTrue(provider.loads.isEmpty())
        assertTrue(provider.generations.isEmpty())
    }

    @Test fun noInstalledPluginReturnsActionableFailure() = runBlocking {
        LoaderShadow.provider = null
        val result = service.proofread("original")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("plugin not installed"))
        assertEquals(modelUri, service.getModelPath())
        assertTrue(provider.loads.isEmpty())
        assertTrue(provider.generations.isEmpty())
    }

    @Test fun refusedPluginModelLoadDoesNotGenerate() = runBlocking {
        provider.loadSucceeds = false
        val result = service.proofread("original")

        assertTrue(result.isFailure)
        assertEquals("Failed to load model in AI plugin.", result.exceptionOrNull()?.message)
        assertFalse(ProofreadService.ModelHolder.isModelLoaded)
        assertFalse(ProofreadService.ModelHolder.isModelAvailable)
        assertEquals(1, provider.loads.size)
        assertTrue(provider.generations.isEmpty())
    }

    @Test fun pluginLoadExceptionReturnsFailureWithoutGenerating() = runBlocking {
        provider.loadError = IllegalStateException("fixture load failure")
        val result = service.proofread("original")

        assertTrue(result.isFailure)
        assertEquals("Failed to load model in AI plugin.", result.exceptionOrNull()?.message)
        assertFalse(ProofreadService.ModelHolder.isModelAvailable)
        assertEquals(1, provider.loads.size)
        assertTrue(provider.generations.isEmpty())
    }

    @Test fun pluginGenerationExceptionReturnsTheUpstreamFailure() = runBlocking {
        provider.generateError = IllegalStateException("fixture generation failure")
        val result = service.proofread("original")

        assertTrue(result.isFailure)
        assertEquals("fixture generation failure", result.exceptionOrNull()?.message)
        assertEquals(1, provider.loads.size)
        assertEquals(1, provider.generations.size)
    }

    @Test fun translationUsesCanonicalTargetInTheProviderGenerationPrompt() = runBlocking {
        service.prefs.edit()
            .putString(SettingsWithoutKey.GEMINI_TARGET_LANGUAGE, "fr")
            .putString(Settings.PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE, "German").commit()
        service.setTranslateSystemPrompt("Translate {text} into {lang}.")
        provider.output = "Bonjour."

        assertEquals("Bonjour.", service.translate("Hello.").getOrThrow())
        assertEquals("Translate Hello. into fr.", provider.generations.single().prompt)
        assertEquals(1, provider.loads.size)
    }

    @Test fun translationWithoutTextPlaceholderUsesTheUpstreamInstructionTemplate() = runBlocking {
        service.setTargetLanguage("fr")
        service.setTranslateSystemPrompt("Translate to {lang}.")

        service.translate("Hello.").getOrThrow()

        assertEquals("Instruction: Translate to fr.\n\nInput: Hello.\nOutput:",
            provider.generations.single().prompt)
    }

    @Test fun proofreadCleansEchoedPromptAndOutputPrefix() = runBlocking {
        provider.output = "Correct original\nOutput: \"Corrected text.\""

        assertEquals("Corrected text.", service.proofread("original").getOrThrow())
        assertEquals(1, provider.generations.size)
    }

    @Test fun emptyPluginOutputKeepsTheOriginalText() = runBlocking {
        provider.output = ""

        assertEquals("original", service.proofread("original").getOrThrow())
        assertEquals(1, provider.generations.size)
    }

    @Test fun showThinkingOverrideUsesUpstreamOutputProcessing() = runBlocking {
        provider.output = "<thinking>reasoning</thinking>Corrected text."

        assertEquals("Corrected text.", service.proofread("original", showThinking = false).getOrThrow())
        assertEquals(provider.output, service.proofread("original", showThinking = true).getOrThrow())
        assertEquals(2, provider.generations.size)
    }

    @Test fun providerFailureAndServiceRecreationPreserveStoredModelAndPreferences() = runBlocking {
        service.prefs.edit()
            .putString(Settings.PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE, "French")
            .putString("unrelated_user_preference", "keep me").commit()
        val before = service.prefs.all.toMap()
        provider.generateError = IllegalStateException("fixture generation failure")

        assertTrue(ProofreadService(context).proofread("original").isFailure)
        service.unloadModel()

        val recreated = ProofreadService(context)
        assertEquals(before, recreated.prefs.all)
        assertEquals(modelUri, recreated.getModelPath())
        assertEquals("French", recreated.getTargetLanguage())
    }

    data class LoadRequest(val context: Context, val path: String, val threads: Int, val nCtx: Int)
    data class GenerationRequest(val prompt: String, val params: Map<String, Any>)

    private class FakeProvider : IOfflineAiProvider {
        val loads = mutableListOf<LoadRequest>()
        val generations = mutableListOf<GenerationRequest>()
        var unloads = 0
        var loaded = false
        var loadSucceeds = true
        var loadError: RuntimeException? = null
        var generateError: RuntimeException? = null
        var output = "Corrected text."

        override fun init(context: Context) = Unit
        override fun isAvailable() = true
        override fun isModelLoaded() = loaded
        override fun loadModel(context: Context, modelPath: String, threads: Int, nCtx: Int): Boolean {
            loads.add(LoadRequest(context, modelPath, threads, nCtx))
            loadError?.let { throw it }
            loaded = loadSucceeds
            return loadSucceeds
        }
        override fun unloadModel() { unloads++; loaded = false }
        override fun generate(prompt: String, params: Map<String, Any>?): String {
            generations.add(GenerationRequest(prompt, params.orEmpty().toMap()))
            generateError?.let { throw it }
            return output
        }
        override fun proofread(text: String, instruction: String?): String =
            error("The upstream service must build its prompt and call generate")
        override fun translate(text: String, sourceLang: String, targetLang: String): String =
            error("The upstream service translates through its proofreading prompt")
        override fun cleanup() = Unit
    }

    @Implements(OfflineAiLoader::class)
    class LoaderShadow {
        companion object {
            var provider: IOfflineAiProvider? = null
            val requestedContexts = mutableListOf<Context>()
        }
        @Implementation fun getProvider(context: Context): IOfflineAiProvider? {
            requestedContexts.add(context)
            return provider
        }
    }
}
