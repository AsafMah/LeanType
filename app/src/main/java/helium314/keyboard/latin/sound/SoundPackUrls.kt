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
            id = "mechanical_thock",
            displayName = "Thocky Mechanical",
            description = "Deep, warm, lubricated switch thock and heavy spacebar",
            isPreset = true
        ),
        SoundPackInfo(
            id = "vintage_typewriter",
            displayName = "Vintage Typewriter",
            description = "Classic metal hammer strike with carriage return enter chime",
            isPreset = true
        ),
        SoundPackInfo(
            id = "retro_terminal",
            displayName = "Retro CRT Terminal",
            description = "1980s IBM mainframe beamspring solenoid mechanical clack",
            isPreset = true
        ),
        SoundPackInfo(
            id = "pop_bubble",
            displayName = "Bubble / Pop",
            description = "Satisfying soft bubbly pop and drop feedback",
            isPreset = true
        ),
        SoundPackInfo(
            id = "soft_pudding",
            displayName = "Soft Velvet / Pudding",
            description = "Gentle, muted, pillow-soft quiet tapping for low noise",
            isPreset = true
        ),
        SoundPackInfo(
            id = "wood_minimal",
            displayName = "Woodblock Minimal",
            description = "Natural acoustic wood tap key sound",
            isPreset = true
        ),
        SoundPackInfo(
            id = "marimba_tone",
            displayName = "Acoustic Marimba",
            description = "Warm melodic wooden mallet bar resonance",
            isPreset = true
        ),
        SoundPackInfo(
            id = "modern_tick",
            displayName = "Modern Crisp Tick",
            description = "Ultra-minimal, high-precision electronic micro tick",
            isPreset = true
        ),
        SoundPackInfo(
            id = "laser_scifi",
            displayName = "Sci-Fi Cyberpunk",
            description = "Futuristic digital holographic laser pulse click",
            isPreset = true
        ),
        SoundPackInfo(
            id = "arcade_8bit",
            displayName = "8-Bit Chiptune Arcade",
            description = "Retro pixel gaming square-wave beep and blip sounds",
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
