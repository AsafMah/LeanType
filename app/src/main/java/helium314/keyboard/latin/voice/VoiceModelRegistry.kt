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
            displayName = "Base",
            engineType = VoiceConstants.ENGINE_WHISPER,
            language = "Multilingual",
            languageCode = "mul",
            sizeMb = "57 MB",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
            browserUrl = "https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-base-q5_1.bin"
        ),
        VoiceModelItem(
            id = "whisper-tiny-q5_1",
            displayName = "Tiny",
            engineType = VoiceConstants.ENGINE_WHISPER,
            language = "Multilingual",
            languageCode = "mul",
            sizeMb = "32 MB",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin",
            browserUrl = "https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-tiny-q5_1.bin"
        ),
        VoiceModelItem(
            id = "whisper-small-q5_1",
            displayName = "Small",
            engineType = VoiceConstants.ENGINE_WHISPER,
            language = "Multilingual",
            languageCode = "mul",
            sizeMb = "182 MB",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
            browserUrl = "https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-small-q5_1.bin"
        )
    )

    fun findById(id: String): VoiceModelItem? {
        return whisperModels.firstOrNull { it.id == id }
    }
}
