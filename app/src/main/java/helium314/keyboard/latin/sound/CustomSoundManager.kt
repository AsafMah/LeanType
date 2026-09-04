// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.sound

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class CustomSoundManager private constructor(private val appContext: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private var soundPool: SoundPool? = null
    private var activePackId: String = SoundPackUrls.SYSTEM_DEFAULT_ID

    private data class LoadedSoundEvent(
        val sampleIds: List<Int>,
        val mode: SoundMode,
        val volume: Float
    )

    private val loadedEvents = ConcurrentHashMap<String, LoadedSoundEvent>()
    private val cycleIndexes = ConcurrentHashMap<String, Int>()
    private val lastPlayedTime = ConcurrentHashMap<String, Long>()
    private var packMasterVolume: Float = 1.0f
    private val random = Random.Default

    private var previewSoundPool: SoundPool? = null
    private val previewCache = ConcurrentHashMap<String, Int>()

    private var isInputViewActive: Boolean = false

    init {
        // SoundPool will be initialized lazily or on onStartInputView
    }

    private fun createSoundPool(): SoundPool {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        return SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(audioAttributes)
            .build()
    }

    @Synchronized
    private fun ensureSoundPool(): SoundPool {
        return soundPool ?: createSoundPool().also { soundPool = it }
    }

    @Synchronized
    fun setSoundPack(packId: String) {
        if (activePackId == packId && (packId == SoundPackUrls.SYSTEM_DEFAULT_ID || loadedEvents.isNotEmpty())) {
            return
        }
        activePackId = packId
        loadActivePack()
    }

    fun reloadIfActive(packId: String) {
        if (activePackId == packId) {
            loadActivePack()
        }
    }

    fun onStartInputView() {
        isInputViewActive = true
        if (activePackId != SoundPackUrls.SYSTEM_DEFAULT_ID && (soundPool == null || loadedEvents.isEmpty())) {
            loadActivePack()
        }
    }

    fun onFinishInputView() {
        isInputViewActive = false
        release()
    }

    fun onDestroy() {
        isInputViewActive = false
        release()
        previewSoundPool?.release()
        previewSoundPool = null
        previewCache.clear()
    }

    @Synchronized
    private fun loadActivePack() {
        loadedEvents.clear()
        cycleIndexes.clear()
        lastPlayedTime.clear()

        if (activePackId == SoundPackUrls.SYSTEM_DEFAULT_ID) {
            release()
            return
        }

        val pool = ensureSoundPool()

        scope.launch {
            if (SoundPackUrls.isPreset(activePackId)) {
                packMasterVolume = 1.0f
                val stdId = loadAssetSample(pool, "sounds/$activePackId/standard.ogg")
                val spcId = loadAssetSample(pool, "sounds/$activePackId/space.ogg").takeIf { it != 0 } ?: stdId
                val delId = loadAssetSample(pool, "sounds/$activePackId/delete.ogg").takeIf { it != 0 } ?: stdId
                val entId = loadAssetSample(pool, "sounds/$activePackId/enter.ogg").takeIf { it != 0 } ?: stdId

                if (stdId != 0) loadedEvents["keypress.default"] = LoadedSoundEvent(listOf(stdId), SoundMode.SINGLE, 1f)
                if (spcId != 0) loadedEvents["keypress.space"] = LoadedSoundEvent(listOf(spcId), SoundMode.SINGLE, 1f)
                if (delId != 0) loadedEvents["keypress.delete"] = LoadedSoundEvent(listOf(delId), SoundMode.SINGLE, 1f)
                if (entId != 0) loadedEvents["keypress.return"] = LoadedSoundEvent(listOf(entId), SoundMode.SINGLE, 1f)
            } else {
                val manifest = SoundPackImporter.getManifest(appContext, activePackId)
                val packDir = SoundPackImporter.getPackDir(appContext, activePackId)

                if (manifest != null && manifest.sounds.isNotEmpty()) {
                    packMasterVolume = manifest.defaultMasterVolume.coerceIn(0f, 1f)
                    manifest.sounds.forEach { (eventName, soundEvent) ->
                        val sampleIds = mutableListOf<Int>()
                        val filesToLoad = soundEvent.files.take(SoundPackRules.MAX_VARIANTS_PER_EVENT)
                        filesToLoad.forEach { relativePath ->
                            val audioFile = File(packDir, relativePath)
                            val sampleId = loadFileSample(pool, audioFile)
                            if (sampleId != 0) {
                                sampleIds.add(sampleId)
                            }
                        }
                        if (sampleIds.isNotEmpty()) {
                            loadedEvents[eventName] = LoadedSoundEvent(
                                sampleIds = sampleIds,
                                mode = soundEvent.mode,
                                volume = soundEvent.volume.coerceIn(0f, 1f)
                            )
                        }
                    }
                } else {
                    // Fallback to audio files
                    val audioFiles = SoundPackImporter.getPackAudioFiles(appContext, activePackId)
                    if (!audioFiles.isValid) return@launch
                    packMasterVolume = 1.0f

                    val stdId = audioFiles.standardFile?.let { loadFileSample(pool, it) } ?: 0
                    val spcId = audioFiles.spaceFile?.let { if (it == audioFiles.standardFile) stdId else loadFileSample(pool, it) } ?: stdId
                    val delId = audioFiles.deleteFile?.let { if (it == audioFiles.standardFile) stdId else loadFileSample(pool, it) } ?: stdId
                    val entId = audioFiles.enterFile?.let { if (it == audioFiles.standardFile) stdId else loadFileSample(pool, it) } ?: stdId

                    if (stdId != 0) loadedEvents["keypress.default"] = LoadedSoundEvent(listOf(stdId), SoundMode.SINGLE, 1f)
                    if (spcId != 0) loadedEvents["keypress.space"] = LoadedSoundEvent(listOf(spcId), SoundMode.SINGLE, 1f)
                    if (delId != 0) loadedEvents["keypress.delete"] = LoadedSoundEvent(listOf(delId), SoundMode.SINGLE, 1f)
                    if (entId != 0) loadedEvents["keypress.return"] = LoadedSoundEvent(listOf(entId), SoundMode.SINGLE, 1f)
                }
            }
        }
    }

    private fun loadAssetSample(pool: SoundPool, assetPath: String): Int {
        return try {
            appContext.assets.openFd(assetPath).use { afd ->
                pool.load(afd, 1)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error loading asset sample from $assetPath", e)
            0
        }
    }

    private fun loadFileSample(pool: SoundPool, file: File): Int {
        return try {
            if (file.exists() && file.isFile) {
                pool.load(file.absolutePath, 1)
            } else 0
        } catch (e: Throwable) {
            Log.e(TAG, "Error loading file sample from ${file.path}", e)
            0
        }
    }

    fun playSound(code: Int, volume: Float): Boolean {
        if (activePackId == SoundPackUrls.SYSTEM_DEFAULT_ID) {
            return false
        }

        val now = SystemClock.elapsedRealtime()
        val last = lastPlayedTime["playback"] ?: 0L
        if (now - last < 10L) {
            return true
        }
        lastPlayedTime["playback"] = now

        val pool = soundPool ?: run {
            if (isInputViewActive) {
                ensureSoundPool().also { loadActivePack() }
            } else {
                return false
            }
        }

        val eventName = when (code) {
            KeyCode.DELETE -> "keypress.delete"
            Constants.CODE_SPACE -> "keypress.space"
            Constants.CODE_ENTER -> "keypress.return"
            KeyCode.SHIFT, KeyCode.CAPS_LOCK -> "keypress.shift"
            KeyCode.SYMBOL, KeyCode.SYMBOL_ALPHA -> "keypress.symbol"
            else -> "keypress.default"
        }

        val event = loadedEvents[eventName]
            ?: loadedEvents["keypress.default"]
            ?: return false

        val sampleIds = event.sampleIds
        if (sampleIds.isEmpty()) {
            return false
        }

        val sampleId = when (event.mode) {
            SoundMode.CYCLE -> {
                val index = cycleIndexes[eventName] ?: 0
                cycleIndexes[eventName] = (index + 1) % sampleIds.size
                sampleIds[index % sampleIds.size]
            }
            SoundMode.RANDOM -> {
                sampleIds[random.nextInt(sampleIds.size)]
            }
            SoundMode.SINGLE -> {
                sampleIds.first()
            }
        }

        val userVol = if (volume < 0f) 0.5f else volume.coerceIn(0f, 1f)
        val finalVol = (userVol * packMasterVolume * event.volume).coerceIn(0f, 1f)

        if (finalVol <= 0f) {
            return true
        }

        try {
            pool.play(sampleId, finalVol, finalVol, 1, 0, 1.0f)
        } catch (_: Throwable) {}
        return true
    }

    fun previewSound(packId: String, volume: Float = 0.8f) {
        if (packId == SoundPackUrls.SYSTEM_DEFAULT_ID) {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, volume)
            return
        }

        val pPool = previewSoundPool ?: createSoundPool().also { previewSoundPool = it }

        scope.launch {
            if (SoundPackUrls.isPreset(packId)) {
                val assetPath = "sounds/$packId/standard.ogg"
                val sampleId = previewCache.getOrPut(assetPath) {
                    try {
                        appContext.assets.openFd(assetPath).use { afd ->
                            pPool.load(afd, 1)
                        }
                    } catch (_: Throwable) { 0 }
                }
                if (sampleId != 0) {
                    val actualVol = if (volume < 0f) 0.8f else volume.coerceIn(0.1f, 1f)
                    pPool.play(sampleId, actualVol, actualVol, 1, 0, 1.0f)
                }
            } else {
                val manifest = SoundPackImporter.getManifest(appContext, packId)
                val packDir = SoundPackImporter.getPackDir(appContext, packId)

                var fileToPlay: File? = null
                if (manifest?.preview != null) {
                    fileToPlay = File(packDir, manifest.preview)
                }
                if (fileToPlay == null || !fileToPlay.exists()) {
                    val defaultFiles = manifest?.sounds?.get("keypress.default")?.files
                    if (!defaultFiles.isNullOrEmpty()) {
                        fileToPlay = File(packDir, defaultFiles.first())
                    }
                }
                if (fileToPlay == null || !fileToPlay.exists()) {
                    val files = SoundPackImporter.getPackAudioFiles(appContext, packId)
                    fileToPlay = files.standardFile ?: files.spaceFile ?: files.deleteFile ?: files.enterFile
                }

                if (fileToPlay != null && fileToPlay.exists()) {
                    val path = fileToPlay.absolutePath
                    val sampleId = previewCache.getOrPut(path) {
                        loadFileSample(pPool, fileToPlay)
                    }
                    if (sampleId != 0) {
                        val actualVol = if (volume < 0f) 0.8f else volume.coerceIn(0.1f, 1f)
                        pPool.play(sampleId, actualVol, actualVol, 1, 0, 1.0f)
                    }
                }
            }
        }
    }

    @Synchronized
    fun release() {
        soundPool?.release()
        soundPool = null
        loadedEvents.clear()
        cycleIndexes.clear()
        lastPlayedTime.clear()
    }

    companion object {
        private const val TAG = "CustomSoundManager"

        @Volatile
        private var instance: CustomSoundManager? = null

        fun getInstance(context: Context): CustomSoundManager {
            return instance ?: synchronized(this) {
                instance ?: CustomSoundManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
