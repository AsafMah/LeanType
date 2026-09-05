// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.ocr.OcrPluginLoader
import helium314.keyboard.latin.utils.AddonPolicy
import helium314.keyboard.settings.FeedbackManager
import helium314.keyboard.settings.dialogs.PreferenceDialog
import helium314.keyboard.settings.filePicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal class OcrPluginPreferenceImports(
    private val context: Context,
    private val scope: CoroutineScope,
    private val uriImporter: (Context, Uri) -> Boolean = OcrPluginLoader::importPlugin,
    private val fileImporter: (Context, File) -> Boolean = OcrPluginLoader::importPluginFromTempFile,
    private val downloader: (Context, File) -> Boolean = { ctx, file ->
        OcrPluginLoader.downloadPluginApk(ctx, null, file)
    },
) {
    fun importUri(uri: Uri, onResult: (Boolean) -> Unit) = runImport(onResult) {
        uriImporter(context, uri)
    }

    fun download(onResult: (Boolean) -> Unit) = runImport(onResult) {
        val file = File(context.cacheDir, "ocr_download_${UUID.randomUUID()}.apk")
        try {
            if (!downloader(context, file)) return@runImport false
            ensureActive()
            fileImporter(context, file)
        } finally {
            file.delete()
        }
    }

    private fun runImport(onResult: (Boolean) -> Unit, operation: suspend CoroutineScope.() -> Boolean) =
        scope.launch(Dispatchers.Main.immediate) {
            val result = withContext(Dispatchers.IO) {
                try {
                    operation()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("LoadOcrPluginPreference", "OCR plugin import failed", e)
                    false
                }
            }
            onResult(result)
        }
}

@Composable
fun LoadOcrPluginPreference(
    title: String,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    onSuccess: (() -> Unit)? = null,
) {
    if (!AddonPolicy.allowsOcrPlugins()) return
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var remoteVersion by remember { mutableStateOf<String?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val imports = remember(ctx, scope) { OcrPluginPreferenceImports(ctx.applicationContext, scope) }

    val hasInternet = remember {
        AddonPolicy.allowsInAppDownloads() && ctx.packageManager.checkPermission(
            "android.permission.INTERNET",
            ctx.packageName
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    var hasPlugin by remember { mutableStateOf(false) }
    var localVersion by remember { mutableStateOf<String?>(null) }
    var refreshGeneration by remember { mutableStateOf(0) }

    LaunchedEffect(refreshGeneration) {
        val installed = withContext(Dispatchers.IO) {
            OcrPluginLoader.hasPlugin(ctx) to OcrPluginLoader.getPluginVersion(ctx)
        }
        hasPlugin = installed.first
        localVersion = installed.second
    }

    LaunchedEffect(hasPlugin) {
        if (!hasInternet) return@LaunchedEffect
        isCheckingUpdate = true
        try {
            remoteVersion = withContext(Dispatchers.IO) {
                val url = URL("https://api.github.com/repos/LeanBitLab/LeanType-OCR-Plugin/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 12000
                    conn.readTimeout = 15000
                    conn.setRequestProperty("User-Agent", "HeliboardL")
                    conn.connect()
                    if (conn.responseCode == 200) {
                        val response = conn.inputStream.bufferedReader().use { it.readText() }
                        "\"tag_name\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(response)?.groupValues?.get(1)
                    } else null
                } finally {
                    conn.disconnect()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            isCheckingUpdate = false
        }
    }

    val launcher = filePicker { uri ->
        if (isDownloading) return@filePicker
        isDownloading = true
        imports.importUri(uri) { success ->
            isDownloading = false
            showDialog = false
            if (success) {
                refreshGeneration++
                FeedbackManager.message(ctx, R.string.load_ocr_plugin_success)
                onSuccess?.invoke()
            } else {
                FeedbackManager.message(ctx, R.string.load_ocr_plugin_failed)
            }
        }
    }

    fun startDownload() {
        if (isDownloading) return
        if (!hasInternet) {
            showDialog = false
            val url = "https://github.com/LeanBitLab/LeanType-OCR-Plugin/releases"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                ctx.startActivity(intent)
                android.widget.Toast.makeText(ctx, "Opening GitHub releases in browser… download the APK and use 'Load APK from storage'", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(ctx, "Failed to open browser: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }

        isDownloading = true
        imports.download { success ->
            isDownloading = false
            showDialog = false
            if (success) {
                refreshGeneration++
                FeedbackManager.message(ctx, R.string.load_ocr_plugin_success)
                onSuccess?.invoke()
            } else {
                FeedbackManager.message(ctx, R.string.load_ocr_plugin_failed)
            }
        }
    }

    val effectiveSummary = when {
        isDownloading -> "Downloading plugin..."
        hasPlugin -> if (localVersion != null) "Active v$localVersion" else "Active"
        else -> summary
    }

    Preference(
        name = title,
        description = effectiveSummary,
        icon = icon,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        PreferenceDialog(
            onDismissRequest = { if (!isDownloading) showDialog = false },
            title = stringResource(R.string.load_ocr_plugin),
            showCloseButton = !isDownloading,
            buttons = {
                if (isDownloading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Downloading...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { startDownload() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val buttonText = when {
                                remoteVersion != null && localVersion != null && remoteVersion != localVersion ->
                                    "Update to $remoteVersion"
                                remoteVersion != null -> "Download plugin ($remoteVersion)"
                                else -> "Download plugin"
                            }
                            Text(buttonText)
                        }

                        OutlinedButton(
                            onClick = {
                                showDialog = false
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType("*/*")
                                try {
                                    launcher.launch(intent)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Load APK from storage")
                        }

                        if (hasPlugin) {
                            Button(
                                onClick = {
                                    isDownloading = true
                                    scope.launch {
                                        withContext(Dispatchers.IO) { OcrPluginLoader.removePlugin(ctx) }
                                        refreshGeneration++
                                        isDownloading = false
                                        FeedbackManager.message(ctx, "OCR plugin removed")
                                        onSuccess?.invoke()
                                        showDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.load_ocr_plugin_button_delete))
                            }
                        }
                    }
                }
            }
        ) {
            val message = when {
                hasPlugin -> "OCR plugin is active (version $localVersion).\n\nWarning: loading external code can be a security risk. Only use a plugin from a source you trust."
                remoteVersion != null -> "Download the latest OCR plugin (version $remoteVersion) from GitHub, or load an APK from local storage.\n\nWarning: loading external code can be a security risk. Only use a plugin from a source you trust."
                else -> "Download the OCR plugin from GitHub, or load an APK from local storage.\n\nWarning: loading external code can be a security risk. Only use a plugin from a source you trust."
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
