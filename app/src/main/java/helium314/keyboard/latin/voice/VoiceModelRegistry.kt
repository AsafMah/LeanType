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
            id = "distil-whisper-small-en-q5_1",
            displayName = "Distil-Whisper Small (English)",
            engineType = VoiceConstants.ENGINE_WHISPER,
            language = "English (Fast & Accurate)",
            languageCode = "en",
            sizeMb = "105 MB",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-distil-small.en-q5_1.bin",
            browserUrl = "https://huggingface.co/distil-whisper/distil-small.en",
            description = "6x faster than Whisper Small, 49% smaller parameters with state-of-the-art English accuracy."
        ),
        VoiceModelItem(
            id = "distil-whisper-medium-en-q5_1",
            displayName = "Distil-Whisper Medium (English)",
            engineType = VoiceConstants.ENGINE_WHISPER,
            language = "English (High Accuracy)",
            languageCode = "en",
            sizeMb = "260 MB",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-distil-medium.en-q5_1.bin",
            browserUrl = "https://huggingface.co/distil-whisper/distil-medium.en",
            description = "6x faster than Whisper Medium with exceptional conversational understanding."
        ),
        VoiceModelItem(
            id = "whisper-base-q5_1",
            displayName = "Whisper Base Q5_1 (Multilingual)",
            engineType = VoiceConstants.ENGINE_WHISPER,
            language = "Multilingual (99+ languages)",
            languageCode = "mul",
            sizeMb = "57 MB",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
            browserUrl = "https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-base-q5_1.bin",
            description = "Fast multilingual CPU inference with balanced accuracy."
        ),
        VoiceModelItem(
            id = "whisper-tiny",
            displayName = "Whisper Tiny (Multilingual)",
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
            displayName = "Whisper Small Q5_1 (Multilingual)",
            engineType = VoiceConstants.ENGINE_WHISPER,
            language = "Multilingual (99+ languages)",
            languageCode = "mul",
            sizeMb = "182 MB",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
            browserUrl = "https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-small-q5_1.bin",
            description = "High accuracy across global accents and languages."
        )
    )

    fun findById(id: String): VoiceModelItem? {
        return whisperModels.firstOrNull { it.id == id }
    }
}
