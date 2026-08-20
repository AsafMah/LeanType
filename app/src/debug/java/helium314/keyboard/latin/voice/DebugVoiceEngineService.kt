// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.leanbitlab.leantype.voice.IVoiceCallback
import com.leanbitlab.leantype.voice.IVoiceEngine
import com.leanbitlab.leantype.voice.ModelImportRequest
import com.leanbitlab.leantype.voice.ModelState
import com.leanbitlab.leantype.voice.VoiceConstants
import com.leanbitlab.leantype.voice.VoiceEngineInfo
import com.leanbitlab.leantype.voice.VoiceSessionConfig
import helium314.keyboard.latin.utils.Log
import java.io.FileInputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DebugVoiceEngineService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    private val isSessionActive = AtomicBoolean(false)

    private val binder = object : IVoiceEngine.Stub() {
        override fun getInfo(): VoiceEngineInfo {
            return VoiceEngineInfo(
                contractVersion = VoiceConstants.VOICE_CONTRACT_VERSION,
                pluginId = "helium314.keyboard.latin.voice.debug",
                displayName = "Debug Voice Stub Engine",
                supportsVosk = true,
                supportsWhisper = true,
                supportsHybrid = true
            )
        }

        override fun getModelState(engineType: String?): ModelState {
            return ModelState(
                engineType = engineType ?: VoiceConstants.ENGINE_VOSK,
                state = ModelState.STATE_READY,
                message = "Debug stub model ready"
            )
        }

        override fun importModel(request: ModelImportRequest?) {
            Log.i(TAG, "Debug stub received model import request for ${request?.engineType}")
            try {
                request?.file?.close()
            } catch (_: Exception) {}
        }

        override fun unloadModel(engineType: String?) {
            Log.i(TAG, "Debug stub unload model: $engineType")
        }

        override fun deleteModel(engineType: String?) {
            Log.i(TAG, "Debug stub delete model: $engineType")
        }

        override fun startSession(
            config: VoiceSessionConfig?,
            audioInput: ParcelFileDescriptor?,
            callback: IVoiceCallback?
        ) {
            Log.i(TAG, "Debug stub starting session: ${config?.sessionId}, mode: ${config?.mode}")
            isSessionActive.set(true)

            executor.execute {
                try {
                    callback?.onSessionStarted()
                    
                    // Read incoming audio pipe data in background thread
                    if (audioInput != null) {
                        val input = FileInputStream(audioInput.fileDescriptor)
                        val buffer = ByteArray(960)
                        var bytesRead = 0
                        var totalRead = 0

                        while (isSessionActive.get() && input.read(buffer).also { bytesRead = it } != -1) {
                            totalRead += bytesRead
                            if (totalRead > 30000 && isSessionActive.get()) {
                                callback?.onPartial("test partial")
                            }
                        }
                    }

                    if (isSessionActive.get()) {
                        Thread.sleep(300)
                        callback?.onFinal("test final")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Debug stub session error", e)
                    callback?.onError(VoiceConstants.VOICE_ERROR_UNKNOWN, e.message)
                } finally {
                    try {
                        audioInput?.close()
                    } catch (_: Exception) {}
                    callback?.onSessionEnded()
                    isSessionActive.set(false)
                }
            }
        }

        override fun stopSession() {
            Log.i(TAG, "Debug stub stopSession called")
            isSessionActive.set(false)
        }

        override fun cancelSession() {
            Log.i(TAG, "Debug stub cancelSession called")
            isSessionActive.set(false)
        }

        override fun release() {
            Log.i(TAG, "Debug stub release called")
            isSessionActive.set(false)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "DebugVoiceEngineService bound")
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    companion object {
        private const val TAG = "DebugVoiceEngineService"
    }
}
