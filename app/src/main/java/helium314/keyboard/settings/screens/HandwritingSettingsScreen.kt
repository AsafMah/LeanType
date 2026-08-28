// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.handwriting.HandwritingLoader
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.HandwritingModelDownloadDialog
import helium314.keyboard.settings.preferences.HandwritingLanguagePreference
import helium314.keyboard.settings.preferences.LoadHandwritingPluginPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory

@Composable
fun HandwritingSettingsScreen(
    onClickBack: () -> Unit,
) {
    val context = LocalContext.current
    var handwritingInstalled by remember { mutableStateOf(HandwritingLoader.hasPlugin(context)) }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.libraries_hub_handwriting_title),
        settings = emptyList()
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
        ) { innerPadding ->
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(vertical = 8.dp)
            ) {
                // Plugin Management Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory("Plugin Management")

                        LoadHandwritingPluginPreference(
                            title = "Handwriting Plugin",
                            summary = if (handwritingInstalled) stringResource(R.string.libraries_status_active) else stringResource(R.string.libraries_status_not_installed),
                            icon = R.drawable.ic_edit,
                            onSuccess = { handwritingInstalled = HandwritingLoader.hasPlugin(context) }
                        )
                    }
                }

                // Configuration Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory("Configuration")

                        HandwritingLanguagePreference()
                    }
                }

                // Offline Recognition Models Card
                if (handwritingInstalled) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column {
                            PreferenceCategory("Offline Models")

                            var showModelsDialog by remember { mutableStateOf(false) }
                            Preference(
                                name = "Handwriting Models",
                                description = "Download and manage offline recognition models",
                                onClick = { showModelsDialog = true },
                                icon = R.drawable.ic_settings_languages
                            )
                            if (showModelsDialog) {
                                HandwritingModelDownloadDialog(
                                    onDismissRequest = { showModelsDialog = false },
                                    onModelChanged = {
                                        HandwritingLoader.resetRecognizer()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
