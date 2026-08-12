// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.leanbitlab.leantype.voice.IVoiceCallback
import com.leanbitlab.leantype.voice.IVoiceEngine
import com.leanbitlab.leantype.voice.ModelImportRequest
import com.leanbitlab.leantype.voice.ModelState
import com.leanbitlab.leantype.voice.VoiceConstants
import com.leanbitlab.leantype.voice.VoiceEngineInfo
import com.leanbitlab.leantype.voice.VoiceSessionConfig
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs

class VoicePluginManager(private val context: Context) : IBinder.DeathRecipient {

    interface PluginConnectionListener {
        fun onPluginConnected(info: VoiceEngineInfo?)
        fun onPluginDisconnected()
    }

    private var engine: IVoiceEngine? = null
    private var isBound = false
    private var isConnecting = false
    private var deathRecipientRegistered = false
    private var connectionListener: PluginConnectionListener? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "VoiceEngineService connected: $name")
            isConnecting = false
            engine = IVoiceEngine.Stub.asInterface(service)
            try {
                service?.linkToDeath(this@VoicePluginManager, 0)
                deathRecipientRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to link to binder death", e)
            }
            val info = getInfo()
            connectionListener?.onPluginConnected(info)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "VoiceEngineService disconnected: $name")
            handleDisconnection()
        }
    }

    fun setConnectionListener(listener: PluginConnectionListener?) {
        this.connectionListener = listener
    }

    fun isPluginInstalled(): Boolean {
        val intent = Intent().apply { component = resolveServiceComponent() }
        val list = context.packageManager.queryIntentServices(intent, 0)
        return list.isNotEmpty()
    }

    fun isPluginConnected(): Boolean = engine != null && isBound

    fun bindIfNeeded(): Boolean {
        if (isPluginConnected()) return true
        if (isConnecting) return false

        val component = resolveServiceComponent()
        val intent = Intent().apply { this.component = component }

        Log.i(TAG, "Binding to voice plugin service: $component")
        isConnecting = true
        val success = try {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind to voice plugin service", e)
            false
        }

        if (!success) {
            isConnecting = false
        }
        return success
    }

    fun unbind() {
        if (isBound || isConnecting) {
            try {
                if (deathRecipientRegistered) {
                    engine?.asBinder()?.unlinkToDeath(this, 0)
                    deathRecipientRegistered = false
                }
                context.unbindService(connection)
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding voice plugin service", e)
            } finally {
                handleDisconnection()
            }
        }
    }

    override fun binderDied() {
        Log.e(TAG, "Voice plugin binder died unexpectedly")
        handleDisconnection()
    }

    private fun handleDisconnection() {
        engine = null
        isBound = false
        isConnecting = false
        deathRecipientRegistered = false
        connectionListener?.onPluginDisconnected()
    }

    private fun resolveServiceComponent(): ComponentName {
        val useDebugStub = BuildConfig.DEBUG &&
                context.prefs().getBoolean(VoiceConstants.PREF_USE_DEBUG_VOICE_STUB, false)

        return if (useDebugStub) {
            ComponentName(
                context.packageName,
                "helium314.keyboard.latin.voice.DebugVoiceEngineService"
            )
        } else {
            ComponentName(
                "com.leanbitlab.leantype.voice.offline",
                "com.leanbitlab.leantype.voice.offline.VoiceEngineService"
            )
        }
    }

    fun getInfo(): VoiceEngineInfo? {
        return try {
            engine?.info
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get plugin info", e)
            null
        }
    }

    fun getModelState(engineType: String): ModelState? {
        return try {
            engine?.getModelState(engineType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get model state for $engineType", e)
            null
        }
    }

    fun importModelSafely(request: ModelImportRequest) {
        try {
            engine?.importModel(request)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import model", e)
        } finally {
            try {
                request.file.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close PFD for model import", e)
            }
        }
    }

    fun unloadModel(engineType: String) {
        try {
            engine?.unloadModel(engineType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unload model $engineType", e)
        }
    }

    fun deleteModel(engineType: String) {
        try {
            engine?.deleteModel(engineType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete model $engineType", e)
        }
    }

    fun startSession(
        config: VoiceSessionConfig,
        audioInput: android.os.ParcelFileDescriptor,
        callback: IVoiceCallback
    ): Boolean {
        return try {
            engine?.startSession(config, audioInput, callback)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice session", e)
            false
        }
    }

    fun stopSession() {
        try {
            engine?.stopSession()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop session", e)
        }
    }

    fun cancelSession() {
        try {
            engine?.cancelSession()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel session", e)
        }
    }

    fun release() {
        try {
            engine?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release voice engine", e)
        } finally {
            unbind()
        }
    }

    companion object {
        private const val TAG = "VoicePluginManager"
    }
}
