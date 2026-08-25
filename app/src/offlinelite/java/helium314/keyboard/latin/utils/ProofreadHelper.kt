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
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.translation.TranslationLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ProofreadHelper for OfflineLite flavor.
 * AI proofread/custom is disabled, but Translation Plugin is fully supported.
 */
object ProofreadHelper {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentJob: Job? = null
    
    @JvmStatic
    val isOperationInProgress: Boolean
        get() = currentJob?.isActive == true
    
    @JvmStatic
    var lastOriginalText: String? = null
        private set
    
    @JvmStatic
    fun preloadModel(context: Context) {
        // No-op for offlinelite flavor (no AI support)
    }

    @JvmStatic
    fun cancelCurrentOperation() {
        currentJob?.cancel()
        currentJob = null
        mainHandler.post {
            KeyboardSwitcher.getInstance().hideLoadingAnimation()
        }
    }
    
    // Callback interface
    interface ProofreadCallback {
        fun onSuccess(proofreadText: String)
        fun onError(errorMessage: String)
    }

    @JvmStatic
    fun proofreadAsync(
        context: Context,
        text: String,
        hasSelection: Boolean,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        showNotSupportedToast()
    }
    
    @JvmStatic
    fun proofreadAsync(
        context: Context,
        text: String,
        hasSelection: Boolean,
        callback: ProofreadCallback
    ) {
        showNotSupportedToast()
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

    @JvmStatic
    fun translateAsync(
        context: Context,
        text: String,
        hasSelection: Boolean,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (text.isBlank()) {
            mainHandler.post {
                KeyboardSwitcher.getInstance().showToast(
                    context.getString(R.string.translate_no_text),
                    true
                )
            }
            return
        }

        val hasPlugin = TranslationLoader.hasPlugin(context)
        if (!hasPlugin) {
            mainHandler.post {
                KeyboardSwitcher.getInstance().showToast(
                    "Translation plugin not installed. Download in Settings > Plugins",
                    true
                )
            }
            onError("Translation plugin not installed")
            return
        }

        val provider = TranslationLoader.getProvider(context)
        if (provider == null || !provider.isAvailable()) {
            mainHandler.post {
                KeyboardSwitcher.getInstance().showToast(
                    context.getString(R.string.translation_model_not_downloaded),
                    true
                )
            }
            onError("Translation plugin not ready")
            return
        }

        val prefs = context.prefs()
        val targetLang = prefs.getString(Settings.PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE, "Spanish") ?: "Spanish"
        val langCode = getLangCode(targetLang)

        val isDownloaded = try {
            provider.isModelDownloaded(langCode)
        } catch (_: Throwable) {
            false
        }

        if (!isDownloaded) {
            mainHandler.post {
                KeyboardSwitcher.getInstance().showToast(
                    context.getString(R.string.translation_model_not_downloaded),
                    true
                )
            }
            onError("Model for $targetLang not downloaded")
            return
        }

        lastOriginalText = text

        mainHandler.post {
            KeyboardSwitcher.getInstance().showLoadingAnimation()
        }

        currentJob = scope.launch(Dispatchers.IO) {
            try {
                val result = provider.translate(text, targetLang)
                mainHandler.post {
                    currentJob = null
                    KeyboardSwitcher.getInstance().hideLoadingAnimation()
                    if (result.isNotBlank()) {
                        onSuccess(result)
                    } else {
                        KeyboardSwitcher.getInstance().showToast(
                            context.getString(R.string.translation_model_not_downloaded),
                            true
                        )
                        onError("Translation returned empty result")
                    }
                }
            } catch (e: Throwable) {
                mainHandler.post {
                    currentJob = null
                    KeyboardSwitcher.getInstance().hideLoadingAnimation()
                    KeyboardSwitcher.getInstance().showToast(
                        context.getString(R.string.translate_error, e.message ?: "Unknown error"),
                        false
                    )
                    onError(e.message ?: "Unknown error")
                }
            }
        }
    }
    
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
        showNotSupportedToast()
    }

    @JvmStatic
    fun customAsync(
        context: Context,
        text: String,
        prompt: String,
        hasSelection: Boolean,
        showThinking: Boolean,
        callback: ProofreadCallback
    ) {
        showNotSupportedToast()
    }

    private fun showNotSupportedToast() {
        mainHandler.post {
            KeyboardSwitcher.getInstance().showToast("Not available in Lite version", false)
        }
    }
}
