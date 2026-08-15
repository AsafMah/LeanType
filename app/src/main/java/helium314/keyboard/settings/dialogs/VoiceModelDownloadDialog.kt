// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    voskState: ModelState?,
    whisperState: ModelState?,
    onRefresh: () -> Unit,
    onImportLocalFile: (String) -> Unit
) {
    val context = LocalContext.current
    val isNetworkAvailable = remember(context) { VoiceDownloadDispatcher.hasInternetPermission(context) }
    val prefs = context.prefs()

    val installedWhisperId = prefs.getString("installed_model_${VoiceConstants.ENGINE_WHISPER}", null)
    val installedVoskId = prefs.getString("installed_model_${VoiceConstants.ENGINE_VOSK}", null)

    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = {},
        confirmButtonText = null,
        cancelButtonText = "Close",
        title = { Text("Speech Recognition Models") },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Section: Whisper Models
                Text(
                    text = "Whisper Models (Accurate & Hybrid)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                val isWhisperInstalled = whisperState?.state == ModelState.STATE_READY

                for (model in VoiceModelRegistry.whisperModels) {
                    val isThisModelInstalled = isWhisperInstalled && (
                        installedWhisperId == model.id || (installedWhisperId == null && model.isRecommended)
                    )

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
                            Toast.makeText(context, "Whisper model deleted", Toast.LENGTH_SHORT).show()
                            onRefresh()
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Section: Vosk Models
                Text(
                    text = "Vosk Models (Fast Streaming)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                val isVoskInstalled = voskState?.state == ModelState.STATE_READY

                for (model in VoiceModelRegistry.voskModels) {
                    val isThisModelInstalled = isVoskInstalled && (
                        installedVoskId == model.id || (installedVoskId == null && model.isRecommended)
                    )

                    ModelDownloadRow(
                        model = model,
                        isThisModelInstalled = isThisModelInstalled,
                        isAnyModelInstalledForEngine = isVoskInstalled,
                        isNetworkAvailable = isNetworkAvailable,
                        onDownload = {
                            VoiceDownloadDispatcher.download(context, model)
                        },
                        onDelete = {
                            prefs.edit().remove("installed_model_${VoiceConstants.ENGINE_VOSK}").apply()
                            pluginManager.deleteModel(VoiceConstants.ENGINE_VOSK)
                            Toast.makeText(context, "Vosk model deleted", Toast.LENGTH_SHORT).show()
                            onRefresh()
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Local manual SAF import section
                Text(
                    text = "Custom / Offline Models",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Import custom GGML .bin or Vosk .zip archive directly from your device storage:",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onImportLocalFile(VoiceConstants.ENGINE_WHISPER) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import Whisper", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { onImportLocalFile(VoiceConstants.ENGINE_VOSK) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import Vosk", style = MaterialTheme.typography.labelMedium)
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "${model.language} • ${model.sizeMb}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isThisModelInstalled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "Installed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    Button(
                        onClick = onDownload
                    ) {
                        val label = if (isAnyModelInstalledForEngine) {
                            if (isNetworkAvailable) "Replace" else "Browser"
                        } else {
                            if (isNetworkAvailable) "Download" else "Browser"
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            if (model.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}
