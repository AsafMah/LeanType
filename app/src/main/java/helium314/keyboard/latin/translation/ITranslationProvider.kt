// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translation

import android.content.Context

fun interface TranslationModelDownloadListener {
    fun onComplete(success: Boolean)
}

interface ITranslationProvider {
    /** Interface version number to ensure backward/forward compatibility. */
    fun getInterfaceVersion(): Int = 2

    /** Initialize provider with Application Context to prevent memory leaks. */
    fun init(context: Context)

    /**
     * Translates text synchronously.
     * @param text Original text to translate
     * @param targetLang Target language ISO code or name (e.g. "es", "fr", "Spanish")
     * @param sourceLang Source language ISO code or "auto"
     * @return Translated text string
     */
    fun translate(text: String, targetLang: String, sourceLang: String = "auto"): String

    /** Check if provider is ready (e.g. models downloaded or API key configured). */
    fun isAvailable(): Boolean

    /** Release heavy resources / models. */
    fun cleanup()

    /** Returns list of supported language codes for offline translation. */
    fun getSupportedLanguages(): List<String> = emptyList()

    /** Check if a specific language model is downloaded offline. */
    fun isModelDownloaded(langCode: String): Boolean = false

    /** Trigger download of a language model. */
    fun downloadModel(langCode: String, listener: TranslationModelDownloadListener) { listener.onComplete(false) }

    /** Delete a downloaded language model to free storage. */
    fun deleteModel(langCode: String): Boolean = false
}
