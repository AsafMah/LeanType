// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import com.leanbitlab.leantype.voice.VoiceConstants

data class VoiceModelItem(
    val id: String,
    val displayName: String,
    val engineType: String,
    val language: String,
    val sizeMb: String,
    val downloadUrl: String,
    val browserUrl: String,
    val isRecommended: Boolean = false,
    val description: String = ""
)

object VoiceModelRegistry {
    val whisperModels = listOf(
        VoiceModelItem(
            id = "whisper-base-q5_1",
            displayName = "Whisper Base Q5_1 (Recommended)",
            engineType = VoiceConstants.ENGINE_WHISPER,
            language = "Multilingual",
            sizeMb = "57 MB",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
            browserUrl = "https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-base-q5_1.bin",
            isRecommended = true,
            description = "High accuracy, quantized for fast CPU inference and low memory."
        ),
        VoiceModelItem(
            id = "whisper-tiny",
            displayName = "Whisper Tiny",
            engineType = VoiceConstants.ENGINE_WHISPER,
            language = "Multilingual",
            sizeMb = "39 MB",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
            browserUrl = "https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-tiny.bin",
            isRecommended = false,
            description = "Ultra-compact and fastest Whisper model."
        ),
        VoiceModelItem(
            id = "whisper-small-q5_1",
            displayName = "Whisper Small Q5_1",
            engineType = VoiceConstants.ENGINE_WHISPER,
            language = "Multilingual",
            sizeMb = "182 MB",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
            browserUrl = "https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-small-q5_1.bin",
            isRecommended = false,
            description = "Maximum recognition accuracy across multilingual accents."
        )
    )

    val voskModels = listOf(
        VoiceModelItem(
            id = "vosk-small-en-us",
            displayName = "Vosk Small US English (Recommended)",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "en-US",
            sizeMb = "40 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            isRecommended = true,
            description = "Lightweight streaming acoustic model for standard English."
        ),
        VoiceModelItem(
            id = "vosk-small-en-in",
            displayName = "Vosk Small Indian English",
            engineType = VoiceConstants.ENGINE_VOSK,
            language = "en-IN",
            sizeMb = "36 MB",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",
            browserUrl = "https://alphacephei.com/vosk/models",
            isRecommended = false,
            description = "Tuned for Indian English accents and vocabulary."
        )
    )

    fun findById(id: String): VoiceModelItem? {
        return whisperModels.firstOrNull { it.id == id } ?: voskModels.firstOrNull { it.id == id }
    }
}
