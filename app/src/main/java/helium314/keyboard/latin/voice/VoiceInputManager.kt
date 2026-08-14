// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.ContextCompat
import com.leanbitlab.leantype.voice.IVoiceCallback
import com.leanbitlab.leantype.voice.VoiceConstants
import com.leanbitlab.leantype.voice.VoiceSessionConfig
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class VoiceInputManager(
    private val ims: LatinIME,
    private val pluginManager: VoicePluginManager
) {

    enum class VoiceState {
        IDLE,
        CONNECTING_PLUGIN,
        STARTING_SESSION,
        RECORDING,
        PROCESSING_FINAL,
        ERROR
    }

    interface VoiceInputListener {
        fun onStateChanged(state: VoiceState)
        fun onError(message: String)
    }

    private var state = VoiceState.IDLE
    private var activeSessionId: String? = null

    private var audioRecord: AudioRecord? = null
    private var audioPipeWriteSide: ParcelFileDescriptor? = null
    private var audioPipeReadSide: ParcelFileDescriptor? = null

    private val isRecording = AtomicBoolean(false)
    private var audioThread: Thread? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingPartialRunnable: Runnable? = null
    private var lastPartialText: String? = null
    private var handshakeTimeoutRunnable: Runnable? = null

    private var listener: VoiceInputListener? = null

    fun setListener(listener: VoiceInputListener?) {
        this.listener = listener
    }

    fun getState(): VoiceState = state

    fun isRecording(): Boolean = state == VoiceState.RECORDING || state == VoiceState.STARTING_SESSION

    fun canStartVoice(): Boolean {
        if (!ims.prefs().getBoolean(VoiceConstants.PREF_VOICE_OFFLINE_ENABLED, false)) {
            return false
        }
        if (ContextCompat.checkSelfPermission(ims, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        if (isBlockedEditor(ims.currentInputEditorInfo)) {
            return false
        }
        return true
    }

    fun startVoice() {
        if (state == VoiceState.RECORDING) {
            stopVoice()
            return
        }

        if (state != VoiceState.IDLE && state != VoiceState.ERROR) {
            Log.w(TAG, "Resetting previous state $state for new voice session")
            cancelVoice()
        }

        if (!canStartVoice()) {
            notifyError("Voice input not available or permission missing")
            return
        }

        pluginManager.cancelSession()
        val sessionId = UUID.randomUUID().toString()
        activeSessionId = sessionId
        updateState(VoiceState.CONNECTING_PLUGIN)

        if (!pluginManager.isPluginConnected()) {
            pluginManager.setConnectionListener(object : VoicePluginManager.PluginConnectionListener {
                override fun onPluginConnected(info: com.leanbitlab.leantype.voice.VoiceEngineInfo?) {
                    mainHandler.post {
                        if (activeSessionId == sessionId && state == VoiceState.CONNECTING_PLUGIN) {
                            initiateSessionHandshake(sessionId)
                        }
                    }
                }

                override fun onPluginDisconnected() {
                    mainHandler.post {
                        if (activeSessionId == sessionId) {
                            notifyError("Plugin disconnected unexpectedly")
                            cleanupSession()
                            updateState(VoiceState.ERROR)
                        }
                    }
                }
            })

            val bound = pluginManager.bindIfNeeded()
            if (!bound) {
                notifyError("Failed to bind to voice plugin")
                updateState(VoiceState.ERROR)
                return
            }
        } else {
            initiateSessionHandshake(sessionId)
        }
    }

    private fun initiateSessionHandshake(sessionId: String) {
        updateState(VoiceState.STARTING_SESSION)

        val pipe: Array<ParcelFileDescriptor>
        try {
            pipe = ParcelFileDescriptor.createPipe()
            audioPipeReadSide = pipe[0]
            audioPipeWriteSide = pipe[1]
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create audio pipe", e)
            notifyError("Failed to create audio pipe")
            updateState(VoiceState.ERROR)
            return
        }

        val mode = ims.prefs().getString(VoiceConstants.PREF_VOICE_MODE, VoiceConstants.MODE_FAST) ?: VoiceConstants.MODE_FAST
        val timeoutMs = ims.prefs().getString(VoiceConstants.PREF_VOICE_HYBRID_TIMEOUT_MS, "900")?.toIntOrNull() ?: 900
        val fallback = ims.prefs().getBoolean(VoiceConstants.PREF_VOICE_HYBRID_FALLBACK, true)

        val config = VoiceSessionConfig(
            sessionId = sessionId,
            mode = mode,
            languageTag = null,
            sampleRate = SAMPLE_RATE,
            enablePartial = true,
            maxSegmentMs = 6000,
            hybridTimeoutMs = timeoutMs,
            hybridFallbackToVosk = fallback
        )

        val callback = object : IVoiceCallback.Stub() {
            override fun onSessionStarted() {
                mainHandler.post {
                    if (activeSessionId == sessionId) {
                        cancelHandshakeTimeout()
                        updateState(VoiceState.RECORDING)
                    }
                }
            }

            override fun onPartial(text: String?) {
                mainHandler.post {
                    if (activeSessionId == sessionId && isRecording.get()) {
                        Log.i(TAG, "onPartial received: '$text' | icNull=${ims.currentInputConnection == null}")
                        if (!text.isNullOrBlank()) {
                            handlePartialText(text)
                        }
                    }
                }
            }

            override fun onFinal(text: String?) {
                Log.i(TAG, "Received onFinal: '$text'")
                mainHandler.post {
                    if (activeSessionId == sessionId) {
                        val ic = ims.currentInputConnection
                        Log.i(TAG, "Committing to InputConnection (isNull=${ic == null}, text='$text')")
                        cancelPendingPartial()
                        handleFinalText(text ?: "")
                    }
                }
            }

            override fun onError(code: Int, message: String?) {
                mainHandler.post {
                    if (activeSessionId == sessionId) {
                        Log.e(TAG, "Received onError: code=$code, message='$message'")
                        cancelPendingPartial()
                        clearComposingText()
                        notifyError(message ?: "Voice error ($code)")
                        cleanupSession()
                        updateState(VoiceState.ERROR)
                    }
                }
            }

            override fun onSessionEnded() {
                mainHandler.post {
                    if (activeSessionId == sessionId) {
                        Log.i(TAG, "Received onSessionEnded, state=$state")
                        cleanupSession()
                        if (state != VoiceState.ERROR) {
                            updateState(VoiceState.IDLE)
                        }
                    }
                }
            }
        }

        // Set handshake timeout guard (8000 ms)
        handshakeTimeoutRunnable = Runnable {
            if (activeSessionId == sessionId && state == VoiceState.STARTING_SESSION) {
                Log.e(TAG, "Session handshake timed out")
                notifyError("Voice session handshake timed out")
                pluginManager.cancelSession()
                cleanupSession()
                updateState(VoiceState.ERROR)
            }
        }
        mainHandler.postDelayed(handshakeTimeoutRunnable!!, HANDSHAKE_TIMEOUT_MS)

        // Start hardware audio capture IMMEDIATELY so the green mic privacy dot appears without IPC delay
        startAudioRecordingThread()

        val started = pluginManager.startSession(config, audioPipeReadSide!!, callback)

        // Close the host's copy of the read side of the pipe after passing it over AIDL binder
        closeQuietly(audioPipeReadSide)
        audioPipeReadSide = null

        if (!started) {
            cancelHandshakeTimeout()
            cleanupSession()
            notifyError("Failed to dispatch voice session")
            updateState(VoiceState.ERROR)
        }
    }

    private fun startAudioRecordingThread() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, FRAME_SIZE_BYTES * 4)

        var source = MediaRecorder.AudioSource.VOICE_RECOGNITION
        try {
            audioRecord = AudioRecord(
                source,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "VOICE_RECOGNITION uninitialized, falling back to MIC source")
                audioRecord?.release()
                source = MediaRecorder.AudioSource.MIC
                audioRecord = AudioRecord(
                    source,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord with VOICE_RECOGNITION, trying MIC", e)
            try {
                source = MediaRecorder.AudioSource.MIC
                audioRecord = AudioRecord(
                    source,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            } catch (ex: Exception) {
                Log.e(TAG, "AudioRecord creation failed completely", ex)
                notifyError("Microphone access failed")
                updateState(VoiceState.ERROR)
                return
            }
        }

        Log.i(TAG, "AudioRecord created: state=${audioRecord?.state} sampleRate=${audioRecord?.sampleRate} source=$source")

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord uninitialized")
            notifyError("Microphone initialization failed")
            audioRecord?.release()
            audioRecord = null
            updateState(VoiceState.ERROR)
            return
        }

        isRecording.set(true)
        audioRecord?.startRecording()
        Log.i(TAG, "AudioRecord started successfully")

        audioThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            val buffer = ByteArray(FRAME_SIZE_BYTES)
            val writeSide = audioPipeWriteSide
            var totalBytesWrote = 0L

            try {
                FileOutputStream(writeSide?.fileDescriptor).use { outputStream ->
                    while (isRecording.get()) {
                        val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                        if (readBytes > 0) {
                            outputStream.write(buffer, 0, readBytes)
                            outputStream.flush()
                            totalBytesWrote += readBytes
                            if (totalBytesWrote % 32000L < readBytes) {
                                Log.i(TAG, "pipe wrote totalBytes=$totalBytesWrote, icNull=${ims.currentInputConnection == null}")
                            }
                        } else if (readBytes < 0) {
                            Log.e(TAG, "AudioRecord read error: $readBytes")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRecording.get()) {
                    Log.e(TAG, "Audio recording pipe error", e)
                }
            } finally {
                Log.i(TAG, "Audio loop ended. Total wrote: $totalBytesWrote bytes")
                closeQuietly(audioPipeWriteSide)
                audioPipeWriteSide = null
            }
        }.apply {
            name = "LeanTypeVoiceAudioThread"
            start()
        }
    }

    fun stopVoice() {
        Log.i(TAG, "stopVoice() called, state=$state")
        if (state == VoiceState.RECORDING || state == VoiceState.STARTING_SESSION || state == VoiceState.CONNECTING_PLUGIN) {
            updateState(VoiceState.PROCESSING_FINAL)
            stopAudioLoop()
            pluginManager.stopSession()
        }
    }

    fun cancelVoice() {
        Log.i(TAG, "cancelVoice() called, state=$state")
        if (state != VoiceState.IDLE) {
            cancelHandshakeTimeout()
            cancelPendingPartial()
            stopAudioLoop()
            pluginManager.cancelSession()
            clearComposingText()
            cleanupSession()
            updateState(VoiceState.IDLE)
        }
    }

    private fun stopAudioLoop() {
        Log.i(TAG, "stopAudioLoop() executing")
        isRecording.set(false)
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null

        audioThread?.let { thread ->
            try {
                thread.join(500)
            } catch (_: InterruptedException) {}
        }
        audioThread = null

        closeQuietly(audioPipeWriteSide)
        audioPipeWriteSide = null

        closeQuietly(audioPipeReadSide)
        audioPipeReadSide = null
    }

    private fun handlePartialText(text: String) {
        if (text == lastPartialText) return
        lastPartialText = text

        cancelPendingPartial()
        pendingPartialRunnable = Runnable {
            val ic = ims.currentInputConnection
            if (ic != null && isRecording.get()) {
                ic.setComposingText(text, 1)
            }
        }
        mainHandler.postDelayed(pendingPartialRunnable!!, PARTIAL_THROTTLE_MS)
    }

    private fun handleFinalText(text: String) {
        stopAudioLoop()
        val ic = ims.currentInputConnection
        val finalText = text.trim()
        Log.i(TAG, "handleFinalText executing: icNull=${ic == null}, text='$finalText'")
        if (ic != null && finalText.isNotBlank()) {
            val committed = ic.commitText("$finalText ", 1)
            Log.i(TAG, "commitText executed: success=$committed")
        } else {
            Log.w(TAG, "Skipped commitText: icNull=${ic == null}, text='$finalText'")
        }
        ic?.finishComposingText()
        cleanupSession()
        updateState(VoiceState.IDLE)
    }

    private fun clearComposingText() {
        val ic = ims.currentInputConnection
        if (ic != null) {
            ic.setComposingText("", 1)
            ic.finishComposingText()
        }
    }

    private fun cancelPendingPartial() {
        pendingPartialRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingPartialRunnable = null
        lastPartialText = null
    }

    private fun cancelHandshakeTimeout() {
        handshakeTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        handshakeTimeoutRunnable = null
    }

    private fun cleanupSession() {
        cancelHandshakeTimeout()
        cancelPendingPartial()
        stopAudioLoop()
        closeQuietly(audioPipeReadSide)
        audioPipeReadSide = null
        closeQuietly(audioPipeWriteSide)
        audioPipeWriteSide = null
        activeSessionId = null
    }

    private fun updateState(newState: VoiceState) {
        this.state = newState
        mainHandler.post {
            listener?.onStateChanged(newState)
        }
    }

    private fun notifyError(message: String) {
        mainHandler.post {
            listener?.onError(message)
        }
    }

    fun release() {
        cancelVoice()
        pluginManager.release()
    }

    companion object {
        private const val TAG = "VoiceInputManager"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE_MS = 30
        private const val FRAME_SIZE_BYTES = (SAMPLE_RATE * FRAME_SIZE_MS / 1000) * 2 // 960 bytes
        private const val HANDSHAKE_TIMEOUT_MS = 8000L
        private const val PARTIAL_THROTTLE_MS = 300L

        fun isBlockedEditor(info: EditorInfo?): Boolean {
            if (info == null) return false

            val variation = info.inputType and InputType.TYPE_MASK_VARIATION

            return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD ||
                    (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        }
    }

    private fun closeQuietly(pfd: ParcelFileDescriptor?) {
        try {
            pfd?.close()
        } catch (_: Exception) {}
    }
}
