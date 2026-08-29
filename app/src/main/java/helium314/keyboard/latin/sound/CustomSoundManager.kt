// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.util.Log
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class CustomSoundManager private constructor(private val appContext: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private var soundPool: SoundPool? = null
    private var activePackId: String = SoundPackUrls.SYSTEM_DEFAULT_ID
    
    private var standardSampleId = 0
    private var spaceSampleId = 0
    private var deleteSampleId = 0
    private var enterSampleId = 0

    private val previewSoundPool: SoundPool by lazy {
        createSoundPool()
    }
    private val previewCache = ConcurrentHashMap<String, Int>()

    init {
        initSoundPool()
    }

    private fun createSoundPool(): SoundPool {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        return SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(audioAttributes)
            .build()
    }

    private fun initSoundPool() {
        soundPool = createSoundPool()
    }

    @Synchronized
    fun setSoundPack(packId: String) {
        if (activePackId == packId && (packId == SoundPackUrls.SYSTEM_DEFAULT_ID || standardSampleId != 0)) {
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

    @Synchronized
    private fun loadActivePack() {
        val pool = soundPool ?: return
        standardSampleId = 0
        spaceSampleId = 0
        deleteSampleId = 0
        enterSampleId = 0

        if (activePackId == SoundPackUrls.SYSTEM_DEFAULT_ID) {
            return
        }

        scope.launch {
            val audioFiles = SoundPackImporter.getPackAudioFiles(appContext, activePackId)
            if (!audioFiles.isValid) {
                return@launch
            }

            val stdId = audioFiles.standardFile?.let { loadSample(pool, it) } ?: 0
            val spcId = audioFiles.spaceFile?.let { if (it == audioFiles.standardFile) stdId else loadSample(pool, it) } ?: stdId
            val delId = audioFiles.deleteFile?.let { if (it == audioFiles.standardFile) stdId else loadSample(pool, it) } ?: stdId
            val entId = audioFiles.enterFile?.let { if (it == audioFiles.standardFile) stdId else loadSample(pool, it) } ?: stdId

            synchronized(this@CustomSoundManager) {
                standardSampleId = stdId
                spaceSampleId = spcId
                deleteSampleId = delId
                enterSampleId = entId
            }
        }
    }

    private fun loadSample(pool: SoundPool, file: File): Int {
        return try {
            if (file.exists()) {
                pool.load(file.absolutePath, 1)
            } else 0
        } catch (e: Throwable) {
            Log.e(TAG, "Error loading sample from ${file.path}", e)
            0
        }
    }

    fun playSound(code: Int, volume: Float): Boolean {
        if (activePackId == SoundPackUrls.SYSTEM_DEFAULT_ID) {
            return false
        }
        val pool = soundPool ?: return false
        val sampleId = when (code) {
            KeyCode.DELETE -> if (deleteSampleId != 0) deleteSampleId else standardSampleId
            Constants.CODE_SPACE -> if (spaceSampleId != 0) spaceSampleId else standardSampleId
            Constants.CODE_ENTER -> if (enterSampleId != 0) enterSampleId else standardSampleId
            else -> standardSampleId
        }

        if (sampleId == 0) {
            return false
        }

        val actualVol = if (volume < 0f) 0.5f else volume.coerceIn(0f, 1f)
        pool.play(sampleId, actualVol, actualVol, 1, 0, 1.0f)
        return true
    }

    fun previewSound(packId: String, volume: Float = 0.8f) {
        if (packId == SoundPackUrls.SYSTEM_DEFAULT_ID) {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, volume)
            return
        }

        scope.launch {
            val files = SoundPackImporter.getPackAudioFiles(appContext, packId)
            val fileToPlay = files.standardFile ?: files.spaceFile ?: files.deleteFile ?: files.enterFile ?: return@launch
            val path = fileToPlay.absolutePath
            val sampleId = previewCache.getOrPut(path) {
                previewSoundPool.load(path, 1)
            }
            if (sampleId != 0) {
                val actualVol = if (volume < 0f) 0.8f else volume.coerceIn(0.1f, 1f)
                previewSoundPool.play(sampleId, actualVol, actualVol, 1, 0, 1.0f)
            }
        }
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
