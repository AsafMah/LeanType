// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.sound

data class SoundPackInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val isPreset: Boolean = false,
    val isCustom: Boolean = false
)

object SoundPackUrls {
    const val SYSTEM_DEFAULT_ID = "system"

    val PRESET_PACKS = listOf(
        SoundPackInfo(
            id = "ios",
            displayName = "iOS / Modern Tap",
            description = "Crisp, subtle tactile key click sound",
            isPreset = true
        ),
        SoundPackInfo(
            id = "mechanical_cherry",
            displayName = "Mechanical (Cherry MX)",
            description = "Tactile mechanical switch click and deep spacebar clack",
            isPreset = true
        ),
        SoundPackInfo(
            id = "vintage_typewriter",
            displayName = "Vintage Typewriter",
            description = "Classic metal hammer strike with carriage return enter chime",
            isPreset = true
        ),
        SoundPackInfo(
            id = "pop_bubble",
            displayName = "Bubble / Pop",
            description = "Satisfying soft bubbly pop and drop feedback",
            isPreset = true
        ),
        SoundPackInfo(
            id = "wood_minimal",
            displayName = "Woodblock Minimal",
            description = "Natural acoustic wood tap key sound",
            isPreset = true
        ),
        SoundPackInfo(
            id = "modern_tick",
            displayName = "Modern Crisp Tick",
            description = "Ultra-minimal, high-precision electronic micro tick",
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
