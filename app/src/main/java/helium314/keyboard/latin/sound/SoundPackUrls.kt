// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.sound

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

    // Default fallback remote catalog if offline or index cannot be reached
    val FALLBACK_CATALOG = listOf(
        RemoteSoundPack(
            id = "dev.leantype.sounds.mechanical_thock",
            name = "Mechanical Thock",
            summary = "Deep, lubricated switch thock with heavy spacebar clack.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/leantype-soundpacks/main/dist/mechanical_thock.zip",
            sizeBytes = 18180
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.box_jade_clicky",
            name = "Kailh Box Jade Clicky",
            summary = "Ultra-crisp high-pitched tactile click bar switches.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/leantype-soundpacks/main/dist/box_jade_clicky.zip",
            sizeBytes = 18675
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.vintage_typewriter",
            name = "Vintage Royal Typewriter",
            summary = "Classic cast-iron hammer strike with newline carriage chime on Enter.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/leantype-soundpacks/main/dist/vintage_typewriter.zip",
            sizeBytes = 19910
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.arcade_8bit",
            name = "8-Bit Retro Arcade",
            summary = "Nostalgic chiptune square-wave gaming blips and chirps.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/leantype-soundpacks/main/dist/arcade_8bit.zip",
            sizeBytes = 19746
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.bubble_pop",
            name = "Water Bubble / Pop",
            summary = "Satisfying soft liquid bubble pop feedback.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/leantype-soundpacks/main/dist/bubble_pop.zip",
            sizeBytes = 16920
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.woodblock_teak",
            name = "Teak Woodblock Minimal",
            summary = "Natural acoustic wooden mallet resonance.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/leantype-soundpacks/main/dist/woodblock_teak.zip",
            sizeBytes = 18335
        )
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun isPreset(id: String): Boolean = false

    fun getPreset(id: String): SoundPackInfo? = null

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
                if (parsed.packs.isNotEmpty()) parsed.packs else FALLBACK_CATALOG
            } else {
                FALLBACK_CATALOG
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to fetch remote sound pack index from $indexUrl: ${e.message}")
            FALLBACK_CATALOG
        }
    }
}
