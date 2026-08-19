// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.leanbitlab.leantype.voice.ModelImportRequest
import com.leanbitlab.leantype.voice.ModelState
import com.leanbitlab.leantype.voice.VoiceConstants
import com.leanbitlab.leantype.voice.VoiceEngineInfo
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.VoiceModelDownloadDialog
import helium314.keyboard.settings.filePicker
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

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
    val installedWhisperPref = remember(prefs) { prefs.getString("installed_model_${VoiceConstants.ENGINE_WHISPER}", null) }
    var whisperState by remember {
        mutableStateOf<ModelState?>(
            pluginManager.getModelState(VoiceConstants.ENGINE_WHISPER)
                ?: if (installedWhisperPref != null) ModelState(VoiceConstants.ENGINE_WHISPER, ModelState.STATE_READY, null) else null
        )
    }
    var showModelDownloadDialog by remember { mutableStateOf(false) }

    var isDownloadingPlugin by remember { mutableStateOf(false) }
    var pluginDownloadProgress by remember { mutableFloatStateOf(0f) }

    fun installDownloadedPlugin(file: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Error starting installation: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun downloadAndInstallPlugin() {
        isDownloadingPlugin = true
        pluginDownloadProgress = 0f
        scope.launch(Dispatchers.IO) {
            try {
                var downloadUrl: String? = null
                try {
                    val url = URL(Links.VOICE_PLUGIN_RELEASES_API)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "LeanType-Android")
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    if (conn.responseCode == 200) {
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(resp)
                        val assets = json.optJSONArray("assets")
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.optString("name", "")
                                if (name.endsWith(".apk", ignoreCase = true)) {
                                    downloadUrl = asset.optString("browser_download_url", "")
                                    break
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("VoiceSettingsScreen", "Failed to fetch plugin release from API", e)
                }

                if (downloadUrl.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        isDownloadingPlugin = false
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Links.VOICE_PLUGIN_REPO)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                        Toast.makeText(context, "Opening plugin repository in browser", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val updatesDir = File(context.cacheDir, "updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()
                val targetFile = File(updatesDir, "voice_plugin.apk")

                var url = URL(downloadUrl)
                var conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "LeanType-Android")
                conn.connect()

                var redirectCount = 0
                while ((conn.responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                            conn.responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                            conn.responseCode == 307 || conn.responseCode == 308) && redirectCount < 5) {
                    val location = conn.getHeaderField("Location") ?: break
                    url = URL(location)
                    conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "LeanType-Android")
                    conn.connect()
                    redirectCount++
                }

                val totalBytes = conn.contentLength
                var downloadedBytes = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val prog = downloadedBytes.toFloat() / totalBytes.toFloat()
                                withContext(Dispatchers.Main) {
                                    pluginDownloadProgress = prog
                                }
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    isDownloadingPlugin = false
                    installDownloadedPlugin(targetFile)
                }
            } catch (e: Exception) {
                Log.e("VoiceSettingsScreen", "Failed to download plugin", e)
                withContext(Dispatchers.Main) {
                    isDownloadingPlugin = false
                    Toast.makeText(context, "Download failed: ${e.localizedMessage}. Opening browser...", Toast.LENGTH_LONG).show()
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Links.VOICE_PLUGIN_REPO)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    val updatePluginStatus = {
        isPluginInstalled = pluginManager.isPluginInstalled()
        if (pluginManager.isPluginConnected()) {
            isPluginConnected = true
            isInitialConnectionPending = false
            engineInfo = pluginManager.getInfo()
            whisperState = pluginManager.getModelState(VoiceConstants.ENGINE_WHISPER)
        } else if (!isInitialConnectionPending) {
            isPluginConnected = false
            engineInfo = null
            whisperState = if (installedWhisperPref != null) {
                ModelState(VoiceConstants.ENGINE_WHISPER, ModelState.STATE_READY, null)
            } else null
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
            updatePluginStatus()
        }

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

    LaunchedEffect(Unit) {
        while (isActive) {
            updatePluginStatus()
            kotlinx.coroutines.delay(1500)
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
                    "Always keep in memory" to "-1",
                    "Keep in memory for 15 minutes" to "900",
                    "Keep in memory for 5 minutes" to "300",
                    "Keep in memory for 1 minute" to "60",
                    "Unload immediately after session" to "0"
                ),
                default = "300"
            )
        }
    }

    val voiceLanguageItems = remember(context) { buildVoiceLanguageEntries(context) }
    val voiceLanguageSetting = remember {
        Setting(
            key = VoiceConstants.PREF_VOICE_LANGUAGE,
            title = context.getString(R.string.pref_voice_language_title)
        ) {
            ListPreference(
                setting = it,
                items = voiceLanguageItems,
                default = VoiceConstants.VOICE_LANG_FOLLOW_KEYBOARD,
                icon = R.drawable.ic_settings_languages
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

            // Plugin status card
            if (isPluginInstalled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Voice Plugin",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isPluginConnected) "Installed & Connected"
                                    else if (isInitialConnectionPending) "Connecting…"
                                    else "Installed (Disconnected)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isPluginConnected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!isPluginConnected && !isInitialConnectionPending) {
                                    Button(
                                        onClick = {
                                            pluginManager.bindIfNeeded()
                                            updatePluginStatus()
                                        },
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("Connect")
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        val appInfoIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${VoiceConstants.VOICE_PLUGIN_PACKAGE}")
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(appInfoIntent)
                                    },
                                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Uninstall")
                                }
                            }
                        }
                    }
                }
            } else {
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
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Offline voice input requires the LeanType Voice Plugin (com.leanbitlab.leantype.voice.offline).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (BuildConfig.FLAVOR == "standardfull") {
                            if (isDownloadingPlugin) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    LinearProgressIndicator(
                                        progress = { pluginDownloadProgress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = "Downloading plugin... ${(pluginDownloadProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { downloadAndInstallPlugin() }
                                ) {
                                    Text("Download & Install Plugin")
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Links.VOICE_PLUGIN_REPO)).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                }
                            ) {
                                Text("Download Plugin")
                            }
                        }
                    }
                }
            }

            voiceLanguageSetting.Preference()
            silenceTimeoutSetting.Preference()

            // Models section
            Text(
                text = "Speech Models",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            val (badgeText, badgeContainerColor, badgeContentColor) = when (whisperState?.state) {
                ModelState.STATE_READY -> Triple("Ready", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                ModelState.STATE_LOADING -> Triple("Loading…", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                ModelState.STATE_ERROR -> Triple("Error", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                else -> if (isPluginConnected) {
                    Triple("No model", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (isInitialConnectionPending) {
                    Triple("Connecting…", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Triple("Disconnected", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Preference(
                name = "Manage & Download Models",
                description = null,
                onClick = {
                    showModelDownloadDialog = true
                },
                value = {
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        color = badgeContainerColor
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeContentColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            )

            whisperKeepLoadedSetting.Preference()
        }
    }
}

private val WHISPER_LANGUAGE_CODES = arrayOf(
    "af", "am", "ar", "as", "az", "ba", "be", "bg", "bn", "bo", "br", "bs", "ca", "cs", "cy", "da",
    "de", "el", "en", "es", "et", "eu", "fa", "fi", "fo", "fr", "gl", "gu", "ha", "haw", "he", "hi",
    "hr", "ht", "hu", "hy", "id", "is", "it", "ja", "jw", "ka", "kk", "km", "kn", "ko", "la", "lb",
    "ln", "lo", "lt", "lv", "mg", "mi", "mk", "ml", "mn", "mr", "ms", "mt", "my", "ne", "nl", "nn",
    "no", "oc", "pa", "pl", "ps", "pt", "ro", "ru", "sa", "sd", "si", "sk", "sl", "sn", "so", "sq",
    "sr", "su", "sv", "sw", "ta", "te", "tg", "th", "tk", "tl", "tr", "tt", "uk", "ur", "uz", "vi",
    "yi", "yo", "yue", "zh"
)

private fun buildVoiceLanguageEntries(context: android.content.Context): List<Pair<String, String>> {
    val sysLocale = context.resources.configuration.locales[0]
    val list = mutableListOf<Pair<String, String>>()
    list.add(context.getString(R.string.voice_lang_follow_keyboard) to VoiceConstants.VOICE_LANG_FOLLOW_KEYBOARD)
    list.add(context.getString(R.string.voice_lang_auto_detect) to VoiceConstants.VOICE_LANG_AUTO)

    val langItems = WHISPER_LANGUAGE_CODES.map { code ->
        val loc = java.util.Locale.forLanguageTag(code)
        val name = loc.getDisplayName(sysLocale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(sysLocale) else it.toString() }
        "$name ($code)" to code
    }.sortedBy { it.first.lowercase(sysLocale) }

    list.addAll(langItems)
    return list
}
