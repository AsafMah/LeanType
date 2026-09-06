// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.sound

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SoundMode {
    @SerialName("single")
    SINGLE,
    @SerialName("random")
    RANDOM,
    @SerialName("cycle")
    CYCLE
}

@Serializable
data class SoundEvent(
    val files: List<String> = emptyList(),
    val mode: SoundMode = SoundMode.SINGLE,
    val volume: Float = 1f
)

@Serializable
data class SoundPackManifest(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val summary: String? = null,
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val author: String? = null,
    val license: String? = null,
    val minAppVersionCode: Int = 1,
    val defaultMasterVolume: Float = 0.85f,
    val preview: String? = null,
    val icon: String? = null,
    val tags: List<String> = emptyList(),
    val sounds: Map<String, SoundEvent> = emptyMap()
)

@Serializable
data class RemoteSoundPack(
    val id: String,
    val name: String,
    val summary: String? = null,
    val author: String? = null,
    val license: String? = null,
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val minAppVersionCode: Int = 1,
    val tags: List<String> = emptyList(),
    val file: String = "",
    val downloadUrl: String,
    val previewUrl: String? = null,
    val iconUrl: String? = null,
    val sha256: String = "",
    val sizeBytes: Long = 0L
)

@Serializable
data class RemoteSoundPackIndex(
    val schemaVersion: Int = 1,
    val packs: List<RemoteSoundPack> = emptyList()
)

object SoundPackRules {
    const val MAX_ZIP_SIZE = 10L * 1024L * 1024L
    const val MAX_UNPACKED_SIZE = 20L * 1024L * 1024L
    const val MAX_ENTRIES = 200
    const val MAX_FILE_SIZE = 500L * 1024L
    const val MAX_VARIANTS_PER_EVENT = 16

    val ALLOWED_EXTENSIONS = setOf(
        "json",
        "ogg",
        "opus",
        "wav",
        "mp3",
        "webp",
        "png",
        "txt",
        "md"
    )

    val AUDIO_EXTENSIONS = setOf(
        "ogg",
        "opus",
        "wav",
        "mp3"
    )

    fun isValidId(id: String): Boolean {
        return id.matches(Regex("^[A-Za-z0-9._-]+$")) && !id.endsWith(".")
    }
}
