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
import helium314.keyboard.latin.handwriting.HandwritingModelPackData
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
                if (importedTags.isNotEmpty()) {
                    val installedMap = HandwritingModelImporter.getInstalledLanguageStatuses(context)
                    withContext(Dispatchers.Main) {
                        statusMap.clear()
                        downloadedMap.clear()
                        installedMap.forEach { (tag, status) ->
                            statusMap[tag] = status
                            downloadedMap[tag] = status.isReady
                        }
                        Toast.makeText(context, "Imported models for: ${importedTags.joinToString(", ")}", Toast.LENGTH_SHORT).show()
                        onModelChanged?.invoke()
                    }
                } else {
                    withContext(Dispatchers.Main) {
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

            val supportedCodes = HandwritingModelPackData.LANGUAGE_PACKS.keys.toList()

            val items = supportedCodes.map { tag ->
                val loc = Locale.forLanguageTag(tag)
                val rawName = loc.getDisplayName(sysLocale).ifBlank { loc.displayName }
                val displayName = if (rawName.isNotBlank()) "$rawName ($tag)" else tag
                val isEnabled = enabledSubtypes.any { subLoc ->
                    val subTag = subLoc.toLanguageTag()
                    subTag.equals(tag, ignoreCase = true) || (tag.equals(subLoc.language, ignoreCase = true))
                }
                HandwritingLanguageItem(tag, displayName, isEnabledSubtype = isEnabled)
            }.sortedWith(
                compareByDescending<HandwritingLanguageItem> { it.isEnabledSubtype }
                    .thenBy { it.displayName }
            )

            // Ultra-fast scan of only installed model directories on disk
            val installedMap = HandwritingModelImporter.getInstalledLanguageStatuses(context)

            withContext(Dispatchers.Main) {
                allLanguages = items
                statusMap.clear()
                downloadedMap.clear()
                installedMap.forEach { (tag, status) ->
                    statusMap[tag] = status
                    downloadedMap[tag] = status.isReady
                }
                isLoadingList = false
            }
        }
    }

    var offlineDownloadItem by remember { mutableStateOf<HandwritingLanguageItem?>(null) }

    if (offlineDownloadItem != null) {
        val currentItem = offlineDownloadItem!!
        val urls = HandwritingModelUrls.getDownloadUrls(currentItem.code)
        PreferenceDialog(
            onDismissRequest = { offlineDownloadItem = null },
            title = "Download ${currentItem.displayName}",
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "This model requires ${urls.size} files. Download each file in your browser, then tap 'Import Files' at the top and multi-select all downloaded files together.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    urls.forEachIndexed { idx, url ->
                        val filename = url.substringAfterLast('/')
                        val fileTypeLabel = when {
                            filename.contains("recospec") -> "1. Config (Recospec)"
                            filename.contains("compact.fst") || filename.contains("fst") -> "3. Dictionary (FST)"
                            filename.contains("tflite") || filename.contains("model") || filename.contains("lstm") -> "2. Neural Model (TFLite)"
                            else -> "File ${idx + 1}"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = fileTypeLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
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
        )
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
                    val filtered = remember(searchQuery, allLanguages, statusMap.size, downloadedMap.size) {
                        val baseList = if (searchQuery.isBlank()) allLanguages
                        else allLanguages.filter {
                            it.displayName.contains(searchQuery, ignoreCase = true) ||
                                it.code.contains(searchQuery, ignoreCase = true)
                        }
                        baseList.sortedWith(
                            compareByDescending<HandwritingLanguageItem> {
                                val st = statusMap[it.code]
                                if (st?.isComplete == true) 3
                                else if (st?.isReady == true || downloadedMap[it.code] == true) 2
                                else if (it.isEnabledSubtype) 1
                                else 0
                            }.thenBy { it.displayName }
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filtered, key = { it.code }) { item ->
                            val status = statusMap[item.code] ?: HandwritingModelImporter.ModelComponentsStatus(hasRecospec = false, hasModel = false, hasFst = false)
                            val isDownloaded = status.isReady || downloadedMap[item.code] == true
                            val isDownloading = downloadingMap[item.code] == true

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = item.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val statusText = when {
                                        isDownloading -> "Downloading..."
                                        status.isComplete -> "Ready (Full: Model + FST)"
                                        status.isReady -> "Ready"
                                        status.hasModel && !status.hasFst -> "Missing dictionary (FST)"
                                        status.hasFst && !status.hasModel -> "Missing neural model"
                                        else -> "Not downloaded"
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                }

                                if (isDownloading) {
                                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                } else if (isDownloaded) {
                                    Button(
                                        onClick = {
                                            val code = item.code
                                            scope.launch(Dispatchers.IO) {
                                                HandwritingModelImporter.deleteModelForLanguage(context, code)
                                                recognizer?.removeModel(code)
                                                val newStatus = HandwritingModelImporter.getComponentsStatus(context, code)
                                                val isReady = newStatus.isReady || try { recognizer?.isLanguageReady(code) == true } catch (_: Throwable) { false }
                                                withContext(Dispatchers.Main) {
                                                    statusMap[code] = newStatus
                                                    downloadedMap[code] = isReady
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
                                                offlineDownloadItem = item
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
