// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.sound

data class SoundPackInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val downloadUrl: String? = null,
    val isPreset: Boolean = false,
    val isCustom: Boolean = false
)

object SoundPackUrls {
    const val SYSTEM_DEFAULT_ID = "system"
    const val GITHUB_REPO_URL = "https://github.com/LeanBitLab/LeanType-Sound-Packs"
    private const val BASE_DOWNLOAD_URL = "https://github.com/LeanBitLab/LeanType-Sound-Packs/releases/latest/download/"

    val PRESET_PACKS = listOf(
        SoundPackInfo(
            id = "ios",
            displayName = "iOS / Modern Tap",
            description = "Crisp, subtle tactile key click sound",
            downloadUrl = "${BASE_DOWNLOAD_URL}ios.zip",
            isPreset = true
        ),
        SoundPackInfo(
            id = "mechanical_cherry",
            displayName = "Mechanical (Cherry MX)",
            description = "Tactile mechanical switch click and deep spacebar clack",
            downloadUrl = "${BASE_DOWNLOAD_URL}mechanical_cherry.zip",
            isPreset = true
        ),
        SoundPackInfo(
            id = "vintage_typewriter",
            displayName = "Vintage Typewriter",
            description = "Classic metal hammer strike with carriage return enter sound",
            downloadUrl = "${BASE_DOWNLOAD_URL}vintage_typewriter.zip",
            isPreset = true
        ),
        SoundPackInfo(
            id = "pop_bubble",
            displayName = "Bubble / Pop",
            description = "Satisfying soft bubbly pop and drop feedback",
            downloadUrl = "${BASE_DOWNLOAD_URL}pop_bubble.zip",
            isPreset = true
        ),
        SoundPackInfo(
            id = "wood_minimal",
            displayName = "Woodblock Minimal",
            description = "Natural acoustic wood tap key sound",
            downloadUrl = "${BASE_DOWNLOAD_URL}wood_minimal.zip",
            isPreset = true
        )
    )

    fun getPreset(id: String): SoundPackInfo? {
        return PRESET_PACKS.firstOrNull { it.id == id }
    }

    fun isPreset(id: String): Boolean {
        return PRESET_PACKS.any { it.id == id }
    }
}
