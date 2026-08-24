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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.handwriting.HandwritingLoader
import helium314.keyboard.latin.handwriting.HandwritingModelImporter
import helium314.keyboard.latin.handwriting.HandwritingModelUrls
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import helium314.keyboard.latin.BuildConfig

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

    val isOffline = remember { BuildConfig.FLAVOR.contains("offline", ignoreCase = true) }
    val downloadedMap = remember { mutableStateMapOf<String, Boolean>() }
    val downloadingMap = remember { mutableStateMapOf<String, Boolean>() }
    val statusMap = remember { mutableStateMapOf<String, HandwritingModelImporter.ModelComponentsStatus>() }
    var allLanguages by remember { mutableStateOf<List<HandwritingLanguageItem>>(emptyList()) }
    var isLoadingList by remember { mutableStateOf(true) }
    var targetImportLang by remember { mutableStateOf<String?>(null) }

    val recognizer = remember { HandwritingLoader.getRecognizer(context) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            scope.launch(Dispatchers.IO) {
                val importedTags = HandwritingModelImporter.importAutoDetectedUris(context, uris)
                withContext(Dispatchers.Main) {
                    if (importedTags.isNotEmpty()) {
                        for (tag in importedTags) {
                            val newStatus = HandwritingModelImporter.getComponentsStatus(context, tag)
                            statusMap[tag] = newStatus
                            downloadedMap[tag] = newStatus.isReady
                        }
                        Toast.makeText(context, "Imported models for: ${importedTags.joinToString(", ")}", Toast.LENGTH_SHORT).show()
                        onModelChanged?.invoke()
                    } else {
                        Toast.makeText(context, "Failed to import model files", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

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

            combined.forEach { item ->
                val status = HandwritingModelImporter.getComponentsStatus(context, item.code)
                val isReady = status.isReady || (recognizer?.isLanguageReady(item.code) == true)
                withContext(Dispatchers.Main) {
                    statusMap[item.code] = status
                    downloadedMap[item.code] = isReady
                }
            }
        }
    }

    PreferenceDialog(
        onDismissRequest = onDismissRequest,
        title = "Handwriting Models",
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isOffline) "Download models in browser, then import" else "Download in app or import files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Button(
                        onClick = { importLauncher.launch("*/*") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Import Files", style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search language…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )

                if (isLoadingList) {
                    Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
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
                        modifier = Modifier.fillMaxWidth().height(360.dp)
                    ) {
                        items(filtered, key = { it.code }) { item ->
                            val status = statusMap[item.code] ?: HandwritingModelImporter.getComponentsStatus(context, item.code)
                            val isDownloaded = status.isReady || downloadedMap[item.code] == true
                            val isDownloading = downloadingMap[item.code] == true

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp, horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                    Text(
                                        text = item.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isDownloaded) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val statusText = when {
                                        status.isComplete -> "● Ready"
                                        status.isReady -> "▲ Missing dictionary"
                                        else -> if (item.isEnabledSubtype) "○ Layout enabled" else "○ Available"
                                    }
                                    val statusColor = when {
                                        status.isComplete -> MaterialTheme.colorScheme.primary
                                        status.isReady -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = statusColor
                                    )
                                }

                                if (isDownloading) {
                                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                } else if (isDownloaded) {
                                    Button(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                HandwritingModelImporter.deleteModelForLanguage(context, item.code)
                                                recognizer?.removeModel(item.code)
                                                val newStatus = HandwritingModelImporter.getComponentsStatus(context, item.code)
                                                withContext(Dispatchers.Main) {
                                                    statusMap[item.code] = newStatus
                                                    downloadedMap[item.code] = newStatus.isReady
                                                    Toast.makeText(context, "Model deleted", Toast.LENGTH_SHORT).show()
                                                    onModelChanged?.invoke()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Delete", style = MaterialTheme.typography.labelSmall)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (isOffline) {
                                                val urls = HandwritingModelUrls.getDownloadUrls(item.code)
                                                for (url in urls) {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    try {
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {}
                                                }
                                                Toast.makeText(context, "Downloading model files in browser…", Toast.LENGTH_SHORT).show()
                                            } else {
                                                downloadingMap[item.code] = true
                                                scope.launch(Dispatchers.IO) {
                                                    val ok = HandwritingModelImporter.downloadPacksForLanguage(context, item.code)
                                                    val newStatus = HandwritingModelImporter.getComponentsStatus(context, item.code)
                                                    withContext(Dispatchers.Main) {
                                                        downloadingMap[item.code] = false
                                                        statusMap[item.code] = newStatus
                                                        downloadedMap[item.code] = newStatus.isReady
                                                        if (ok && newStatus.isReady) {
                                                            Toast.makeText(context, "Downloaded ${item.displayName}", Toast.LENGTH_SHORT).show()
                                                            onModelChanged?.invoke()
                                                        } else {
                                                            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Download", style = MaterialTheme.typography.labelSmall)
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
