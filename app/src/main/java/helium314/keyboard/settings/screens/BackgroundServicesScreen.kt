// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.BackButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundServicesScreen(
    onClickBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.prefs() }

    var spellCheckerEnabled by remember {
        mutableStateOf(prefs.getBoolean(Settings.PREF_ENABLE_SPELL_CHECKER_SERVICE, Defaults.PREF_ENABLE_SPELL_CHECKER_SERVICE))
    }
    var contactsEnabled by remember {
        mutableStateOf(prefs.getBoolean(Settings.PREF_USE_CONTACTS, Defaults.PREF_USE_CONTACTS))
    }
    var clipboardEnabled by remember {
        mutableStateOf(prefs.getBoolean(Settings.PREF_ENABLE_CLIPBOARD_LISTENER, Defaults.PREF_ENABLE_CLIPBOARD_LISTENER))
    }
    var smsOtpEnabled by remember {
        mutableStateOf(prefs.getBoolean(Settings.PREF_AUTO_READ_OTP, Defaults.PREF_AUTO_READ_OTP))
    }
    var appSyncEnabled by remember {
        mutableStateOf(prefs.getBoolean(Settings.PREF_USE_APPS, Defaults.PREF_USE_APPS))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Background Services") },
                navigationIcon = { BackButton(onClickBack) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Manage background listeners and memory locks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1. Spell Checker Service
            CompactServiceCard(
                title = "Spell Checker Service",
                description = "System spellchecker & dictionary cache.",
                status = if (spellCheckerEnabled) "ACTIVE" else "DISABLED",
                enabled = spellCheckerEnabled,
                onToggle = { enabled ->
                    spellCheckerEnabled = enabled
                    prefs.edit().putBoolean(Settings.PREF_ENABLE_SPELL_CHECKER_SERVICE, enabled).apply()
                },
                onStopClicked = {
                    spellCheckerEnabled = false
                    prefs.edit().putBoolean(Settings.PREF_ENABLE_SPELL_CHECKER_SERVICE, false).apply()
                    Toast.makeText(context, "Spell Checker stopped & memory flushed", Toast.LENGTH_SHORT).show()
                }
            )

            // 2. Contacts Observer
            CompactServiceCard(
                title = "Contacts Observer",
                description = "Monitors contact changes for name suggestions.",
                status = if (contactsEnabled) "LISTENING" else "DISABLED",
                enabled = contactsEnabled,
                onToggle = { enabled ->
                    contactsEnabled = enabled
                    prefs.edit().putBoolean(Settings.PREF_USE_CONTACTS, enabled).apply()
                },
                onStopClicked = {
                    contactsEnabled = false
                    prefs.edit().putBoolean(Settings.PREF_USE_CONTACTS, false).apply()
                    Toast.makeText(context, "Contacts observer stopped & unregistered", Toast.LENGTH_SHORT).show()
                }
            )

            // 3. Clipboard History Listener
            CompactServiceCard(
                title = "Clipboard Listener",
                description = "Listens to system primary clip changes.",
                status = if (clipboardEnabled) "LISTENING" else "DISABLED",
                enabled = clipboardEnabled,
                onToggle = { enabled ->
                    clipboardEnabled = enabled
                    prefs.edit().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_LISTENER, enabled).apply()
                },
                onStopClicked = {
                    clipboardEnabled = false
                    prefs.edit().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_LISTENER, false).apply()
                    Toast.makeText(context, "Clipboard listener stopped", Toast.LENGTH_SHORT).show()
                }
            )

            // 4. SMS OTP Receiver
            CompactServiceCard(
                title = "SMS OTP Reader",
                description = "Reads SMS to suggest OTP passcodes.",
                status = if (smsOtpEnabled) "READY" else "DISABLED",
                enabled = smsOtpEnabled,
                onToggle = { enabled ->
                    smsOtpEnabled = enabled
                    prefs.edit().putBoolean(Settings.PREF_AUTO_READ_OTP, enabled).apply()
                },
                onStopClicked = {
                    smsOtpEnabled = false
                    prefs.edit().putBoolean(Settings.PREF_AUTO_READ_OTP, false).apply()
                    Toast.makeText(context, "SMS Receiver stopped & unregistered", Toast.LENGTH_SHORT).show()
                }
            )

            // 5. App Name Launcher Sync
            CompactServiceCard(
                title = "App Launcher Sync",
                description = "Monitors app installs for app name suggestions.",
                status = if (appSyncEnabled) "LISTENING" else "DISABLED",
                enabled = appSyncEnabled,
                onToggle = { enabled ->
                    appSyncEnabled = enabled
                    prefs.edit().putBoolean(Settings.PREF_USE_APPS, enabled).apply()
                },
                onStopClicked = {
                    appSyncEnabled = false
                    prefs.edit().putBoolean(Settings.PREF_USE_APPS, false).apply()
                    Toast.makeText(context, "App sync listener stopped", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun CompactServiceCard(
    title: String,
    description: String,
    status: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onStopClicked: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = " • $status",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (enabled) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onStopClicked,
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text("Stop & Free Memory", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
