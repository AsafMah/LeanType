// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
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
        // SoundPool is loaded lazily or when keyboard opens
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
            val packDir = SoundPackImporter.tryGetPackDir(appContext, activePackId) ?: return@launch
            val manifest = SoundPackImporter.getManifest(appContext, activePackId)
            if (manifest == null && File(packDir, "pack.json").exists()) return@launch

            if (manifest != null && manifest.sounds.isNotEmpty()) {
                packMasterVolume = manifest.defaultMasterVolume.coerceIn(0f, 1f)
                manifest.sounds.forEach { (eventName, soundEvent) ->
                    val sampleIds = mutableListOf<Int>()
                    val filesToLoad = soundEvent.files.take(SoundPackRules.MAX_VARIANTS_PER_EVENT)
                    filesToLoad.forEach soundFile@{ relativePath ->
                        val audioFile = SoundPackImporter.resolveAudioFile(packDir, relativePath)
                            ?: return@soundFile
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
                // Fallback to heuristic audio files
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

    fun playSound(code: Int, volume: Float, keyXRatio: Float = 0.5f): Boolean {
        if (activePackId == SoundPackUrls.SYSTEM_DEFAULT_ID) {
            return false
        }

        val now = SystemClock.elapsedRealtime()
        val last = lastPlayedTime["playback"] ?: 0L
        val delta = now - last
        if (delta < 8L) {
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

        val prefs = appContext.prefs()

        val eventName: String
        val keyVolMultiplier: Float
        when (code) {
            KeyCode.DELETE, KeyCode.DELETE_WORD, KeyCode.FORWARD_DELETE, KeyCode.FORWARD_DELETE_WORD -> {
                eventName = "keypress.delete"
                keyVolMultiplier = prefs.getFloat(Settings.PREF_SOUND_VOL_DELETE, Defaults.PREF_SOUND_VOL_DELETE)
            }
            Constants.CODE_SPACE -> {
                eventName = "keypress.space"
                keyVolMultiplier = prefs.getFloat(Settings.PREF_SOUND_VOL_SPACE, Defaults.PREF_SOUND_VOL_SPACE)
            }
            Constants.CODE_ENTER -> {
                eventName = "keypress.return"
                keyVolMultiplier = prefs.getFloat(Settings.PREF_SOUND_VOL_ENTER, Defaults.PREF_SOUND_VOL_ENTER)
            }
            KeyCode.SHIFT, KeyCode.CAPS_LOCK -> {
                eventName = "keypress.shift"
                keyVolMultiplier = prefs.getFloat(Settings.PREF_SOUND_VOL_MODIFIERS, Defaults.PREF_SOUND_VOL_MODIFIERS)
            }
            KeyCode.SYMBOL, KeyCode.SYMBOL_ALPHA -> {
                eventName = "keypress.symbol"
                keyVolMultiplier = prefs.getFloat(Settings.PREF_SOUND_VOL_MODIFIERS, Defaults.PREF_SOUND_VOL_MODIFIERS)
            }
            else -> {
                eventName = "keypress.default"
                keyVolMultiplier = 1.0f
            }
        }

        if (keyVolMultiplier <= 0f) {
            return true
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

        // Dynamic typing velocity: subtle volume boost on typing bursts (<140ms between strokes)
        val dynamicVelocityEnabled = prefs.getBoolean(Settings.PREF_SOUND_DYNAMIC_VELOCITY, Defaults.PREF_SOUND_DYNAMIC_VELOCITY)
        val velocityFactor = if (dynamicVelocityEnabled && delta < 140L) {
            1.0f + ((140L - delta).toFloat() / 140f) * 0.18f
        } else {
            1.0f
        }

        val userVol = if (volume < 0f) 0.5f else volume.coerceIn(0f, 1f)
        val baseVol = (userVol * packMasterVolume * event.volume * keyVolMultiplier * velocityFactor).coerceIn(0f, 1f)

        if (baseVol <= 0f) {
            return true
        }

        // Spatial stereo panning based on keyboard X ratio
        val stereoPanEnabled = prefs.getBoolean(Settings.PREF_SOUND_STEREO_PAN, Defaults.PREF_SOUND_STEREO_PAN)
        val (leftVol, rightVol) = if (stereoPanEnabled) {
            val pan = ((keyXRatio.coerceIn(0f, 1f) - 0.5f) * 2f) // -1.0 (left) to +1.0 (right)
            val leftScale = (1.0f - pan.coerceAtLeast(0f) * 0.35f).coerceIn(0.1f, 1.0f)
            val rightScale = (1.0f + pan.coerceAtMost(0f) * 0.35f).coerceIn(0.1f, 1.0f)
            Pair((baseVol * leftScale).coerceIn(0f, 1f), (baseVol * rightScale).coerceIn(0f, 1f))
        } else {
            Pair(baseVol, baseVol)
        }

        // Base pitch shift + random micro-pitch jitter
        val basePitch = prefs.getFloat(Settings.PREF_SOUND_PITCH_SCALE, Defaults.PREF_SOUND_PITCH_SCALE).coerceIn(0.5f, 2.0f)
        val randomPitchEnabled = prefs.getBoolean(Settings.PREF_SOUND_RANDOM_PITCH, Defaults.PREF_SOUND_RANDOM_PITCH)
        val finalRate = if (randomPitchEnabled) {
            val jitter = (random.nextFloat() - 0.5f) * 0.08f // ±4% jitter
            (basePitch + jitter).coerceIn(0.5f, 2.0f)
        } else {
            basePitch
        }

        try {
            pool.play(sampleId, leftVol, rightVol, 1, 0, finalRate)
        } catch (_: Throwable) {}
        return true
    }

    fun previewSound(packId: String, volume: Float = 0.8f, pitch: Float = 1.0f) {
        if (packId == SoundPackUrls.SYSTEM_DEFAULT_ID) {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, volume)
            return
        }

        val pPool = previewSoundPool ?: createSoundPool().also { previewSoundPool = it }

        scope.launch {
            val packDir = SoundPackImporter.tryGetPackDir(appContext, packId) ?: return@launch
            val manifest = SoundPackImporter.getManifest(appContext, packId)
            if (manifest == null && File(packDir, "pack.json").exists()) return@launch

            var fileToPlay: File? = null
            if (manifest?.preview != null) {
                fileToPlay = SoundPackImporter.resolveAudioFile(packDir, manifest.preview)
            }
            if (fileToPlay == null || !fileToPlay.exists()) {
                val defaultFiles = manifest?.sounds?.get("keypress.default")?.files
                if (!defaultFiles.isNullOrEmpty()) {
                    fileToPlay = SoundPackImporter.resolveAudioFile(packDir, defaultFiles.first())
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
                    val rate = pitch.coerceIn(0.5f, 2.0f)
                    pPool.play(sampleId, actualVol, actualVol, 1, 0, rate)
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
