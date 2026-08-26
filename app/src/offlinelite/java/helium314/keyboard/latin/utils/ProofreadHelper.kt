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
import helium314.keyboard.latin.RichInputMethodManager
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

    private fun getLanguageDisplayName(context: Context, code: String): String {
        val names = context.resources.getStringArray(R.array.translate_language_names)
        val codes = context.resources.getStringArray(R.array.translate_language_codes)
        val index = codes.indexOfFirst { it.equals(code, ignoreCase = true) }
        if (index != -1 && index < names.size) {
            return names[index]
        }
        val localeName = java.util.Locale(code).getDisplayLanguage(java.util.Locale.ENGLISH)
        return if (localeName.isNotBlank()) localeName else code.uppercase()
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

        if (!TranslationLoader.hasPlugin(context)) {
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

        val service = ProofreadService(context)
        val targetLang = service.getTargetLanguage()
        val targetLangCode = getLangCode(targetLang)
        val sourceLangCode = detectSourceLanguage(text)

        val missingModels = mutableListOf<String>()
        if (sourceLangCode != "auto" && sourceLangCode != "en") {
            try {
                if (!provider.isModelDownloaded(sourceLangCode)) {
                    missingModels.add(sourceLangCode)
                }
            } catch (_: Throwable) {
                missingModels.add(sourceLangCode)
            }
        }
        if (targetLangCode != "en" && !missingModels.contains(targetLangCode)) {
            try {
                if (!provider.isModelDownloaded(targetLangCode)) {
                    missingModels.add(targetLangCode)
                }
            } catch (_: Throwable) {
                missingModels.add(targetLangCode)
            }
        }

        if (missingModels.isNotEmpty()) {
            val missingNames = missingModels.joinToString(", ") { getLanguageDisplayName(context, it) }
            val errorMsg = context.getString(R.string.translation_specific_model_not_downloaded, missingNames)
            mainHandler.post {
                KeyboardSwitcher.getInstance().showToast(errorMsg, true)
            }
            onError(errorMsg)
            return
        }

        lastOriginalText = text

        mainHandler.post {
            KeyboardSwitcher.getInstance().showLoadingAnimation()
        }

        currentJob = scope.launch(Dispatchers.IO) {
            try {
                val result = provider.translate(text, targetLangCode, sourceLangCode)
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
