// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    val downloadingMap = remember { mutableStateMapOf<String, Boolean>() }
    var allLanguages by remember { mutableStateOf<List<TranslationLanguageItem>>(emptyList()) }
    var isLoadingList by remember { mutableStateOf(true) }

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
                    .height(420.dp)
            ) {
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
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(filtered, key = { it.code }) { item ->
                            val isEnglish = item.code == "en"
                            val isDownloaded = downloadedMap[item.code] == true
                            val isDownloading = downloadingMap[item.code] == true

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (isEnglish) "Built-in (Base)"
                                        else if (isDownloaded) "Downloaded (~30 MB)"
                                        else "Available (~30 MB)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDownloaded || isEnglish)
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
                                } else if (isDownloading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 8.dp), strokeWidth = 2.dp)
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
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("Delete")
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            downloadingMap[item.code] = true
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    provider.downloadModel(item.code) { success, errorMsg ->
                                                        scope.launch(Dispatchers.Main) {
                                                            downloadingMap[item.code] = false
                                                            if (success) {
                                                                downloadedMap[item.code] = true
                                                                Toast.makeText(context, "${item.displayName} model downloaded", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                val msg = if (!errorMsg.isNullOrBlank()) "Download failed: $errorMsg" else "Download failed for ${item.displayName}"
                                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    }
                                                } catch (e: Throwable) {
                                                    android.util.Log.e("TranslationDialog", "downloadModel invocation exception", e)
                                                    scope.launch(Dispatchers.Main) {
                                                        downloadingMap[item.code] = false
                                                        Toast.makeText(context, "Download failed: ${e.message ?: "error"}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("Download")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
