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
            id = "dev.leantype.sounds.thock",
            name = "Deep Thock",
            summary = "Deep lubricated switch clack.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/thock.zip",
            sizeBytes = 29856
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.clicky",
            name = "Crisp Click",
            summary = "High-pitched sharp click.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/clicky.zip",
            sizeBytes = 30437
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.tactile",
            name = "Tactile Pop",
            summary = "Snappy tactile bump and pop.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/tactile.zip",
            sizeBytes = 30394
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.mechanical",
            name = "Mechanical Click",
            summary = "Retro mechanical spring click.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/mechanical.zip",
            sizeBytes = 31909
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.typewriter",
            name = "Typewriter",
            summary = "Vintage carriage and chime.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/typewriter.zip",
            sizeBytes = 30870
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.creamy",
            name = "Creamy Linear",
            summary = "Soft dampened linear tap.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/creamy.zip",
            sizeBytes = 27676
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.chiptune",
            name = "8-Bit Chiptune",
            summary = "Retro square-wave arcade blips.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/chiptune.zip",
            sizeBytes = 31549
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.glass",
            name = "Glass Marble",
            summary = "Polished mineral tap.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/glass.zip",
            sizeBytes = 28859
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.bubble",
            name = "Bubble Pop",
            summary = "Soft liquid droplet burst.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/bubble.zip",
            sizeBytes = 28296
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.woodblock",
            name = "Woodblock",
            summary = "Acoustic wooden mallet tap.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/woodblock.zip",
            sizeBytes = 30088
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.piano",
            name = "Piano",
            summary = "Warm harmonic key strike.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/piano.zip",
            sizeBytes = 30014
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.acoustic-pluck",
            name = "Acoustic Pluck",
            summary = "Plucked nylon string tone.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/acoustic-pluck.zip",
            sizeBytes = 31686
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.folk-drum",
            name = "Folk Drum",
            summary = "High-tension rim and drum hit.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/folk-drum.zip",
            sizeBytes = 29959
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.resonant-drum",
            name = "Resonant Drum",
            summary = "Deep pitch-bending drum tap.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/resonant-drum.zip",
            sizeBytes = 29543
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.kalimba",
            name = "Kalimba",
            summary = "Plucked metal tines.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/kalimba.zip",
            sizeBytes = 30265
        ),
        RemoteSoundPack(
            id = "dev.leantype.sounds.pizzicato",
            name = "Pizzicato",
            summary = "Short finger-plucked string.",
            author = "LeanType Sound Lab",
            versionName = "1.0.0",
            downloadUrl = "https://raw.githubusercontent.com/LeanBitLab/LeanType-SoundPacks/main/dist/pizzicato.zip",
            sizeBytes = 30498
        )
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun isPreset(id: String): Boolean = false

    fun getPreset(id: String): SoundPackInfo? = null

    fun fetchRemoteIndex(indexUrl: String = DEFAULT_INDEX_URL): List<RemoteSoundPack> {
        if (!helium314.keyboard.latin.utils.AddonPolicy.allowsInAppDownloads()) {
            Log.i(TAG, "Using the bundled sound catalog: in-app downloads are disabled")
            return FALLBACK_CATALOG
        }
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
