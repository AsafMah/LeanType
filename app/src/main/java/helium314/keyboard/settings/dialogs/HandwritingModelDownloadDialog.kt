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
    var allLanguages by remember { mutableStateOf<List<HandwritingLanguageItem>>(emptyList()) }
    var isLoadingList by remember { mutableStateOf(true) }
    var targetImportLang by remember { mutableStateOf<String?>(null) }

    val recognizer = remember { HandwritingLoader.getRecognizer(context) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val lang = targetImportLang
        if (uri != null && lang != null) {
            scope.launch(Dispatchers.IO) {
                val success = HandwritingModelImporter.importForLanguage(context, lang, uri)
                withContext(Dispatchers.Main) {
                    if (success) {
                        downloadedMap[lang] = true
                        Toast.makeText(context, "Handwriting model imported for $lang", Toast.LENGTH_SHORT).show()
                        onModelChanged?.invoke()
                    } else {
                        Toast.makeText(context, "Failed to import handwriting model", Toast.LENGTH_SHORT).show()
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

            if (recognizer != null) {
                combined.forEach { item ->
                    val isReady = recognizer.isLanguageReady(item.code)
                    withContext(Dispatchers.Main) {
                        downloadedMap[item.code] = isReady
                    }
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
                    .height(440.dp)
            ) {
                Text(
                    text = "Download model in browser, then tap Import",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

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
                                        fontWeight = if (isDownloaded) FontWeight.Bold else FontWeight.Normal
                                    )
                                    val statusText = when {
                                        isDownloaded -> if (item.isEnabledSubtype) "Downloaded (Enabled Layout)" else "Downloaded (Offline ready)"
                                        else -> if (item.isEnabledSubtype) "Available for layout" else "Available"
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDownloaded) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isDownloaded) {
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
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Delete", style = MaterialTheme.typography.labelMedium)
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                targetImportLang = item.code
                                                importLauncher.launch("*/*")
                                            },
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("Import", style = MaterialTheme.typography.labelMedium)
                                        }

                                        Button(
                                            onClick = {
                                                val url = HandwritingModelUrls.getDownloadUrl(item.code)
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                                Toast.makeText(context, "Downloading in browser… tap Import once finished", Toast.LENGTH_LONG).show()
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
            }
        },
        scrollContent = false
    )
}
