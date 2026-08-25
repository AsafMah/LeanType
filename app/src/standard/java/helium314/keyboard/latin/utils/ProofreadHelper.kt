/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputConnection
import helium314.keyboard.latin.RichInputMethodManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Helper class to handle proofreading async operations from Java code.
 * This avoids the complexity of Java-Kotlin coroutine interop.
 */
object ProofreadHelper {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Track current operation for cancellation
    private var currentJob: Job? = null
    
    // Check if an operation is in progress
    @JvmStatic
    val isOperationInProgress: Boolean
        get() = currentJob?.isActive == true
    
    // Store original text for potential undo
    @JvmStatic
    var lastOriginalText: String? = null
        private set
    
    /**
     * Preload the model in the background to avoid initial latency.
     */
    @JvmStatic
    fun preloadModel(context: Context) {
        // No-op for standard flavor (runs API based proofreader)
    }

    /**
     * Cancel the current proofreading/translation operation if one is in progress.
     */
    @JvmStatic
    fun cancelCurrentOperation() {
        if (currentJob?.isActive == true) {
            currentJob?.cancel()
            currentJob = null
            mainHandler.post {
                KeyboardSwitcher.getInstance().hideLoadingAnimation()
                // Toast removed as visual feedback (stopping animation) is sufficient
            }
        }
    }
    
    private fun performAsyncOperation(
        context: Context,
        text: String,
        noTextErrorResId: Int,
        errorResId: Int,
        apiCall: suspend (ProofreadService) -> Result<String>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
        allowEmptyInput: Boolean = false,
        skipApiKeyCheck: Boolean = false
    ) {
        val service = ProofreadService(context)

        // Check if API key/token is configured based on provider (unless plugin handles operation)
        if (!skipApiKeyCheck) {
            val provider = service.getProvider()
            when (provider) {
                ProofreadService.AIProvider.GEMINI -> {
                    if (!service.hasApiKey()) {
                        mainHandler.post {
                            KeyboardSwitcher.getInstance().showToast(
                                context.getString(R.string.proofread_no_api_key),
                                true
                            )
                        }
                        return
                    }
                }
                ProofreadService.AIProvider.GROQ -> {
                    if (service.getGroqToken() == null) {
                        mainHandler.post {
                            KeyboardSwitcher.getInstance().showToast(
                                context.getString(R.string.huggingface_no_token),
                                true
                            )
                        }
                        return
                    }
                }
                ProofreadService.AIProvider.OPENAI -> {
                    if (service.getHuggingFaceToken() == null) {
                        mainHandler.post {
                            KeyboardSwitcher.getInstance().showToast(
                                context.getString(R.string.huggingface_no_token),
                                true
                            )
                        }
                        return
                    }
                }
            }
        }

        if (!allowEmptyInput && text.isBlank()) {
            mainHandler.post {
                KeyboardSwitcher.getInstance().showToast(
                    context.getString(noTextErrorResId),
                    true
                )
            }
            return
        }

        // Store original text for undo
        lastOriginalText = text

        // Show loading animation on suggestion strip
        mainHandler.post {
            KeyboardSwitcher.getInstance().showLoadingAnimation()
        }

        // Launch coroutine for API call and track it for cancellation
        currentJob = scope.launch {
            val result = apiCall(service)

            mainHandler.post {
                currentJob = null
                // Hide loading animation
                KeyboardSwitcher.getInstance().hideLoadingAnimation()

                result.fold(
                    onSuccess = { resultText ->
                        onSuccess(resultText)
                    },
                    onFailure = { error ->
                        onError(error.message ?: "Unknown error")
                        KeyboardSwitcher.getInstance().showToast(
                            context.getString(errorResId, error.message ?: "Unknown error"),
                            false
                        )
                    }
                )
            }
        }
    }

    /**
     * Proofread text asynchronously and call the callback with the result.
     * 
     * @param context Application context
     * @param text Text to proofread
     * @param hasSelection Whether text was selected (false = entire field)
     * @param onSuccess Callback with proofread text
     * @param onError Callback with error message
     */
    @JvmStatic
    fun proofreadAsync(
        context: Context,
        text: String,
        hasSelection: Boolean,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        performAsyncOperation(
            context = context,
            text = text,
            noTextErrorResId = R.string.proofread_no_text,
            errorResId = R.string.proofread_error,
            apiCall = { service -> service.proofread(text) },
            onSuccess = onSuccess,
            onError = onError
        )
    }
    
    /**
     * Simple Java-friendly interface for proofreading.
     */
    interface ProofreadCallback {
        fun onSuccess(proofreadText: String)
        fun onError(errorMessage: String)
    }
    
    /**
     * Java-friendly version using callback interface.
     */
    @JvmStatic
    fun proofreadAsync(
        context: Context,
        text: String,
        hasSelection: Boolean,
        callback: ProofreadCallback
    ) {
        proofreadAsync(
            context = context,
            text = text,
            hasSelection = hasSelection,
            onSuccess = { callback.onSuccess(it) },
            onError = { callback.onError(it) }
        )
    }

    private fun getLangCode(targetLang: String): String {
        val trimmed = targetLang.trim()
        if (trimmed.length == 2) return trimmed.lowercase()
        if (trimmed.contains("-")) return trimmed.substringBefore("-").lowercase()
        return when (trimmed.lowercase()) {
            "english" -> "en"
            "spanish" -> "es"
            "french" -> "fr"
            "german" -> "de"
            "italian" -> "it"
            "portuguese" -> "pt"
            "chinese", "chinese (simplified)", "chinese (traditional)" -> "zh"
            "japanese" -> "ja"
            "korean" -> "ko"
            "arabic" -> "ar"
            "russian" -> "ru"
            "hindi" -> "hi"
            "bengali" -> "bn"
            "indonesian" -> "id"
            "dutch" -> "nl"
            "turkish" -> "tr"
            "polish" -> "pl"
            "ukrainian" -> "uk"
            "swedish" -> "sv"
            "danish" -> "da"
            "norwegian" -> "no"
            "finnish" -> "fi"
            "greek" -> "el"
            "hebrew" -> "he"
            "thai" -> "th"
            "vietnamese" -> "vi"
            "tamil" -> "ta"
            "telugu" -> "te"
            "marathi" -> "mr"
            "gujarati" -> "gu"
            "kannada" -> "kn"
            "malayalam" -> "ml"
            "urdu" -> "ur"
            "persian (farsi)", "persian", "farsi" -> "fa"
            "swahili" -> "sw"
            "romanian" -> "ro"
            "czech" -> "cs"
            "hungarian" -> "hu"
            "filipino (tagalog)", "tagalog", "filipino" -> "tl"
            "malay" -> "ms"
            "serbian" -> "sr"
            "croatian" -> "hr"
            "bulgarian" -> "bg"
            "slovak" -> "sk"
            "slovenian" -> "sl"
            "lithuanian" -> "lt"
            "latvian" -> "lv"
            "estonian" -> "et"
            "catalan" -> "ca"
            "basque" -> "eu"
            "afrikaans" -> "af"
            "albanian" -> "sq"
            "belarusian" -> "be"
            "esperanto" -> "eo"
            "galician" -> "gl"
            "georgian" -> "ka"
            "haitian creole", "haitian" -> "ht"
            "icelandic" -> "is"
            "irish" -> "ga"
            "macedonian" -> "mk"
            "maltese" -> "mt"
            "welsh" -> "cy"
            else -> trimmed.take(2).lowercase()
        }
    }

    private fun detectSourceLanguage(text: String): String {
        for (cp in text.codePoints()) {
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_TAMIL)) return "ta"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_MALAYALAM)) return "ml"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_TELUGU)) return "te"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_KANNADA)) return "kn"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_GUJARATI)) return "gu"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_BENGALI)) return "bn"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_DEVANAGARI)) return "hi"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_ARABIC)) return "ar"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_GREEK)) return "el"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_HEBREW)) return "he"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_HANGUL)) return "ko"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_THAI)) return "th"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_GEORGIAN)) return "ka"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_ARMENIAN)) return "hy"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_SINHALA)) return "si"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_MYANMAR)) return "my"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_KHMER)) return "km"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_LAO)) return "lo"
        }
        try {
            val currentSubtype = RichInputMethodManager.getInstance().currentSubtype
            val lang = currentSubtype.locale.language
            if (lang.isNotBlank() && lang != "zz") {
                return lang.lowercase()
            }
        } catch (_: Throwable) {}
        return "auto"
    }

    /**
     * Translate text asynchronously and call the callback with the result.
     * 
     * @param context Application context
     * @param text Text to translate
     * @param hasSelection Whether text was selected (false = entire field)
     * @param onSuccess Callback with translated text
     * @param onError Callback with error message
     */
    @JvmStatic
    fun translateAsync(
        context: Context,
        text: String,
        hasSelection: Boolean,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val prefs = context.prefs()
        val translationEngine = prefs.getString("pref_translation_engine", prefs.getString("pref_translation_method", "auto") ?: "auto") ?: "auto"
        val translationMode = prefs.getString("pref_translation_mode", "auto") ?: "auto"
        val isOfflineOnly = translationMode == "offline_only"
        val isOnlineOnly = translationMode == "online_only"

        val hasPlugin = helium314.keyboard.latin.translation.TranslationLoader.hasPlugin(context)
        val usePlugin = when {
            isOfflineOnly -> true
            isOnlineOnly -> hasPlugin
            translationEngine == "plugin" -> hasPlugin
            translationEngine == "ai" -> false
            else -> hasPlugin
        }
        performAsyncOperation(
            context = context,
            text = text,
            noTextErrorResId = R.string.translate_no_text,
            errorResId = R.string.translate_error,
            skipApiKeyCheck = usePlugin || isOfflineOnly,
            apiCall = { service ->
                val pluginProvider = if (usePlugin) helium314.keyboard.latin.translation.TranslationLoader.getProvider(context) else null
                val targetLang = service.getTargetLanguage()
                val targetLangCode = getLangCode(targetLang)
                val sourceLangCode = detectSourceLanguage(text)
                val requiredModelCode = if (targetLangCode == "en") sourceLangCode else targetLangCode

                if (isOfflineOnly) {
                    if (pluginProvider == null || !pluginProvider.isAvailable()) {
                        mainHandler.post {
                            KeyboardSwitcher.getInstance().showToast(
                                context.getString(R.string.translation_model_not_downloaded),
                                true
                            )
                        }
                        return@performAsyncOperation Result.failure(
                            Exception(context.getString(R.string.translation_model_not_downloaded))
                        )
                    }

                    val isDownloaded = if (requiredModelCode == "auto" || requiredModelCode == "en") {
                        true
                    } else {
                        try {
                            pluginProvider.isModelDownloaded(requiredModelCode)
                        } catch (_: Throwable) {
                            false
                        }
                    }

                    if (!isDownloaded) {
                        mainHandler.post {
                            KeyboardSwitcher.getInstance().showToast(
                                context.getString(R.string.translation_model_not_downloaded),
                                true
                            )
                        }
                        return@performAsyncOperation Result.failure(
                            Exception(context.getString(R.string.translation_model_not_downloaded))
                        )
                    }

                    try {
                        Log.i("ProofreadHelper", "Translating via Offline ML Kit (source: $sourceLangCode, target: $targetLangCode, model: $requiredModelCode)")
                        val result = pluginProvider.translate(text, targetLangCode, sourceLangCode)
                        if (result.isNotBlank()) {
                            Result.success(result)
                        } else {
                            mainHandler.post {
                                KeyboardSwitcher.getInstance().showToast(
                                    context.getString(R.string.translation_model_not_downloaded),
                                    true
                                )
                            }
                            Result.failure(Exception(context.getString(R.string.translation_model_not_downloaded)))
                        }
                    } catch (e: Throwable) {
                        Log.e("ProofreadHelper", "Offline translation failed", e)
                        mainHandler.post {
                            KeyboardSwitcher.getInstance().showToast(
                                context.getString(R.string.translation_model_not_downloaded),
                                true
                            )
                        }
                        Result.failure(e)
                    }
                } else if (pluginProvider != null && pluginProvider.isAvailable()) {
                    try {
                        Log.i("ProofreadHelper", "Translating via Translation Plugin (source: $sourceLangCode, target: $targetLangCode)")
                        val result = pluginProvider.translate(text, targetLangCode, sourceLangCode)
                        if (result.isNotBlank()) {
                            Result.success(result)
                        } else if (translationEngine == "plugin") {
                            Result.failure(Exception("Plugin translation returned empty result"))
                        } else {
                            Log.w("ProofreadHelper", "Plugin returned blank text, falling back to built-in AI")
                            service.translate(text)
                        }
                    } catch (e: Throwable) {
                        if (translationEngine == "plugin") {
                            Result.failure(e)
                        } else {
                            Log.e("ProofreadHelper", "Plugin translation failed, falling back to built-in AI", e)
                            service.translate(text)
                        }
                    }
                } else if (translationEngine == "plugin") {
                    mainHandler.post {
                        KeyboardSwitcher.getInstance().showToast(
                            context.getString(R.string.translation_model_not_downloaded),
                            true
                        )
                    }
                    Result.failure(Exception("Translation plugin not available"))
                } else {
                    Log.i("ProofreadHelper", "Translating via built-in AI service")
                    service.translate(text)
                }
            },
            onSuccess = onSuccess,
            onError = onError
        )
    }
    
    /**
     * Simple Java-friendly interface for translation (reuses ProofreadCallback).
     */
    @JvmStatic
    fun translateAsync(
        context: Context,
        text: String,
        hasSelection: Boolean,
        callback: ProofreadCallback
    ) {
        translateAsync(
            context = context,
            text = text,
            hasSelection = hasSelection,
            onSuccess = { callback.onSuccess(it) },
            onError = { callback.onError(it) }
        )
    }
    /**
     * Perform custom AI action asynchronously.
     */
    @JvmStatic
    fun customAsync(
        context: Context,
        text: String,
        prompt: String,
        hasSelection: Boolean,
        showThinking: Boolean,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        performAsyncOperation(
            context = context,
            text = text,
            noTextErrorResId = R.string.proofread_no_text,
            errorResId = R.string.proofread_error,
            apiCall = { service -> service.proofread(text, overridePrompt = prompt, showThinking = showThinking) },
            onSuccess = onSuccess,
            onError = onError,
            allowEmptyInput = true
        )
    }

    /**
     * Java-friendly interface for custom action.
     */
    @JvmStatic
    fun customAsync(
        context: Context,
        text: String,
        prompt: String,
        hasSelection: Boolean,
        showThinking: Boolean = false,
        callback: ProofreadCallback
    ) {
        customAsync(
            context = context,
            text = text,
            prompt = prompt,
            hasSelection = hasSelection,
            showThinking = showThinking,
            onSuccess = { callback.onSuccess(it) },
            onError = { callback.onError(it) }
        )
    }
}
