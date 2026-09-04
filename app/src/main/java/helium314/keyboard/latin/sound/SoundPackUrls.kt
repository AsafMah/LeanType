// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.sound

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

data class SoundPackInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val author: String? = null,
    val versionName: String? = null,
    val isPreset: Boolean = false,
    val isCustom: Boolean = false
)

object SoundPackUrls {
    private const val TAG = "SoundPackUrls"
    const val SYSTEM_DEFAULT_ID = "system"

    const val DEFAULT_INDEX_URL = "https://raw.githubusercontent.com/LeanBitLab/leantype-soundpacks/main/index.json"

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

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun getPreset(id: String): SoundPackInfo? {
        return PRESET_PACKS.firstOrNull { it.id == id }
    }

    fun isPreset(id: String): Boolean {
        return PRESET_PACKS.any { it.id == id }
    }

    fun fetchRemoteIndex(indexUrl: String = DEFAULT_INDEX_URL): List<RemoteSoundPack> {
        return try {
            val url = URL(indexUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "LeanType")
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed = json.decodeFromString<RemoteSoundPackIndex>(body)
                parsed.packs
            } else {
                emptyList()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to fetch remote sound pack index from $indexUrl: ${e.message}")
            emptyList()
        }
    }
}
