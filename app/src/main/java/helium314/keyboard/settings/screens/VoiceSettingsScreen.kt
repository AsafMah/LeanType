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
    var engineInfo by remember { mutableStateOf<VoiceEngineInfo?>(pluginManager.getInfo()) }
    var isPluginConnected by remember { mutableStateOf(pluginManager.isPluginConnected()) }
    var isPluginInstalled by remember { mutableStateOf(pluginManager.isPluginInstalled()) }
    var isInitialConnectionPending by remember { mutableStateOf(!isPluginConnected && isPluginInstalled) }

    var whisperState by remember { mutableStateOf<ModelState?>(pluginManager.getModelState(VoiceConstants.ENGINE_WHISPER)) }
    var showModelDownloadDialog by remember { mutableStateOf(false) }

    val updatePluginStatus = {
        isPluginInstalled = pluginManager.isPluginInstalled()
        if (pluginManager.isPluginConnected()) {
            isPluginConnected = true
            isInitialConnectionPending = false
            engineInfo = pluginManager.getInfo()
            whisperState = pluginManager.getModelState(VoiceConstants.ENGINE_WHISPER)
        } else {
            isPluginConnected = false
            engineInfo = null
            whisperState = null
        }
    }

    DisposableEffect(context) {
        pluginManager.setConnectionListener(object : VoicePluginManager.PluginConnectionListener {
            override fun onPluginConnected(info: VoiceEngineInfo?) {
                isPluginConnected = true
                isInitialConnectionPending = false
                engineInfo = info
                updatePluginStatus()
            }

            override fun onPluginDisconnected() {
                isPluginConnected = false
                isInitialConnectionPending = false
                engineInfo = null
                whisperState = null
            }
        })
        val bound = pluginManager.bindIfNeeded()
        if (!bound) {
            isInitialConnectionPending = false
        }
        updatePluginStatus()

        onDispose {
            pluginManager.unbind()
        }
    }

    LaunchedEffect(Unit) {
        if (isInitialConnectionPending) {
            kotlinx.coroutines.delay(1200)
            isInitialConnectionPending = false
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
                        prefs.edit().putString("installed_model_${VoiceConstants.ENGINE_WHISPER}", "custom").apply()
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

    val silenceTimeoutSetting = remember {
        Setting(
            key = VoiceConstants.PREF_VOICE_SILENCE_TIMEOUT_SECONDS,
            title = "Silence Timeout"
        ) {
            ListPreference(
                setting = it,
                items = listOf(
                    "3 seconds" to "3",
                    "5 seconds (Recommended)" to "5",
                    "7 seconds" to "7",
                    "10 seconds" to "10",
                    "15 seconds" to "15",
                    "Never (Listen until mic tapped)" to "0"
                ),
                default = "5"
            )
        }
    }

    if (showModelDownloadDialog) {
        VoiceModelDownloadDialog(
            onDismissRequest = { showModelDownloadDialog = false },
            pluginManager = pluginManager,
            whisperState = whisperState,
            onRefresh = { updatePluginStatus() },
            onImportLocalFile = {
                val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                whisperPicker.launch(intent)
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

            // Microphone permission card (only show when not granted)
            if (!isMicPermissionGranted) {
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
                                text = "Permission required for voice dictation",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                            Text("Grant")
                        }
                    }
                }
            }

            // Plugin status card (only show when not installed or confirmed disconnected)
            if (!isPluginInstalled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Voice Plugin Not Installed",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Offline voice input requires the LeanType Voice Plugin (com.leanbitlab.leantype.voice.offline).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else if (!isPluginConnected && !isInitialConnectionPending) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Voice Plugin Disconnected",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "The voice engine service is currently disconnected.",
                            style = MaterialTheme.typography.bodyMedium
                        )
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

            silenceTimeoutSetting.Preference()

            // Models section
            Text(
                text = "Speech Models (Whisper & Distil-Whisper)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            val whisperDesc = when (whisperState?.state) {
                ModelState.STATE_READY -> "Status: Ready"
                ModelState.STATE_LOADING -> "Status: Loading…"
                ModelState.STATE_ERROR -> "Status: Error"
                else -> if (isPluginConnected) "Status: No model installed" else if (isInitialConnectionPending) "Status: Connecting…" else "Status: Plugin disconnected"
            }

            Preference(
                name = "Manage & Download Models",
                description = "$whisperDesc. Tap to download Distil-Whisper or standard Whisper models",
                onClick = {
                    showModelDownloadDialog = true
                }
            )

            whisperKeepLoadedSetting.Preference()
        }
    }
}
