// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    val isNetworkAvailable = remember(context) { VoiceDownloadDispatcher.hasInternetPermission(context) }
    val prefs = context.prefs()

    val installedWhisperId = prefs.getString("installed_model_${VoiceConstants.ENGINE_WHISPER}", null)

    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = {},
        confirmButtonText = null,
        cancelButtonText = null,
        scrollContent = true,
        title = { Text("Whisper Models") },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                val isWhisperInstalled = whisperState?.state == ModelState.STATE_READY
                val matchedPredefinedModel = VoiceModelRegistry.whisperModels.any { it.id == installedWhisperId }

                for (model in VoiceModelRegistry.whisperModels) {
                    val isThisModelInstalled = isWhisperInstalled && installedWhisperId == model.id

                    ModelDownloadRow(
                        model = model,
                        isThisModelInstalled = isThisModelInstalled,
                        isAnyModelInstalledForEngine = isWhisperInstalled,
                        isNetworkAvailable = isNetworkAvailable,
                        onDownload = {
                            VoiceDownloadDispatcher.download(context, model)
                        },
                        onDelete = {
                            prefs.edit().remove("installed_model_${VoiceConstants.ENGINE_WHISPER}").apply()
                            pluginManager.deleteModel(VoiceConstants.ENGINE_WHISPER)
                            Toast.makeText(context, "Model removed", Toast.LENGTH_SHORT).show()
                            onRefresh()
                        }
                    )
                }

                // Section: Custom Model File
                val isCustomWhisperInstalled = isWhisperInstalled && (installedWhisperId == "custom" || !matchedPredefinedModel)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCustomWhisperInstalled)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isCustomWhisperInstalled && installedWhisperId == null)
                                "Loaded Model (Local / External)"
                            else
                                "Custom GGML / GGUF",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCustomWhisperInstalled) {
                            Button(
                                onClick = {
                                    prefs.edit().remove("installed_model_${VoiceConstants.ENGINE_WHISPER}").apply()
                                    pluginManager.deleteModel(VoiceConstants.ENGINE_WHISPER)
                                    Toast.makeText(context, "Model removed", Toast.LENGTH_SHORT).show()
                                    onRefresh()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Remove")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onImportLocalFile(VoiceConstants.ENGINE_WHISPER) },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Import")
                            }
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
    isAnyModelInstalledForEngine: Boolean,
    isNetworkAvailable: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isThisModelInstalled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${model.language} • ${model.sizeMb}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isThisModelInstalled) {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Remove")
                }
            } else {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.height(36.dp)
                ) {
                    val label = if (isAnyModelInstalledForEngine) {
                        "Replace"
                    } else {
                        "Download"
                    }
                    Text(label)
                }
            }
        }
    }
}
