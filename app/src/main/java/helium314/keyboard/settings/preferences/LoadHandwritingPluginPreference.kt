// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
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
import helium314.keyboard.latin.handwriting.HandwritingLoader
import helium314.keyboard.settings.FeedbackManager
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.filePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun LoadHandwritingPluginPreference(
    title: String,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    onSuccess: (() -> Unit)? = null,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var isDownloading by rememberSaveable { mutableStateOf(false) }
    var remoteVersion by remember { mutableStateOf<String?>(null) }
    var updateAvailable by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val hasPlugin = HandwritingLoader.hasPlugin(ctx)
    val localVersion = remember(hasPlugin) { HandwritingLoader.getPluginVersion(ctx) }

    LaunchedEffect(hasPlugin) {
        isCheckingUpdate = true
        scope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/LeanBitLab/Leantype-Handwriting-Plugin/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "HeliboardL")
                conn.connect()
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val regex = "\"tag_name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    val match = regex.find(response)
                    if (match != null) {
                        val tag = match.groupValues[1]
                        remoteVersion = tag
                        if (hasPlugin && localVersion != null) {
                            updateAvailable = isUpdateAvailable(localVersion, tag)
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore network errors
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    val launcher = filePicker { uri ->
        val success = HandwritingLoader.importPlugin(ctx, uri)
        showDialog = false
        if (success) {
            FeedbackManager.message(ctx, R.string.load_handwriting_plugin_success)
            onSuccess?.invoke()
        } else {
            FeedbackManager.message(ctx, R.string.load_handwriting_plugin_failed)
        }
    }

    fun startDownload() {
        isDownloading = true
        scope.launch(Dispatchers.IO) {
            try {
                val tag = remoteVersion ?: "latest"
                val urlStr = if (tag == "latest") {
                    "https://github.com/LeanBitLab/Leantype-Handwriting-Plugin/releases/latest/download/handwriting_plugin.apk"
                } else {
                    "https://github.com/LeanBitLab/Leantype-Handwriting-Plugin/releases/download/$tag/handwriting_plugin.apk"
                }
                var url = URL(urlStr)
                var conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "HeliboardL")
                conn.connect()

                var redirectConn = conn
                var status = redirectConn.responseCode
                var redirectCount = 0
                while ((status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) && redirectCount < 5) {
                    val newUrl = redirectConn.getHeaderField("Location")
                    redirectConn.disconnect()
                    val nextUrl = URL(newUrl)
                    redirectConn = nextUrl.openConnection() as HttpURLConnection
                    redirectConn.setRequestProperty("User-Agent", "HeliboardL")
                    redirectConn.connect()
                    status = redirectConn.responseCode
                    redirectCount++
                }

                if (status != HttpURLConnection.HTTP_OK) {
                    throw IOException("Server returned HTTP $status")
                }

                val tempFile = File(ctx.cacheDir, "temp_handwriting_plugin.apk")
                redirectConn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                redirectConn.disconnect()

                val success = HandwritingLoader.importPlugin(ctx, Uri.fromFile(tempFile))
                tempFile.delete()

                withContext(Dispatchers.Main) {
                    isDownloading = false
                    if (success) {
                        FeedbackManager.message(ctx, R.string.load_handwriting_plugin_success)
                        onSuccess?.invoke()
                        showDialog = false
                    } else {
                        FeedbackManager.message(ctx, R.string.load_handwriting_plugin_failed)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    Toast.makeText(ctx, "Download failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Preference(
        name = title,
        description = summary,
        icon = icon,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        ConfirmationDialog(
            onDismissRequest = { if (!isDownloading) showDialog = false },
            onConfirmed = {
                if (!isDownloading) {
                    if (hasPlugin && !updateAvailable) {
                        HandwritingLoader.removePlugin(ctx)
                        FeedbackManager.message(ctx, "Handwriting plugin removed")
                        onSuccess?.invoke()
                        showDialog = false
                    } else {
                        startDownload()
                    }
                }
            },
            confirmButtonText = when {
                isDownloading -> "Downloading..."
                hasPlugin && !updateAvailable -> stringResource(R.string.load_handwriting_plugin_button_delete)
                hasPlugin && updateAvailable -> "Update"
                else -> "Download"
            },
            title = { Text(stringResource(R.string.load_handwriting_plugin)) },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val message = when {
                        hasPlugin && updateAvailable -> "An update is available for the handwriting plugin!\nLocal version: $localVersion\nLatest version: $remoteVersion\n\nDo you want to download and update?"
                        hasPlugin -> "Handwriting plugin is active (version $localVersion).\n\nWarning: loading external code can be a security risk. Only use a plugin from a source you trust."
                        remoteVersion != null -> "Download the latest handwriting plugin (version $remoteVersion) from GitHub, or load an APK from local storage.\n\nWarning: loading external code can be a security risk. Only use a plugin from a source you trust."
                        else -> "Download the handwriting plugin from GitHub, or load an APK from local storage.\n\nWarning: loading external code can be a security risk. Only use a plugin from a source you trust."
                    }
                    Text(message)
                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }
                }
            },
            neutralButtonText = when {
                isDownloading -> null
                hasPlugin && updateAvailable -> "Delete"
                hasPlugin -> null
                else -> "Load from file"
            },
            onNeutral = {
                if (hasPlugin) {
                    HandwritingLoader.removePlugin(ctx)
                    FeedbackManager.message(ctx, "Handwriting plugin removed")
                    onSuccess?.invoke()
                    showDialog = false
                } else {
                    showDialog = false
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*")
                    try {
                        launcher.launch(intent)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        )
    }
}

private fun isUpdateAvailable(local: String, remote: String): Boolean {
    val cleanLocal = local.removePrefix("v").trim()
    val cleanRemote = remote.removePrefix("v").trim()
    if (cleanLocal == cleanRemote) return false

    val localParts = cleanLocal.split(".").mapNotNull { it.toIntOrNull() }
    val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }

    val maxLength = maxOf(localParts.size, remoteParts.size)
    for (i in 0 until maxLength) {
        val localPart = localParts.getOrElse(i) { 0 }
        val remotePart = remoteParts.getOrElse(i) { 0 }
        if (remotePart > localPart) return true
        if (localPart > remotePart) return false
    }
    return false
}
