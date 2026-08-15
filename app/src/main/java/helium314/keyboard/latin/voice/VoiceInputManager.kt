// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.Manifest
import android.content.Context
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
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.leanbitlab.leantype.voice.IVoiceCallback
import com.leanbitlab.leantype.voice.VoiceConstants
import com.leanbitlab.leantype.voice.VoiceSessionConfig
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
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
    private var lastPartialText: String? = null
    private var handshakeTimeoutRunnable: Runnable? = null
    private var needsCapitalStart = true

    private var listener: VoiceInputListener? = null

    fun setListener(listener: VoiceInputListener?) {
        this.listener = listener
    }

    fun getState(): VoiceState = state

    fun isRecording(): Boolean = state == VoiceState.RECORDING || state == VoiceState.STARTING_SESSION

    fun canStartVoice(): Boolean {
        if (!ims.prefs().getBoolean(VoiceConstants.PREF_VOICE_OFFLINE_ENABLED, false)) {
            Log.w(TAG, "canStartVoice: Voice input not enabled in preferences")
            return false
        }
        if (ContextCompat.checkSelfPermission(ims, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "canStartVoice: Missing RECORD_AUDIO permission")
            return false
        }
        if (isBlockedEditor(ims.currentInputEditorInfo)) {
            Log.w(TAG, "canStartVoice: Blocked editor (password or no-suggestions)")
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

        val isConnected = pluginManager.isPluginConnected()
        Log.i(TAG, "startVoice: isConnected=$isConnected")

        pluginManager.cancelSession()
        val sessionId = UUID.randomUUID().toString()
        activeSessionId = sessionId
        needsCapitalStart = true

        if (!isConnected) {
            updateState(VoiceState.CONNECTING_PLUGIN)
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
        if (state == VoiceState.CONNECTING_PLUGIN) {
            updateState(VoiceState.STARTING_SESSION)
        }

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

        val languageTag = try {
            RichInputMethodManager.getInstance().currentSubtypeLocale.toLanguageTag()
        } catch (_: Exception) {
            java.util.Locale.getDefault().toLanguageTag()
        }

        val config = VoiceSessionConfig(
            sessionId = sessionId,
            mode = mode,
            languageTag = languageTag,
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
                Log.i(TAG, "Received onPartial from plugin: '$text'")
                mainHandler.post {
                    if (activeSessionId == sessionId && isRecording.get()) {
                        if (!text.isNullOrBlank()) {
                            handlePartialText(text)
                        }
                    }
                }
            }

            override fun onFinal(text: String?) {
                Log.i(TAG, "Received onFinal: '$text' (isRecording=${isRecording.get()})")
                mainHandler.post {
                    if (activeSessionId == sessionId) {
                        val ic = ims.currentInputConnection
                        val rawText = text.orEmpty()
                        val prefs = ims.prefs()
                        val cmdsEnabled = prefs.getBoolean(VoiceConstants.PREF_VOICE_COMMANDS_ENABLED, true)
                        val punctEnabled = prefs.getBoolean(VoiceConstants.PREF_VOICE_SMART_PUNCTUATION, true)

                        val results = VoiceTextProcessor.process(rawText, cmdsEnabled, punctEnabled, needsCapitalStart)

                        var lastWasTerminal = false
                        var lastWasNewline = false

                        for (result in results) {
                            when (result) {
                                is VoiceTextProcessor.Result.Command -> {
                                    Log.i(TAG, "Executing voice command: ${result.action} ('${result.commandText}')")
                                    if (ic != null) {
                                        executeVoiceCommand(result.action, ic)
                                    }
                                    if (result.action == VoiceTextProcessor.Action.NEW_LINE ||
                                        result.action == VoiceTextProcessor.Action.NEW_PARAGRAPH
                                    ) {
                                        lastWasNewline = true
                                    }
                                }
                                is VoiceTextProcessor.Result.Text -> {
                                    val finalText = result.value.trim()
                                    Log.i(TAG, "Processing onFinal text='$finalText' (icNull=${ic == null}, isRecording=${isRecording.get()})")

                                    if (ic != null && finalText.isNotEmpty()) {
                                        val committed = ic.commitText("$finalText ", 1)
                                        Log.i(TAG, "commitText executed: success=$committed")
                                        ic.finishComposingText()
                                        lastWasTerminal = result.isTerminal
                                        lastWasNewline = false
                                    } else {
                                        ic?.finishComposingText()
                                    }
                                }
                            }
                        }

                        if (results.isNotEmpty()) {
                            needsCapitalStart = lastWasTerminal || lastWasNewline
                        }

                        lastPartialText = null

                        if (!isRecording.get()) {
                            Log.i(TAG, "Final session commit complete, transitioning to IDLE")
                            cleanupSession()
                            updateState(VoiceState.IDLE)
                        } else {
                            Log.i(TAG, "Segment refined & committed. Continuing continuous recording.")
                        }
                    }
                }
            }

            override fun onError(code: Int, message: String?) {
                Log.e(TAG, "Received onError: code=$code, message='$message'")
                mainHandler.post {
                    if (activeSessionId == sessionId) {
                        clearComposingText()
                        notifyError(message ?: "Voice error ($code)")
                        cleanupSession()
                        updateState(VoiceState.ERROR)
                    }
                }
            }

            override fun onSessionEnded() {
                Log.i(TAG, "Received onSessionEnded, state=$state")
                mainHandler.post {
                    if (activeSessionId == sessionId) {
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
        handshakeTimeoutRunnable?.let {
            mainHandler.postDelayed(it, HANDSHAKE_TIMEOUT_MS)
        }

        // Start hardware audio capture IMMEDIATELY so the green mic privacy dot appears without IPC delay
        startAudioRecordingThread()

        try {
            val pfdForPlugin = audioPipeReadSide
            if (pfdForPlugin != null) {
                audioPipeReadSide = null
                pluginManager.startSession(config, pfdForPlugin, callback)
            } else {
                throw IllegalStateException("Read-side pipe descriptor is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remote exception starting voice session", e)
            cancelHandshakeTimeout()
            notifyError("Failed to start voice session with plugin")
            cleanupSession()
            updateState(VoiceState.ERROR)
        }
    }

    private fun startAudioRecordingThread(): Boolean {
        if (ContextCompat.checkSelfPermission(ims, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "startAudioRecordingThread: Missing RECORD_AUDIO permission")
            return false
        }

        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufSize <= 0) {
            Log.e(TAG, "startAudioRecordingThread: Invalid min buffer size: $minBufSize")
            return false
        }

        // Multiply by 4 (at least 8192) to prevent hardware buffer overruns during Whisper inference blocks
        val bufferSize = maxOf(minBufSize * 4, FRAME_SIZE_BYTES * 8, 8192)

        var record: AudioRecord? = null
        try {
            record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create AudioRecord with VOICE_RECOGNITION, trying MIC", e)
        }

        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            try {
                record?.release()
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create AudioRecord with MIC fallback", e)
                return false
            }
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize (state=${record.state})")
            record.release()
            return false
        }

        audioRecord = record
        try {
            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Exception in AudioRecord.startRecording", e)
            audioRecord?.release()
            audioRecord = null
            return false
        }

        isRecording.set(true)
        val writePfd = audioPipeWriteSide ?: return false

        audioThread = Thread({
            val buffer = ByteArray(FRAME_SIZE_BYTES)
            var outputStream: FileOutputStream? = null
            var totalBytesWritten = 0L

            try {
                outputStream = FileOutputStream(writePfd.fileDescriptor)
                while (isRecording.get()) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        outputStream.write(buffer, 0, read)
                        outputStream.flush()
                        totalBytesWritten += read
                    } else if (read < 0) {
                        Log.e(TAG, "AudioRecord read error: $read")
                        break
                    }
                }
            } catch (e: Exception) {
                if (isRecording.get()) {
                    Log.e(TAG, "Exception in audio write loop", e)
                }
            } finally {
                try { outputStream?.close() } catch (_: Exception) {}
                Log.i(TAG, "Audio loop ended. Total wrote: $totalBytesWritten bytes")
            }
        }, "VoiceAudioThread").apply {
            isDaemon = true
            start()
        }

        return true
    }

    private fun cancelHandshakeTimeout() {
        handshakeTimeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            handshakeTimeoutRunnable = null
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
            cleanupSession()
            pluginManager.cancelSession()
            clearComposingText()
            updateState(VoiceState.IDLE)
        }
    }

    private fun stopAudioLoop() {
        if (!isRecording.getAndSet(false)) return
        Log.i(TAG, "stopAudioLoop() executing")

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null

        audioThread?.let { thread ->
            try {
                thread.join(500)
            } catch (e: InterruptedException) {
                Log.w(TAG, "Interrupted while joining audioThread", e)
            }
        }
        audioThread = null

        closeQuietly(audioPipeWriteSide)
        audioPipeWriteSide = null
    }

    private fun handlePartialText(text: String) {
        if (text == lastPartialText) return
        lastPartialText = text

        val ic = ims.currentInputConnection
        Log.i(TAG, "Setting composing text on InputConnection: '$text' (icNull=${ic == null}, isRecording=${isRecording.get()})")
        if (ic != null && isRecording.get()) {
            ic.setComposingText(text, 1)
        }
    }

    private fun executeVoiceCommand(action: VoiceTextProcessor.Action, ic: InputConnection) {
        ic.finishComposingText()
        when (action) {
            VoiceTextProcessor.Action.NEW_LINE -> {
                ic.commitText("\n", 1)
            }
            VoiceTextProcessor.Action.NEW_PARAGRAPH -> {
                ic.commitText("\n\n", 1)
            }
            VoiceTextProcessor.Action.DELETE_LAST_WORD -> {
                val before = ic.getTextBeforeCursor(100, 0)?.toString() ?: return
                val trimmed = before.trimEnd()
                val lastSpace = trimmed.lastIndexOf(' ')
                val wordLen = if (lastSpace == -1) trimmed.length else trimmed.length - lastSpace - 1
                val totalDelete = wordLen + (before.length - trimmed.length)
                if (totalDelete > 0) {
                    ic.deleteSurroundingText(totalDelete, 0)
                }
            }
            VoiceTextProcessor.Action.CLEAR_ALL -> {
                ic.beginBatchEdit()
                ic.performContextMenuAction(android.R.id.selectAll)
                ic.commitText("", 1)
                ic.endBatchEdit()
            }
            VoiceTextProcessor.Action.SEND -> {
                val editorInfo = ims.currentInputEditorInfo
                if (editorInfo != null) {
                    val actionId = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
                    if (actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
                        ic.performEditorAction(actionId)
                    } else {
                        ic.performEditorAction(EditorInfo.IME_ACTION_SEND)
                    }
                }
            }
        }
        Toast.makeText(ims, R.string.voice_command_executed, Toast.LENGTH_SHORT).show()
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
        lastPartialText = null
    }

    private fun cleanupSession() {
        cancelHandshakeTimeout()
        stopAudioLoop()
        closeQuietly(audioPipeReadSide)
        audioPipeReadSide = null
        closeQuietly(audioPipeWriteSide)
        audioPipeWriteSide = null
        activeSessionId = null
    }

    @Synchronized
    private fun updateState(newState: VoiceState) {
        val oldState = this.state
        if (oldState == newState) {
            Log.d(TAG, "State dedup: $oldState -> $newState (ignored)")
            return
        }
        Log.i(TAG, "State transition: $oldState -> $newState")
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
