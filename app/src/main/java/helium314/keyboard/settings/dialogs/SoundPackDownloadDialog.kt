// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.sound.CustomSoundManager
import helium314.keyboard.latin.sound.SoundPackImporter
import helium314.keyboard.latin.sound.SoundPackInfo
import helium314.keyboard.latin.sound.SoundPackUrls
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SoundPackDownloadDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.prefs() }
    val isOffline = BuildConfig.FLAVOR.contains("offline", ignoreCase = true)

    var currentSelectedStyle by remember {
        mutableStateOf(prefs.getString(Settings.PREF_KEYPRESS_SOUND_STYLE, Defaults.PREF_KEYPRESS_SOUND_STYLE) ?: Defaults.PREF_KEYPRESS_SOUND_STYLE)
    }

    val installedMap = remember { mutableStateMapOf<String, Boolean>() }
    val downloadingMap = remember { mutableStateMapOf<String, Boolean>() }
    var customPacks by remember { mutableStateOf<List<SoundPackInfo>>(emptyList()) }

    fun refreshInstalledStatus() {
        SoundPackUrls.PRESET_PACKS.forEach { preset ->
            installedMap[preset.id] = SoundPackImporter.isPackInstalled(context, preset.id)
        }
        customPacks = SoundPackImporter.getInstalledCustomPacks(context)
        customPacks.forEach { pack ->
            installedMap[pack.id] = true
        }
    }

    remember {
        refreshInstalledStatus()
        true
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val importedId = SoundPackImporter.importFromUri(context, uri)
                withContext(Dispatchers.Main) {
                    if (importedId != null) {
                        refreshInstalledStatus()
                        currentSelectedStyle = importedId
                        prefs.edit().putString(Settings.PREF_KEYPRESS_SOUND_STYLE, importedId).apply()
                        CustomSoundManager.getInstance(context).setSoundPack(importedId)
                        CustomSoundManager.getInstance(context).previewSound(importedId)
                        Toast.makeText(context, "Sound pack imported successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to import sound pack (must contain .ogg, .wav, or .mp3)", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun selectPack(packId: String) {
        currentSelectedStyle = packId
        prefs.edit().putString(Settings.PREF_KEYPRESS_SOUND_STYLE, packId).apply()
        CustomSoundManager.getInstance(context).setSoundPack(packId)
        CustomSoundManager.getInstance(context).previewSound(packId)
    }

    PreferenceDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.prefs_keypress_sound_style_dialog_title),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isOffline) "Download presets in browser or import" else "Select or download sound style",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Button(
                        onClick = { importLauncher.launch("*/*") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(stringResource(R.string.sound_pack_import_button), style = MaterialTheme.typography.labelMedium)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // System Default Item
                    item(key = SoundPackUrls.SYSTEM_DEFAULT_ID) {
                        val isSelected = currentSelectedStyle == SoundPackUrls.SYSTEM_DEFAULT_ID
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectPack(SoundPackUrls.SYSTEM_DEFAULT_ID) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectPack(SoundPackUrls.SYSTEM_DEFAULT_ID) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.prefs_keypress_sound_style_system),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = "Default system keypress click sound",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { CustomSoundManager.getInstance(context).previewSound(SoundPackUrls.SYSTEM_DEFAULT_ID) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_play_arrow),
                                        contentDescription = "Preview",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Online Presets Header
                    item {
                        Text(
                            text = "Sound Presets",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
                        )
                    }

                    // Presets List
                    items(SoundPackUrls.PRESET_PACKS, key = { it.id }) { preset ->
                        val isInstalled = installedMap[preset.id] == true
                        val isDownloading = downloadingMap[preset.id] == true
                        val isSelected = currentSelectedStyle == preset.id

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(enabled = isInstalled) { selectPack(preset.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    enabled = isInstalled,
                                    onClick = { selectPack(preset.id) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = preset.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = preset.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isDownloading) {
                                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                } else if (isInstalled) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { CustomSoundManager.getInstance(context).previewSound(preset.id) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_play_arrow),
                                                contentDescription = "Preview",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                SoundPackImporter.deletePack(context, preset.id)
                                                installedMap[preset.id] = false
                                                if (currentSelectedStyle == preset.id) {
                                                    selectPack(SoundPackUrls.SYSTEM_DEFAULT_ID)
                                                }
                                                Toast.makeText(context, "Deleted ${preset.displayName}", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                            ),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Delete", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (isOffline) {
                                                val url = preset.downloadUrl ?: SoundPackUrls.GITHUB_REPO_URL
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                try {
                                                    context.startActivity(intent)
                                                    Toast.makeText(context, "Downloading in browser… use 'Import' once finished", Toast.LENGTH_LONG).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Failed to open browser: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                downloadingMap[preset.id] = true
                                                scope.launch(Dispatchers.IO) {
                                                    val ok = SoundPackImporter.downloadPreset(context, preset.id)
                                                    withContext(Dispatchers.Main) {
                                                        downloadingMap[preset.id] = false
                                                        if (ok) {
                                                            installedMap[preset.id] = true
                                                            selectPack(preset.id)
                                                            Toast.makeText(context, "Downloaded and set ${preset.displayName}", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, "Failed to download sound pack", Toast.LENGTH_SHORT).show()
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

                    // Custom Packs Section (if any)
                    if (customPacks.isNotEmpty()) {
                        item {
                            Text(
                                text = "Custom Sound Packs",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
                            )
                        }

                        items(customPacks, key = { it.id }) { pack ->
                            val isSelected = currentSelectedStyle == pack.id

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectPack(pack.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectPack(pack.id) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = pack.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            text = pack.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { CustomSoundManager.getInstance(context).previewSound(pack.id) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_play_arrow),
                                                contentDescription = "Preview",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                SoundPackImporter.deletePack(context, pack.id)
                                                refreshInstalledStatus()
                                                if (currentSelectedStyle == pack.id) {
                                                    selectPack(SoundPackUrls.SYSTEM_DEFAULT_ID)
                                                }
                                                Toast.makeText(context, "Deleted ${pack.displayName}", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                            ),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Delete", style = MaterialTheme.typography.labelSmall)
                                        }
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
