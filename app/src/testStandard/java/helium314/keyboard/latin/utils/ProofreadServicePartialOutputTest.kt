package helium314.keyboard.latin.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.R
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProofreadServicePartialOutputTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun geminiProofreadMaxTokensFinishReasonReturnsFailure() = runBlocking {
        val service = ProofreadService(
            context,
            geminiModelFactory = fakeGeminiFactory(
                GeminiContentResult("corrected but cut off", "MAX_TOKENS")
            ),
            connectionFactory = { error("HTTP must not be used for Gemini") }
        )
        service.setApiKey("fake-gemini-key")
        service.setProvider(ProofreadService.AIProvider.GEMINI)

        val result = service.proofread("teh original")

        assertTrue(result.isFailure)
        assertEquals(
            context.getString(R.string.ai_output_truncated),
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun geminiTranslateLengthFinishReasonReturnsFailure() = runBlocking {
        val service = ProofreadService(
            context,
            geminiModelFactory = fakeGeminiFactory(
                GeminiContentResult("translation but cut off", "LENGTH")
            ),
            connectionFactory = { error("HTTP must not be used for Gemini") }
        )
        service.setApiKey("fake-gemini-key")
        service.setProvider(ProofreadService.AIProvider.GEMINI)

        val result = service.translate("hello world")

        assertTrue(result.isFailure)
        assertEquals(
            context.getString(R.string.ai_output_truncated),
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun openAiLengthFinishReasonThroughHttpResponseReturnsFailure() = runBlocking {
        val response = """
            {
              "choices": [
                {
                  "finish_reason": "length",
                  "message": {
                    "content": "proofread text but cut off"
                  }
                }
              ]
            }
        """.trimIndent()
        val connection = FakeHttpURLConnection(URL("https://example.invalid/v1/chat/completions"), response)
        val service = ProofreadService(
            context,
            geminiModelFactory = fakeGeminiFactory(GeminiContentResult(null, "")),
            connectionFactory = { connection }
        )
        service.setProvider(ProofreadService.AIProvider.OPENAI)
        service.setHuggingFaceToken("fake-openai-token")
        service.setHuggingFaceModel("gpt-test")
        service.setHuggingFaceEndpoint("https://example.invalid/v1/chat/completions")

        val result = service.proofread("teh original")

        assertTrue(result.isFailure)
        assertEquals(
            context.getString(R.string.ai_output_truncated),
            result.exceptionOrNull()?.message
        )
        assertTrue(connection.requestBody.contains("\"model\":\"gpt-test\""))
        val translation = service.translate("hello world")
        assertTrue(translation.isFailure)
        assertEquals(context.getString(R.string.ai_output_truncated),
            translation.exceptionOrNull()?.message)
    }

    @Test
    fun completedGeminiProofreadingAndTranslationRemainSuccessful() = runBlocking {
        val configs = mutableListOf<GeminiContentModelConfig>()
        val service = ProofreadService(
            context,
            geminiModelFactory = fakeGeminiFactory(GeminiContentResult("A complete response.", "STOP")) {
                configs.add(it)
            },
            connectionFactory = { error("HTTP must not be used for Gemini") }
        )
        service.setApiKey("fake-gemini-key")
        service.setProvider(ProofreadService.AIProvider.GEMINI)

        assertEquals("A complete response.", service.proofread("teh original").getOrThrow())
        assertEquals("A complete response.", service.translate("hello world").getOrThrow())
        assertEquals(listOf(0.1f, 0.3f), configs.map { it.temperature })
    }

    @Test
    fun completedHttpResponseRemainsSuccessful() = runBlocking {
        val response = """{"choices":[{"finish_reason":"stop","message":{"content":"A complete response."}}]}"""
        val connection = FakeHttpURLConnection(URL("https://example.invalid/v1/chat/completions"), response)
        val service = ProofreadService(
            context,
            geminiModelFactory = fakeGeminiFactory(GeminiContentResult(null, "")),
            connectionFactory = { connection }
        )
        service.setProvider(ProofreadService.AIProvider.OPENAI)
        service.setHuggingFaceToken("fake-openai-token")
        service.setHuggingFaceModel("gpt-test")
        service.setHuggingFaceEndpoint("https://example.invalid/v1/chat/completions")

        assertEquals("A complete response.", service.proofread("teh original").getOrThrow())
        assertEquals("A complete response.", service.translate("hello world").getOrThrow())
    }

    @Test
    fun geminiProofreadingAndTranslationHonorCloudTokenSetting() = runBlocking {
        val configs = mutableListOf<GeminiContentModelConfig>()
        val service = ProofreadService(
            context,
            geminiModelFactory = fakeGeminiFactory(GeminiContentResult("A complete response.", "STOP")) {
                configs.add(it)
            },
            connectionFactory = { error("HTTP must not be used for Gemini") }
        )
        service.setApiKey("fake-gemini-key")
        service.setProvider(ProofreadService.AIProvider.GEMINI)
        service.setCloudMaxTokens(2048)

        service.proofread("teh original").getOrThrow()
        service.translate("hello world").getOrThrow()

        assertEquals(listOf(2048, 2048), configs.map { it.maxOutputTokens })
    }

    private fun fakeGeminiFactory(
        result: GeminiContentResult,
        onConfig: (GeminiContentModelConfig) -> Unit = {},
    ) = GeminiContentModelFactory { config ->
        onConfig(config)
        object : GeminiContentModel {
            override suspend fun generateContent(prompt: String): GeminiContentResult = result
        }
    }

    private class FakeHttpURLConnection(url: URL, private val response: String) : HttpURLConnection(url) {
        private val postedBody = ByteArrayOutputStream()

        val requestBody: String
            get() = postedBody.toString(Charsets.UTF_8.name())

        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun getOutputStream(): OutputStream = postedBody

        override fun getResponseCode(): Int = HTTP_OK

        override fun getInputStream(): InputStream =
            ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
    }
}
