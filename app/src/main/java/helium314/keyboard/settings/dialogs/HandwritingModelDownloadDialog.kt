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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.handwriting.HandwritingLoader
import helium314.keyboard.latin.handwriting.ModelDownloadListener
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class HandwritingLanguageItem(
    val code: String,
    val displayName: String,
    val isEnabledSubtype: Boolean = false
)

@Composable
fun HandwritingModelDownloadDialog(
    onDismissRequest: () -> Unit,
    onModelChanged: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }

    val downloadedMap = remember { mutableStateMapOf<String, Boolean>() }
    val downloadingMap = remember { mutableStateMapOf<String, Boolean>() }
    val progressMap = remember { mutableStateMapOf<String, Float>() }
    var allLanguages by remember { mutableStateOf<List<HandwritingLanguageItem>>(emptyList()) }
    var isLoadingList by remember { mutableStateOf(true) }

    val recognizer = remember { HandwritingLoader.getRecognizer(context) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(true).map { it.locale() }
            val sysLocale = context.resources.configuration.locales[0] ?: Locale.getDefault()

            val enabledItems = enabledSubtypes.map { loc ->
                val tag = loc.toLanguageTag()
                val name = loc.getDisplayName(sysLocale).ifBlank { loc.displayName }
                HandwritingLanguageItem(tag, "$name ($tag)", isEnabledSubtype = true)
            }

            val availableLocales = Locale.getAvailableLocales()
                .filter { !it.language.isNullOrEmpty() && it.toLanguageTag() != "und" }
                .distinctBy { it.toLanguageTag() }
                .sortedBy { it.getDisplayName(sysLocale).lowercase(sysLocale) }

            val otherItems = availableLocales.mapNotNull { loc ->
                val tag = loc.toLanguageTag()
                if (enabledSubtypes.any { it.toLanguageTag() == tag }) null
                else {
                    val name = loc.getDisplayName(sysLocale).ifBlank { loc.displayName }
                    HandwritingLanguageItem(tag, "$name ($tag)", isEnabledSubtype = false)
                }
            }

            val combined = (enabledItems + otherItems).distinctBy { it.code }

            withContext(Dispatchers.Main) {
                allLanguages = combined
                isLoadingList = false
            }

            // Check download status for all languages
            combined.forEach { item ->
                val ready = try {
                    recognizer?.isLanguageReady(item.code) == true
                } catch (_: Throwable) {
                    false
                }
                withContext(Dispatchers.Main) {
                    downloadedMap[item.code] = ready
                }
            }
        }
    }

    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = {},
        confirmButtonText = null,
        cancelButtonText = null,
        title = { Text("Handwriting Models") },
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
                            val isDownloaded = downloadedMap[item.code] == true
                            val isDownloading = downloadingMap[item.code] == true
                            val progress = progressMap[item.code] ?: 0f

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = item.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    val statusText = when {
                                        isDownloading -> "Downloading... ${(progress * 100).toInt()}%"
                                        isDownloaded -> if (item.isEnabledSubtype) "Downloaded (Enabled Layout)" else "Downloaded (~20 MB)"
                                        else -> if (item.isEnabledSubtype) "Available for layout (~20 MB)" else "Available (~20 MB)"
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDownloaded) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp).padding(end = 4.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                } else if (isDownloaded) {
                                    Button(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                val removed = recognizer?.removeModel(item.code) == true
                                                withContext(Dispatchers.Main) {
                                                    if (removed) {
                                                        downloadedMap[item.code] = false
                                                        Toast.makeText(context, "Handwriting model deleted", Toast.LENGTH_SHORT).show()
                                                        onModelChanged?.invoke()
                                                    } else {
                                                        Toast.makeText(context, "Failed to delete handwriting model", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Delete", style = MaterialTheme.typography.labelMedium)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            if (recognizer == null) {
                                                Toast.makeText(context, "Handwriting plugin not loaded", Toast.LENGTH_SHORT).show()
                                                return@OutlinedButton
                                            }
                                            downloadingMap[item.code] = true
                                            progressMap[item.code] = 0f
                                            recognizer.downloadModel(item.code, object : ModelDownloadListener {
                                                override fun onProgress(progress: Float) {
                                                    scope.launch(Dispatchers.Main) {
                                                        progressMap[item.code] = progress
                                                    }
                                                }

                                                override fun onComplete(success: Boolean) {
                                                    scope.launch(Dispatchers.Main) {
                                                        downloadingMap[item.code] = false
                                                        if (success) {
                                                            downloadedMap[item.code] = true
                                                            Toast.makeText(context, "Handwriting model downloaded", Toast.LENGTH_SHORT).show()
                                                            onModelChanged?.invoke()
                                                        } else {
                                                            Toast.makeText(context, "Failed to download handwriting model", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            })
                                        },
                                        modifier = Modifier.height(32.dp)
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
