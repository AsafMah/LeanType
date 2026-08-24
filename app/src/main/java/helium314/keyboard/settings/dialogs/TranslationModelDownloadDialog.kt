// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.translation.ITranslationProvider
import helium314.keyboard.latin.translation.TranslationModelImporter
import helium314.keyboard.latin.translation.TranslationModelUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class TranslationLanguageItem(
    val code: String,
    val displayName: String
)

@Composable
fun TranslationModelDownloadDialog(
    provider: ITranslationProvider,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    
    val downloadedMap = remember { mutableStateMapOf<String, Boolean>() }
    var allLanguages by remember { mutableStateOf<List<TranslationLanguageItem>>(emptyList()) }
    var isLoadingList by remember { mutableStateOf(true) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val importedModel = TranslationModelImporter.importFromUri(context, uri)
                withContext(Dispatchers.Main) {
                    if (importedModel != null) {
                        allLanguages.forEach { item ->
                            val mName = TranslationModelUrls.getModelName(item.code)
                            if (mName == importedModel || item.code == importedModel) {
                                downloadedMap[item.code] = true
                            }
                        }
                        Toast.makeText(context, "Model $importedModel imported successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to import translation model .zip", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val codes = try {
                provider.getSupportedLanguages()
            } catch (_: Throwable) {
                emptyList()
            }.ifEmpty {
                // Fallback standard ML Kit 59 language tags
                listOf(
                    "af", "sq", "ar", "be", "bg", "bn", "ca", "zh", "hr", "cs", "da", "nl",
                    "en", "eo", "et", "fi", "fr", "gl", "ka", "de", "el", "gu", "ht", "he",
                    "hi", "hu", "is", "id", "ga", "it", "ja", "kn", "ko", "lv", "lt", "mk",
                    "ms", "mt", "mr", "no", "fa", "pl", "pt", "ro", "ru", "sk", "sl", "es",
                    "sw", "sv", "tl", "ta", "te", "th", "tr", "uk", "ur", "vi", "cy"
                )
            }
            val sysLocale = context.resources.configuration.locales[0] ?: Locale.getDefault()
            val list = codes.map { code ->
                val locale = Locale.forLanguageTag(code)
                val name = locale.getDisplayName(sysLocale).ifBlank {
                    locale.getDisplayName(Locale.ENGLISH).ifBlank { code }
                }.replaceFirstChar { it.uppercase(sysLocale) }
                TranslationLanguageItem(code, "$name ($code)")
            }.sortedBy { it.displayName }

            withContext(Dispatchers.Main) {
                allLanguages = list
                isLoadingList = false
            }

            // Check download status for all languages
            codes.forEach { code ->
                if (code == "en") {
                    withContext(Dispatchers.Main) { downloadedMap[code] = true }
                } else {
                    val downloaded = try {
                        provider.isModelDownloaded(code)
                    } catch (_: Throwable) {
                        false
                    }
                    withContext(Dispatchers.Main) { downloadedMap[code] = downloaded }
                }
            }
        }
    }

    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = {},
        confirmButtonText = null,
        cancelButtonText = null,
        title = { Text(stringResource(R.string.offline_translation_models_title)) },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Download model in browser, then import .zip",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Button(
                        onClick = { importLauncher.launch("application/zip") },
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Import .zip", style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search language…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                if (isLoadingList) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val filtered = remember(searchQuery, allLanguages) {
                        if (searchQuery.isBlank()) allLanguages
                        else allLanguages.filter {
                            it.displayName.contains(searchQuery, ignoreCase = true) ||
                                it.code.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filtered, key = { it.code }) { item ->
                            val isDownloaded = downloadedMap[item.code] == true
                            val isEnglish = item.code == "en"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = item.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isDownloaded) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = if (isEnglish) "Built-in" else if (isDownloaded) "Downloaded (Offline ready)" else "Not downloaded",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDownloaded)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isEnglish) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                } else if (isDownloaded) {
                                    Button(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                val deleted = try {
                                                    provider.deleteModel(item.code)
                                                } catch (_: Throwable) {
                                                    false
                                                }
                                                withContext(Dispatchers.Main) {
                                                    if (deleted) {
                                                        downloadedMap[item.code] = false
                                                        Toast.makeText(context, "${item.displayName} model removed", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Failed to remove model", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        ),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Delete", style = MaterialTheme.typography.labelMedium)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            val url = TranslationModelUrls.getDownloadUrl(item.code)
                                            if (url != null) {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                                Toast.makeText(context, "Downloading in browser… import .zip once finished", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Download URL not available", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Download", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        scrollContent = false
    )
}
