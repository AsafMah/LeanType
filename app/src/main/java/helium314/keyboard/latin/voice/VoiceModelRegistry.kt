// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import com.leanbitlab.leantype.voice.VoiceConstants

data class VoiceModelItem(
    val id: String,
    val displayName: String,
    val engineType: String,
    val language: String,
    val languageCode: String = "",
    val sizeMb: String,
    val downloadUrl: String,
    val browserUrl: String,
    val description: String = ""
)

object VoiceModelRegistry {
    val whisperModels = listOf(
        VoiceModelItem(
            id = "whisper-base-q5_1",
            displayName = "Whisper Base Q5_1",
            engineType = VoiceConstants.ENGINE_WHISPER,
            language = "Multilingual (99+ languages)",
            languageCode = "mul",
            sizeMb = "57 MB",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
            browserUrl = "https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-base-q5_1.bin",
            description = "High accuracy, quantized for fast CPU inference and low memory."
        ),
        VoiceModelItem(
            id = "whisper-tiny",
            displayName = "Whisper Tiny",
            engineType = VoiceConstants.ENGINE_WHISPER,
            language = "Multilingual (99+ languages)",
            languageCode = "mul",
            sizeMb = "39 MB",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
            browserUrl = "https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-tiny.bin",
            description = "Ultra-compact and fastest Whisper model."
        ),
        VoiceModelItem(
            id = "whisper-small-q5_1",
            displayName = "Whisper Small Q5_1",
            engineType = VoiceConstants.ENGINE_WHISPER,
            language = "Multilingual (99+ languages)",
            languageCode = "mul",
            sizeMb = "182 MB",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
            browserUrl = "https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-small-q5_1.bin",
            description = "Maximum recognition accuracy across multilingual accents."
        )
    )

    val voskModels = listOf(
        VoiceModelItem(
            id = "vosk-small-en-us",
            displayName = "Vosk US English",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "en-US",
            languageCode = "en",
            sizeMb = "40 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            description = "Lightweight streaming acoustic model for American English."
        ),
        VoiceModelItem(
            id = "vosk-small-en-in",
            displayName = "Vosk Indian English",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "en-IN",
            languageCode = "en",
            sizeMb = "36 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            description = "Tuned for Indian English accents and vocabulary."
        ),
        VoiceModelItem(
            id = "vosk-small-es",
            displayName = "Vosk Spanish",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "es",
            languageCode = "es",
            sizeMb = "39 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            description = "Streaming acoustic model for Spanish."
        ),
        VoiceModelItem(
            id = "vosk-small-fr",
            displayName = "Vosk French",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "fr",
            languageCode = "fr",
            sizeMb = "41 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            description = "Streaming acoustic model for French."
        ),
        VoiceModelItem(
            id = "vosk-small-de",
            displayName = "Vosk German",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "de",
            languageCode = "de",
            sizeMb = "45 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            description = "Streaming acoustic model for German."
        ),
        VoiceModelItem(
            id = "vosk-small-hi",
            displayName = "Vosk Hindi",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "hi",
            languageCode = "hi",
            sizeMb = "42 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            description = "Streaming acoustic model for Hindi."
        ),
        VoiceModelItem(
            id = "vosk-small-it",
            displayName = "Vosk Italian",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "it",
            languageCode = "it",
            sizeMb = "48 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            description = "Streaming acoustic model for Italian."
        ),
        VoiceModelItem(
            id = "vosk-small-pt",
            displayName = "Vosk Portuguese",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "pt",
            languageCode = "pt",
            sizeMb = "31 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            description = "Streaming acoustic model for Portuguese."
        ),
        VoiceModelItem(
            id = "vosk-small-ru",
            displayName = "Vosk Russian",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "ru",
            languageCode = "ru",
            sizeMb = "45 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            description = "Streaming acoustic model for Russian."
        ),
        VoiceModelItem(
            id = "vosk-small-ja",
            displayName = "Vosk Japanese",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "ja",
            languageCode = "ja",
            sizeMb = "48 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            description = "Streaming acoustic model for Japanese."
        ),
        VoiceModelItem(
            id = "vosk-small-cn",
            displayName = "Vosk Chinese",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "zh",
            languageCode = "zh",
            sizeMb = "42 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            description = "Streaming acoustic model for Mandarin Chinese."
        ),
        VoiceModelItem(
            id = "vosk-small-ko",
            displayName = "Vosk Korean",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "ko",
            languageCode = "ko",
            sizeMb = "67 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            description = "Streaming acoustic model for Korean."
        ),
        VoiceModelItem(
            id = "vosk-small-tr",
            displayName = "Vosk Turkish",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "tr",
            languageCode = "tr",
            sizeMb = "35 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            description = "Streaming acoustic model for Turkish."
        ),
        VoiceModelItem(
            id = "vosk-small-uk",
            displayName = "Vosk Ukrainian",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "uk",
            languageCode = "uk",
            sizeMb = "41 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-uk-v3-small.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            description = "Streaming acoustic model for Ukrainian."
        ),
        VoiceModelItem(
            id = "vosk-small-nl",
            displayName = "Vosk Dutch",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "nl",
            languageCode = "nl",
            sizeMb = "39 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-nl-0.22.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            description = "Streaming acoustic model for Dutch."
        ),
        VoiceModelItem(
            id = "vosk-small-pl",
            displayName = "Vosk Polish",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "pl",
            languageCode = "pl",
            sizeMb = "48 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-pl-0.22.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            description = "Streaming acoustic model for Polish."
        ),
        VoiceModelItem(
            id = "vosk-small-vn",
            displayName = "Vosk Vietnamese",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "vi",
            languageCode = "vi",
            sizeMb = "32 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-vn-0.4.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            description = "Streaming acoustic model for Vietnamese."
        )
    )

    fun findById(id: String): VoiceModelItem? {
        return whisperModels.firstOrNull { it.id == id }
            ?: voskModels.firstOrNull { it.id == id }
    }
}
