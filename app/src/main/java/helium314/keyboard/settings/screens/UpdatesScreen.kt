// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.core.net.toUri
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class ChangelogEntry(
    val version: String,
    val date: String,
    val items: List<String>
)

private val changelogEntries = listOf(
    ChangelogEntry(
        version = "v4.1.1",
        date = "August 2026",
        items = listOf(
            "✨ Offline Voice Input: Integrated Whisper speech recognition models (Base, Tiny, Small) with real-time waveform visualizer",
            "✨ Keep Whisper in memory option with background retention strategies",
            "🎨 Redesigned voice toolbar indicator, improved button contrast & dark/light theme tinting",
            "🎨 Cleaned Speech Models download dialog with single-row custom model import",
            "🌱 Added Open Collective support alongside GitHub Sponsors",
            "🐛 Fixed settings screen flickering on plugin status and model ready state"
        )
    ),
    ChangelogEntry(
        version = "v4.1.0",
        date = "July 2026",
        items = listOf(
            "✨ Integrated on-device proofreading & AI suggestions",
            "✨ Text Expander and shortcut expansion system",
            "✨ Handwrite and Translation plugin management hubs",
            "🎨 Modernized Compose settings hierarchy with card groupings",
            "🐛 Fixed clipboard preview clipping and one-handed toolbar alignment"
        )
    ),
    ChangelogEntry(
        version = "v4.0.0",
        date = "June 2026",
        items = listOf(
            "✨ Complete UI overhaul with Material 3 expressive dynamic theming",
            "✨ Re-engineered gesture typing engine with improved multilingual accuracy",
            "✨ Custom AI keys with configurable system prompts and custom actions"
        )
    )
)

@Composable
fun UpdatesScreen(
    onClickBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.prefs()

    val hasInternetAccess = BuildConfig.FLAVOR == "standard" || BuildConfig.FLAVOR == "standardfull"

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateCheckResult by remember { mutableStateOf<String?>(null) }
    var latestVersionTag by remember { mutableStateOf<String?>(null) }
    var isUpdateAvailable by remember { mutableStateOf(false) }

    fun checkForUpdates() {
        if (!hasInternetAccess) {
            val intent = Intent(Intent.ACTION_VIEW, Links.GITHUB_RELEASES_PAGE.toUri())
            context.startActivity(intent)
            return
        }

        isCheckingUpdates = true
        updateCheckResult = null
        scope.launch(Dispatchers.IO) {
            try {
                val url = URL(Links.GITHUB_RELEASES_API)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 7000
                conn.readTimeout = 7000
                conn.setRequestProperty("User-Agent", "LeanType-Android")
                conn.connect()

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val regex = "\"tag_name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    val match = regex.find(response)
                    val tag = match?.groupValues?.get(1)?.trim() ?: ""

                    withContext(Dispatchers.Main) {
                        latestVersionTag = tag
                        val cleanCurrent = BuildConfig.VERSION_NAME.removePrefix("v").trim()
                        val cleanRemote = tag.removePrefix("v").trim()

                        if (cleanRemote.isNotBlank() && isNewerVersion(cleanCurrent, cleanRemote)) {
                            isUpdateAvailable = true
                            updateCheckResult = context.getString(R.string.updates_available, tag)
                        } else {
                            isUpdateAvailable = false
                            updateCheckResult = context.getString(R.string.updates_up_to_date)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        updateCheckResult = "Could not check for updates (HTTP ${conn.responseCode})"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateCheckResult = "Network error while checking updates"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isCheckingUpdates = false
                }
            }
        }
    }

    val autoCheckSetting = remember {
        Setting(
            key = "pref_auto_check_updates",
            title = context.getString(R.string.updates_auto_check_title),
            description = context.getString(R.string.updates_auto_check_summary)
        ) {
            SwitchPreference(
                setting = it,
                default = true
            )
        }
    }

    val frequencySetting = remember {
        Setting(
            key = "pref_auto_check_updates_frequency",
            title = context.getString(R.string.updates_frequency_title)
        ) {
            ListPreference(
                setting = it,
                items = listOf(
                    "Daily" to "1",
                    "Weekly" to "7",
                    "Monthly" to "30"
                ),
                default = "7"
            )
        }
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_updates),
        settings = emptyList()
    ) {
        Scaffold(contentWindowInsets = WindowInsets(0)) { innerPadding ->
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(vertical = 8.dp)
            ) {
                // Section 1: App Updates
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        val currentVersionText = "Installed: v${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})"
                        val checkDescription = when {
                            isCheckingUpdates -> stringResource(R.string.updates_checking)
                            updateCheckResult != null -> updateCheckResult!!
                            else -> currentVersionText
                        }

                        Preference(
                            name = stringResource(R.string.updates_check_title),
                            description = checkDescription,
                            icon = R.drawable.ic_settings_updates,
                            onClick = {
                                if (isUpdateAvailable) {
                                    val intent = Intent(Intent.ACTION_VIEW, Links.GITHUB_RELEASES_PAGE.toUri())
                                    context.startActivity(intent)
                                } else {
                                    checkForUpdates()
                                }
                            },
                            value = {
                                if (isUpdateAvailable) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "Update",
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                } else if (updateCheckResult != null && !isCheckingUpdates) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "Up to date",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        )

                        if (hasInternetAccess) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            autoCheckSetting.Preference()

                            val isAutoCheckEnabled = prefs.getBoolean("pref_auto_check_updates", true)
                            if (isAutoCheckEnabled) {
                                frequencySetting.Preference()
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.updates_offline_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Section 2: Changelog
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.updates_changelog_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        for (entry in changelogEntries) {
                            ChangelogCard(entry = entry)
                            if (entry != changelogEntries.last()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // Section 3: Support & Sponsorship
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = stringResource(R.string.updates_support_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.updates_support_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Preference(
                            name = stringResource(R.string.updates_github_sponsor_title),
                            description = stringResource(R.string.updates_github_sponsor_desc),
                            icon = R.drawable.ic_settings_about_github,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Links.SPONSOR.toUri())
                                context.startActivity(intent)
                            }
                        )

                        Preference(
                            name = stringResource(R.string.updates_opencollective_title),
                            description = stringResource(R.string.updates_opencollective_desc),
                            icon = R.drawable.ic_settings_default,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Links.OPEN_COLLECTIVE.toUri())
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangelogCard(entry: ChangelogEntry) {
    var expanded by remember { mutableStateOf(entry == changelogEntries.first()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = entry.version,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = " • ${entry.date}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    for (item in entry.items) {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.25f,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun isNewerVersion(current: String, remote: String): Boolean {
    val currentParts = current.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
    val remoteParts = remote.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
    val maxLen = maxOf(currentParts.size, remoteParts.size)
    for (i in 0 until maxLen) {
        val c = currentParts.getOrElse(i) { 0 }
        val r = remoteParts.getOrElse(i) { 0 }
        if (r > c) return true
        if (r < c) return false
    }
    return false
}
