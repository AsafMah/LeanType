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

    const val DEFAULT_INDEX_URL = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/index.json"

    // Default fallback remote catalog if offline or index cannot be reached
    val FALLBACK_CATALOG = listOf(
        RemoteSoundPack(
            id = "dev.leantype.sounds.gateron-oil-king-thock",
            name = "Gateron Oil King Thock",
            summary = "Deep, warm, lubricated mechanical switch with heavy spacebar clack.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/gateron-oil-king-thock.zip",
            sizeBytes = 27976
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.kailh-box-jade-clicky",
            name = "Kailh Box Jade Clicky",
            summary = "High-pitch crisp tactile click-bar mechanism.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/kailh-box-jade-clicky.zip",
            sizeBytes = 29836
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.holy-panda-tactile",
            name = "Holy Panda Tactile",
            summary = "Snappy tactile bump with distinct bottom-out pop.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/holy-panda-tactile.zip",
            sizeBytes = 29764
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.ibm-model-m-beamspring",
            name = "IBM Model M Beamspring",
            summary = "Heavy vintage solenoid click with subtle metallic resonance.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/ibm-model-m-beamspring.zip",
            sizeBytes = 33085
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.classic-1930s-royal-typewriter",
            name = "Classic 1930s Royal Typewriter",
            summary = "Metal hammer striker, ratchet spacebar, and carriage-return chime.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/classic-1930s-royal-typewriter.zip",
            sizeBytes = 30974
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.creamy-linear-jelly",
            name = "Creamy Linear Jelly",
            summary = "Muted, ultra-smooth dampened linear switch sound.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/creamy-linear-jelly.zip",
            sizeBytes = 27048
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.8-bit-chiptune-arcade",
            name = "8-Bit Chiptune Arcade",
            summary = "Retro square-wave arcade blips and chirps.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/8-bit-chiptune-arcade.zip",
            sizeBytes = 31556
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.minimalistic-ceramic-glass-marble",
            name = "Minimalistic Ceramic / Glass Marble",
            summary = "Smooth polished mineral tap with glassy overtones.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/minimalistic-ceramic-glass-marble.zip",
            sizeBytes = 30012
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.water-bubble-pop",
            name = "Water Bubble Pop",
            summary = "Resonant liquid droplet burst with soft pop.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/water-bubble-pop.zip",
            sizeBytes = 28455
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.acoustic-teak-woodblock",
            name = "Acoustic Teak Woodblock",
            summary = "Natural acoustic percussion mallet tap.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/acoustic-teak-woodblock.zip",
            sizeBytes = 29276
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
