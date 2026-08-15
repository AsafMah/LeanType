// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.leanbitlab.leantype.voice.ModelImportRequest
import com.leanbitlab.leantype.voice.ModelState
import com.leanbitlab.leantype.voice.VoiceConstants
import com.leanbitlab.leantype.voice.VoiceEngineInfo
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.VoiceModelDownloadDialog
import helium314.keyboard.settings.filePicker
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.SwitchPreference

@Composable
fun VoiceSettingsScreen(
    onClickBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.prefs()

    var isMicPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isMicPermissionGranted = granted
    }

    val pluginManager = remember(context) { VoicePluginManager(context) }
    var engineInfo by remember { mutableStateOf<VoiceEngineInfo?>(null) }
    var isPluginConnected by remember { mutableStateOf(false) }
    var isPluginInstalled by remember { mutableStateOf(false) }

    var voskState by remember { mutableStateOf<ModelState?>(null) }
    var whisperState by remember { mutableStateOf<ModelState?>(null) }
    var showModelDownloadDialog by remember { mutableStateOf(false) }

    val updatePluginStatus = {
        isPluginInstalled = pluginManager.isPluginInstalled()
        if (pluginManager.isPluginConnected()) {
            isPluginConnected = true
            engineInfo = pluginManager.getInfo()
            voskState = pluginManager.getModelState(VoiceConstants.ENGINE_VOSK)
            whisperState = pluginManager.getModelState(VoiceConstants.ENGINE_WHISPER)
        } else {
            isPluginConnected = false
            engineInfo = null
            voskState = null
            whisperState = null
        }
    }

    DisposableEffect(context) {
        pluginManager.setConnectionListener(object : VoicePluginManager.PluginConnectionListener {
            override fun onPluginConnected(info: VoiceEngineInfo?) {
                isPluginConnected = true
                engineInfo = info
                updatePluginStatus()
            }

            override fun onPluginDisconnected() {
                isPluginConnected = false
                engineInfo = null
                voskState = null
                whisperState = null
            }
        })
        pluginManager.bindIfNeeded()
        updatePluginStatus()

        onDispose {
            pluginManager.unbind()
        }
    }

    LaunchedEffect(isPluginConnected) {
        if (isPluginConnected) {
            while (isActive) {
                updatePluginStatus()
                kotlinx.coroutines.delay(1500)
            }
        }
    }

    val voskPicker = filePicker { uri ->
        scope.launch(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val size = pfd.statSize
                    val request = ModelImportRequest(
                        engineType = VoiceConstants.ENGINE_VOSK,
                        language = "en-US",
                        sha256 = null,
                        sizeBytes = size,
                        file = pfd
                    )
                    if (!pluginManager.isPluginConnected()) {
                        pluginManager.bindIfNeeded()
                    }
                    pluginManager.importModelSafely(request)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Vosk model import dispatched", Toast.LENGTH_SHORT).show()
                        updatePluginStatus()
                    }
                }
            } catch (e: Exception) {
                Log.e("VoiceSettingsScreen", "Failed to import Vosk model", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Model import failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val whisperPicker = filePicker { uri ->
        scope.launch(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val size = pfd.statSize
                    val request = ModelImportRequest(
                        engineType = VoiceConstants.ENGINE_WHISPER,
                        language = "multilingual",
                        sha256 = null,
                        sizeBytes = size,
                        file = pfd
                    )
                    if (!pluginManager.isPluginConnected()) {
                        pluginManager.bindIfNeeded()
                    }
                    pluginManager.importModelSafely(request)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Whisper model import dispatched", Toast.LENGTH_SHORT).show()
                        updatePluginStatus()
                    }
                }
            } catch (e: Exception) {
                Log.e("VoiceSettingsScreen", "Failed to import Whisper model", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Model import failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val offlineEnabledSetting = remember {
        Setting(
            key = VoiceConstants.PREF_VOICE_OFFLINE_ENABLED,
            title = context.getString(R.string.offline_voice_title),
            description = context.getString(R.string.pref_offline_voice_summary)
        ) {
            SwitchPreference(
                setting = it,
                default = false
            )
        }
    }

    val voiceModeSetting = remember {
        Setting(
            key = VoiceConstants.PREF_VOICE_MODE,
            title = "Voice Engine Mode"
        ) {
            ListPreference(
                setting = it,
                items = listOf(
                    "Fast (Vosk streaming)" to VoiceConstants.MODE_FAST,
                    "Accurate (Whisper offline)" to VoiceConstants.MODE_ACCURATE,
                    "Hybrid (Vosk + Whisper)" to VoiceConstants.MODE_HYBRID
                ),
                default = VoiceConstants.MODE_FAST
            )
        }
    }

    val whisperKeepLoadedSetting = remember {
        Setting(
            key = VoiceConstants.PREF_VOICE_WHISPER_KEEP_LOADED_SECONDS,
            title = "Keep Whisper Loaded"
        ) {
            ListPreference(
                setting = it,
                items = listOf(
                    "Keep in memory for 1 minute" to "60",
                    "Keep in memory for 5 minutes (Recommended)" to "300",
                    "Keep in memory for 15 minutes" to "900",
                    "Unload immediately after session" to "0"
                ),
                default = "300"
            )
        }
    }

    val hybridTimeoutSetting = remember {
        Setting(
            key = VoiceConstants.PREF_VOICE_HYBRID_TIMEOUT_MS,
            title = "Hybrid Whisper Timeout (ms)"
        ) {
            ListPreference(
                setting = it,
                items = listOf(
                    "500 ms" to "500",
                    "700 ms" to "700",
                    "900 ms (Recommended)" to "900",
                    "1200 ms" to "1200",
                    "1500 ms" to "1500"
                ),
                default = "900"
            )
        }
    }

    val hybridFallbackSetting = remember {
        Setting(
            key = VoiceConstants.PREF_VOICE_HYBRID_FALLBACK,
            title = "Fallback to Vosk if Whisper fails",
            description = "Allow Fast Vosk output if Whisper times out or model is missing"
        ) {
            SwitchPreference(
                setting = it,
                default = true
            )
        }
    }

    if (showModelDownloadDialog) {
        VoiceModelDownloadDialog(
            onDismissRequest = { showModelDownloadDialog = false },
            pluginManager = pluginManager,
            voskState = voskState,
            whisperState = whisperState,
            onRefresh = { updatePluginStatus() },
            onImportLocalFile = { engineType ->
                val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                if (engineType == VoiceConstants.ENGINE_WHISPER) {
                    whisperPicker.launch(intent)
                } else {
                    voskPicker.launch(intent)
                }
            }
        )
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = context.getString(R.string.offline_voice_title),
        settings = emptyList()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            offlineEnabledSetting.Preference()

            // Microphone permission card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Microphone Permission",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isMicPermissionGranted) "Granted" else "Permission required for voice dictation",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!isMicPermissionGranted) {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                            Text("Grant")
                        }
                    }
                }
            }

            // Plugin status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Plugin Status",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val statusText = when {
                        isPluginConnected -> "Connected: ${engineInfo?.displayName ?: "Voice Plugin"}"
                        isPluginInstalled -> "Plugin installed (disconnected)"
                        else -> "Plugin not installed (com.leanbitlab.leantype.voice.offline)"
                    }
                    Text(text = statusText, style = MaterialTheme.typography.bodyMedium)

                    if (!isPluginConnected) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            pluginManager.bindIfNeeded()
                            updatePluginStatus()
                        }) {
                            Text("Connect Plugin")
                        }
                    }
                }
            }

            voiceModeSetting.Preference()

            // Models section
            Text(
                text = "Local Speech Models",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            val whisperDesc = when (whisperState?.state) {
                ModelState.STATE_READY -> "Whisper: Ready"
                ModelState.STATE_LOADING -> "Whisper: Loading…"
                ModelState.STATE_ERROR -> "Whisper: Error"
                else -> "Whisper: Not installed"
            }
            val voskDesc = when (voskState?.state) {
                ModelState.STATE_READY -> "Vosk: Ready"
                ModelState.STATE_LOADING -> "Vosk: Loading…"
                ModelState.STATE_ERROR -> "Vosk: Error"
                else -> "Vosk: Not installed"
            }

            Preference(
                name = "Manage & Download Speech Models",
                description = "$whisperDesc • $voskDesc. Tap to manage curated models",
                onClick = {
                    showModelDownloadDialog = true
                }
            )

            whisperKeepLoadedSetting.Preference()
            hybridTimeoutSetting.Preference()
            hybridFallbackSetting.Preference()
        }
    }
}
