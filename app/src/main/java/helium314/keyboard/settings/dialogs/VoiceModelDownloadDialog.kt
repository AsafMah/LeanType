// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leanbitlab.leantype.voice.ModelState
import com.leanbitlab.leantype.voice.VoiceConstants
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.voice.VoiceDownloadDispatcher
import helium314.keyboard.latin.voice.VoiceModelItem
import helium314.keyboard.latin.voice.VoiceModelRegistry
import helium314.keyboard.latin.voice.VoicePluginManager
import helium314.keyboard.settings.DeleteButton

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

    val enabledLanguages = remember {
        val list = SubtypeSettings.getEnabledSubtypes(true).map { it.locale().language.lowercase() }.toSet()
        if (list.isEmpty()) setOf("en") else list
    }

    var showAllLanguages by remember { mutableStateOf(false) }

    val activeVoskModels = remember(enabledLanguages, installedVoskId) {
        VoiceModelRegistry.voskModels.filter {
            it.languageCode in enabledLanguages || installedVoskId == it.id
        }.ifEmpty {
            VoiceModelRegistry.voskModels.filter { it.languageCode == "en" }
        }
    }

    val otherVoskModels = remember(activeVoskModels) {
        VoiceModelRegistry.voskModels.filterNot { it in activeVoskModels }
    }

    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = {},
        confirmButtonText = null,
        cancelButtonText = null,
        scrollContent = true,
        title = { Text("Speech Models") },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Section: Whisper Models
                Text(
                    text = "Whisper Models (Accurate & Hybrid)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                val isWhisperInstalled = whisperState?.state == ModelState.STATE_READY

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
                            Toast.makeText(context, "Whisper model deleted", Toast.LENGTH_SHORT).show()
                            onRefresh()
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Section: Vosk Models
                Text(
                    text = "Vosk Models (Fast Streaming)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                val isVoskInstalled = voskState?.state == ModelState.STATE_READY

                for (model in activeVoskModels) {
                    val isThisModelInstalled = isVoskInstalled && installedVoskId == model.id

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

                if (showAllLanguages) {
                    for (model in otherVoskModels) {
                        val isThisModelInstalled = isVoskInstalled && installedVoskId == model.id

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
                }

                if (otherVoskModels.isNotEmpty()) {
                    TextButton(
                        onClick = { showAllLanguages = !showAllLanguages },
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(if (showAllLanguages) "Hide other languages" else "Browse other languages (${otherVoskModels.size})")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Section: Custom / Offline Models
                Text(
                    text = "Custom / Offline Models",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Custom Whisper Row
                val isCustomWhisperInstalled = isWhisperInstalled && installedWhisperId == "custom"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Custom Whisper Model",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Import GGML .bin model file from device storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isCustomWhisperInstalled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "✓ Active",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            DeleteButton(
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            ) {
                                prefs.edit().remove("installed_model_${VoiceConstants.ENGINE_WHISPER}").apply()
                                pluginManager.deleteModel(VoiceConstants.ENGINE_WHISPER)
                                Toast.makeText(context, "Whisper model deleted", Toast.LENGTH_SHORT).show()
                                onRefresh()
                            }
                        }
                    } else {
                        TextButton(
                            onClick = { onImportLocalFile(VoiceConstants.ENGINE_WHISPER) }
                        ) {
                            Text("Import")
                        }
                    }
                }

                // Custom Vosk Row
                val isCustomVoskInstalled = isVoskInstalled && installedVoskId == "custom"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Custom Vosk Model",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Import Vosk .zip archive from device storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isCustomVoskInstalled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "✓ Active",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            DeleteButton(
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            ) {
                                prefs.edit().remove("installed_model_${VoiceConstants.ENGINE_VOSK}").apply()
                                pluginManager.deleteModel(VoiceConstants.ENGINE_VOSK)
                                Toast.makeText(context, "Vosk model deleted", Toast.LENGTH_SHORT).show()
                                onRefresh()
                            }
                        }
                    } else {
                        TextButton(
                            onClick = { onImportLocalFile(VoiceConstants.ENGINE_VOSK) }
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
    isAnyModelInstalledForEngine: Boolean,
    isNetworkAvailable: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
            if (model.description.isNotEmpty()) {
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        if (isThisModelInstalled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "✓ Installed",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 4.dp)
                )
                DeleteButton(
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                ) {
                    onDelete()
                }
            }
        } else {
            TextButton(
                onClick = onDownload
            ) {
                val label = if (isAnyModelInstalledForEngine) {
                    if (isNetworkAvailable) "Replace" else "Browser"
                } else {
                    if (isNetworkAvailable) "Download" else "Browser"
                }
                Text(label)
            }
        }
    }
}
