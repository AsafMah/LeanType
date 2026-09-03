// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leanbitlab.leantype.voice.ModelState
import com.leanbitlab.leantype.voice.VoiceConstants
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.voice.VoiceDownloadDispatcher
import helium314.keyboard.latin.voice.VoiceModelItem
import helium314.keyboard.latin.voice.VoiceModelRegistry
import helium314.keyboard.latin.voice.VoicePluginManager
import kotlinx.coroutines.launch

@Composable
fun VoiceModelDownloadDialog(
    onDismissRequest: () -> Unit,
    pluginManager: VoicePluginManager,
    voskState: ModelState? = null,
    whisperState: ModelState?,
    onRefresh: () -> Unit,
    onImportLocalFile: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.prefs()

    val installedWhisperId = prefs.getString("installed_model_${VoiceConstants.ENGINE_WHISPER}", null)
    val activeDownloadingId = VoiceDownloadDispatcher.downloadingModelId.value
    val currentProgress = VoiceDownloadDispatcher.downloadProgress.floatValue

    val isWhisperInstalled = whisperState?.state == ModelState.STATE_READY
    val matchedPredefinedModel = VoiceModelRegistry.whisperModels.any { it.id == installedWhisperId }

    ThreeButtonAlertDialog(
        onDismissRequest = {
            if (activeDownloadingId == null) {
                onDismissRequest()
            }
        },
        onConfirmed = {},
        confirmButtonText = null,
        cancelButtonText = null,
        scrollContent = true,
        title = { Text("Whisper Models") },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                for (model in VoiceModelRegistry.whisperModels) {
                    val isThisModelInstalled = isWhisperInstalled && installedWhisperId == model.id
                    val isThisModelDownloading = activeDownloadingId == model.id

                    ModelDownloadRow(
                        model = model,
                        isThisModelInstalled = isThisModelInstalled,
                        isThisModelDownloading = isThisModelDownloading,
                        isAnyModelDownloading = activeDownloadingId != null,
                        downloadProgress = currentProgress,
                        isAnyModelInstalledForEngine = isWhisperInstalled,
                        onDownload = {
                            scope.launch {
                                VoiceDownloadDispatcher.downloadAndInstall(
                                    context = context,
                                    model = model,
                                    pluginManager = pluginManager,
                                    onSuccess = { onRefresh() },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        },
                        onDelete = {
                            prefs.edit().remove("installed_model_${VoiceConstants.ENGINE_WHISPER}").apply()
                            pluginManager.deleteModel(VoiceConstants.ENGINE_WHISPER)
                            Toast.makeText(context, "${model.displayName} model removed", Toast.LENGTH_SHORT).show()
                            onRefresh()
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Custom Local Model Option
                val isCustomWhisperInstalled = isWhisperInstalled && (installedWhisperId == "custom" || !matchedPredefinedModel)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isCustomWhisperInstalled && installedWhisperId == null)
                                "Loaded External Model"
                            else
                                "Custom GGML Model",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isCustomWhisperInstalled) "Imported & Ready" else "Load external .bin file",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCustomWhisperInstalled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isCustomWhisperInstalled) {
                        Button(
                            onClick = {
                                prefs.edit().remove("installed_model_${VoiceConstants.ENGINE_WHISPER}").apply()
                                pluginManager.deleteModel(VoiceConstants.ENGINE_WHISPER)
                                Toast.makeText(context, "Custom model removed", Toast.LENGTH_SHORT).show()
                                onRefresh()
                            },
                            enabled = activeDownloadingId == null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Delete")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onImportLocalFile(VoiceConstants.ENGINE_WHISPER) },
                            enabled = activeDownloadingId == null,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Import")
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun ModelDownloadRow(
    model: VoiceModelItem,
    isThisModelInstalled: Boolean,
    isThisModelDownloading: Boolean,
    isAnyModelDownloading: Boolean,
    downloadProgress: Float,
    isAnyModelInstalledForEngine: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "${model.displayName} Model",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isThisModelDownloading) {
                        "Downloading... ${(downloadProgress * 100).toInt()}% (${model.sizeMb})"
                    } else if (isThisModelInstalled) {
                        "Downloaded (${model.sizeMb})"
                    } else {
                        "${model.language} • ${model.sizeMb}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isThisModelInstalled || isThisModelDownloading)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isThisModelDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 8.dp),
                    strokeWidth = 2.dp
                )
            } else if (isThisModelInstalled) {
                Button(
                    onClick = onDelete,
                    enabled = !isAnyModelDownloading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Delete")
                }
            } else {
                OutlinedButton(
                    onClick = onDownload,
                    enabled = !isAnyModelDownloading,
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(if (isAnyModelInstalledForEngine) "Replace" else "Download")
                }
            }
        }

        if (isThisModelDownloading) {
            LinearProgressIndicator(
                progress = { downloadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }
    }
}
